/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;

import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWood;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Vanilla cauldrons have no block entity; NeoForge exposes their fluid handler as a block-only capability. Pins that
 * fluid pipes connect to one and move fluid in its whole-bucket steps (water and lava move 1000 mB at a
 * time, far more than a pipe's per-tick transfer). The tests drive the pipe by hand, so no chunk ticking matters.
 *
 * <p>The cauldron sits at (1,2,1), the pipe one block SOUTH of it, so the pipe's NORTH face points at it.
 */
public class CauldronPipeTester {

    private static final BlockPos CAULDRON = new BlockPos(1, 2, 1);
    private static final BlockPos PIPE = new BlockPos(1, 2, 2);
    private static final Direction TO_CAULDRON = Direction.NORTH;

    /** Ticks of hand-driven flow: enough to back a full section up behind the cauldron several times over. */
    private static final int FLOW_TICKS = 600;

    private static Pipe placePipe(GameTestHelper helper, Item pipeItem) {
        helper.setBlock(PIPE, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(PIPE, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(PIPE);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        Pipe pipe = tile.getPipe();
        pipe.markForUpdate();
        pipe.onTick();
        helper.assertTrue(pipe.isConnected(TO_CAULDRON) && pipe.getConnectedType(TO_CAULDRON) == ConnectedType.TILE,
            "Fluid pipe should connect to the cauldron as a TILE neighbour");
        return pipe;
    }

    /** A stone fluid pipe fed water fills an empty cauldron to the brim. */
    public static void testPipeFillsCauldron(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.CAULDRON);
        Pipe pipe = placePipe(helper, BCTransportItems.PIPE_STONE_FLUID.get());
        PipeFlowFluids flow = (PipeFlowFluids) pipe.getFlow();

        for (int i = 0; i < FLOW_TICKS; i++) {
            flow.insertFluidsForce(new FluidStack(Fluids.WATER, 200), null, false);
            pipe.onTick();
        }

        BlockState state = helper.getBlockState(CAULDRON);
        helper.assertTrue(state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) == 3,
            "Cauldron should be full of water after " + FLOW_TICKS + " ticks of flow, got " + state);
        helper.succeed();
    }

    /** A powered wooden fluid pipe empties a full water cauldron in one bucket. */
    public static void testWoodPipeDrainsCauldron(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        Pipe pipe = placePipe(helper, BCTransportItems.PIPE_WOOD_FLUID.get());

        ((PipeBehaviourWood) pipe.getBehaviour()).receivePower(512 * MjAPI.MJ, false);

        BlockState state = helper.getBlockState(CAULDRON);
        helper.assertTrue(state.is(Blocks.CAULDRON), "Wooden pipe should have drained the cauldron, got " + state);
        helper.succeed();
    }

    /** The delivery budget of the composter poll, counted from the cell ticking (an item travels only while the
     *  pipe's chunk ticks); the manifest's {@code max_ticks} stays above {@code CELL_READY_TICKS} plus this. */
    private static final int COMPOSTER_DELIVERY_WORK_TICKS = 110;

    /** An item pipe resting on a composter (also block-entity-less; it takes items only from above) delivers into
     *  it. Cake composts with certainty, so one delivered cake always raises the level. */
    public static void testItemPipeFeedsComposter(GameTestHelper helper) {
        BlockPos composterPos = new BlockPos(2, 2, 2);
        BlockPos pipePos = composterPos.above();
        EntityArenaUtil.forceLoadCell(helper);
        helper.setBlock(composterPos, Blocks.COMPOSTER);
        helper.setBlock(pipePos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pipePos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pipePos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));
        Pipe pipe = tile.getPipe();
        pipe.markForUpdate();
        // From the unconnected top face: the composter below is the pipe's only way out.
        ((PipeFlowItems) pipe.getFlow()).insertItemsForce(new ItemStack(Items.CAKE), Direction.UP, null, 0.08);

        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + COMPOSTER_DELIVERY_WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, COMPOSTER_DELIVERY_WORK_TICKS,
                () -> helper.getBlockState(composterPos).getValue(ComposterBlock.LEVEL) > 0,
                () -> (pipe.isConnected(Direction.DOWN) ? "the pipe is connected to the composter"
                    : "the pipe is not connected to the composter")
                    + ", composter " + helper.getBlockState(composterPos)),
            helper::succeed,
            "Item pipe should connect to the composter below it and compost the cake");
    }

    /** A wooden fluid pipe already carrying water must not pull lava into it: the pipe holds one fluid, and lava
     *  extracted into it would leave labelled as water. */
    public static void testWoodPipeKeepsItsFluid(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.LAVA_CAULDRON);
        Pipe pipe = placePipe(helper, BCTransportItems.PIPE_WOOD_FLUID.get());
        ((PipeFlowFluids) pipe.getFlow()).insertFluidsForce(new FluidStack(Fluids.WATER, 100), null, false);

        ((PipeBehaviourWood) pipe.getBehaviour()).receivePower(512 * MjAPI.MJ, false);

        BlockState state = helper.getBlockState(CAULDRON);
        helper.assertTrue(state.is(Blocks.LAVA_CAULDRON),
            "A wooden pipe holding water must not extract lava, but the cauldron is now " + state);
        helper.succeed();
    }
}
