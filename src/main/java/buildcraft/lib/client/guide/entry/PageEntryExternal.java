package buildcraft.lib.client.guide.entry;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.BCLog;
import buildcraft.api.registry.IScriptableRegistry.OptionallyDisabled;

import buildcraft.lib.gui.GuiStack;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.misc.RegistryUtilBC;

/** An external page type that doesn't correspond to any object in the game. Used for detailing configs, resource packs,
 * model files, etc.
 *
 * <p>Having no object, an external entry has nothing to take an icon from, so it draws none unless its JSON carries
 * the optional {@code icon} field: a one-member object whose key is a <em>kind</em> of icon and whose value is that
 * kind's string argument, e.g. {@code "icon": {"item": "minecraft:diamond"}}. {@code item} is built in; a subsystem
 * adds kinds with {@link #registerIconKind} (robotics adds {@code board}, a programmed Redstone Board, which no plain
 * item id can name), so lib needs no knowledge of them. The icon is resolved when the line is built, never when the
 * entry is read, so a kind may depend on registries that are only complete by then; one that cannot be resolved just
 * leaves the line without an icon. */
public class PageEntryExternal extends PageValueType<String> {

    /** The JSON member holding an entry's optional icon. */
    public static final String ICON_FIELD = "icon";

    private static final Map<String, Function<String, ItemStack>> ICON_KINDS = new ConcurrentHashMap<>();

    public static final PageEntryExternal INSTANCE = new PageEntryExternal();

    static {
        registerIconKind("item", PageEntryExternal::itemIcon);
    }

    /** Adds a kind of {@code icon} an external entry may name. Call during client init, before any guide page loads.
     *  @param resolver Maps the entry's argument string to the stack to draw; an empty (or null) stack, or a throw,
     *                  means "no icon". */
    public static void registerIconKind(String kind, Function<String, ItemStack> resolver) {
        ICON_KINDS.put(kind, resolver);
    }

    private static ItemStack itemIcon(String id) {
        Identifier location = Identifier.tryParse(id);
        Item item = location == null ? null : RegistryUtilBC.getValue(BuiltInRegistries.ITEM, location);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** An external entry's icon: which kind, and the argument it is given. */
    private record IconSpec(String kind, String argument) {}

    /** The {@code icon} member as a spec, or an {@link OptionallyDisabled} reason when it is not a one-member
     *  {@code {kind: "argument"}} object. */
    private static OptionallyDisabled<IconSpec> parseIcon(Identifier name, JsonElement icon) {
        if (icon.isJsonObject() && icon.getAsJsonObject().size() == 1) {
            Map.Entry<String, JsonElement> only = icon.getAsJsonObject().entrySet().iterator().next();
            JsonElement argument = only.getValue();
            if (argument.isJsonPrimitive() && argument.getAsJsonPrimitive().isString()) {
                return new OptionallyDisabled<>(new IconSpec(only.getKey(), argument.getAsString()));
            }
        }
        return new OptionallyDisabled<>("Entry '" + name + "' has a malformed '" + ICON_FIELD + "' (expected a "
            + "one-member object such as {\"item\": \"minecraft:stone\"}): " + icon);
    }

    /** An external entry that draws its {@code icon}. */
    private static final class IconEntry extends PageEntry<String> {
        private final Identifier name;
        private final IconSpec icon;
        private volatile boolean warned;

        IconEntry(PageEntryExternal type, Identifier name, JsonObject json, String value, IconSpec icon) {
            super(type, name, json, value);
            this.name = name;
            this.icon = icon;
        }

        @Override
        @Nullable
        public ISimpleDrawable createDrawable() {
            ItemStack stack = ItemStack.EMPTY;
            Function<String, ItemStack> resolver = ICON_KINDS.get(icon.kind());
            if (resolver != null) {
                try {
                    ItemStack resolved = resolver.apply(icon.argument());
                    stack = resolved == null ? ItemStack.EMPTY : resolved;
                } catch (RuntimeException e) {
                    BCLog.logger.warn("[lib.guide] The '" + icon.kind() + "' icon of " + name + " threw.", e);
                }
            }
            if (stack.isEmpty()) {
                if (!warned) {
                    warned = true;
                    BCLog.logger.warn("[lib.guide] No icon for " + name + ": '" + icon.kind() + "' "
                        + (resolver == null ? "is not a registered icon kind" : "gave no stack for '"
                        + icon.argument() + "'"));
                }
                return null;
            }
            return new GuiStack(stack);
        }
    }

    @Override
    public OptionallyDisabled<PageEntry<String>> deserialize(Identifier name, JsonObject json,
        JsonDeserializationContext ctx) {
        String value = PageValue.getTitle(json);
        if (!json.has(ICON_FIELD)) {
            return new OptionallyDisabled<>(new PageEntry<>(this, name, json, value));
        }
        OptionallyDisabled<IconSpec> icon = parseIcon(name, json.get(ICON_FIELD));
        if (!icon.isPresent()) {
            return new OptionallyDisabled<>(icon.getDisabledReason());
        }
        return new OptionallyDisabled<>(new IconEntry(this, name, json, value, icon.get()));
    }

    @Override
    public Class<String> getEntryClass() {
        return String.class;
    }

    @Override
    public List<String> getTooltip(String value) {
        return Collections.singletonList(value);
    }

    @Override
    @Nullable
    public ISimpleDrawable createDrawable(String value) {
        return null;
    }

    @Override
    public String getTitle(String value) {
        return value;
    }

    @Override
    public void iterateAllDefault(IEntryLinkConsumer consumer, ProfilerFiller prof) {
        // NO-OP: everything is provided as-is.
    }
}
