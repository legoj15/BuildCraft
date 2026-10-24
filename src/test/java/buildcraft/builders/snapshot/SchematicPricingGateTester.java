/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicBlock;

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
        // A scan types every state with a fluid as the fluid schematic, which charges the fluid: a default entry
        // naming lava would charge nothing (no rule prices lava) and place it.
        Blueprint lava = roundTrip(schematic(Blocks.LAVA.defaultBlockState(), Blocks.LAVA, null));
        Assertions.assertInstanceOf(SchematicBlockAir.class, lava.palette.get(0), "a default-typed lava source");
        // And the other way round: a fluid entry naming a dry block charges no fluid and places that block.
        Blueprint diamond = roundTrip(fluidSchematic(Blocks.DIAMOND_BLOCK.defaultBlockState()));
        Assertions.assertInstanceOf(SchematicBlockAir.class, diamond.palette.get(0), "a fluid-typed diamond block");
        // The positive control: a water source typed as a scan types it survives.
        Blueprint water = roundTrip(fluidSchematic(Blocks.WATER.defaultBlockState()));
        Assertions.assertInstanceOf(SchematicBlockFluid.class, water.palette.get(0), "a fluid-typed water source");
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
