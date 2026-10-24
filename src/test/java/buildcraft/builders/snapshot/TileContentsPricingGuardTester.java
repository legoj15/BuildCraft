/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
//? if >=1.21.10 {
import net.minecraft.util.ProblemReporter;
//?}
import net.minecraft.world.Clearable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.recipes.AssemblyRecipe;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.builders.BCBuilders;
import buildcraft.factory.tile.TileHeatExchange;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.RegistryUtilBC;
import buildcraft.lib.recipe.AssemblyRecipeRegistry;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.silicon.BCSiliconRecipes;
import buildcraft.silicon.EnumAssemblyRecipeState;
import buildcraft.silicon.tile.TileAssemblyTable;

/**
 * A BuildCraft block entity's contents are never rebuilt for free. {@link SchematicBlockDefault#contentsArePriced()}
 * is decided per block entity: if ANY extractor of ANY matching rule prices tile contents, the deny-by-default
 * {@link Clearable} wipe in {@code loadTileData} is skipped for the whole tile. A rule that names one inventory of a
 * tile but not a second (or names a save key the tile has since renamed) therefore rebuilds the unnamed one full for
 * the price of the empty block, with every other test green. This walks every block entity type BuildCraft registers
 * and checks it both ways:
 * <ul>
 *   <li><b>Priced tile</b> (some matching extractor {@link RequiredExtractor#pricesTileContents() prices contents}):
 *       every item stack and fluid the tile saves must sit at a key those extractors read, on the very tag the
 *       Architect would capture. That is checked by path, not by content: the stack's key is taken out of the tag,
 *       and the extractors must then price less of that item (or fluid) than before. A key that changes nothing is
 *       named by block, key path and content.</li>
 *   <li><b>Unpriced tile</b> (no rule, or rules that only price the block): loading the captured tag into a fresh tile
 *       and then clearing it, as {@code loadTileData} does, must leave no stack and no fluid behind.</li>
 * </ul>
 * Note that a fluid-only extractor also disables the item clear for its block ({@code RequiredExtractorTank} answers
 * {@code pricesTileContents()} true), which is why the item keys of a tank-priced tile (a distiller's container
 * slots) have to be named by its rule too: with the tank extractors alone, nothing prices them and nothing clears
 * them.
 * <p>
 * The tile is filled by reflection on its own direct fields: every droppable {@link ItemHandlerManager} handler, every
 * raw {@link ItemHandlerSimple} field, every {@link ItemStack} field, every {@link BCFluidTank} (and tank array), each
 * with a distinct item or fluid. A new holder of one of those kinds is covered the day it is added. A holder reached
 * only through a collection, a map or a nested object is not: the walk leaves it empty and it passes on an empty
 * save, unless {@code Filler.prepare} is taught it (the heat exchanger's sections and the assembly table's recipe
 * list are) or it saves as an item handler list, which the empty-handler check catches. Phantom handlers (a filter's
 * or a crafting grid's ghost slots) are configuration, not contents, and are neither filled nor priced; so are the
 * keys in {@link #NOT_CONTENTS}.
 * <p>
 * A pipe holder is checked empty: without a pipe in it there is no cargo to fill. No rule prices it
 * ({@code SchematicBlockPipe} prices only the pipe item and its pluggables), so its cargo is wiped by the
 * deny-by-default clear through {@code TilePipeHolder.clearContent} and {@code PipeFlow.clearCargo}. That wipe is not
 * exercised here; {@code CommandReplaceTester}'s clear-then-remove case runs it on a loaded item pipe and fluid pipe.
 */
public class TileContentsPricingGuardTester extends VanillaSetupBaseTester {

    /** The sorts of holder the filler reaches; {@link #theFillerReachesEveryKindOfHolder} fails if one goes unused. */
    private enum Kind {
        MANAGED_HANDLER, RAW_HANDLER, ITEM_FIELD, TANK
    }

    /** One block entity type with the block that places it. */
    private record Fixture(String name, BlockEntityType<?> type, Block block) {}

    /** Stack-shaped save keys that are not contents, by block entity type, each with why. Each must match something
     *  in its tile's filled save, or the entry is stale and fails. */
    private static final Map<String, Pattern> NOT_CONTENTS = Map.of(
        // The item a recipe selection is set to make, not one the table holds. Crafting it still takes the inputs
        // out of the priced input inventory (updateRecipes re-checks them every tick), so it rebuilds nothing.
        "buildcraftunofficial:assembly_table", Pattern.compile("recipes_states\\.entries\\[\\d+]\\.output"));

    /** One item stack or fluid found in a saved tag, at {@code path} (for messages) reached by {@code where} (the
     *  compound keys and list indices leading to it). {@code amountKnown} is false for a fluid saved as a bare id
     *  string, whose amount sits under a key of the tile's own choosing. */
    private record Content(String path, List<Object> where, @Nullable ItemStack item, @Nullable FluidStack fluid,
                           boolean amountKnown) {
        String describe() {
            return item != null
                ? item.getCount() + "x " + BuiltInRegistries.ITEM.getKey(item.getItem())
                : (amountKnown ? fluid.getAmount() + " mB " : "") + BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        }
    }

    /** What filling one tile produced: the captured tag before and after, and what the filler touched. */
    private record Outcome(CompoundTag idle, CompoundTag filled, Set<String> phantomKeys, EnumSet<Kind> kinds) {}

    // --- fixtures ---

    private static List<Fixture> fixtures() {
        List<Fixture> fixtures = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()) {
            if (!id.getNamespace().equals(BCBuilders.MODID)) continue;
            BlockEntityType<?> type = RegistryUtilBC.getValue(BuiltInRegistries.BLOCK_ENTITY_TYPE, id);
            for (Block block : type.getValidBlocks()) {
                fixtures.add(new Fixture(id.toString(), type, block));
            }
        }
        fixtures.sort((a, b) -> a.name().compareTo(b.name()));
        return fixtures;
    }

    private static BlockEntity newTile(Fixture fixture) {
        BlockEntity tile = fixture.type().create(BlockPos.ZERO, fixture.block().defaultBlockState());
        Assertions.assertNotNull(tile, "no block entity for " + fixture.name());
        return tile;
    }

    /** The tag the Architect captures for {@code tile}: its save, run through the capture's own sanitizing. */
    private static CompoundTag capture(BlockEntity tile, BlockState state) {
        return SchematicBlockDefault.sanitizeTileNbt(tile.saveWithoutMetadata(vanillaLookup()), state);
    }

    /** The load a placed block entity goes through. */
    private static void load(BlockEntity tile, CompoundTag tag) {
        //? if >=1.21.10 {
        tile.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, vanillaLookup(), tag));
        //?} else {
        /*tile.loadWithComponents(tag, vanillaLookup());*/
        //?}
    }

    private static List<String> keysOf(CompoundTag tag) {
        //? if >=1.21.10 {
        List<String> keys = new ArrayList<>(tag.keySet());
        //?} else {
        /*List<String> keys = new ArrayList<>(tag.getAllKeys());*/
        //?}
        Collections.sort(keys);
        return keys;
    }

    private static SchematicBlockDefault schematic(Block block, CompoundTag tileNbt) {
        SchematicBlockDefault schematic = new SchematicBlockDefault();
        schematic.blockState = block.defaultBlockState();
        schematic.placeBlock = block;
        schematic.tileRotation = Rotation.NONE;
        schematic.tileNbt = tileNbt;
        schematic.canBeReplacedWithBlocks.add(block);
        return schematic;
    }

    // --- the filler ---

    /** Puts something recognisable into every holder a tile has, by reflection on the tile's own fields. */
    private static final class Filler {
        private static final List<Item> ITEMS = List.of(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT,
            Items.IRON_INGOT, Items.COAL, Items.REDSTONE, Items.LAPIS_LAZULI, Items.QUARTZ, Items.COPPER_INGOT,
            Items.AMETHYST_SHARD, Items.FLINT, Items.CLAY_BALL, Items.BRICK, Items.STICK, Items.BONE,
            Items.NETHERITE_INGOT);

        /** ItemStack fields that are not contents: the recipe result a crafting table's output slot displays
         *  ({@code SlotDisplay}), recomputed from the grid and never extractable. Saved only to sync the display. */
        private static final Set<String> DISPLAY_ONLY = Set.of("resultClient");

        private final BlockEntity tile;
        private final List<Fluid> fluids = new ArrayList<>();
        private final Set<Object> done = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Object> phantom = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<String> phantomKeys = new TreeSet<>();
        final EnumSet<Kind> kinds = EnumSet.noneOf(Kind.class);
        private int nextItem;
        private int nextFluid;

        Filler(BlockEntity tile) {
            this.tile = tile;
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                if (fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState())) {
                    fluids.add(fluid);
                }
            }
            Assertions.assertTrue(fluids.size() >= 6, "too few source fluids to tell the tanks apart: " + fluids);
        }

        void run() throws ReflectiveOperationException {
            prepare();
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = tile.getClass(); c != null && c != BlockEntity.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        fields.add(field);
                    }
                }
            }
            // The manager first: it says which handlers are phantom, which the raw fields below must then skip.
            for (Field field : fields) {
                if (field.get(tile) instanceof ItemHandlerManager manager) {
                    fillManager(manager);
                }
            }
            for (Field field : fields) {
                fillField(field);
            }
        }

        /** The holders the field walk cannot reach. A heat exchanger holds fluid only once it is part of a multiblock
         *  section: load it the way a saved one is. An assembly table saves an item stack per recipe selection, kept
         *  in a map: give it one. */
        private void prepare() {
            if (tile instanceof TileHeatExchange) {
                CompoundTag section = new CompoundTag();
                section.putBoolean("hasSection", true);
                section.putBoolean("isStart", true);
                load(tile, section);
            } else if (tile instanceof TileAssemblyTable table) {
                BCSiliconRecipes.ensureInitialized();
                AssemblyRecipe recipe = AssemblyRecipeRegistry.REGISTRY.values().stream()
                    .filter(r -> !r.getOutputPreviews().isEmpty())
                    .min(AssemblyRecipe::compareTo)
                    .orElseThrow(() -> new AssertionError("no assembly recipe to select"));
                table.recipesStates.put(new TileAssemblyTable.AssemblyInstruction(recipe,
                    recipe.getOutputPreviews().iterator().next()), EnumAssemblyRecipeState.SAVED);
            }
        }

        @SuppressWarnings("unchecked")
        private void fillManager(ItemHandlerManager manager) throws ReflectiveOperationException {
            Field dropField = ItemHandlerManager.class.getDeclaredField("handlersToDrop");
            Field allField = ItemHandlerManager.class.getDeclaredField("handlers");
            dropField.setAccessible(true);
            allField.setAccessible(true);
            List<Object> droppable = (List<Object>) dropField.get(manager);
            Map<String, Object> all = (Map<String, Object>) allField.get(manager);
            for (Map.Entry<String, Object> entry : all.entrySet()) {
                if (droppable.stream().noneMatch(handler -> handler == entry.getValue())) {
                    phantomKeys.add(entry.getKey());
                    phantom.add(entry.getValue());
                }
            }
            for (Object handler : droppable) {
                Assertions.assertInstanceOf(ItemHandlerSimple.class, handler,
                    "a droppable handler the filler cannot fill: " + handler);
                fillHandler((ItemHandlerSimple) handler, Kind.MANAGED_HANDLER);
            }
        }

        private void fillField(Field field) throws ReflectiveOperationException {
            Object value = field.get(tile);
            if (value instanceof ItemHandlerSimple handler) {
                if (!phantom.contains(handler)) {
                    fillHandler(handler, Kind.RAW_HANDLER);
                }
            } else if (value instanceof BCFluidTank tank) {
                fillTank(tank);
            } else if (value instanceof BCFluidTank[] tanks) {
                for (BCFluidTank tank : tanks) {
                    fillTank(tank);
                }
            } else if (value instanceof TileHeatExchange.ExchangeSection section) {
                fillTank(section.tankInput);
                fillTank(section.tankOutput);
            } else if (value instanceof ItemStack stack && stack.isEmpty() && !Modifier.isFinal(field.getModifiers())
                && !DISPLAY_ONLY.contains(field.getName())) {
                field.set(tile, new ItemStack(takeItem(), 3));
                kinds.add(Kind.ITEM_FIELD);
            }
        }

        private Item takeItem() {
            return ITEMS.get(nextItem++ % ITEMS.size());
        }

        private void fillHandler(ItemHandlerSimple handler, Kind kind) {
            if (!done.add(handler)) return;
            Item item = takeItem();
            for (int slot = 0; slot < handler.size(); slot++) {
                handler.setStackInSlot(slot, new ItemStack(item, slot + 1));
            }
            kinds.add(kind);
        }

        private void fillTank(BCFluidTank tank) {
            if (!done.add(tank)) return;
            Fluid fluid = fluids.get(nextFluid % fluids.size());
            int amount = Math.min(100 + 50 * nextFluid++, tank.getCapacityMb(0));
            tank.setFluidStack(0, new FluidStack(fluid, amount));
            kinds.add(Kind.TANK);
        }
    }

    private static Outcome fillAndSave(Fixture fixture) {
        BlockState state = fixture.block().defaultBlockState();
        BlockEntity tile = newTile(fixture);
        CompoundTag idle = capture(tile, state);
        Filler filler = new Filler(tile);
        try {
            filler.run();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(fixture.name() + ": the filler could not read the tile", e);
        }
        return new Outcome(idle, capture(tile, state), filler.phantomKeys, filler.kinds);
    }

    // --- walking a saved tag ---

    private static boolean isItemId(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null && BuiltInRegistries.ITEM.containsKey(parsed)
            && RegistryUtilBC.getValue(BuiltInRegistries.ITEM, parsed) != Items.AIR;
    }

    private static boolean isFluidId(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null && BuiltInRegistries.FLUID.containsKey(parsed)
            && RegistryUtilBC.getValue(BuiltInRegistries.FLUID, parsed) != Fluids.EMPTY;
    }

    /** The stack a compound saves, in vanilla's codec form ({@code id}, {@code count}) or wrapped in a BuildCraft
     *  handler slot ({@code stack}); null when it is neither. */
    @Nullable
    private static ItemStack itemOf(CompoundTag tag) {
        CompoundTag wrapped = NBTUtilBC.getCompoundOrNull(tag, "stack");
        CompoundTag payload = wrapped != null ? wrapped : tag;
        if (!isItemId(NBTUtilBC.getString(payload, "id", ""))) return null;
        ItemStack stack = RequiredExtractor.parseItemStack(tag);
        return stack.isEmpty() ? null : stack;
    }

    /** The fluid a compound saves in {@code FluidStack.CODEC} form ({@code id}, {@code amount}); null otherwise. */
    @Nullable
    private static FluidStack fluidOf(CompoundTag tag) {
        if (!tag.contains("amount") || !isFluidId(NBTUtilBC.getString(tag, "id", ""))) return null;
        FluidStack stack = FluidStack.CODEC.parse(NBTUtilBC.registryAwareOps(), tag).resultOrPartial()
            .orElse(FluidStack.EMPTY);
        return stack.isEmpty() ? null : stack;
    }

    /** Every item stack and fluid under {@code tag}: where it sits (dotted keys, {@code [i]} for list entries) and
     *  what it is. A fluid saved as a bare id string beside an amount key is found at the id's key. */
    private static List<Content> scan(Tag tag) {
        List<Content> found = new ArrayList<>();
        scan(tag, "", List.of(), found);
        return found;
    }

    private static void scan(Tag tag, String path, List<Object> where, List<Content> found) {
        if (tag instanceof CompoundTag compound) {
            ItemStack item = itemOf(compound);
            if (item != null) {
                found.add(new Content(path, where, item, null, true));
                return;
            }
            FluidStack fluid = fluidOf(compound);
            if (fluid != null) {
                found.add(new Content(path, where, null, fluid, true));
                return;
            }
            for (String key : keysOf(compound)) {
                Tag child = compound.get(key);
                String childPath = path.isEmpty() ? key : path + "." + key;
                List<Object> childWhere = append(where, key);
                if (child instanceof StringTag && isFluidId(RequiredExtractor.readString(child))) {
                    Identifier id = Identifier.parse(RequiredExtractor.readString(child));
                    found.add(new Content(childPath, childWhere, null,
                        new FluidStack(RegistryUtilBC.getValue(BuiltInRegistries.FLUID, id), 1), false));
                } else {
                    scan(child, childPath, childWhere, found);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                scan(list.get(i), path + "[" + i + "]", append(where, i), found);
            }
        }
    }

    private static List<Object> append(List<Object> where, Object step) {
        List<Object> longer = new ArrayList<>(where);
        longer.add(step);
        return List.copyOf(longer);
    }

    /** {@code root} with the content at {@code where} taken out: a compound key removed, a list entry emptied (so the
     *  entries after it keep their indices). */
    private static CompoundTag without(CompoundTag root, List<Object> where) {
        CompoundTag copy = root.copy();
        Tag parent = copy;
        for (Object step : where.subList(0, where.size() - 1)) {
            parent = step instanceof String key ? ((CompoundTag) parent).get(key) : ((ListTag) parent).get((Integer) step);
        }
        Object last = where.get(where.size() - 1);
        if (parent instanceof CompoundTag compound) {
            compound.remove((String) last);
        } else {
            ((ListTag) parent).set((Integer) last, new CompoundTag());
        }
        return copy;
    }

    /** The places in {@code idle} that are an item handler's saved list: a compound whose {@code items} is a list of
     *  slot compounds. Each is returned as its key path. */
    private static List<List<String>> handlerRegions(CompoundTag idle) {
        List<List<String>> regions = new ArrayList<>();
        collectRegions(idle, new ArrayList<>(), regions);
        return regions;
    }

    private static void collectRegions(CompoundTag tag, List<String> path, List<List<String>> regions) {
        if (tag.get("items") instanceof ListTag list && !list.isEmpty()
            && list.stream().allMatch(slot -> slot instanceof CompoundTag)) {
            regions.add(List.copyOf(path));
        }
        for (String key : keysOf(tag)) {
            if (tag.get(key) instanceof CompoundTag child) {
                List<String> childPath = new ArrayList<>(path);
                childPath.add(key);
                collectRegions(child, childPath, regions);
            }
        }
    }

    @Nullable
    private static CompoundTag at(CompoundTag root, List<String> path) {
        CompoundTag current = root;
        for (String key : path) {
            current = NBTUtilBC.getCompoundOrNull(current, key);
            if (current == null) return null;
        }
        return current;
    }

    // --- the checks ---

    /** What the matching rules' contents-pricing extractors surface from {@code tileNbt}. */
    private static List<ItemStack> pricedItems(Set<JsonRule> rules, BlockState state, CompoundTag tileNbt) {
        List<ItemStack> priced = new ArrayList<>();
        for (RequiredExtractor extractor : pricingExtractors(rules)) {
            priced.addAll(extractor.extractItemsFromBlock(state, tileNbt));
        }
        return priced;
    }

    private static List<FluidStack> pricedFluids(Set<JsonRule> rules, BlockState state, CompoundTag tileNbt) {
        List<FluidStack> priced = new ArrayList<>();
        for (RequiredExtractor extractor : pricingExtractors(rules)) {
            priced.addAll(extractor.extractFluidsFromBlock(state, tileNbt));
        }
        return priced;
    }

    private static List<RequiredExtractor> pricingExtractors(Set<JsonRule> rules) {
        List<RequiredExtractor> extractors = new ArrayList<>();
        for (JsonRule rule : rules) {
            if (rule.requiredExtractors == null) continue;
            for (RequiredExtractor extractor : rule.requiredExtractors) {
                if (extractor.pricesTileContents()) {
                    extractors.add(extractor);
                }
            }
        }
        return extractors;
    }

    /** One message per saved list or key under which {@code leftover} sits, naming the block, that key path and what
     *  it holds (the first few entries of a list), then {@code why}. */
    private static List<String> describe(String name, List<Content> leftover, String why) {
        Map<String, List<Content>> byKey = new LinkedHashMap<>();
        for (Content content : leftover) {
            byKey.computeIfAbsent(content.path().replaceFirst("\\[\\d+]$", ""), key -> new ArrayList<>()).add(content);
        }
        List<String> messages = new ArrayList<>();
        byKey.forEach((key, group) -> messages.add(name + " at \"" + key + "\" holds "
            + group.stream().limit(3).map(Content::describe).collect(Collectors.joining(", "))
            + (group.size() > 3 ? " and " + (group.size() - 3) + " more" : "") + ": " + why));
        return messages;
    }

    /** {@code before} less one equal stack for each of {@code after}: what taking a key out stopped pricing. */
    private static <T> List<T> lost(List<T> before, List<T> after, BiPredicate<T, T> equal) {
        List<T> lost = new ArrayList<>(before);
        for (T kept : after) {
            for (int i = 0; i < lost.size(); i++) {
                if (equal.test(lost.get(i), kept)) {
                    lost.remove(i);
                    break;
                }
            }
        }
        return lost;
    }

    /** Whether a contents-pricing extractor reads the key {@code content} sits at: with that key taken out of
     *  {@code tileNbt}, the extractors price at least that much less of its item (or fluid). The amount is a lower
     *  bound, not an equality: a stone engine saves its fuel as a one-count stack and the real count under another
     *  key, and its extractor reads both. */
    private static boolean isRead(Content content, Set<JsonRule> rules, BlockState state, CompoundTag tileNbt) {
        if (content.where().isEmpty()) return false;
        CompoundTag ablated = without(tileNbt, content.where());
        if (content.item() != null) {
            ItemStack wanted = content.item();
            int lost = lost(pricedItems(rules, state, tileNbt), pricedItems(rules, state, ablated), ItemStack::matches)
                .stream().filter(s -> ItemStack.isSameItemSameComponents(s, wanted)).mapToInt(ItemStack::getCount).sum();
            return lost >= wanted.getCount();
        }
        FluidStack wanted = content.fluid();
        int lost = lost(pricedFluids(rules, state, tileNbt), pricedFluids(rules, state, ablated), FluidStack::matches)
            .stream().filter(f -> f.getFluid() == wanted.getFluid()).mapToInt(FluidStack::getAmount).sum();
        return content.amountKnown() ? lost >= wanted.getAmount() : lost > 0;
    }

    /** The contents of a priced tile that sit at a key no contents-pricing extractor reads, as messages. */
    private static List<String> unpriced(String name, List<Content> contents, Set<JsonRule> rules, BlockState state,
                                         CompoundTag tileNbt) {
        List<Content> leftover = contents.stream().filter(c -> !isRead(c, rules, state, tileNbt)).toList();
        return describe(name, leftover, "no contents-pricing extractor of a matching rule reads this key, so a"
            + " blueprint would rebuild it for free");
    }

    /** {@code contents} less the keys {@code notContents} (a {@link #NOT_CONTENTS} entry, or null) excludes. */
    private static List<Content> withoutNotContents(List<Content> contents, Pattern notContents) {
        return notContents == null ? contents
            : contents.stream().filter(c -> !notContents.matcher(c.path()).matches()).toList();
    }

    /** The problems with one filled tile; empty when its contents are priced or wiped. */
    private static List<String> problems(Fixture fixture, Outcome outcome) {
        List<String> problems = new ArrayList<>();
        BlockState state = fixture.block().defaultBlockState();
        CompoundTag filled = outcome.filled();
        List<Content> contents = scan(filled);
        Pattern notContents = NOT_CONTENTS.get(fixture.name());
        if (notContents != null) {
            if (contents.stream().noneMatch(c -> notContents.matcher(c.path()).matches())) {
                problems.add(fixture.name() + " saves nothing at " + notContents.pattern() + " once filled: the"
                    + " NOT_CONTENTS entry is stale, or the filler no longer reaches that holder");
            }
            contents = withoutNotContents(contents, notContents);
        }

        // The filler must have reached every inventory the tile saves as a handler list (phantom ones excepted), or a
        // holder it does not know would pass below on an empty save.
        for (List<String> region : handlerRegions(outcome.idle())) {
            boolean phantomRegion = region.size() == 2 && region.get(0).equals("items")
                && outcome.phantomKeys().contains(region.get(1));
            CompoundTag now = at(filled, region);
            if (!phantomRegion && (now == null || scan(now).stream().noneMatch(c -> c.item() != null))) {
                problems.add(fixture.name() + " saves an item handler at \"" + String.join(".", region)
                    + "\" that the filler left empty: teach the filler this holder before trusting this test");
            }
        }
        if (contents.isEmpty()) {
            return problems;
        }

        SchematicBlockDefault schematic = schematic(fixture.block(), filled.copy());
        if (schematic.contentsArePriced()) {
            Set<JsonRule> rules = RulesLoader.getRules(state, filled.copy());
            problems.addAll(unpriced(fixture.name(), contents, rules, state, filled));
        } else {
            // No rule prices this tile, so build() loads the captured tag and then wipes the tile.
            BlockEntity fresh = newTile(fixture);
            load(fresh, filled.copy());
            List<Content> loaded = withoutNotContents(scan(capture(fresh, state)), notContents);
            if (loaded.size() != contents.size()) {
                problems.add(fixture.name() + " does not load back the contents it saves, so the wipe check would"
                    + " pass for nothing: saved " + contents.size() + ", loaded " + loaded.size());
            }
            if (fresh instanceof Clearable clearable) {
                clearable.clearContent();
                problems.addAll(describe(fixture.name(), withoutNotContents(scan(capture(fresh, state)), notContents),
                    "it survives clearContent and no rule prices it, so a blueprint would rebuild it for free"));
            } else {
                problems.add(fixture.name() + " holds contents, no rule prices them, and the tile is not Clearable:"
                    + " a blueprint would rebuild them for free");
            }
        }
        return problems;
    }

    @TestFactory
    public Stream<DynamicTest> everyTilesContentsArePricedOrWiped() {
        List<Fixture> fixtures = fixtures();
        Assertions.assertFalse(fixtures.isEmpty(), "no BuildCraft block entity types are registered");
        return fixtures.stream().map(fixture -> DynamicTest.dynamicTest(fixture.name(), () -> {
            List<String> problems = problems(fixture, fillAndSave(fixture));
            Assertions.assertTrue(problems.isEmpty(), String.join("\n", problems));
        }));
    }

    @Test
    public void theFillerReachesEveryKindOfHolder() {
        EnumSet<Kind> reached = EnumSet.noneOf(Kind.class);
        int stacks = 0;
        int fluids = 0;
        for (Fixture fixture : fixtures()) {
            Outcome outcome = fillAndSave(fixture);
            reached.addAll(outcome.kinds());
            for (Content content : scan(outcome.filled())) {
                if (content.item() != null) stacks++;
                else fluids++;
            }
        }
        Assertions.assertEquals(EnumSet.allOf(Kind.class), reached,
            "a kind of holder no tile had, or the filler no longer finds: the guard checks nothing for it");
        Assertions.assertTrue(stacks > 0 && fluids > 0,
            "the filled saves hold " + stacks + " item stacks and " + fluids + " fluids");
    }
}
