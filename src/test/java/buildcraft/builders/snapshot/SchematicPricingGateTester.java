/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.RegistryUtilBC;

/**
 * A blueprint charges for what it places. A block entity's captured contents are priced only through a JSON rule,
 * so an item holder no rule names (a copper chest, before its rule existed) was rebuilt full for the price of the
 * empty block; and an uploaded palette can name a block no Architect scan would capture (an ender chest, a block
 * whose {@code placeBlock} differs from the state it is priced by). Both are pinned here.
 */
public class SchematicPricingGateTester extends VanillaSetupBaseTester {

    private static SchematicBlockDefault schematic(BlockState state, Block placeBlock, CompoundTag tileNbt) {
        SchematicBlockDefault schematic = new SchematicBlockDefault();
        schematic.blockState = state;
        schematic.placeBlock = placeBlock;
        schematic.tileRotation = Rotation.NONE;
        schematic.tileNbt = tileNbt;
        schematic.canBeReplacedWithBlocks.add(state.getBlock());
        return schematic;
    }

    private static CompoundTag itemsTileNbt(String itemId, int count) {
        CompoundTag stack = new CompoundTag();
        stack.putByte("Slot", (byte) 0);
        stack.putString("id", itemId);
        stack.putInt("count", count);
        ListTag items = new ListTag();
        items.add(stack);
        CompoundTag tileNbt = new CompoundTag();
        tileNbt.put("Items", items);
        return tileNbt;
    }

    private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /** A one-cell blueprint holding {@code entry}, written and read back the way the store and an upload read it. */
    private static Blueprint roundTrip(ISchematicBlock entry) throws InvalidInputDataException {
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, 1);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(entry);
        blueprint.data = new int[] {0};
        blueprint.computeKey();
        Snapshot read = Snapshot.readFromNBT(Snapshot.writeToNBT(blueprint));
        Assertions.assertInstanceOf(Blueprint.class, read);
        return (Blueprint) read;
    }

    @Test
    public void aCopperChestChargesForItsContents() {
        // Copper chests exist from MC 1.21.9: on the older node there is nothing to price.
        Identifier copperChestId = Identifier.parse("minecraft:copper_chest");
        Assumptions.assumeTrue(BuiltInRegistries.BLOCK.containsKey(copperChestId), "no copper chest on this node");
        Block copperChest = RegistryUtilBC.getValue(BuiltInRegistries.BLOCK, copperChestId);

        SchematicBlockDefault schematic = schematic(
            copperChest.defaultBlockState(), copperChest, itemsTileNbt("minecraft:diamond", 5));
        List<ItemStack> required = schematic.computeRequiredItems();

        Assertions.assertEquals(1, count(required, copperChest.asItem()), "the chest itself: " + required);
        Assertions.assertEquals(5, count(required, Items.DIAMOND),
            "build() places the captured contents, so they must be charged: " + required);
    }

    @Test
    public void aSingleItemHolderIsChargedForItsItemOnlyWhenTheBuilderPlacesContents() {
        // A lectern keeps one stored item (its book) rather than an item list. With contents placed it is charged;
        // in the Builder's IGNORE mode the block is placed empty, so the book must not be asked for either.
        CompoundTag book = new CompoundTag();
        book.putString("id", "minecraft:writable_book");
        book.putInt("count", 1);
        CompoundTag tileNbt = new CompoundTag();
        tileNbt.put("Book", book);
        SchematicBlockDefault schematic = schematic(Blocks.LECTERN.defaultBlockState(), Blocks.LECTERN, tileNbt);

        List<ItemStack> withContents = schematic.computeRequiredItems(true);
        Assertions.assertEquals(1, count(withContents, Items.WRITABLE_BOOK), "contents placed: " + withContents);
        List<ItemStack> ignored = schematic.computeRequiredItems(false);
        Assertions.assertEquals(1, count(ignored, Items.LECTERN), "the lectern itself: " + ignored);
        Assertions.assertEquals(0, count(ignored, Items.WRITABLE_BOOK), "contents ignored: " + ignored);
    }

    @Test
    public void aPaletteEntryNamingAnIgnoredBlockReadsBackAsAir() throws InvalidInputDataException {
        // ignored.json: the Architect never captures an ender chest (its inventory is per player), so an upload
        // naming one is reduced to air rather than refused: a modpack rule change must not brick stored files.
        Blueprint read = roundTrip(schematic(Blocks.ENDER_CHEST.defaultBlockState(), Blocks.ENDER_CHEST, null));
        Assertions.assertEquals(1, read.palette.size(), "the index stays, so data[] still resolves");
        Assertions.assertInstanceOf(SchematicBlockAir.class, read.palette.get(0));
        // Reduced, so the store never writes it over its file; a copy (the Replacer's, the library download's) too.
        Assertions.assertFalse(read.readFully());
        Assertions.assertFalse(read.copy().readFully());
    }

    /** A used single-schematic item carrying {@code entry}, as a forged item's NBT would. */
    private static ItemStack singleSchematicItem(ISchematicBlock entry) {
        ItemStack stack = new ItemStack(buildcraft.builders.BCBuildersItems.SCHEMATIC_SINGLE_USED.get());
        CompoundTag itemData = new CompoundTag();
        itemData.put(buildcraft.builders.item.ItemSchematicSingle.NBT_KEY, SchematicBlockManager.writeToNBT(entry));
        NBTUtilBC.setItemData(stack, itemData);
        return stack;
    }

    @Test
    public void aSingleSchematicItemPassesTheSameCaptureGate() throws InvalidInputDataException {
        // The item is placed directly and feeds the Replacer's from/to pair, so it is gated like a palette entry; with
        // no file to keep readable around it, an uncapturable one is refused rather than reduced to air.
        ItemStack enderChest = singleSchematicItem(
            schematic(Blocks.ENDER_CHEST.defaultBlockState(), Blocks.ENDER_CHEST, null));
        Assertions.assertThrows(InvalidInputDataException.class,
            () -> buildcraft.builders.item.ItemSchematicSingle.getSchematic(enderChest));
        ItemStack stone = singleSchematicItem(schematic(Blocks.STONE.defaultBlockState(), Blocks.STONE, null));
        Assertions.assertInstanceOf(SchematicBlockDefault.class,
            buildcraft.builders.item.ItemSchematicSingle.getSchematic(stone));
    }

    @Test
    public void aPaletteEntryWhosePlaceBlockIsNotItsOwnReadsBackAsAir() throws InvalidInputDataException {
        // Priced by the state (one stone), placed by placeBlock (a diamond block): a scan only ever writes the
        // block itself or the rule's placeBlock there.
        Blueprint read = roundTrip(schematic(Blocks.STONE.defaultBlockState(), Blocks.DIAMOND_BLOCK, null));
        Assertions.assertInstanceOf(SchematicBlockAir.class, read.palette.get(0));
    }

    @Test
    public void anOrdinaryPaletteEntryAndARuledPlaceBlockSurvive() throws InvalidInputDataException {
        // The positive control for both gates above: an inverted gate would pass them.
        Blueprint stone = roundTrip(schematic(Blocks.STONE.defaultBlockState(), Blocks.STONE, null));
        Assertions.assertInstanceOf(SchematicBlockDefault.class, stone.palette.get(0));
        // piston.json: a piston head is captured with placeBlock air (the piston base re-extends it).
        Blueprint head = roundTrip(schematic(Blocks.PISTON_HEAD.defaultBlockState(), Blocks.AIR, null));
        Assertions.assertInstanceOf(SchematicBlockDefault.class, head.palette.get(0));
    }

    /** A fluid schematic as its own NBT names it: the fields are private, and an upload sets them this way. */
    private static SchematicBlockFluid fluidSchematic(BlockState state) throws InvalidInputDataException {
        CompoundTag data = new CompoundTag();
        data.put("blockState", buildcraft.lib.misc.BlockStateNbt.write(state));
        data.putBoolean("isFlowing", false);
        SchematicBlockFluid schematic = new SchematicBlockFluid();
        schematic.deserializeNbt(data);
        return schematic;
    }

    @Test
    public void aPaletteEntryTypedAsAClassNoScanWouldPickReadsBackAsAir() throws InvalidInputDataException {
        // A scan types a fluid's own block as the fluid schematic, which charges the fluid: a default entry naming
        // lava would charge nothing (no rule prices lava) and place it.
        Blueprint lava = roundTrip(schematic(Blocks.LAVA.defaultBlockState(), Blocks.LAVA, null));
        Assertions.assertInstanceOf(SchematicBlockAir.class, lava.palette.get(0), "a default-typed lava source");
        // And the other way round: a fluid entry naming a dry block charges no fluid and places that block.
        Blueprint diamond = roundTrip(fluidSchematic(Blocks.DIAMOND_BLOCK.defaultBlockState()));
        Assertions.assertInstanceOf(SchematicBlockAir.class, diamond.palette.get(0), "a fluid-typed diamond block");
        // The positive control: a water source typed as a scan types it survives.
        Blueprint water = roundTrip(fluidSchematic(Blocks.WATER.defaultBlockState()));
        Assertions.assertInstanceOf(SchematicBlockFluid.class, water.palette.get(0), "a fluid-typed water source");
    }

    private static BlockState wet(BlockState state) {
        return state.setValue(BlockStateProperties.WATERLOGGED, true);
    }

    /** The fluid predicate reads only the state, so the context needs no level. */
    private static boolean typedAsFluid(BlockState state) {
        return SchematicBlockFluid.predicate(
            new SchematicBlockContext(null, BlockPos.ZERO, BlockPos.ZERO, state, state.getBlock()));
    }

    @Test
    public void onlyAFluidsOwnBlockIsTypedAsFluid() {
        // A waterlogged block has a fluid state too, but it is a slab or a pipe: typed as fluid it charged 1000 mB of
        // water and no item, and a wet pipe lost its pipe and pluggables (the fluid schematic keeps no tile data).
        Block holder = buildcraft.transport.BCTransportBlocks.PIPE_HOLDER.get();
        Assertions.assertFalse(typedAsFluid(wet(Blocks.OAK_SLAB.defaultBlockState())), "a waterlogged slab");
        Assertions.assertFalse(typedAsFluid(wet(holder.defaultBlockState())), "a waterlogged pipe holder");
        // The positive controls: the fluid blocks themselves, source and flowing.
        Assertions.assertTrue(typedAsFluid(Blocks.WATER.defaultBlockState()), "a water source");
        Assertions.assertTrue(typedAsFluid(Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3)),
            "flowing water");
        Assertions.assertTrue(typedAsFluid(Blocks.LAVA.defaultBlockState()), "a lava source");
        // A bubble column is water that is not a LiquidBlock and not water's legacy block, and has no item: it stays
        // the fluid schematic (water charged), as the Builder's own fluid-only test (SnapshotBuilder.isFluidOnly) has it.
        Assertions.assertTrue(typedAsFluid(Blocks.BUBBLE_COLUMN.defaultBlockState()), "a bubble column");
    }

    @Test
    public void aBubbleColumnIsChargedItsWaterAndADefaultTypedOneReadsBackAsAir() throws InvalidInputDataException {
        // Nothing prices a bubble column as an item, so a default entry naming one would place it for free: the gate
        // refuses it like lava, and the fluid entry a scan writes survives and charges the water it stands in.
        BlockState column = Blocks.BUBBLE_COLUMN.defaultBlockState();
        Blueprint asDefault = roundTrip(schematic(column, Blocks.BUBBLE_COLUMN, null));
        Assertions.assertInstanceOf(SchematicBlockAir.class, asDefault.palette.get(0), "a default-typed bubble column");
        Blueprint asFluid = roundTrip(fluidSchematic(column));
        SchematicBlockFluid kept = Assertions.assertInstanceOf(SchematicBlockFluid.class, asFluid.palette.get(0),
            "a fluid-typed bubble column");
        Assertions.assertEquals(1, kept.computeRequiredFluids().size(), "the water it stands in is charged");
    }

    @Test
    public void kelpAndTallSeagrassAreChargedTheirOneItemPerCell() {
        // kelp_plant (the body of every kelp column) and tall_seagrass have no item of their own: a rule names the
        // kelp / seagrass item they are planted with. The upper half of seagrass is placed by its lower half.
        Assertions.assertEquals(1, count(schematic(Blocks.KELP_PLANT.defaultBlockState(), Blocks.KELP_PLANT, null)
            .computeRequiredItems(), Items.KELP), "a kelp body cell");
        Assertions.assertEquals(1, count(schematic(Blocks.KELP.defaultBlockState(), Blocks.KELP, null)
            .computeRequiredItems(), Items.KELP), "the kelp tip");
        BlockState lower = Blocks.TALL_SEAGRASS.defaultBlockState();
        List<ItemStack> lowerRequired = schematic(lower, Blocks.TALL_SEAGRASS, null).computeRequiredItems();
        Assertions.assertEquals(1, count(lowerRequired, Items.SEAGRASS), "tall seagrass: " + lowerRequired);
        BlockState upper = lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        Assertions.assertTrue(schematic(upper, Blocks.TALL_SEAGRASS, null).computeRequiredItems().isEmpty(),
            "the upper half is placed with the lower one");
    }

    @Test
    public void everyBlockHoldingWaterThatIsNotWaterIsChargedForAnItem() {
        // A scan types such a block (waterlogged slab, kelp, seagrass) as a default entry, which charges only what a
        // rule or the block's own item names. One that names nothing is placed for free: kelp_plant and tall_seagrass
        // have no item of their own, so each needs a rule, and the Mining Well / Pump tube (no item, unbreakable in
        // survival) is ignored by one. Only blocks a scan would capture as a default entry count (a pipe holder is the
        // pipe schematic's), and the upper half of a double plant is placed by its lower half, so it is free by design.
        List<String> checked = new ArrayList<>();
        List<String> free = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                state = wet(state);
            }
            if (state.getFluidState().isEmpty() || SchematicBlockFluid.isFluidBlock(state)) {
                continue;
            }
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                continue;
            }
            SchematicBlockDefault schematic = schematic(state, block, null);
            if (!schematic.capturableByScan()) {
                continue;
            }
            String id = String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
            checked.add(id);
            if (schematic.computeRequiredItems().isEmpty()) {
                free.add(id);
            }
        }
        // Liveness: the walk reached ordinary waterloggable blocks, so an empty `free` is not an empty walk.
        Assertions.assertTrue(checked.contains("minecraft:oak_slab") && checked.contains("minecraft:seagrass"),
            "the walk checked " + checked);
        Assertions.assertEquals(List.of(), free, "blocks that hold a fluid and are rebuilt for free");
    }

    @Test
    public void aMiningWellTubeIsNeverCapturedWetOrDry() throws InvalidInputDataException {
        // The tube has no item and cannot be broken in survival; the Mining Well and Pump extend their own. Stored or
        // uploaded, dry, wet, or typed as fluid by an older scan, it reads back as air rather than a free block.
        BlockState dry = buildcraft.factory.BCFactoryBlocks.TUBE.get().defaultBlockState();
        Block tube = dry.getBlock();
        Assertions.assertTrue(dry.hasProperty(BlockStateProperties.WATERLOGGED), "the tube is waterloggable");
        Assertions.assertInstanceOf(SchematicBlockAir.class, roundTrip(schematic(dry, tube, null)).palette.get(0),
            "a dry tube");
        Assertions.assertInstanceOf(SchematicBlockAir.class, roundTrip(schematic(wet(dry), tube, null)).palette.get(0),
            "a wet tube");
        Assertions.assertInstanceOf(SchematicBlockAir.class, roundTrip(fluidSchematic(wet(dry))).palette.get(0),
            "a wet tube stored as fluid");
    }

    @Test
    public void aScannedWaterloggedBlockSurvivesTheCaptureGateAndIsChargedAsItself() throws InvalidInputDataException {
        // What a scan now writes for a wet slab: a default entry. The capture gate must not read its fluid state as
        // "a fluid typed as something else" and reduce it to air.
        Blueprint read = roundTrip(schematic(wet(Blocks.OAK_SLAB.defaultBlockState()), Blocks.OAK_SLAB, null));
        SchematicBlockDefault slab = Assertions.assertInstanceOf(SchematicBlockDefault.class, read.palette.get(0));
        Assertions.assertEquals(wet(Blocks.OAK_SLAB.defaultBlockState()), slab.blockState, "it stays waterlogged");
        List<ItemStack> required = slab.computeRequiredItems();
        Assertions.assertEquals(1, count(required, Items.OAK_SLAB), "the slab is charged: " + required);
        Assertions.assertTrue(slab.computeRequiredFluids().isEmpty(), "no water is charged for it");
    }

    @Test
    public void aWaterloggedBlockStoredAsFluidIsReReadAsItself() throws InvalidInputDataException {
        // Blueprints scanned while every waterlogged block was typed as fluid hold fluid entries naming a slab. They
        // are not lost and not priced as water: they are re-read as the schematic a scan picks now, minus tile data.
        BlockState wetSlab = wet(Blocks.OAK_SLAB.defaultBlockState());
        Blueprint read = roundTrip(fluidSchematic(wetSlab));
        SchematicBlockDefault slab = Assertions.assertInstanceOf(SchematicBlockDefault.class, read.palette.get(0),
            "a fluid-typed waterlogged slab");
        Assertions.assertEquals(wetSlab, slab.blockState, "it stays waterlogged");
        Assertions.assertSame(Blocks.OAK_SLAB, slab.placeBlock);
        List<ItemStack> required = slab.computeRequiredItems();
        Assertions.assertEquals(1, count(required, Items.OAK_SLAB), "the slab is charged: " + required);
        Assertions.assertTrue(slab.computeRequiredFluids().isEmpty(), "no water is charged for it");
    }

    @Test
    public void aWetPipeKeepsItsPipeSchematicAndAStoredFluidTypedOneIsReducedToAir() throws InvalidInputDataException {
        Block holder = buildcraft.transport.BCTransportBlocks.PIPE_HOLDER.get();
        BlockState wetHolder = wet(holder.defaultBlockState());
        // A wet holder read as a default entry is re-read as the pipe schematic (the legacy pipe upgrade), and the
        // gate lets it through; the real scan of a wet pipe is the wet_blocks_scan_as_themselves game test.
        Blueprint scanned = roundTrip(schematic(wetHolder, holder, new CompoundTag()));
        SchematicBlockPipe pipe = Assertions.assertInstanceOf(SchematicBlockPipe.class, scanned.palette.get(0));
        Assertions.assertEquals(wetHolder, pipe.blockState, "it stays waterlogged");
        // A fluid entry naming one has no pipe to be had (the fluid schematic never kept the tile data), and an
        // empty holder is no pipe: it is reduced to air, like any entry the gate refuses.
        Blueprint stored = roundTrip(fluidSchematic(wetHolder));
        Assertions.assertInstanceOf(SchematicBlockAir.class, stored.palette.get(0), "a fluid-typed wet pipe holder");
    }

    @Test
    public void aDefaultTypedPipeIsReReadAsThePipeSchematic() throws InvalidInputDataException {
        // Blueprints scanned before the pipe schematic existed typed pipes as default, which prices no pluggable
        // (only the pipe schematic charges facades, gates and lenses). The pipe is kept, as the pipe schematic.
        Block holder = buildcraft.transport.BCTransportBlocks.PIPE_HOLDER.get();
        Blueprint read = roundTrip(schematic(holder.defaultBlockState(), holder, new CompoundTag()));
        Assertions.assertInstanceOf(SchematicBlockPipe.class, read.palette.get(0));
    }

    @Test
    public void serializingAndRotatingNeverShareTheLiveTileNbt() {
        CompoundTag tileNbt = itemsTileNbt("minecraft:diamond", 5);
        SchematicBlockDefault schematic = schematic(Blocks.CHEST.defaultBlockState(), Blocks.CHEST, tileNbt);
        CompoundTag before = tileNbt.copy();

        NBTUtilBC.getCompoundOrNull(schematic.serializeNbt(), "tileNbt").putString("tampered", "yes");
        schematic.getRotated(Rotation.CLOCKWISE_90).tileNbt.putString("tampered", "yes");

        Assertions.assertEquals(before, schematic.tileNbt, "the schematic's own tileNbt must not change");
    }
}
