/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeEventItem;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.WorkClaimMod;
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
                    // The cell the stripes pipe left was emptied and refilled in one tick, so a client never sees
                    // it empty: it gets the new pipe's data on the holder that still holds the stripes pipe.
                    TilePipeHolder client = clientView(helper, pipeAt(helper, pipeRel),
                            BCTransportItems.PIPE_STRIPES_ITEM.get());
                    helper.assertTrue(client.getPipe().getDefinition() == BCTransportPipes.cobbleItem,
                            "a client still holding the stripes pipe in the cell it left draws the placed pipe after"
                                    + " the update, got " + client.getPipe().getDefinition().identifier);
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
     * The release-2026-2-1 smoke's L rig, with the line carrying on past the corner: the pipe behind the stripes pipe
     * also feeds from a chest, so the straight retraction is refused and the stripes pipe retracts sideways onto the
     * pipe at the corner. It must stand there pointing back at the cell it left, the corner pipe and the void pipe
     * travelling down it into the rest of the line, nothing dropped. The corner cell was emptied and refilled in one
     * tick, so a client never sees it empty: it gets the stripes pipe's data on the holder that still holds the corner
     * pipe, and drew the corner pipe (with the stripes pipe's lens) until the next chunk reload.
     */
    public static void testVoidPipeRetractsSideways(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(0, 2, 3);
        BlockPos feederRel = chestRel.east();
        BlockPos pipeRel = feederRel.east();
        BlockPos cornerRel = pipeRel.north();
        BlockPos lineRel = cornerRel.north();
        helper.setBlock(chestRel, Blocks.CHEST);
        TilePipeHolder feeder = placePipe(helper, feederRel, BCTransportItems.PIPE_COBBLE_ITEM.get());
        TilePipeHolder tile = placePipe(helper, pipeRel, BCTransportItems.PIPE_STRIPES_ITEM.get());
        TilePipeHolder corner = placePipe(helper, cornerRel, BCTransportItems.PIPE_COBBLE_ITEM.get());
        TilePipeHolder line = placePipe(helper, lineRel, BCTransportItems.PIPE_COBBLE_ITEM.get());
        connect(feeder, tile, corner, line);
        PipeBehaviourStripes stripes = (PipeBehaviourStripes) tile.getPipe().getBehaviour();
        // Touching two pipes it points nowhere by itself; a direction on an unconnected face stays (the smoke rig
        // saves it the same way).
        stripes.setDirection(Direction.EAST);
        helper.assertTrue(feeder.getPipe().getConnectedType(Direction.WEST) == IPipe.ConnectedType.TILE,
                "precondition: the pipe behind feeds from the chest, so the straight retraction is refused");
        PipeBehaviourStripesDropTester.StripesRig rig = new PipeBehaviourStripesDropTester.StripesRig(
                helper, tile, stripes, (PipeFlowItems) tile.getPipe().getFlow());
        PipeEventItem.Drop drop = rig.drop(new ItemStack(BCTransportItems.PIPE_VOID_ITEM.get()));
        stripes.onDrop(drop);
        helper.assertTrue(drop.getStack().isEmpty(), "the void pipe is taken for the retraction");

        Item cobble = BCTransportItems.PIPE_COBBLE_ITEM.get();
        Item voidPipe = BCTransportItems.PIPE_VOID_ITEM.get();
        AABB around = new AABB(helper.absolutePos(pipeRel)).inflate(2.5);
        EntityArenaUtil.tickUntil(helper, POLL_TICKS,
                () -> isStripes(pipeAt(helper, cornerRel)) && helper.getBlockState(pipeRel).isAir(),
                () -> {
                    TilePipeHolder moved = pipeAt(helper, cornerRel);
                    Direction facing = ((PipeBehaviourStripes) moved.getPipe().getBehaviour()).direction;
                    helper.assertTrue(facing == Direction.SOUTH,
                            "the stripes pipe at the corner points back at the cell it left, got " + facing);
                    helper.assertTrue(countLine(helper, voidPipe, cornerRel, lineRel) == 1,
                            "the void pipe travels down the moved stripes pipe, found "
                                    + countLine(helper, voidPipe, cornerRel, lineRel));
                    helper.assertTrue(countLine(helper, cobble, cornerRel, lineRel) == 1,
                            "...and so does the corner pipe it took the place of, found "
                                    + countLine(helper, cobble, cornerRel, lineRel));
                    helper.assertTrue(isPipe(pipeAt(helper, feederRel), BCTransportPipes.cobbleItem)
                                    && isPipe(pipeAt(helper, lineRel), BCTransportPipes.cobbleItem),
                            "the pipe behind and the line past the corner stay where they were");
                    List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around);
                    helper.assertTrue(dropped.isEmpty(), "nothing is dropped, found " + dropped);

                    TilePipeHolder client = clientView(helper, moved, cobble);
                    helper.assertTrue(client.getPipe().getDefinition() == BCTransportPipes.stripesItem,
                            "a client still holding the corner pipe draws the stripes pipe after the update, got "
                                    + client.getPipe().getDefinition().identifier);
                    Direction clientFacing = ((PipeBehaviourStripes) client.getPipe().getBehaviour()).direction;
                    helper.assertTrue(clientFacing == Direction.SOUTH,
                            "...pointing SOUTH, got " + clientFacing);
                    helper.succeed();
                }, "the stripes pipe never retracted sideways onto the corner pipe", () -> {
                    discardItems(helper, around);
                    empty(pipeAt(helper, feederRel), pipeAt(helper, pipeRel), pipeAt(helper, cornerRel),
                            pipeAt(helper, lineRel));
                });
    }

    /**
     * What a client still holding {@code oldPipe} in {@code tile}'s cell makes of the update the server sends for
     * it: a cell emptied and refilled in one tick reaches the client as the same block, so its holder is kept and
     * only the block entity data arrives (vanilla's handleUpdateTag and data packet both load it).
     */
    private static TilePipeHolder clientView(GameTestHelper helper, TilePipeHolder tile, Item oldPipe) {
        TilePipeHolder client = new TilePipeHolder(tile.getBlockPos(), tile.getBlockState());
        client.setLevel(helper.getLevel());
        client.onPlacedBy(null, new ItemStack(oldPipe));
        CompoundTag update = tile.getUpdateTag(helper.getLevel().registryAccess());
        //? if >=1.21.10 {
        client.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), update));
        //?} else {
        /*client.loadWithComponents(update, helper.getLevel().registryAccess());*/
        //?}
        return client;
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
     * A claim that refuses the cell ahead (the placement event the moved stripes pipe posts there) puts the stripes
     * pipe back as it was, which makes it a new block entity. A second pipe stack that reached the same stripes pipe
     * in the same tick must still travel back down the line with the first, not be dropped where the pipe stands
     * because the pipe it named was replaced.
     */
    public static void testRefusedPlacementSendsBothSameTickStacksBack(GameTestHelper helper) {
        BlockPos feederRel = new BlockPos(1, 2, 2);
        BlockPos pipeRel = feederRel.east();
        BlockPos frontRel = pipeRel.east();
        TilePipeHolder feeder = placePipe(helper, feederRel, BCTransportItems.PIPE_COBBLE_ITEM.get());
        TilePipeHolder tile = placePipe(helper, pipeRel, BCTransportItems.PIPE_STRIPES_ITEM.get());
        connect(feeder, tile);
        PipeBehaviourStripes stripes = (PipeBehaviourStripes) tile.getPipe().getBehaviour();
        PipeBehaviourStripesDropTester.StripesRig rig = new PipeBehaviourStripesDropTester.StripesRig(
                helper, tile, stripes, (PipeFlowItems) tile.getPipe().getFlow());
        BlockPos front = helper.absolutePos(frontRel);
        WorkClaimMod claim = new WorkClaimMod(front::equals, front::equals);
        PipeEventItem.Drop first = rig.drop(new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get(), 2));
        stripes.onDrop(first);
        PipeEventItem.Drop second = rig.drop(new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));
        stripes.onDrop(second);
        helper.assertTrue(first.getStack().isEmpty() && second.getStack().isEmpty(),
                "both pipe stacks are taken for the extension");

        Item cobble = BCTransportItems.PIPE_COBBLE_ITEM.get();
        AABB around = new AABB(helper.absolutePos(pipeRel)).inflate(1.5);
        EntityArenaUtil.tickUntil(helper, POLL_TICKS,
                () -> claim.placeAsks(front) >= 1 && countLine(helper, cobble, feederRel, pipeRel) == 3,
                () -> {
                    helper.assertTrue(isStripes(pipeAt(helper, pipeRel)), "a refused move leaves the stripes pipe");
                    helper.assertTrue(helper.getBlockState(frontRel).isAir(), "...and places nothing in front");
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, around,
                            e -> e.getItem().is(cobble)).isEmpty(), "...and drops no pipe stack in the world");
                    helper.succeed();
                }, "both same-tick pipe stacks never came back down the line after a refused placement", () -> {
                    claim.close();
                    discardItems(helper, around);
                    empty(pipeAt(helper, feederRel), pipeAt(helper, pipeRel), pipeAt(helper, frontRel));
                });
    }

    /** How many of {@code item} travel in the pipes at {@code rels} (each looked up now: a re-placed pipe is new). */
    private static int countLine(GameTestHelper helper, Item item, BlockPos... rels) {
        int found = 0;
        for (BlockPos rel : rels) {
            TilePipeHolder tile = pipeAt(helper, rel);
            if (tile != null) {
                found += count(tile.getPipe().getFlow(), item);
            }
        }
        return found;
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
