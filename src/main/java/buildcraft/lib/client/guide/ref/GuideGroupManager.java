package buildcraft.lib.client.guide.ref;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.statements.IStatement;

import buildcraft.lib.client.guide.entry.FluidStackValueFilter;
import buildcraft.lib.client.guide.entry.ItemStackValueFilter;
import buildcraft.lib.client.guide.entry.PageEntryFluidStack;
import buildcraft.lib.client.guide.entry.PageEntryItemStack;
import buildcraft.lib.client.guide.entry.PageEntryStatement;
import buildcraft.lib.client.guide.entry.PageValue;
import buildcraft.lib.client.guide.entry.PageValueType;

public class GuideGroupManager {
    public static final List<PageValueType<?>> knownTypes = new ArrayList<>();
    public static final Map<Identifier, GuideGroupSet> sets = new HashMap<>();

    private static final Map<Class<?>, PageValueType<?>> knownClasses = new WeakHashMap<>();
    private static final Map<Class<?>, Function<Object, PageValue<?>>> transformers = new WeakHashMap<>();

    static {
        addValidClass(ItemStackValueFilter.class, PageEntryItemStack.INSTANCE);
        addValidClass(FluidStackValueFilter.class, PageEntryFluidStack.INSTANCE);
        addValidClass(IStatement.class, PageEntryStatement.INSTANCE);
        addTransformer(ItemStack.class, ItemStackValueFilter.class, ItemStackValueFilter::new);
        addTransformer(Item.class, ItemStack.class, ItemStack::new);
        addTransformer(Block.class, ItemStack.class, ItemStack::new);
        addTransformer(FluidStack.class, FluidStackValueFilter.class, FluidStackValueFilter::new);
        addTransformer(Fluid.class, FluidStack.class, fluid -> new FluidStack(fluid, 1));

        // Group population happens lazily in populateDefaultGroups(), called from
        // GuideManager.reload0() — by which time BC modules and registries are loaded.
    }

    public static <F, T> void addTransformer(Class<F> fromClass, Class<T> toClass, Function<F, T> transform) {
        if (isValidClass(fromClass)) {
            throw new IllegalArgumentException("You cannot register a transformer from an already-registered class!");
        }
        PageValueType<?> destType = getEntryType(toClass);
        if (destType == null) {
            Function<Object, PageValue<?>> destTransform = getTransform(toClass);
            if (destTransform != null) {
                Function<Object, PageValue<?>> realTransform = o -> {
                    F from = fromClass.cast(o);
                    T to = transform.apply(from);
                    return destTransform.apply(to);
                };
                transformers.put(fromClass, realTransform);
                return;
            }
            throw new IllegalArgumentException("You cannot register a transformer to an unregistered class!");
        }
        Function<Object, PageValue<?>> realTransform = o -> {
            F from = fromClass.cast(o);
            T to = transform.apply(from);
            return destType.wrap(to);
        };
        transformers.put(fromClass, realTransform);
    }

    public static <T> void addValidClass(Class<T> clazz, PageValueType<T> type) {
        if (clazz.isArray()) {
            throw new IllegalArgumentException("Arrays are never valid!");
        }
        knownClasses.put(clazz, type);
        knownTypes.add(type);
    }

    static boolean isValidObject(Object value) {
        if (value == null) return false;
        return isValidClass(value.getClass());
    }

    public static PageValue<?> toPageValue(Object value) {
        if (value == null) return null;
        if (value instanceof PageValue) return (PageValue<?>) value;
        PageValueType<?> entryType = getEntryType(value.getClass());
        if (entryType != null) return entryType.wrap(value);
        Function<Object, PageValue<?>> transform = getTransform(value.getClass());
        if (transform != null) return transform.apply(value);
        throw new IllegalArgumentException("Unknown " + value.getClass()
            + " - is this a programming mistake, or have you forgotten to register the class as valid?");
    }

    private static boolean isValidClass(Class<?> clazz) {
        return getEntryType(clazz) != null;
    }

    @Nullable
    private static PageValueType<?> getEntryType(Class<?> clazz) {
        if (knownClasses.containsKey(clazz)) return knownClasses.get(clazz);
        PageValueType<?> type = null;
        if (!clazz.isArray()) {
            search: {
                Class<?> superClazz = clazz.getSuperclass();
                if (superClazz != null) {
                    type = getEntryType(superClazz);
                    if (type != null) break search;
                }
                for (Class<?> cls : clazz.getInterfaces()) {
                    type = getEntryType(cls);
                    if (type != null) break search;
                }
            }
            knownClasses.put(clazz, type);
        }
        return type;
    }

    private static Function<Object, PageValue<?>> getTransform(Class<? extends Object> clazz) {
        Function<Object, PageValue<?>> func = transformers.get(clazz);
        if (func != null) return func;
        if (!clazz.isArray()) {
            search: {
                Class<?> superClazz = clazz.getSuperclass();
                if (superClazz != null) {
                    func = getTransform(superClazz);
                    if (func != null) break search;
                }
                for (Class<?> cls : clazz.getInterfaces()) {
                    func = getTransform(cls);
                    if (func != null) break search;
                }
            }
            transformers.put(clazz, func);
        }
        return func;
    }

    @Nullable
    public static GuideGroupSet get(Identifier group) {
        return sets.get(group);
    }

    @Nullable
    public static GuideGroupSet get(String domain, String group) {
        return get(Identifier.fromNamespaceAndPath(domain, group));
    }

    public static GuideGroupSet getOrCreate(String domain, String group) {
        return sets.computeIfAbsent(Identifier.fromNamespaceAndPath(domain, group), GuideGroupSet::new);
    }

    public static GuideGroupSet addEntry(String domain, String group, Object value) {
        return getOrCreate(domain, group).addSingle(value);
    }

    public static GuideGroupSet addEntries(String domain, String group, Object... values) {
        return getOrCreate(domain, group).addArray(values);
    }

    public static GuideGroupSet addEntries(String domain, String group, Collection<Object> values) {
        return getOrCreate(domain, group).addCollection(values);
    }

    public static GuideGroupSet addKey(String domain, String group, Object value) {
        return getOrCreate(domain, group).addKey(value);
    }

    public static GuideGroupSet addKeys(String domain, String group, Object... values) {
        return getOrCreate(domain, group).addKeyArray(values);
    }

    public static GuideGroupSet addKeys(String domain, String group, Collection<Object> values) {
        return getOrCreate(domain, group).addKeyCollection(values);
    }

    /** One-shot population of all guide groups, called from {@link
     *  buildcraft.lib.client.guide.GuideManager#reload(net.minecraft.server.packs.resources.ResourceManager)}.
     *  Clears existing groups (re-entry safe under {@code /reload} or F3+T) then registers
     *  the four conceptual groups carried over from 1.12.2 plus auto-derived groups
     *  populated from the fuel/coolant/refinery registries. */
    public static void populateDefaultGroups() {
        sets.clear();

        // ---- Hardcoded conceptual groups (1.12.2 verbatim, retargeted to 26.1.x constants) ----

        // Pipe-power providers: things that emit MJ into a wood/diamond-wood/emzuli pipe.
        addEntries("buildcraft", "pipe_power_providers",
            buildcraft.silicon.BCSiliconItems.PLUG_PULSAR.get(),
            buildcraft.transport.BCTransportItems.PLUG_POWER_ADAPTOR.get(),
            buildcraft.core.BCCoreItems.ENGINE_REDSTONE.get(),
            buildcraft.energy.BCEnergyItems.ENGINE_STONE.get(),
            buildcraft.energy.BCEnergyItems.ENGINE_IRON.get());
        addKeys("buildcraft", "pipe_power_providers",
            buildcraft.transport.BCTransportItems.PIPE_WOOD_ITEM.get(),
            buildcraft.transport.BCTransportItems.PIPE_DIAMOND_WOOD_ITEM.get(),
            buildcraft.transport.BCTransportItems.PIPE_EMZULI_ITEM.get(),
            buildcraft.transport.BCTransportItems.PIPE_WOOD_FLUID.get(),
            buildcraft.transport.BCTransportItems.PIPE_DIAMOND_WOOD_FLUID.get());

        // Full-power providers: machines that accept MJ via direct contact (not pipes).
        addEntries("buildcraft", "full_power_providers",
            buildcraft.energy.BCEnergyItems.ENGINE_STONE.get(),
            buildcraft.energy.BCEnergyItems.ENGINE_IRON.get());
        addKeys("buildcraft", "full_power_providers",
            buildcraft.builders.BCBuildersItems.BUILDER.get(),
            buildcraft.builders.BCBuildersItems.FILLER.get(),
            buildcraft.builders.BCBuildersItems.QUARRY.get(),
            buildcraft.factory.BCFactoryItems.DISTILLER.get(),
            buildcraft.factory.BCFactoryItems.MINING_WELL.get(),
            buildcraft.factory.BCFactoryItems.PUMP.get(),
            buildcraft.silicon.BCSiliconItems.LASER.get());

        // Laser-power providers: laser tables that the laser block beams power into.
        addEntries("buildcraft", "laser_power_providers",
            buildcraft.silicon.BCSiliconItems.LASER.get());
        addKeys("buildcraft", "laser_power_providers",
            buildcraft.silicon.BCSiliconItems.ADVANCED_CRAFTING_TABLE.get(),
            buildcraft.silicon.BCSiliconItems.ASSEMBLY_TABLE.get(),
            buildcraft.silicon.BCSiliconItems.INTEGRATION_TABLE.get());

        // Area markers: the quarry/architect/filler area-of-effect machines depend on these.
        addEntries("buildcraft", "area_markers",
            buildcraft.core.BCCoreItems.MARKER_VOLUME.get(),
            buildcraft.core.BCCoreItems.VOLUME_BOX.get());
        addKeys("buildcraft", "area_markers",
            buildcraft.builders.BCBuildersItems.QUARRY.get(),
            buildcraft.builders.BCBuildersItems.ARCHITECT.get(),
            buildcraft.builders.BCBuildersItems.FILLER.get());

        // ---- Auto-derived groups from registries ----

        // Combustion fuels: every registered IFuel becomes an entry, the iron (combustion)
        // engine is the consumer/key. Skip the empty-fluid sentinel.
        if (buildcraft.api.fuels.BuildcraftFuelRegistry.fuel != null) {
            for (buildcraft.api.fuels.IFuel fuel : buildcraft.api.fuels.BuildcraftFuelRegistry.fuel.getFuels()) {
                net.neoforged.neoforge.fluids.FluidStack fs = fuel.getFluid();
                if (fs == null || fs.isEmpty()) continue;
                addEntry("buildcraft", "combustion_fuels", fs);
            }
            addKey("buildcraft", "combustion_fuels",
                buildcraft.energy.BCEnergyItems.ENGINE_IRON.get());
        }

        // Coolants: both fluid and solid coolants flow into the combustion (iron) engine.
        // Solid coolants get listed alongside fluids in the same group.
        if (buildcraft.api.fuels.BuildcraftFuelRegistry.coolant != null) {
            for (buildcraft.api.fuels.ICoolant c : buildcraft.api.fuels.BuildcraftFuelRegistry.coolant.getCoolants()) {
                net.neoforged.neoforge.fluids.FluidStack fs = c.getRepresentativeFluid();
                if (fs == null || fs.isEmpty()) continue;
                addEntry("buildcraft", "coolants", fs);
            }
            for (buildcraft.api.fuels.ISolidCoolant sc : buildcraft.api.fuels.BuildcraftFuelRegistry.coolant.getSolidCoolants()) {
                net.minecraft.world.item.ItemStack stack = sc.getRepresentativeStack();
                if (stack == null || stack.isEmpty()) continue;
                addEntry("buildcraft", "coolants", stack);
            }
            addKey("buildcraft", "coolants",
                buildcraft.energy.BCEnergyItems.ENGINE_IRON.get());
        }

        // Distillation recipes: each input fluid links into the distiller, each output
        // links from it. The two are split into separate groups so the distiller's page
        // shows "Distillation Inputs" (entries it consumes) and "Distillation Outputs"
        // (entries it produces) under distinct headings.
        if (buildcraft.api.recipes.BuildcraftRecipeRegistry.refineryRecipes != null) {
            net.minecraft.world.item.Item distiller = buildcraft.factory.BCFactoryItems.DISTILLER.get();
            for (var recipe : buildcraft.api.recipes.BuildcraftRecipeRegistry.refineryRecipes
                .getDistillationRegistry().getAllRecipes()) {
                if (recipe.in() != null && !recipe.in().isEmpty()) {
                    addEntry("buildcraft", "distillation_inputs", recipe.in());
                }
                if (recipe.outGas() != null && !recipe.outGas().isEmpty()) {
                    addEntry("buildcraft", "distillation_outputs", recipe.outGas());
                }
                if (recipe.outLiquid() != null && !recipe.outLiquid().isEmpty()) {
                    addEntry("buildcraft", "distillation_outputs", recipe.outLiquid());
                }
            }
            addKey("buildcraft", "distillation_inputs", distiller);
            addKey("buildcraft", "distillation_outputs", distiller);
        }

        // Filler patterns: every IFillerPattern (in the order it shows up in the filler's
        // GUI, NOT alphabetical) becomes an entry; the filler block is the consumer/key.
        // The "Filler Patterns" category entry in the TOC opens a page that lists this
        // group, so individual patterns are reachable from there instead of cluttering
        // the top-level Actions chapter with N alphabetical leaves. Keep the order in
        // sync with BCBuildersStatements.PATTERNS — that array is the GUI's own source.
        addEntries("buildcraft", "filler_patterns",
            (Object[]) buildcraft.builders.BCBuildersStatements.PATTERNS);
        addKey("buildcraft", "filler_patterns",
            buildcraft.builders.BCBuildersItems.FILLER.get());

        // Emzuli extraction presets: the four colour-keyed extraction-preset actions, in
        // SlotIndex order (RED → GREEN → BLUE → YELLOW). Like filler patterns, surfaced
        // via a single "Emzuli Extraction Presets" category entry in the TOC, with the
        // emzuli pipe as the consumer/key so its page auto-emits a Linked-To listing the
        // presets — and the presets' pages auto-emit a Linked-From back to the pipe.
        addEntries("buildcraft", "extraction_presets",
            (Object[]) buildcraft.transport.BCTransportStatements.ACTION_EXTRACTION_PRESET);
        addKey("buildcraft", "extraction_presets",
            buildcraft.transport.BCTransportItems.PIPE_EMZULI_ITEM.get());

        // Heat exchanger recipes: heatable inputs/outputs and coolable inputs/outputs both
        // funnel through the heat exchange block.
        if (buildcraft.api.recipes.BuildcraftRecipeRegistry.refineryRecipes != null) {
            net.minecraft.world.item.Item heatExchange = buildcraft.factory.BCFactoryItems.HEAT_EXCHANGE.get();
            for (var recipe : buildcraft.api.recipes.BuildcraftRecipeRegistry.refineryRecipes
                .getHeatableRegistry().getAllRecipes()) {
                if (recipe.in() != null && !recipe.in().isEmpty()) {
                    addEntry("buildcraft", "heat_exchange_inputs", recipe.in());
                }
                if (recipe.out() != null && !recipe.out().isEmpty()) {
                    addEntry("buildcraft", "heat_exchange_outputs", recipe.out());
                }
            }
            for (var recipe : buildcraft.api.recipes.BuildcraftRecipeRegistry.refineryRecipes
                .getCoolableRegistry().getAllRecipes()) {
                if (recipe.in() != null && !recipe.in().isEmpty()) {
                    addEntry("buildcraft", "heat_exchange_inputs", recipe.in());
                }
                if (recipe.out() != null && !recipe.out().isEmpty()) {
                    addEntry("buildcraft", "heat_exchange_outputs", recipe.out());
                }
            }
            addKey("buildcraft", "heat_exchange_inputs", heatExchange);
            addKey("buildcraft", "heat_exchange_outputs", heatExchange);
        }

        int totalEntries = 0;
        for (GuideGroupSet set : sets.values()) {
            totalEntries += set.entries.size() + set.sources.size();
        }
        buildcraft.api.core.BCLog.logger.info(
            "[lib.guide] Populated " + sets.size() + " guide groups with " + totalEntries
                + " total members.");
    }

    /** Appends a "Linked To" / "Linked From" pair of chapters to {@code parts} for any
     *  group that contains {@code wrapped}. This is the auto-derivation hook called from
     *  each {@link PageValueType}'s {@code addPageEntries} override.
     *
     *  <p>Algorithm (faithful to 1.12.2): for each {@link GuideGroupSet} —
     *  <ul>
     *    <li>If {@code wrapped} is in the set's {@code sources}, this entry is a "key/source" and
     *        the set's {@code entries} are what it links TO. Append a {@link buildcraft.lib.client.guide.parts.GuidePartGroup}
     *        with {@link GuideGroupSet.GroupDirection#SRC_TO_ENTRY}.</li>
     *    <li>If {@code wrapped} is in the set's {@code entries}, this entry is a "value/target" and
     *        the set's {@code sources} link FROM it. Append with {@link GuideGroupSet.GroupDirection#ENTRY_TO_SRC}.</li>
     *  </ul>
     *  Then de-duplicate against any {@code <group>} tag the markdown already declared, and emit
     *  {@code GuideChapterWithin} headings labelled "Linked To" / "Linked From" before the
     *  respective groups, each followed by a forced page break. */
    public static void appendLinkedChapters(@Nullable PageValue<?> wrapped,
        buildcraft.lib.client.guide.GuiGuide gui,
        List<buildcraft.lib.client.guide.parts.GuidePart> parts) {
        if (wrapped == null) return;

        List<buildcraft.lib.client.guide.parts.GuidePartGroup> linksToOther = new ArrayList<>();
        List<buildcraft.lib.client.guide.parts.GuidePartGroup> linksToThis = new ArrayList<>();

        for (GuideGroupSet set : sets.values()) {
            if (containsValue(set.sources, wrapped)) {
                linksToOther.add(new buildcraft.lib.client.guide.parts.GuidePartGroup(
                    gui, set, GuideGroupSet.GroupDirection.SRC_TO_ENTRY));
            } else if (containsValue(set.entries, wrapped)) {
                linksToThis.add(new buildcraft.lib.client.guide.parts.GuidePartGroup(
                    gui, set, GuideGroupSet.GroupDirection.ENTRY_TO_SRC));
            }
        }

        // De-dup against <group> tags already declared in the markdown.
        for (buildcraft.lib.client.guide.parts.GuidePart p : parts) {
            if (p instanceof buildcraft.lib.client.guide.parts.GuidePartGroup g) {
                linksToOther.removeIf(x -> x.group == g.group);
                linksToThis.removeIf(x -> x.group == g.group);
            }
        }

        if (!linksToOther.isEmpty()) {
            parts.add(new buildcraft.lib.client.guide.parts.GuideChapterWithin(gui,
                buildcraft.lib.misc.LocaleUtil.localize("buildcraft.guide.meta.group.linking_to")));
            for (buildcraft.lib.client.guide.parts.GuidePartGroup g : linksToOther) {
                parts.add(g);
                parts.add(new buildcraft.lib.client.guide.parts.GuidePartNewPage(gui,
                    buildcraft.lib.client.guide.loader.XmlPageLoader.RECIPE_BREAK_THRESHOLD));
            }
        }
        if (!linksToThis.isEmpty()) {
            parts.add(new buildcraft.lib.client.guide.parts.GuideChapterWithin(gui,
                buildcraft.lib.misc.LocaleUtil.localize("buildcraft.guide.meta.group.linked_from")));
            for (buildcraft.lib.client.guide.parts.GuidePartGroup g : linksToThis) {
                parts.add(g);
                parts.add(new buildcraft.lib.client.guide.parts.GuidePartNewPage(gui,
                    buildcraft.lib.client.guide.loader.XmlPageLoader.RECIPE_BREAK_THRESHOLD));
            }
        }
    }

    /** Linear search using the type's {@link PageValueType#matches} contract instead of
     *  {@link Object#equals}. {@code matches} captures stack-vs-stack/item/fluid identity
     *  semantics that {@code equals} on {@link ItemStackValueFilter} doesn't, and was the
     *  lookup mechanism 1.12.2 used. */
    private static boolean containsValue(List<PageValue<?>> list, PageValue<?> wrapped) {
        if (wrapped == null) return false;
        for (PageValue<?> pv : list) {
            if (pv == wrapped) return true;
            if (pv != null && pv.matches(wrapped.value)) return true;
        }
        return false;
    }
}
