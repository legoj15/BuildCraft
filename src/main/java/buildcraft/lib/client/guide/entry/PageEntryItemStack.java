package buildcraft.lib.client.guide.entry;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.core.registries.BuiltInRegistries;

import buildcraft.api.registry.IScriptableRegistry.OptionallyDisabled;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.GuideManager;
import buildcraft.lib.client.guide.data.JsonTypeTags;
import buildcraft.lib.client.guide.loader.MarkdownPageLoader;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.client.guide.parts.contents.PageLinkItemStack;
import buildcraft.lib.client.guide.ref.GuideGroupManager;
import buildcraft.lib.gui.GuiStack;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.misc.ItemStackKey;

/** Guide page entry type for items/item stacks. Handles deserialisation from JSON,
 * matching logic, and icon rendering via {@link GuiStack}. */
public class PageEntryItemStack extends PageValueType<ItemStackValueFilter> {

    public static final PageEntryItemStack INSTANCE = new PageEntryItemStack();
    private static final JsonTypeTags TAGS = new JsonTypeTags("buildcraft.guide.contents.item_stacks");

    @Override
    public Class<ItemStackValueFilter> getEntryClass() {
        return ItemStackValueFilter.class;
    }

    @Override
    protected boolean isValid(ItemStackValueFilter typed) {
        return !typed.stack.baseStack.isEmpty();
    }

    /** Tag used to file vanilla / non-guide items under a search-only chapter. The
     *  chapter is invisible by default (every emitted link uses startVisible=false,
     *  so {@link buildcraft.lib.client.guide.parts.contents.ContentsNode#isVisible}
     *  returns false until a search match flips at least one child visible) and
     *  surfaces under "Other Items" only while a search box query is active. */
    private static final JsonTypeTags OTHER_ITEMS_TAGS =
        new JsonTypeTags("buildcraft.guide.contents.other_items");

    @Override
    public void iterateAllDefault(IEntryLinkConsumer consumer, ProfilerFiller prof) {
        // 1.12.2 parity: every registered item is searchable, even ones without a BC
        // markdown page. Click resolution falls through to GuideManager#getPageFor's
        // GuidePageStandInRecipes synthesis so the result is always openable.
        prof.push("iterate_all_items");
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) continue;
            // Dedup against guide-defined entries (matched JSON loop adds the Item
            // instance) so BC items keep their authored chapter placement.
            if (!GuideManager.INSTANCE.objectsAdded.add(item)) continue;
            String displayName = stack.getHoverName().getString();
            if (displayName == null || displayName.trim().isEmpty()) continue;
            consumer.addChild(OTHER_ITEMS_TAGS, PageLinkItemStack.create(false, stack, prof));
        }
        prof.pop();
    }

    @Override
    public OptionallyDisabled<PageEntry<ItemStackValueFilter>> deserialize(Identifier name, JsonObject json,
        JsonDeserializationContext ctx) {
        if (!json.has("stack")) {
            throw new JsonSyntaxException(
                "Expected either a string or an object for 'stack', but got nothing for " + json
            );
        }
        String str = json.get("stack").getAsString();
        // Strip parentheses wrapper: (mod:item) -> mod:item
        if (str.startsWith("(") && str.endsWith(")")) {
            str = str.substring(1, str.length() - 1);
        }
        // Strip legacy 1.12 curly-brace metadata format: {mod:item, meta, damage} -> mod:item
        if (str.startsWith("{") && str.endsWith("}")) {
            str = str.substring(1, str.length() - 1);
        }
        // Strip metadata values after comma (1.12 used meta/damage, 1.21 doesn't)
        if (str.contains(",")) {
            str = str.substring(0, str.indexOf(',')).trim();
        }
        str = str.trim();
        Identifier loc = Identifier.parse(str);
        Item item = BuiltInRegistries.ITEM.get(loc).map(ref -> ref.value()).orElse(null);
        if (item == null) {
            return new OptionallyDisabled<>("Unknown item '" + str + "' (from stack '" + json.get("stack").getAsString() + "')");
        }
        ItemStack stack = new ItemStack(item);
        ItemStackValueFilter filter = new ItemStackValueFilter(new ItemStackKey(stack), false, false);
        return new OptionallyDisabled<>(new PageEntry<>(this, name, json, filter));
    }

    @Override
    public String getTitle(ItemStackValueFilter value) {
        return value.stack.baseStack.getHoverName().getString();
    }

    @Override
    public List<String> getTooltip(ItemStackValueFilter value) {
        return Collections.singletonList(value.stack.baseStack.getHoverName().getString());
    }

    @Override
    public boolean matches(ItemStackValueFilter entry, Object obj) {
        if (obj instanceof ItemStackValueFilter) {
            obj = ((ItemStackValueFilter) obj).stack.baseStack;
        }
        if (obj instanceof ItemStackKey) {
            obj = ((ItemStackKey) obj).baseStack;
        }
        if (obj instanceof ItemStack) {
            ItemStack base = entry.stack.baseStack;
            ItemStack test = (ItemStack) obj;
            if (base.isEmpty() || test.isEmpty()) {
                return false;
            }
            return base.getItem() == test.getItem();
        }
        return false;
    }

    @Override
    @Nullable
    public ISimpleDrawable createDrawable(ItemStackValueFilter value) {
        return new GuiStack(value.stack.baseStack);
    }

    @Override
    public Object getBasicValue(ItemStackValueFilter value) {
        return value.stack.baseStack.getItem();
    }

    @Override
    public void addPageEntries(ItemStackValueFilter value, GuiGuide gui, List<GuidePart> parts) {
        GuideGroupManager.appendLinkedChapters(INSTANCE.wrap(value), gui, parts);
    }

    @Override
    public OptionallyDisabled<Object> createLink(String to, ProfilerFiller prof) {
        // Mirrors 1.12.2: parse the "to" string as an item identifier (e.g. "minecraft:iron_ingot"),
        // build a PageLinkItemStack from the resulting stack. Without this override, link tags
        // with type="item_stack" go through PageValueType.createLink's stub and render as red
        // failed-tag text.
        OptionallyDisabled<ItemStack> stackq = MarkdownPageLoader.parseItemStack(to);
        if (stackq.isPresent()) {
            return new OptionallyDisabled<>(PageLinkItemStack.create(true, stackq.get(), prof));
        }
        return new OptionallyDisabled<>(stackq.getDisabledReason());
    }
}
