/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.PipeEventItem;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.BCTransportPipes;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.TravellingItem;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * The stripes handlers that act on the pipe line or on what stands in front rather than on the item in hand, both
 * dead in this port (and the minecart one in 8.0.x) until wired back the way 7.1.x ran them: a minecart in front of a
 * powered stripes pipe is broken and comes back down the pipe (after the same attack ask a player's swing posts), and
 * an item pipe reaching the open end extends the line by one cell (the stripes pipe moves forward, the fed pipe is
 * placed behind it), a void pipe retracts it, and a move a claim refuses sends the pipe stack back.
 */
public class PipeBehaviourStripesMovesTester {

    private static final int POLL_TICKS = 60;

    /**
     * A powered stripes pipe breaks the minecart in front of it, before its block-break logic (the cell is air, so
     * there is nothing to dig): the cart is asked about as a player's attack would be, a refusal keeps it and costs
     * no power, and once allowed the cart is gone and its item travels back down the pipe for the fixed cost.
     */
    public static void testMinecartInFrontIsBrokenAfterAnAttackAsk(GameTestHelper helper) {
        BlockPos feederRel = new BlockPos(1, 2, 2);
        BlockPos pipeRel = feederRel.east();
        BlockPos frontRel = pipeRel.east();
        EntityArenaUtil.forceLoadEntityArena(helper, frontRel);
        TilePipeHolder feeder = placePipe(helper, feederRel, BCTransportItems.PIPE_COBBLE_ITEM.get());
        TilePipeHolder tile = placePipe(helper, pipeRel, BCTransportItems.PIPE_STRIPES_ITEM.get());
        connect(feeder, tile);
        PipeBehaviourStripes stripes = (PipeBehaviourStripes) tile.getPipe().getBehaviour();
        helper.assertTrue(stripes.direction == Direction.EAST,
                "precondition: the fed stripes pipe points away from its feeder, got " + stripes.direction);

        Entity[] cart = new Entity[1];
        int[] asks = { 0 };
        boolean[] refuse = { true };
        int[] phase = { 0 };
        long[] storedBefore = { 0 };
        Consumer<AttackEntityEvent> claim = event -> {
            if (cart[0] != null && event.getTarget() == cart[0]) {
                asks[0]++;
                if (refuse[0]) {
                    event.setCanceled(true);
                }
            }
        };
        NeoForge.EVENT_BUS.addListener(AttackEntityEvent.class, claim);
        EntityArenaUtil.tickUntil(helper, POLL_TICKS, () -> {
            switch (phase[0]) {
                case 0 -> {
                    if (!EntityArenaUtil.isEntityTicking(helper, frontRel)) {
                        return false;
                    }
                    //? if >=26.2 {
                    cart[0] = helper.spawn(net.minecraft.world.entity.EntityTypes.MINECART, frontRel);
                    //?} else {
                    /*cart[0] = helper.spawn(net.minecraft.world.entity.EntityType.MINECART, frontRel);*/
                    //?}
                    cart[0].setNoGravity(true);
                    stripes.receivePower(16 * MjAPI.MJ, false);
                    storedBefore[0] = stripes.getStoredPower();
                    phase[0] = 1;
                    return false;
                }
                case 1 -> {
                    if (asks[0] == 0) {
                        return false;
                    }
                    helper.assertTrue(!cart[0].isRemoved(), "a refused attack ask keeps the cart");
                    helper.assertTrue(stripes.getStoredPower() == storedBefore[0],
                            "...and costs the pipe no power, stored " + stripes.getStoredPower());
                    refuse[0] = false;
                    phase[0] = 2;
                    return false;
                }
                default -> {
                    return cart[0].isRemoved();
                }
            }
        }, () -> {
            helper.assertTrue(count(tile.getPipe().getFlow(), Items.MINECART) == 1,
                    "the broken cart's item travels back down the pipe, found "
                            + count(tile.getPipe().getFlow(), Items.MINECART));
            helper.assertTrue(stripes.getStoredPower() == storedBefore[0] - PipeBehaviourStripes.BLOCK_HANDLER_COST,
                    "breaking the cart costs the fixed block-handler price, stored " + stripes.getStoredPower());
            helper.succeed();
        }, "the stripes pipe never broke the minecart in front of it", () -> {
            NeoForge.EVENT_BUS.unregister(claim);
            EntityArenaUtil.discard(cart[0]);
            empty(feeder, tile);
        });
    }

    /**
     * An item pipe that reaches the open end of a stripes pipe extends the line: the stripes pipe moves one cell
     * forward and ONE of the fed pipes is placed in the cell it left; the rest of the stack travels back down the
     * moved stripes pipe. 7.1.x (PipeExtensionListener) and 8.0.x (PipeExtensionManager) both did this at the end of
     * the tick; the port had the handler but no manager, so the dispenser fallback placed the pipe in front instead.
     * A second pipe stack reaching the same stripes pipe in the same tick finds it already moved, and travels back
     * down it too (to extend the line when it comes round again), instead of being dropped where the pipe stood.
     */
    public static void testItemPipeExtendsTheLine(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos frontRel = pipeRel.east();
        PipeBehaviourStripesDropTester.StripesRig rig =
                PipeBehaviourStripesDropTester.StripesRig.build(helper, pipeRel, Direction.EAST);
        PipeEventItem.Drop drop = rig.drop(new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get(), 2));
        rig.stripes().onDrop(drop);
        helper.assertTrue(drop.getStack().isEmpty(), "the pipe stack is taken for the extension");
        PipeEventItem.Drop sameTick = rig.drop(new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));
        rig.stripes().onDrop(sameTick);
        helper.assertTrue(sameTick.getStack().isEmpty(), "a second pipe stack in the same tick is taken too");

        EntityArenaUtil.tickUntil(helper, POLL_TICKS,
                () -> isStripes(pipeAt(helper, frontRel)) && isPipe(pipeAt(helper, pipeRel), BCTransportPipes.cobbleItem),
                () -> {
                    TilePipeHolder moved = pipeAt(helper, frontRel);
                    helper.assertTrue(count(moved.getPipe().getFlow(), BCTransportItems.PIPE_COBBLE_ITEM.get()) == 2,
                            "one fed pipe was placed, and the rest of its stack and the same-tick stack travel back"
                                    + " down the moved stripes pipe, found "
                                    + count(moved.getPipe().getFlow(), BCTransportItems.PIPE_COBBLE_ITEM.get()));
                    helper.succeed();
                }, "the stripes pipe never moved forward with the fed pipe behind it",
                () -> empty(pipeAt(helper, pipeRel), pipeAt(helper, frontRel)));
    }

    /**
     * A void pipe reaching a stripes pipe retracts the line: the stripes pipe takes the place of the pipe behind it,
     * and that pipe and the void pipe travel back down the stripes pipe, into the rest of the line.
     */
    public static void testVoidPipeRetractsTheLine(GameTestHelper helper) {
        BlockPos sourceRel = new BlockPos(0, 2, 2);
        BlockPos feederRel = sourceRel.east();
        BlockPos pipeRel = feederRel.east();
        TilePipeHolder source = placePipe(helper, sourceRel, BCTransportItems.PIPE_COBBLE_ITEM.get());
        TilePipeHolder feeder = placePipe(helper, feederRel, BCTransportItems.PIPE_COBBLE_ITEM.get());
        TilePipeHolder tile = placePipe(helper, pipeRel, BCTransportItems.PIPE_STRIPES_ITEM.get());
        connect(source, feeder, tile);
        PipeBehaviourStripes stripes = (PipeBehaviourStripes) tile.getPipe().getBehaviour();
        helper.assertTrue(stripes.direction == Direction.EAST,
                "precondition: the fed stripes pipe points away from its feeder, got " + stripes.direction);
        PipeBehaviourStripesDropTester.StripesRig rig = new PipeBehaviourStripesDropTester.StripesRig(
                helper, tile, stripes, (PipeFlowItems) tile.getPipe().getFlow());
        PipeEventItem.Drop drop = rig.drop(new ItemStack(BCTransportItems.PIPE_VOID_ITEM.get()));
        stripes.onDrop(drop);
        helper.assertTrue(drop.getStack().isEmpty(), "the void pipe is taken for the retraction");

        EntityArenaUtil.tickUntil(helper, POLL_TICKS,
                () -> isStripes(pipeAt(helper, feederRel)) && helper.getBlockState(pipeRel).isAir(),
                () -> {
                    PipeFlow flow = pipeAt(helper, feederRel).getPipe().getFlow();
                    helper.assertTrue(count(flow, BCTransportItems.PIPE_VOID_ITEM.get()) == 1,
                            "the void pipe travels back down the retracted stripes pipe, found "
                                    + count(flow, BCTransportItems.PIPE_VOID_ITEM.get()));
                    helper.assertTrue(count(flow, BCTransportItems.PIPE_COBBLE_ITEM.get()) == 1,
                            "...and so does the pipe it took the place of, found "
                                    + count(flow, BCTransportItems.PIPE_COBBLE_ITEM.get()));
                    helper.succeed();
                }, "the stripes pipe never retracted onto the pipe behind it",
                () -> empty(pipeAt(helper, sourceRel), pipeAt(helper, feederRel), pipeAt(helper, pipeRel)));
    }

    /**
     * Moving the stripes pipe takes it out of its cell, so break protection decides first, as the pipe's owner: a
     * claim that refuses the break leaves the line as it was, nothing placed in front, and the whole pipe stack
     * travels back down the pipe.
     */
    public static void testRefusedExtensionSendsTheStackBack(GameTestHelper helper) {
        BlockPos feederRel = new BlockPos(1, 2, 2);
        BlockPos pipeRel = feederRel.east();
        BlockPos frontRel = pipeRel.east();
        TilePipeHolder feeder = placePipe(helper, feederRel, BCTransportItems.PIPE_COBBLE_ITEM.get());
        TilePipeHolder tile = placePipe(helper, pipeRel, BCTransportItems.PIPE_STRIPES_ITEM.get());
        connect(feeder, tile);
        PipeBehaviourStripes stripes = (PipeBehaviourStripes) tile.getPipe().getBehaviour();
        PipeBehaviourStripesDropTester.StripesRig rig = new PipeBehaviourStripesDropTester.StripesRig(
                helper, tile, stripes, (PipeFlowItems) tile.getPipe().getFlow());
        ClaimProtectionMod claim = new ClaimProtectionMod(helper.absolutePos(pipeRel), true);
        PipeEventItem.Drop drop = rig.drop(new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get(), 2));
        stripes.onDrop(drop);

        EntityArenaUtil.tickUntil(helper, POLL_TICKS,
                () -> claim.asks() >= 1 && rig.count(BCTransportItems.PIPE_COBBLE_ITEM.get()) == 2,
                () -> {
                    helper.assertTrue(isStripes(pipeAt(helper, pipeRel)), "a refused move leaves the stripes pipe");
                    helper.assertTrue(helper.getBlockState(frontRel).isAir(), "...and places nothing in front");
                    helper.succeed();
                }, "a refused extension never sent the whole pipe stack back down the pipe", () -> {
                    claim.close();
                    empty(feeder, pipeAt(helper, pipeRel), pipeAt(helper, frontRel));
                });
    }

    /**
     * A void pipe reaching a stripes pipe that has no pipe behind it to take up and nothing it is connected to: there
     * is no line to send the void pipe back down, so it is dropped where the pipe stands. Sent into the lone stripes
     * pipe, it came straight back out of the pipe's own open end and asked for the same impossible retraction again
     * every few ticks (and a returned item pipe re-extended the line).
     */
    public static void testLoneStripesPipeDropsWhatItCannotSendBack(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        PipeBehaviourStripesDropTester.StripesRig rig =
                PipeBehaviourStripesDropTester.StripesRig.build(helper, pipeRel, Direction.EAST);
        PipeEventItem.Drop drop = rig.drop(new ItemStack(BCTransportItems.PIPE_VOID_ITEM.get()));
        rig.stripes().onDrop(drop);
        helper.assertTrue(drop.getStack().isEmpty(), "the void pipe is taken for the retraction");

        AABB around = new AABB(helper.absolutePos(pipeRel)).inflate(1.5);
        EntityArenaUtil.tickUntil(helper, POLL_TICKS,
                () -> !helper.getLevel().getEntitiesOfClass(ItemEntity.class, around,
                        e -> e.getItem().is(BCTransportItems.PIPE_VOID_ITEM.get())).isEmpty(),
                () -> {
                    helper.assertTrue(rig.count(BCTransportItems.PIPE_VOID_ITEM.get()) == 0,
                            "nothing is sent into the lone stripes pipe, found "
                                    + rig.count(BCTransportItems.PIPE_VOID_ITEM.get()));
                    helper.assertTrue(isStripes(pipeAt(helper, pipeRel)), "...and the stripes pipe stays put");
                    discardItems(helper, around);
                    helper.succeed();
                }, "the void pipe a lone stripes pipe could not retract with was never dropped", () -> {
                    discardItems(helper, around);
                    rig.close();
                });
    }

    private static void discardItems(GameTestHelper helper, AABB box) {
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            item.discard();
        }
    }

    private static TilePipeHolder placePipe(GameTestHelper helper, BlockPos rel, Item pipeItem) {
        helper.setBlock(rel, BCTransportBlocks.PIPE_HOLDER.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof TilePipeHolder tile)) {
            helper.fail("no pipe holder at " + rel);
            throw new IllegalStateException();
        }
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    /** Runs each pipe's connection update now (the server tick path), so they connect before the test goes on. */
    private static void connect(TilePipeHolder... tiles) {
        for (TilePipeHolder tile : tiles) {
            tile.getPipe().markForUpdate();
        }
        for (TilePipeHolder tile : tiles) {
            ((Pipe) tile.getPipe()).onTick();
        }
    }

    @Nullable
    private static TilePipeHolder pipeAt(GameTestHelper helper, BlockPos rel) {
        BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        return be instanceof TilePipeHolder tile && tile.getPipe() != null ? tile : null;
    }

    private static boolean isStripes(@Nullable TilePipeHolder tile) {
        return tile != null && tile.getPipe().getBehaviour() instanceof PipeBehaviourStripes;
    }

    private static boolean isPipe(@Nullable TilePipeHolder tile, buildcraft.api.transport.pipe.PipeDefinition def) {
        return tile != null && tile.getPipe().getDefinition() == def;
    }

    private static int count(PipeFlow flow, Item item) {
        int found = 0;
        if (flow instanceof PipeFlowItems items) {
            for (TravellingItem travelling : items.getAllItemsForRender()) {
                if (travelling.getStack().is(item)) {
                    found += travelling.getStack().getCount();
                }
            }
        }
        return found;
    }

    /** Empties the cargo of each pipe, so removing the arena's pipes drops nothing into neighbouring tests. */
    private static void empty(@Nullable TilePipeHolder... tiles) {
        for (TilePipeHolder tile : tiles) {
            if (tile != null && tile.getPipe().getFlow() instanceof PipeFlowItems items) {
                for (TravellingItem item : items.getAllItemsForRender()) {
                    item.getStack().setCount(0);
                }
            }
        }
    }
}
