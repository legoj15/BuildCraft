/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import buildcraft.api.core.EnumHandlerPriority;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandlerItem;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.PipeEventItem;

import buildcraft.core.BCCoreConfig;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.lib.test.EntityArenaUtil;
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
            FakePlayer player = lease.player();
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

    private static final int ENTITY_POLL_TICKS = 60;

    /**
     * A claim that refuses the interaction with a mob in front of the pipe ends the whole dispatch: the entity
     * handler's refusal is a cancelled entity-interaction event, which comes back from {@code Player#interactOn} as
     * the default PASS, indistinguishable from a mob that simply ignored the item. The dispatch used to go on to the
     * dispenser fallback and right-click the mob's block with the same item. The control shows the fallback is
     * reached when nothing refuses, so the absence of the click is the refusal and not an unreachable item.
     */
    public static void testRefusedEntityInteractionEndsTheDispatch(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = pipeRel.east();
        BlockPos target = helper.absolutePos(targetRel);
        EntityArenaUtil.forceLoadEntityArena(helper, targetRel);
        StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST);
        Mob[] cow = new Mob[1];
        EntityArenaUtil.tickUntil(helper, ENTITY_POLL_TICKS,
                () -> EntityArenaUtil.isEntityTicking(helper, targetRel), () -> {
            //? if >=26.2 {
            cow[0] = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, targetRel);
            //?} else {
            /*cow[0] = helper.spawn(net.minecraft.world.entity.EntityType.COW, targetRel);*/
            //?}
            cow[0].setNoAi(true);
            cow[0].setNoGravity(true);
            Set<BlockPos> watched = Set.of(target);
            int[] interactions = new int[1];
            // A claim cancelling the interaction: the cancellation result is left at its default, PASS.
            Consumer<PlayerInteractEvent.EntityInteract> protectCow = event -> {
                if (event.getTarget() == cow[0]) {
                    interactions[0]++;
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, protectCow);
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.STICK, 3));
                rig.stripes.onDrop(refused);

                helper.assertTrue(interactions[0] >= 1, "the pipe tried to interact with the cow");
                helper.assertTrue(claim.toolAsks(target) == 0,
                        "a refused interaction ends the use: the cow's block is never right-clicked, got "
                                + claim.toolAsks(target));
                helper.assertTrue(refused.getStack().is(Items.STICK) && refused.getStack().getCount() == 3,
                        "...and the refused item is not consumed, got " + refused.getStack());
            } finally {
                NeoForge.EVENT_BUS.unregister(protectCow);
            }

            // The same refusal from a claim that leaves a consuming result on the cancelled event: Player#interactOn
            // then answers "used", yet nothing was done to the cow, so the item must come back unused all the same.
            int[] swallowed = new int[1];
            Consumer<PlayerInteractEvent.EntityInteract> swallowCow = event -> {
                if (event.getTarget() == cow[0]) {
                    swallowed[0]++;
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.CONSUME);
                }
            };
            NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, swallowCow);
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.STICK, 3));
                rig.stripes.onDrop(refused);

                helper.assertTrue(swallowed[0] >= 1, "the pipe tried to interact with the cow again");
                helper.assertTrue(claim.toolAsks(target) == 0,
                        "a refused interaction ends the use whatever result the claim left: the cow's block is never"
                                + " right-clicked, got " + claim.toolAsks(target));
                helper.assertTrue(refused.getStack().is(Items.STICK) && refused.getStack().getCount() == 3,
                        "...and the item is not taken as used, got " + refused.getStack());
            } finally {
                NeoForge.EVENT_BUS.unregister(swallowCow);
            }

            // Control: nothing refuses, so the item reaches the dispenser fallback, which clicks the block.
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false)) {
                PipeEventItem.Drop allowed = rig.drop(new ItemStack(Items.STICK, 3));
                rig.stripes.onDrop(allowed);

                helper.assertTrue(claim.toolAsks(target) >= 1,
                        "control: without a refusal the dispenser fallback right-clicks the block");
            }
            helper.succeed();
        }, "the cow's chunk never became entity-ticking", () -> {
            EntityArenaUtil.discard(cow[0]);
            rig.close();
        });
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
