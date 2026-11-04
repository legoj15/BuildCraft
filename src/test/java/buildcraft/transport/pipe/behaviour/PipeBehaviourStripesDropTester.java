/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.EnumHandlerPriority;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerBlock;
import buildcraft.api.transport.IStripesHandlerItem;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.PipeEventItem;

import buildcraft.core.BCCoreConfig;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.TestOnlyBlocks;
import buildcraft.lib.test.WorkClaimMod;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.pipe.StripesRegistry;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.TravellingItem;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Pins the stripes pipe's item drop through its (now cached, leased) fake player: a stack of blocks reaching the
 * pipe's open end places ONE block and the remainder goes back down the pipe. The travelling stack is handed to the
 * fake player's main hand and the handler shrinks that same stack; the pipe then drains the player's inventory back
 * into the flow before the lease scrubs it — if the main hand were not part of that drain, the remainder would be
 * silently deleted when the lease closes.
 */
public class PipeBehaviourStripesDropTester {

    public static void testDropPlacesOneAndReturnsRemainder(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = pipeRel.above();
        helper.setBlock(pipeRel, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pipeRel, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pipeRel);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_STRIPES_ITEM.get()));

        if (!(tile.getPipe().getBehaviour() instanceof PipeBehaviourStripes stripes)) {
            helper.fail("the stripes pipe item did not produce a stripes behaviour");
            return;
        }
        // Set synchronously and used in the same call: an unconnected stripes pipe clears its direction on tick.
        stripes.direction = Direction.UP;

        ItemStack travelling = new ItemStack(Items.COBBLESTONE, 5);
        ItemEntity carrier = new ItemEntity(helper.getLevel(), 0, 0, 0, travelling);
        PipeFlowItems flow = (PipeFlowItems) tile.getPipe().getFlow();
        PipeEventItem.Drop drop = new PipeEventItem.Drop(tile, (IFlowItems) flow, carrier);

        stripes.onDrop(drop);

        helper.assertBlockPresent(Blocks.COBBLESTONE, targetRel);
        helper.assertTrue(drop.getStack().isEmpty(), "a handled drop no longer carries a stack");

        List<TravellingItem> returned = flow.getAllItemsForRender();
        int returnedCobble = 0;
        for (TravellingItem item : returned) {
            ItemStack stack = item.getStack();
            if (stack.is(Items.COBBLESTONE)) {
                returnedCobble += stack.getCount();
            }
        }
        helper.assertTrue(returnedCobble == 4,
                "the 4 unplaced cobblestone must travel back down the pipe, found " + returnedCobble);

        // Empty the cargo so removing the arena's pipe drops nothing into neighbouring tests.
        for (TravellingItem item : returned) {
            item.getStack().setCount(0);
        }
        helper.succeed();
    }

    /**
     * A hoe reaching the open end of a stripes pipe asks the right-click-block event before it tills, as a player's
     * right-click does: a claim mod that cancels that event keeps the dirt dirt and the hoe unconsumed, and the same
     * pipe tills when nothing refuses. On 26.3 that event is the only ask a till gets (hoes became a block
     * transformer, which posts no tool-modification event), so a pipe that called {@code useOn} directly would till
     * a claimed block. The helper the pipe uses also keeps vanilla's gate: a hoe on the player's cooldown tills
     * nothing even when nothing refuses.
     */
    public static void testHoeAsksTheRightClickEventBeforeTilling(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos soilRel = pipeRel.above();
        BlockPos soil = helper.absolutePos(soilRel);
        helper.setBlock(pipeRel, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pipeRel, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pipeRel);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_STRIPES_ITEM.get()));

        if (!(tile.getPipe().getBehaviour() instanceof PipeBehaviourStripes stripes)) {
            helper.fail("the stripes pipe item did not produce a stripes behaviour");
            return;
        }
        // Set synchronously and used in the same call: an unconnected stripes pipe clears its direction on tick.
        stripes.direction = Direction.UP;
        helper.setBlock(soilRel, Blocks.DIRT);
        PipeFlowItems flow = (PipeFlowItems) tile.getPipe().getFlow();

        try (WorkClaimMod claim = new WorkClaimMod(soil::equals, soil::equals)) {
            PipeEventItem.Drop refused = hoeDrop(helper, tile, flow);
            stripes.onDrop(refused);

            helper.assertTrue(claim.toolAsks(soil) >= 1,
                    "the pipe asks the right-click event for the block it would till, got " + claim.toolAsks(soil));
            helper.assertTrue(helper.getBlockState(soilRel).is(Blocks.DIRT), "a refused till leaves the dirt alone");
            helper.assertTrue(refused.getStack().is(Items.DIAMOND_HOE),
                    "a refused hoe is not consumed by the pipe, got " + refused.getStack());
        }

        try (WorkClaimMod claim = new WorkClaimMod(soil::equals, pos -> false)) {
            PipeEventItem.Drop allowed = hoeDrop(helper, tile, flow);
            stripes.onDrop(allowed);

            helper.assertTrue(claim.toolAsks(soil) >= 1,
                    "the pipe asks the right-click event when nothing refuses too, got " + claim.toolAsks(soil));
            helper.assertTrue(helper.getBlockState(soilRel).is(Blocks.FARMLAND), "an allowed till tills the dirt");
            helper.assertTrue(allowed.getStack().isEmpty(), "a handled drop no longer carries a stack");
        }

        // Empty the cargo (the used hoe goes back down the pipe) so removing the arena's pipe drops nothing into
        // neighbouring tests.
        for (TravellingItem item : flow.getAllItemsForRender()) {
            item.getStack().setCount(0);
        }

        // Vanilla's item gate, on the shared helper: a hoe on cooldown is not used. The fresh lease at the end is the
        // control, so the refusal above is the gate and not a hit that misses.
        helper.setBlock(soilRel, Blocks.DIRT);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false);
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(helper.getLevel())) {
            ServerPlayer player = lease.player();
            ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
            // In the hand, as the helper's contract has it: the right-click event reads the hand, not the stack.
            player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            //? if >=1.21.10 {
            player.getCooldowns().addCooldown(hoe, 200);
            //?} else {
            /*player.getCooldowns().addCooldown(hoe.getItem(), 200);*/
            //?}
            helper.assertTrue(!BlockUtil.useItemOnBlock(helper.getLevel(), player, InteractionHand.MAIN_HAND, hoe, hit),
                    "a hoe on cooldown is not used");
            helper.assertTrue(helper.getBlockState(soilRel).is(Blocks.DIRT), "...so the dirt stays dirt");
        }
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(helper.getLevel())) {
            ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
            lease.player().setItemInHand(InteractionHand.MAIN_HAND, hoe);
            helper.assertTrue(
                    BlockUtil.useItemOnBlock(helper.getLevel(), lease.player(), InteractionHand.MAIN_HAND, hoe, hit),
                    "control: the same hoe off cooldown tills");
            helper.assertTrue(helper.getBlockState(soilRel).is(Blocks.FARMLAND), "...the dirt");
        }
        helper.succeed();
    }

    /**
     * A claim that guards only right-clicks stops a stripes pipe's hoe and rocket, not just a claim that also cancels
     * the placement: the pipe's dispenser fallback used to call {@code ItemStack#useOn} with no ask, so once the hoe
     * handler's own ask was refused the fallback tilled the same dirt (and launched any rocket) behind the claim's
     * back. The pipe points sideways because the fallback clicks the target's side face, which a hoe tills from; a
     * pipe pointing up clicks the soil's underside, which it does not. A refused click ends the use at the first
     * refusal: the pipe's own block, which strategy 2 of the fallback would name, is never asked, and the item is
     * not consumed. The rocket also shows the fallback launches it, which is why the upstream "use" handler for
     * rockets is gone.
     */
    public static void testRefusedClickEndsTheDispenserFallthrough(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = pipeRel.east();
        BlockPos pipe = helper.absolutePos(pipeRel);
        BlockPos target = helper.absolutePos(targetRel);
        Set<BlockPos> watched = Set.of(pipe, target, target.below());
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            helper.setBlock(targetRel, Blocks.DIRT);
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, target::equals, pos -> false, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.DIAMOND_HOE));
                rig.stripes.onDrop(refused);

                helper.assertTrue(claim.toolAsks(target) >= 1,
                        "the pipe asks the right-click event for the dirt it would till");
                helper.assertTrue(helper.getBlockState(targetRel).is(Blocks.DIRT),
                        "a claim that refuses only the right-click keeps the dirt dirt");
                helper.assertTrue(refused.getStack().is(Items.DIAMOND_HOE),
                        "...and the refused hoe is not consumed, got " + refused.getStack());
                helper.assertTrue(claim.toolAsks(pipe) == 0,
                        "a refused click ends the use: the pipe's own block is never asked about, got "
                                + claim.toolAsks(pipe));
            }

            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, target::equals, pos -> false, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.FIREWORK_ROCKET, 3));
                rig.stripes.onDrop(refused);

                helper.assertTrue(claim.toolAsks(target) >= 1,
                        "the pipe asks the right-click event for the rocket's block");
                helper.assertTrue(refused.getStack().is(Items.FIREWORK_ROCKET) && refused.getStack().getCount() == 3,
                        "a refused rocket is not launched, got " + refused.getStack());
            }

            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false, pos -> false, pos -> false)) {
                PipeEventItem.Drop allowed = rig.drop(new ItemStack(Items.FIREWORK_ROCKET, 3));
                rig.stripes.onDrop(allowed);

                helper.assertTrue(allowed.getStack().isEmpty(),
                        "an allowed rocket is launched by the dispenser fallback");
                int returned = 0;
                for (TravellingItem item : rig.flow.getAllItemsForRender()) {
                    if (item.getStack().is(Items.FIREWORK_ROCKET)) {
                        returned += item.getStack().getCount();
                    }
                }
                helper.assertTrue(returned == 2, "one rocket was used and the other 2 travel back, found " + returned);
            }
        }
        helper.succeed();
    }

    /**
     * A refusal ends the pipe's whole dispatch, not just the handler that was refused. A claim that refuses the
     * right-click on the soil keeps a seed from being planted by any handler: the planting handler asks the soil, is
     * refused and gives up, and the dispenser fallback after it would click the empty cell above the soil, a cell the
     * claim was never asked about, where the seed's block item places itself on the soil. The placement event and
     * that cell are left open on purpose, so only the dispatch can stop it. Nether wart on soul sand, because wheat
     * needs light the arena does not have.
     */
    public static void testRefusedSoilClickEndsTheSeedFallthrough(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos cropRel = pipeRel.east();
        BlockPos soilRel = cropRel.below();
        BlockPos crop = helper.absolutePos(cropRel);
        BlockPos soil = helper.absolutePos(soilRel);
        Set<BlockPos> watched = Set.of(soil, crop);
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            helper.setBlock(soilRel, Blocks.SOUL_SAND);
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, soil::equals, pos -> false, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.NETHER_WART, 3));
                rig.stripes.onDrop(refused);

                helper.assertTrue(claim.toolAsks(soil) >= 1,
                        "the pipe asks the right-click event for the soil it would plant on");
                helper.assertTrue(helper.getBlockState(cropRel).isAir(),
                        "a claim that refuses the soil's right-click keeps any handler from planting above it");
                helper.assertTrue(refused.getStack().is(Items.NETHER_WART) && refused.getStack().getCount() == 3,
                        "...and the refused seed is not consumed, got " + refused.getStack());
            }

            // Control: nothing refuses, so the same pipe plants (the claim only counts).
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false, pos -> false, pos -> false)) {
                PipeEventItem.Drop allowed = rig.drop(new ItemStack(Items.NETHER_WART, 3));
                rig.stripes.onDrop(allowed);

                helper.assertTrue(helper.getBlockState(cropRel).is(Blocks.NETHER_WART), "an allowed seed is planted");
                helper.assertTrue(allowed.getStack().isEmpty(), "a handled drop no longer carries a stack");
            }
        }
        helper.succeed();
    }

    /**
     * A claim stops a stripes pipe placing a block whichever event it guards, and the refused block item is not
     * consumed: one that cancels the right-click, and one that cancels only the placement. The block-placing handler
     * used to call {@code Item#useOn} itself, which neither asks the right-click event nor goes through
     * {@code ItemStack#useOn}, where NeoForge captures the placement and posts the event a claim cancels, so it
     * placed the block the dispenser fallback had just been refused, and it reported success when nothing was
     * placed. The fallback's first click is the same click, so that handler is gone.
     */
    public static void testRefusedClaimStopsAStripesPipePlacingABlock(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = pipeRel.east();
        BlockPos pipe = helper.absolutePos(pipeRel);
        BlockPos target = helper.absolutePos(targetRel);
        Set<BlockPos> watched = Set.of(pipe, target);
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, target::equals, pos -> false, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.COBBLESTONE, 3));
                rig.stripes.onDrop(refused);

                helper.assertTrue(claim.toolAsks(target) >= 1,
                        "the pipe asks the right-click event for the block it would place");
                helper.assertTrue(helper.getBlockState(targetRel).isAir(),
                        "a claim that refuses only the right-click keeps the block from being placed");
                helper.assertTrue(refused.getStack().is(Items.COBBLESTONE) && refused.getStack().getCount() == 3,
                        "...and the refused block item is not consumed, got " + refused.getStack());
                helper.assertTrue(claim.toolAsks(pipe) == 0,
                        "a refused click ends the use: the pipe's own block is never asked about, got "
                                + claim.toolAsks(pipe));
            }

            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false, target::equals, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.COBBLESTONE, 3));
                rig.stripes.onDrop(refused);

                helper.assertTrue(claim.placeAsks(target) >= 1, "the placement event is posted for the block");
                helper.assertTrue(helper.getBlockState(targetRel).isAir(),
                        "a claim that refuses only the placement keeps the block from being placed");
                helper.assertTrue(refused.getStack().is(Items.COBBLESTONE) && refused.getStack().getCount() == 3,
                        "...and the refused block item is not consumed, got " + refused.getStack());
            }

            // Control: nothing refuses, so the block is placed (the claim only counts).
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false, pos -> false, pos -> false)) {
                PipeEventItem.Drop allowed = rig.drop(new ItemStack(Items.COBBLESTONE, 3));
                rig.stripes.onDrop(allowed);

                helper.assertTrue(helper.getBlockState(targetRel).is(Blocks.COBBLESTONE),
                        "an allowed placement places");
                helper.assertTrue(allowed.getStack().isEmpty(), "a handled drop no longer carries a stack");
            }
        }
        helper.succeed();
    }

    /**
     * A stripes pipe's fire charge or flint and steel lights the cell in front of it only where the owner may build.
     * The dispenser's first click names that cell and the item passes it; the second click, which sets the fire, names
     * the pipe's own block. So for a claim that leaves clicks alone, what guards the cell is the placement event
     * NeoForge posts for the fire, because the click goes through {@code ItemStack#useOn}, which captures the blocks
     * an item sets and reverts them when the event is cancelled (issue #78 suspected there was no such event). A
     * handler that called {@code Item#useOn} directly would skip that capture and light a claimed cell. The refused
     * fire charge is not consumed. A refused flint and steel's durability is not checked: NeoForge's revert puts back
     * the count but re-applies the stack's own live component map, so it keeps the wear, as a player's does.
     */
    public static void testFireNeedsThePlacementAskAtTheTarget(GameTestHelper helper) {
        helper.assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected is off, so the pipe asks claims at all");
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = pipeRel.east();
        BlockPos target = helper.absolutePos(targetRel);
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            helper.setBlock(targetRel.below(), Blocks.STONE);
            for (Item lighter : List.of(Items.FIRE_CHARGE, Items.FLINT_AND_STEEL)) {
                // Every way out puts the cell out, so a failed refusal leaves no fire in the arena.
                try {
                    try (WorkClaimMod claim = new WorkClaimMod(target::equals, pos -> false, target::equals,
                            pos -> false)) {
                        PipeEventItem.Drop refused = rig.drop(new ItemStack(lighter));
                        rig.stripes.onDrop(refused);

                        helper.assertTrue(claim.placeAsks(target) >= 1,
                                "the pipe asks the placement question for the cell its " + lighter + " would light");
                        helper.assertTrue(helper.getBlockState(targetRel).isAir(),
                                "a claim on the target cell keeps " + lighter + " from lighting it, got "
                                        + helper.getBlockState(targetRel));
                        helper.assertTrue(refused.getStack().is(lighter) && refused.getStack().getCount() == 1,
                                "...and the refused " + lighter + " is not consumed, got " + refused.getStack());
                    }
                    rig.empty();

                    // Control: nothing refuses, so the same pipe lights the cell (the claim only counts).
                    try (WorkClaimMod claim = new WorkClaimMod(target::equals, pos -> false, pos -> false,
                            pos -> false)) {
                        rig.stripes.onDrop(rig.drop(new ItemStack(lighter)));
                        helper.assertTrue(helper.getBlockState(targetRel).is(Blocks.FIRE),
                                "an allowed " + lighter + " lights the cell, got " + helper.getBlockState(targetRel));
                    }
                } finally {
                    helper.setBlock(targetRel, Blocks.AIR);
                    rig.empty();
                }
            }
        }
        helper.succeed();
    }

    /**
     * Shearing a block away is a break, so a stripes pipe carrying shears asks the same break protection its own
     * digging does, as the pipe's owner standing on the block (as {@code BlockUtil#canMachineBreak} and a robot ask
     * it, so a claim that judges where the actor stands answers all three alike): a claim that cancels the break keeps
     * the leaves standing and the shears unused, and a fresh pipe shears them when nothing refuses. The shears handler
     * used to remove the block and emit its drops with no ask at all, and then asked as a player standing in the pipe.
     * The refusal is remembered as the pipe's digging remembers one: a second pair of shears is not asked about the
     * same block again inside the refusal window.
     */
    public static void testShearsAskBreakProtectionBeforeCutting(GameTestHelper helper) {
        helper.assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos leavesRel = pipeRel.east();
        BlockPos leaves = helper.absolutePos(leavesRel);
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            helper.setBlock(leavesRel, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            // The right-click claim only counts: a refused break ends the dispatch, so the dispenser fallback never
            // right-clicks the leaves with the shears it was just refused cutting.
            try (ClaimProtectionMod claim = new ClaimProtectionMod(leaves, true);
                    WorkClaimMod clicks = new WorkClaimMod(leaves::equals, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.SHEARS));
                rig.stripes.onDrop(refused);

                helper.assertTrue(claim.asks() == 1, "the pipe asks break protection about the block it would shear");
                helper.assertTrue(leaves.equals(claim.askedFrom.get(0)),
                        "...as a player standing on that block, asked from " + claim.askedFrom.get(0));
                helper.assertTrue(helper.getBlockState(leavesRel).is(Blocks.OAK_LEAVES),
                        "a claim that refuses the break keeps the leaves standing");
                helper.assertTrue(refused.getStack().is(Items.SHEARS) && !refused.getStack().isDamaged(),
                        "...and the refused shears are not used, got " + refused.getStack());
                helper.assertTrue(clicks.toolAsks(leaves) == 0,
                        "a refused break ends the use: the leaves are never right-clicked with the shears, got "
                                + clicks.toolAsks(leaves));

                PipeEventItem.Drop again = rig.drop(new ItemStack(Items.SHEARS));
                rig.stripes.onDrop(again);
                helper.assertTrue(claim.asks() == 1,
                        "a remembered refusal is not asked again by the next shears, asks " + claim.asks());
                helper.assertTrue(again.getStack().is(Items.SHEARS) && !again.getStack().isDamaged(),
                        "...and those shears are not used either, got " + again.getStack());
            }
        }

        // Control: nothing refuses, so a fresh pipe (no remembered refusal) cuts (the claim only counts).
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST);
                ClaimProtectionMod claim = new ClaimProtectionMod(leaves, false)) {
            PipeEventItem.Drop allowed = rig.drop(new ItemStack(Items.SHEARS));
            rig.stripes.onDrop(allowed);

            helper.assertTrue(helper.getBlockState(leavesRel).isAir(), "an allowed shearing removes the leaves");
            helper.assertTrue(allowed.getStack().isEmpty(), "a handled drop no longer carries a stack");
        }
        helper.succeed();
    }

    /**
     * A refusal the shears handler never made is not a refused break. The pipe remembers a refused break ask for the
     * cell in front (and so stops digging it for the refusal window) only when the break ask itself was refused, not
     * whenever a click was refused while shears were being handled: with the shears handler passing (a shearable block
     * that is not ready for shears), the dispenser fallback after it right-clicks the block with the shears, and a
     * claim that guards only clicks refuses that. The pipe took that for the break being refused and then left a block
     * its owner may break alone for the window. The claim here refuses the click and nothing else, and the break
     * claim only counts, so the block may be broken: the shears come back unused, and the pipe still digs it.
     */
    public static void testRefusedClickAfterShearsPassDoesNotStopTheDig(GameTestHelper helper) {
        helper.assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos frontRel = pipeRel.east();
        BlockPos front = helper.absolutePos(frontRel);
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            helper.setBlock(frontRel, TestOnlyBlocks.unshearable());
            try (ClaimProtectionMod breaks = new ClaimProtectionMod(front, false);
                    WorkClaimMod clicks = new WorkClaimMod(front::equals, front::equals)) {
                PipeEventItem.Drop passed = rig.drop(new ItemStack(Items.SHEARS));
                rig.stripes.onDrop(passed);

                helper.assertTrue(clicks.toolAsks(front) >= 1,
                        "precondition: the dispenser fallback right-clicked the block and was refused, got "
                                + clicks.toolAsks(front));
                helper.assertTrue(breaks.asks() == 0,
                        "precondition: the shears handler passed, so no break was asked, got " + breaks.asks());
                helper.assertTrue(passed.getStack().is(Items.SHEARS) && !passed.getStack().isDamaged(),
                        "...and the shears come back unused, got " + passed.getStack());

                for (int i = 0; i < 20 && !helper.getBlockState(frontRel).isAir(); i++) {
                    rig.stripes.receivePower(64 * MjAPI.MJ, false);
                    rig.stripes.onTick();
                }
                helper.assertTrue(helper.getBlockState(frontRel).isAir(),
                        "a refused click is not a refused break: the pipe still digs the block its owner may break,"
                                + " found " + helper.getBlockState(frontRel));
                helper.assertTrue(breaks.asks() >= 1, "the dig asked break protection, got " + breaks.asks());
            }
        }
        helper.succeed();
    }

    /**
     * An item a stripes pipe uses in the air asks the item right-click event first, as a player's use in the air
     * does ({@code ServerPlayerGameMode#useItem}): a claim that cancels it keeps the snowball unthrown and
     * unconsumed. The dispenser fallback's third strategy used to call {@code Item#use} with no ask.
     */
    public static void testAirUseAsksTheItemRightClickEvent(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        // Where the aimed player stands: its eye is at the pipe's output face, so its feet are in the column of the
        // pipe or of the cell in front, up to two blocks down. Every one of those cells is watched (and refused).
        Set<BlockPos> watched = new HashSet<>();
        for (BlockPos column : new BlockPos[] { pipeRel, pipeRel.east() }) {
            for (int down = 0; down <= 2; down++) {
                watched.add(helper.absolutePos(column.below(down)));
            }
        }
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            try (WorkClaimMod claim =
                    new WorkClaimMod(watched::contains, pos -> false, pos -> false, watched::contains)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.SNOWBALL, 3));
                rig.stripes.onDrop(refused);

                helper.assertTrue(claim.totalItemAsks() >= 1,
                        "the pipe asks the item right-click event for the item it would use");
                helper.assertTrue(refused.getStack().is(Items.SNOWBALL) && refused.getStack().getCount() == 3,
                        "a refused item is not used up, got " + refused.getStack());
                helper.assertTrue(
                        helper.getLevel().getEntitiesOfClass(Projectile.class, EntityArenaUtil.cellBounds(helper))
                                .isEmpty(),
                        "...and nothing was thrown");
            }

            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false, pos -> false, pos -> false)) {
                PipeEventItem.Drop allowed = rig.drop(new ItemStack(Items.SNOWBALL, 3));
                rig.stripes.onDrop(allowed);

                helper.assertTrue(claim.totalItemAsks() >= 1,
                        "the pipe asks the item right-click event when nothing refuses too");
                helper.assertTrue(allowed.getStack().isEmpty(), "an allowed item is used: the drop is handled");
            }
        }
        helper.succeed();
    }

    /**
     * Issue #71: a bucket a stripes pipe uses in the air lands wherever its ray from the pipe's output face meets a
     * block, which is not where the right-click event (posted at the player's feet) is asked about, and
     * {@code BucketItem#use} posts no placement or break event of its own. The pipe must ask the cell the bucket
     * pours into and the fluid it takes, as the robot's use in the air does. Here the stone beyond the cell in front
     * is what the ray meets, so the water would land in front: a claim that refuses placements there keeps it air and
     * the bucket full, a claim that refuses breaks of a source in front keeps the empty bucket from taking it, and
     * the same pipe pours and takes when nothing refuses (so the refusals above are the claim's).
     */
    public static void testAirUseBucketAsksTheCellsItTouches(GameTestHelper helper) {
        helper.assertTrue(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos frontRel = pipeRel.east();
        BlockPos beyondRel = frontRel.east();
        BlockPos front = helper.absolutePos(frontRel);
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            helper.setBlock(beyondRel, Blocks.STONE);

            // A thrown item touches no cell itself (its impact events judge where it lands), so a claim that refuses
            // the cell it aims at does not stop the throw.
            try (WorkClaimMod claim = new WorkClaimMod(front::equals, pos -> false, front::equals, pos -> false)) {
                PipeEventItem.Drop thrown = rig.drop(new ItemStack(Items.SNOWBALL, 2));
                rig.stripes.onDrop(thrown);
                helper.assertTrue(thrown.getStack().getCount() < 2,
                        "a snowball aimed at a refused cell is still thrown, got " + thrown.getStack());
            }

            try (WorkClaimMod claim = new WorkClaimMod(front::equals, pos -> false, front::equals, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.WATER_BUCKET));
                rig.stripes.onDrop(refused);
                helper.assertTrue(helper.getBlockState(frontRel).isAir(),
                        "the pipe poured into a cell the claim refuses: " + helper.getBlockState(frontRel));
                helper.assertTrue(refused.getStack().is(Items.WATER_BUCKET) && rig.count(Items.BUCKET) == 0,
                        "a refused bucket stays full and unused, got " + refused.getStack());
                helper.assertTrue(claim.placeAsks(front) >= 1, "the pipe asked about the cell it would pour into");
            }
            try (WorkClaimMod claim = new WorkClaimMod(front::equals, pos -> false, pos -> false, pos -> false)) {
                PipeEventItem.Drop allowed = rig.drop(new ItemStack(Items.WATER_BUCKET));
                rig.stripes.onDrop(allowed);
                helper.assertTrue(helper.getBlockState(frontRel).is(Blocks.WATER),
                        "the pipe pours in front when nothing refuses: " + helper.getBlockState(frontRel));
                helper.assertTrue(claim.placeAsks(front) >= 1, "...and asked about that cell first");
            }
            rig.empty();
            helper.setBlock(frontRel, Blocks.AIR);
            helper.setBlock(beyondRel, Blocks.AIR);

            helper.setBlock(frontRel, Blocks.WATER);
            try (ClaimProtectionMod claim = new ClaimProtectionMod(front, true)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.BUCKET));
                rig.stripes.onDrop(refused);
                helper.assertTrue(helper.getBlockState(frontRel).is(Blocks.WATER),
                        "the pipe took a source from a cell the claim refuses breaks in");
                helper.assertTrue(refused.getStack().is(Items.BUCKET) && rig.count(Items.WATER_BUCKET) == 0,
                        "a refused empty bucket stays empty and unused, got " + refused.getStack());
                helper.assertTrue(claim.asks() >= 1, "the pipe asked about the source it would take");
            }
            try (ClaimProtectionMod claim = new ClaimProtectionMod(front, false)) {
                PipeEventItem.Drop allowed = rig.drop(new ItemStack(Items.BUCKET));
                rig.stripes.onDrop(allowed);
                helper.assertTrue(!helper.getBlockState(frontRel).is(Blocks.WATER),
                        "the pipe takes the source in front when nothing refuses");
                helper.assertTrue(claim.asks() >= 1, "...and asked about it first");
            }
            helper.setBlock(frontRel, Blocks.AIR);
        }
        helper.succeed();
    }

    /**
     * What an item does to the stack it was used from is kept, as a player keeps it, and an item used in the air acts
     * on the cell in front of the pipe: the pipe aims its player out of the output face (7.1.x aimed it too; 8.0.x
     * used dispenser behaviours on that cell). A stripes pipe carrying ONE empty bucket takes the water source in front
     * of it and sends the water bucket back down the pipe; pouring is the mirror (the water lands in front, against
     * the stone beyond, and the empty bucket comes back) and a bottle that takes water comes back as a water bottle.
     * A pipe pointing down takes the source below it, which pins the pitch. Before the aim the player looked south,
     * level, from about two blocks above the pipe, so the source in front was never reached; before the product fix a
     * single bucket was consumed with its product nowhere.
     */
    public static void testAirUseHandsTheProductBackDownThePipe(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos frontRel = pipeRel.east();
        BlockPos beyondRel = frontRel.east();
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            // An empty bucket takes the water source in front.
            helper.setBlock(frontRel, Blocks.WATER);
            PipeEventItem.Drop bucket = rig.drop(new ItemStack(Items.BUCKET));
            rig.stripes.onDrop(bucket);
            helper.assertTrue(!helper.getBlockState(frontRel).is(Blocks.WATER),
                    "the bucket took the water source in front of the pipe");
            helper.assertTrue(rig.count(Items.WATER_BUCKET) == 1,
                    "...and the water bucket it filled travels back down the pipe, found "
                            + rig.count(Items.WATER_BUCKET));
            rig.empty();

            // A water bucket pours against the stone beyond the cell in front: the water lands in front.
            helper.setBlock(frontRel, Blocks.AIR);
            helper.setBlock(beyondRel, Blocks.STONE);
            PipeEventItem.Drop water = rig.drop(new ItemStack(Items.WATER_BUCKET));
            rig.stripes.onDrop(water);
            helper.assertTrue(helper.getBlockState(frontRel).is(Blocks.WATER),
                    "the bucket poured its water in front of the pipe");
            helper.assertTrue(rig.count(Items.BUCKET) == 1,
                    "...and the empty bucket travels back down the pipe, found " + rig.count(Items.BUCKET));
            rig.empty();
            helper.setBlock(frontRel, Blocks.AIR);
            helper.setBlock(beyondRel, Blocks.AIR);

            // A glass bottle takes water and leaves the source.
            helper.setBlock(frontRel, Blocks.WATER);
            PipeEventItem.Drop bottle = rig.drop(new ItemStack(Items.GLASS_BOTTLE));
            rig.stripes.onDrop(bottle);
            helper.assertTrue(rig.count(Items.POTION) == 1,
                    "a bottle that took water travels back down the pipe as a water bottle, found "
                            + rig.count(Items.POTION));
            helper.setBlock(frontRel, Blocks.AIR);
        }

        // Pointing down: the source below the pipe.
        BlockPos downRel = new BlockPos(1, 4, 5);
        BlockPos belowRel = downRel.below();
        try (StripesRig rig = StripesRig.build(helper, downRel, Direction.DOWN)) {
            helper.setBlock(belowRel, Blocks.WATER);
            PipeEventItem.Drop bucket = rig.drop(new ItemStack(Items.BUCKET));
            rig.stripes.onDrop(bucket);
            helper.assertTrue(!helper.getBlockState(belowRel).is(Blocks.WATER),
                    "a pipe pointing down takes the water source below it");
            helper.assertTrue(rig.count(Items.WATER_BUCKET) == 1,
                    "...and sends the water bucket back down the pipe, found " + rig.count(Items.WATER_BUCKET));
            helper.setBlock(belowRel, Blocks.AIR);
        }
        helper.succeed();
    }

    /**
     * A refused till ends the hoe handler, not just that click: with the claim refusing the right-click on the empty
     * cell in front of the pipe, the handler used to go on to the dirt below it (a second click on a block a player
     * who was just refused would not have clicked) and till that. The control shows the same setup tills when
     * nothing refuses.
     */
    public static void testRefusedTillEndsTheHoeHandler(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = pipeRel.east();
        BlockPos soilRel = targetRel.below();
        BlockPos target = helper.absolutePos(targetRel);
        BlockPos soil = helper.absolutePos(soilRel);
        Set<BlockPos> watched = Set.of(target, soil);
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            helper.setBlock(soilRel, Blocks.DIRT);
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, target::equals, pos -> false, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.DIAMOND_HOE));
                rig.stripes.onDrop(refused);

                helper.assertTrue(claim.toolAsks(target) >= 1,
                        "the pipe asks the right-click event for the cell it clicks");
                helper.assertTrue(helper.getBlockState(soilRel).is(Blocks.DIRT),
                        "a refused till does not go on to till the dirt below the cell");
                helper.assertTrue(claim.toolAsks(soil) == 0,
                        "...the dirt below is never asked about, got " + claim.toolAsks(soil));
                helper.assertTrue(refused.getStack().is(Items.DIAMOND_HOE),
                        "...and the refused hoe is not consumed, got " + refused.getStack());
            }

            // Control: nothing refuses, so the same pipe tills the dirt below the empty cell.
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false, pos -> false, pos -> false)) {
                PipeEventItem.Drop allowed = rig.drop(new ItemStack(Items.DIAMOND_HOE));
                rig.stripes.onDrop(allowed);

                helper.assertTrue(helper.getBlockState(soilRel).is(Blocks.FARMLAND),
                        "an allowed till reaches the dirt below the empty cell");
                helper.assertTrue(allowed.getStack().isEmpty(), "a handled drop no longer carries a stack");
            }
        }
        helper.succeed();
    }

    /** Clicks the handler got refused for its detached player: the test's proof that the handler ran. */
    private static int otherPlayersRefusalsMade;

    /**
     * A third-party style handler that has another machine's player refused a click (an empty stack is refused by
     * vanilla's item gate, no claim needed) and then declines the stack. Registered only for the one synchronous test
     * that uses it, and removed again.
     */
    private static final class OtherPlayersRefusal implements IStripesHandlerItem {
        @Override
        public boolean handle(Level world, BlockPos pos, Direction direction, ItemStack stack, Player player,
                IStripesActivator activator) {
            // The pipe's own lease is open, so this one is a different, detached player.
            try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease((ServerLevel) world)) {
                if (lease.player() == player) {
                    throw new IllegalStateException("the nested lease was expected to be a different player");
                }
                BlockPos target = pos.relative(direction);
                BlockHitResult hit =
                        new BlockHitResult(Vec3.atCenterOf(target), direction.getOpposite(), target, false);
                if (BlockUtil.tryUseItemOnBlock(world, lease.player(), InteractionHand.MAIN_HAND, ItemStack.EMPTY, hit)
                        == BlockUtil.ItemUse.REFUSED) {
                    otherPlayersRefusalsMade++;
                }
            }
            return false;
        }
    }

    /**
     * A click refused for ANOTHER machine's player while a handler runs does not end this pipe's dispatch: the
     * refusal tally belongs to the player whose click it was. It used to be one process-wide counter the registry
     * compared around each handler, so a handler (an addon's nested dispatch) that got some other player refused
     * made the registry give up on a stack the pipe's own player was never refused for, and the cobblestone below
     * was not placed. The refusals counter proves the armed handler really got that other player refused, so the
     * placement below cannot pass because the handler never ran.
     */
    public static void testAnotherPlayersRefusalDoesNotEndTheDispatch(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = pipeRel.east();
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            OtherPlayersRefusal handler = new OtherPlayersRefusal();
            StripesRegistry.INSTANCE.addHandler(handler, EnumHandlerPriority.HIGHEST);
            otherPlayersRefusalsMade = 0;
            try {
                PipeEventItem.Drop drop = rig.drop(new ItemStack(Items.COBBLESTONE, 3));
                rig.stripes.onDrop(drop);

                helper.assertTrue(otherPlayersRefusalsMade >= 1,
                        "the armed handler ran and got another player's click refused, got " + otherPlayersRefusalsMade);
                helper.assertTrue(helper.getBlockState(targetRel).is(Blocks.COBBLESTONE),
                        "another player's refusal does not stop the pipe placing its block");
                helper.assertTrue(drop.getStack().isEmpty(), "a handled drop no longer carries a stack");
            } finally {
                StripesRegistry.INSTANCE.removeHandler(handler);
            }
        }
        helper.succeed();
    }

    /**
     * A handler that files the stack it used into a second slot as well as the hand (one object in two slots) has it
     * sent back down the pipe once, not twice: the success sweep takes each distinct stack off the player once, the
     * rule the robot's take-back already followed. It used to send every slot's stack, so the aliased stack travelled
     * twice and its items were duplicated.
     */
    public static void testAliasedStackIsSentBackOnce(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        IStripesHandlerItem aliasing = (world, pos, direction, stack, player, activator) -> {
            if (!stack.is(Items.PAPER)) {
                return false;
            }
            player.getInventory().setItem(9, stack);
            return true;
        };
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            StripesRegistry.INSTANCE.addHandler(aliasing, EnumHandlerPriority.HIGHEST);
            try {
                PipeEventItem.Drop drop = rig.drop(new ItemStack(Items.PAPER, 3));
                rig.stripes.onDrop(drop);
                helper.assertTrue(drop.getStack().isEmpty(), "a handled drop no longer carries a stack");
                helper.assertTrue(rig.count(Items.PAPER) == 3,
                        "the one stack in two slots travels back once (3 paper), found " + rig.count(Items.PAPER));
            } finally {
                StripesRegistry.INSTANCE.removeHandler(aliasing);
            }
        }
        helper.succeed();
    }

    /** A handler that takes itself out of the registry the first time it is asked (and, when given another handler,
     *  puts that one in then), declining everything. Counts its calls, for the item and the block dispatch alike. */
    private static final class SelfRemovingHandler implements IStripesHandlerItem, IStripesHandlerBlock {
        int calls;
        private final IStripesHandlerItem addsOnFirstCall;

        SelfRemovingHandler(IStripesHandlerItem addsOnFirstCall) {
            this.addsOnFirstCall = addsOnFirstCall;
        }

        @Override
        public boolean handle(Level world, BlockPos pos, Direction direction, ItemStack stack, Player player,
                IStripesActivator activator) {
            calls++;
            StripesRegistry.INSTANCE.removeHandler((IStripesHandlerItem) this);
            if (addsOnFirstCall != null && calls == 1) {
                StripesRegistry.INSTANCE.addHandler(addsOnFirstCall, EnumHandlerPriority.HIGHEST);
            }
            return false;
        }

        @Override
        public boolean handle(Level world, BlockPos pos, Direction direction, Player player,
                IStripesActivator activator) {
            calls++;
            StripesRegistry.INSTANCE.removeHandler((IStripesHandlerBlock) this);
            return false;
        }
    }

    /**
     * A handler that takes itself out of the registry (or adds another) while the registry is dispatching to it does
     * not break the dispatch: the pipe's tick used to throw a {@code ConcurrentModificationException} out of the
     * priority list it was walking, for items and for blocks alike. The handlers after it still run (the block is
     * placed), and one added mid-dispatch takes part from the next dispatch.
     */
    public static void testHandlerMayChangeTheRegistryWhileItIsDispatched(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = pipeRel.east();
        int[] lateCalls = new int[1];
        IStripesHandlerItem late = (world, pos, direction, stack, player, activator) -> {
            lateCalls[0]++;
            return false;
        };
        SelfRemovingHandler item = new SelfRemovingHandler(late);
        SelfRemovingHandler block = new SelfRemovingHandler(null);
        try (StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST)) {
            StripesRegistry.INSTANCE.addHandler((IStripesHandlerItem) item, EnumHandlerPriority.HIGHEST);
            StripesRegistry.INSTANCE.addHandler((IStripesHandlerBlock) block, EnumHandlerPriority.HIGHEST);
            try {
                PipeEventItem.Drop drop = rig.drop(new ItemStack(Items.COBBLESTONE, 3));
                rig.stripes.onDrop(drop);
                helper.assertTrue(item.calls == 1, "the self-removing item handler ran once, got " + item.calls);
                helper.assertTrue(helper.getBlockState(targetRel).is(Blocks.COBBLESTONE),
                        "the handlers after it still ran: the block is placed");
                helper.assertTrue(lateCalls[0] == 0,
                        "a handler added mid-dispatch waits for the next one, got " + lateCalls[0]);

                helper.setBlock(targetRel, Blocks.AIR);
                PipeEventItem.Drop again = rig.drop(new ItemStack(Items.COBBLESTONE, 3));
                rig.stripes.onDrop(again);
                helper.assertTrue(item.calls == 1,
                        "a handler that removed itself is not run again, got " + item.calls);
                helper.assertTrue(lateCalls[0] == 1,
                        "...and the one it added takes part in the next dispatch, got " + lateCalls[0]);

                try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(helper.getLevel())) {
                    boolean handled = StripesRegistry.INSTANCE.handleBlock(helper.getLevel(),
                            helper.absolutePos(pipeRel), Direction.EAST, lease.player(), rig.stripes);
                    helper.assertTrue(!handled, "nothing to act on in front of the pipe");
                }
                helper.assertTrue(block.calls == 1,
                        "the self-removing block handler ran once, got " + block.calls);
            } catch (ConcurrentModificationException e) {
                // The exception has no message, which the game test runner cannot report: name it.
                helper.fail("the registry threw while a handler changed it mid-dispatch: " + e);
            } finally {
                StripesRegistry.INSTANCE.removeHandler((IStripesHandlerItem) item);
                StripesRegistry.INSTANCE.removeHandler((IStripesHandlerBlock) block);
                StripesRegistry.INSTANCE.removeHandler(late);
            }
        }
        helper.succeed();
    }

    /** A stripes pipe at {@code pipeRel} pointing {@code direction}, with its item flow. Closing it discards what
     *  the test launched and empties the cargo (used and returned stacks go back down the pipe), so removing the
     *  arena's pipe drops nothing into neighbouring tests and a failed assertion leaves nothing live in the arena. */
    record StripesRig(GameTestHelper helper, TilePipeHolder tile, PipeBehaviourStripes stripes,
            PipeFlowItems flow) implements AutoCloseable {
        static StripesRig build(GameTestHelper helper, BlockPos pipeRel, Direction direction) {
            helper.setBlock(pipeRel, BCTransportBlocks.PIPE_HOLDER.get());
            //? if >=1.21.10 {
            TilePipeHolder tile = helper.getBlockEntity(pipeRel, TilePipeHolder.class);
            //?} else {
            /*TilePipeHolder tile = helper.getBlockEntity(pipeRel);*/
            //?}
            tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_STRIPES_ITEM.get()));
            if (!(tile.getPipe().getBehaviour() instanceof PipeBehaviourStripes)) {
                helper.fail("the stripes pipe item did not produce a stripes behaviour");
            }
            PipeBehaviourStripes stripes = (PipeBehaviourStripes) tile.getPipe().getBehaviour();
            // Set synchronously and used in the same call: an unconnected stripes pipe clears its direction on tick.
            stripes.direction = direction;
            return new StripesRig(helper, tile, stripes, (PipeFlowItems) tile.getPipe().getFlow());
        }

        PipeEventItem.Drop drop(ItemStack stack) {
            ItemEntity carrier = new ItemEntity(helper.getLevel(), 0, 0, 0, stack);
            return new PipeEventItem.Drop(tile, (IFlowItems) flow, carrier);
        }

        /** How many of {@code item} are travelling in the flow now: what the pipe sent back down. */
        int count(Item item) {
            int found = 0;
            for (TravellingItem travelling : flow.getAllItemsForRender()) {
                if (travelling.getStack().is(item)) {
                    found += travelling.getStack().getCount();
                }
            }
            return found;
        }

        /** Empties the cargo between the steps of one test. */
        void empty() {
            for (TravellingItem item : flow.getAllItemsForRender()) {
                item.getStack().setCount(0);
            }
        }

        @Override
        public void close() {
            for (Projectile thrown : helper.getLevel().getEntitiesOfClass(Projectile.class,
                    EntityArenaUtil.cellBounds(helper))) {
                thrown.discard();
            }
            empty();
        }
    }

    private static PipeEventItem.Drop hoeDrop(GameTestHelper helper, TilePipeHolder tile, PipeFlowItems flow) {
        ItemEntity carrier = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.DIAMOND_HOE));
        return new PipeEventItem.Drop(tile, (IFlowItems) flow, carrier);
    }
}
