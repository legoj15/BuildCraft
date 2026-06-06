/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.BCTransportPlugs;
import buildcraft.transport.plug.PluggableBlocker;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Anchors the pipe required-item fix ({@link SchematicBlockPipe}). BuildCraft pipes are all one
 * block (the pipe holder) with the type living in block-entity NBT, not the block state. The
 * generic {@link SchematicBlockDefault} resolves a placement's required item via
 * {@code blockState.getBlock().asItem()}, which returns the SAME item for every pipe — so a
 * blueprint of varied pipes used to list a single pipe type in the Builder's resource panel even
 * though each pipe still <em>placed</em> correctly (placement reads the captured BE NBT).
 *
 * <p>This pins, for three pipes spanning all three flow kinds (item / fluid / power), that each
 * captured pipe schematic (a) routes to {@link SchematicBlockPipe} and (b) resolves the correct,
 * <em>distinct</em> pipe item — directly guarding against the "all pipes collapse to one item"
 * regression.
 */
public class SchematicBlockPipeTester {

    /** Place a pipe of the given type, capture it as a schematic, and return its single required item. */
    private static ItemStack requiredItemFor(GameTestHelper helper, BlockPos relPos, Item pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        // Initialises the pipe (and its definition) on the BE exactly as a player placement would.
        tile.onPlacedBy(null, new ItemStack(pipeItem));

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        SchematicBlockContext context = new SchematicBlockContext(
                level, BlockPos.ZERO, abs, state, state.getBlock());
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(context);
        helper.assertTrue(schematic instanceof SchematicBlockPipe,
                "Pipe holder should route to SchematicBlockPipe, got " + schematic.getClass().getSimpleName());

        List<ItemStack> required = schematic.computeRequiredItems();
        helper.assertTrue(required.size() == 1,
                "A pipe should require exactly one item, got " + required.size() + " for "
                        + BuiltInRegistries.ITEM.getKey(pipeItem));
        return required.get(0);
    }

    /** Each pipe resolves to ITS OWN item, and distinct pipe types stay distinct. */
    public static void pipeSchematicResolvesPerTypeItem(GameTestHelper helper) {
        Item wood = BCTransportItems.PIPE_WOOD_ITEM.get();
        Item goldFluid = BCTransportItems.PIPE_GOLD_FLUID.get();
        Item diamondPower = BCTransportItems.PIPE_DIAMOND_POWER.get();

        ItemStack woodReq = requiredItemFor(helper, new BlockPos(1, 2, 1), wood);
        ItemStack goldReq = requiredItemFor(helper, new BlockPos(2, 2, 1), goldFluid);
        ItemStack diamondReq = requiredItemFor(helper, new BlockPos(3, 2, 1), diamondPower);

        helper.assertTrue(woodReq.is(wood),
                "Wooden item pipe must require the wooden item pipe, got "
                        + BuiltInRegistries.ITEM.getKey(woodReq.getItem()));
        helper.assertTrue(goldReq.is(goldFluid),
                "Gold fluid pipe must require the gold fluid pipe, got "
                        + BuiltInRegistries.ITEM.getKey(goldReq.getItem()));
        helper.assertTrue(diamondReq.is(diamondPower),
                "Diamond power pipe must require the diamond power pipe, got "
                        + BuiltInRegistries.ITEM.getKey(diamondReq.getItem()));

        // The direct regression guard: three different pipe types must not collapse to one item.
        helper.assertFalse(
                woodReq.getItem() == goldReq.getItem()
                        || woodReq.getItem() == diamondReq.getItem()
                        || goldReq.getItem() == diamondReq.getItem(),
                "Distinct pipe types must resolve to distinct required items (the reported 'all pipes are one' bug)");
        helper.succeed();
    }

    /**
     * A pluggable captured on a pipe (here a blocker) must be costed in the required items.
     * {@code build()} restores pluggables from the captured NBT, so the Builder must charge for them
     * rather than placing them for free — the regression this guards.
     */
    public static void pipeSchematicCostsPluggables(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        Item pipeItem = BCTransportItems.PIPE_COBBLE_ITEM.get();
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        // Attach a blocker pluggable (fixed item, holder-free getPickStack) on one face.
        tile.replacePluggable(Direction.UP, new PluggableBlocker(BCTransportPlugs.blocker, tile, Direction.UP));

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, BlockPos.ZERO, abs, state, state.getBlock()));
        helper.assertTrue(schematic instanceof SchematicBlockPipe,
                "Pipe holder should route to SchematicBlockPipe, got " + schematic.getClass().getSimpleName());

        List<ItemStack> required = schematic.computeRequiredItems();
        Item blockerItem = BCTransportItems.PLUG_BLOCKER.get();
        boolean hasPipe = required.stream().anyMatch(s -> s.is(pipeItem));
        boolean hasBlocker = required.stream().anyMatch(s -> s.is(blockerItem));
        helper.assertTrue(hasPipe,
                "Required items must include the cobble item pipe, got " + itemNames(required));
        helper.assertTrue(hasBlocker,
                "Required items must include the blocker plug — pluggables must be costed, not placed for free. Got "
                        + itemNames(required));
        helper.succeed();
    }

    /**
     * Pluggables must rotate with the blueprint. A blocker captured on NORTH must move to EAST under
     * a 90° clockwise rotation (otherwise a facade would stay on its captured face after a rotated
     * build and block the wrong pipe connection). getRotated must also leave the original untouched.
     */
    public static void pipeSchematicRotatesPluggableFaces(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));
        tile.replacePluggable(Direction.NORTH, new PluggableBlocker(BCTransportPlugs.blocker, tile, Direction.NORTH));

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, BlockPos.ZERO, abs, state, state.getBlock()));
        helper.assertTrue(schematic instanceof SchematicBlockPipe,
                "Pipe holder should route to SchematicBlockPipe, got " + schematic.getClass().getSimpleName());

        // Sanity: captured on NORTH.
        helper.assertFalse(NBTUtilBC.getCompound(plugsOf(schematic), "north").isEmpty(),
                "Captured pluggable should be on NORTH before rotation");

        // 90° clockwise: NORTH → EAST.
        ISchematicBlock rotated = schematic.getRotated(Rotation.CLOCKWISE_90);
        CompoundTag rotatedPlugs = plugsOf(rotated);
        helper.assertFalse(NBTUtilBC.getCompound(rotatedPlugs, "east").isEmpty(),
                "After CLOCKWISE_90 the pluggable must move to EAST");
        helper.assertTrue(NBTUtilBC.getCompound(rotatedPlugs, "north").isEmpty(),
                "After CLOCKWISE_90 the pluggable must no longer be on NORTH");
        helper.assertTrue(NBTUtilBC.getString(NBTUtilBC.getCompound(rotatedPlugs, "east"), "id", "").contains("blocker"),
                "The rotated EAST entry must still carry the blocker pluggable");

        // getRotated returns a copy — the original schematic's NBT must be untouched.
        helper.assertFalse(NBTUtilBC.getCompound(plugsOf(schematic), "north").isEmpty(),
                "getRotated must not mutate the original schematic (its pluggable should still be on NORTH)");
        helper.succeed();
    }

    private static CompoundTag plugsOf(ISchematicBlock schematic) {
        return NBTUtilBC.getCompound(schematic.getTileNbtForRender(), "plugs");
    }

    private static String itemNames(List<ItemStack> stacks) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(BuiltInRegistries.ITEM.getKey(stacks.get(i).getItem()));
        }
        return sb.append("]").toString();
    }
}
