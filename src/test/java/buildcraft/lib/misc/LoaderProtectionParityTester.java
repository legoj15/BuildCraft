/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import static buildcraft.lib.test.GameTestUtil.check;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.core.BCCoreConfig;
import buildcraft.core.BCCoreItems;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.test.DropArenaUtil;
import buildcraft.lib.test.DropArenaUtil.Drops;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.JoinOrderProbeBlockEntity;
import buildcraft.lib.test.LoaderTestEvents;
import buildcraft.lib.test.TestOnlyBlocks;
import buildcraft.lib.test.WorkClaimMod;
import buildcraft.platform.Platform;
import buildcraft.platform.event.PlatformProtection.ClickVerdict;

/**
 * What a claim, a machine's ask, a refused placement and a joining block entity do must not depend on the loader:
 * each test here pins an answer NeoForge gives through its own events and hooks, which the Fabric route (BuildCraft's
 * Fabric API callbacks, its Common Protection API asks, its placement capture and its mixins) must give too.
 */
public class LoaderProtectionParityTester {

    private static void requireProtectionChecksOn() {
        check(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
    }

    /**
     * A machine's placement of a two-cell block that a claim refuses is put back whole, with the machine keeping its
     * item and nothing dropping: putting one half back breaks the other through its shape update, and that break pops
     * no item. NeoForge puts the snapshots back with its {@code restoringBlockSnapshots} drop guard set; the Fabric
     * capture puts them back with drops suppressed the same way. The block drops from both halves (no vanilla two-cell
     * block does: a door drops from the half the put-back never breaks), so whichever half the put-back breaks has
     * something to drop.
     */
    public static void refusedTwoCellPlacementDropsNothing(GameTestHelper helper) {
        requireProtectionChecksOn();
        BlockPos floorRel = new BlockPos(2, 1, 3);
        BlockPos lowerRel = floorRel.above();
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(floorRel);
        BlockPos lower = floor.above();
        BlockPos upper = lower.above();
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(floorRel, Blocks.DIRT);
            ItemStack placer = new ItemStack(TestOnlyBlocks.twoCellDropItem());
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
            int[] asks = { 0 };
            try (LoaderTestEvents.Observer claim = LoaderTestEvents.claimPlacements((by, at) -> {
                        boolean ours = at.equals(lower) || at.equals(upper);
                        if (ours) {
                            asks[0]++;
                        }
                        return ours;
                    });
                    FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, null, lower.south(2))) {
                ServerPlayer player = lease.player();
                player.setItemInHand(InteractionHand.MAIN_HAND, placer);
                BlockUtil.ItemUse use = BlockUtil.tryUseItemOnBlock(level, player, InteractionHand.MAIN_HAND, placer,
                        hit);
                check(asks[0] >= 1, "liveness: the block must have been placed and the claim asked about it (use "
                        + use + ")");
                check(use != BlockUtil.ItemUse.USED, "a refused placement must not count as used, was " + use);
            }
            check(level.getBlockState(lower).isAir() && level.getBlockState(upper).isAir(),
                    "the refused block must be put back to air, lower " + level.getBlockState(lower) + ", upper "
                            + level.getBlockState(upper));
            check(placer.getCount() == 1, "the machine keeps its item when the placement is refused, has "
                    + placer.getCount());
        }, Drops.near(lowerRel, 4.0).absent(Items.STICK));
    }

    /**
     * A machine's placement asks do not ask vanilla's {@code mayInteract}: a cell outside the world border is a
     * placement the claims are asked about and nothing else, as NeoForge's placement events are all a machine's
     * placement asks there (vanilla's border check gates a player's click packet, never a machine). The border is
     * moved away and put back inside this one call, so no other test sees it.
     */
    public static void machinePlacementIgnoresWorldBorder(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(new BlockPos(2, 2, 3));
        WorldBorder border = level.getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();
        boolean placeAllowed;
        boolean placeOverAllowed;
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, null, cell)) {
            border.setCenter(cell.getX() + 1000.5, cell.getZ() + 0.5);
            border.setSize(16);
            check(!border.isWithinBounds(cell), "precondition: the cell must lie outside the moved border");
            check(!lease.player().mayInteract(level, cell),
                    "precondition: vanilla's mayInteract must refuse a cell outside the border");
            placeAllowed = BlockUtil.canMachinePlace(level, cell, new GameProfile(UUID.randomUUID(), "bc_border_test"));
            placeOverAllowed = Platform.protection().canPlaceOver(lease.player(),
                    Platform.protection().capture(level, cell), Direction.UP);
        } finally {
            border.setCenter(centerX, centerZ);
            border.setSize(size);
        }
        check(placeAllowed, "a machine's placement probe outside the border must be allowed when no claim refuses it");
        check(placeOverAllowed, "a machine placing over a cell outside the border must be allowed when no claim "
                + "refuses it");
        helper.succeed();
    }

    /** The work budget of {@link #blockEntityJoinsAfterBlockTicks}, counted once the cell ticks. */
    private static final int JOIN_ORDER_WORK_TICKS = 20;

    /**
     * A block entity set down in a level is told it joined it (NeoForge's {@code onLoad}, BuildCraft's
     * {@code onJoinedLevel}) at the head of that level's next block-entity tick, after the level's scheduled block
     * ticks of the same tick have run, not at the start of the server tick. The probe's block has a tick scheduled
     * for the next game tick, which runs in the level's block ticks, before its block entities.
     */
    public static void blockEntityJoinsAfterBlockTicks(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 3);
        EntityArenaUtil.forceLoadCell(helper);
        boolean[] placed = { false };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + JOIN_ORDER_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, JOIN_ORDER_WORK_TICKS, () -> {
                    if (!placed[0]) {
                        placed[0] = true;
                        helper.setBlock(rel, TestOnlyBlocks.joinOrderProbe());
                        helper.getLevel().scheduleTick(helper.absolutePos(rel), TestOnlyBlocks.joinOrderProbe(), 1);
                        return false;
                    }
                    JoinOrderProbeBlockEntity probe = probeAt(helper, rel);
                    return probe != null && probe.joined() && probe.joinedAtFirstBlockTick() != null;
                }), () -> {
                    JoinOrderProbeBlockEntity probe = probeAt(helper, rel);
                    check(Boolean.FALSE.equals(probe.joinedAtFirstBlockTick()),
                            "the block entity must not have been told it joined the level before the level's block "
                                    + "ticks ran (NeoForge tells it at the head of the block-entity tick)");
                    helper.succeed();
                }, "the probe's block tick and its block entity's join must both have run",
                () -> helper.setBlock(rel, Blocks.AIR));
    }

    private static JoinOrderProbeBlockEntity probeAt(GameTestHelper helper, BlockPos rel) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof JoinOrderProbeBlockEntity probe
                ? probe : null;
    }

    /** A crouching trapdoor click with a wrench, a vanilla rotatable block: what the wrench turns before the block. */
    private static BlockPos placeTrapdoor(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH));
        return helper.absolutePos(rel);
    }

    private static Direction trapdoorFacing(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getValue(TrapDoorBlock.FACING);
    }

    /**
     * A player's crouching wrench click on a claimed vanilla block is refused by the claim before the wrench turns
     * it: NeoForge posts the right-click event before {@code onItemUseFirst}, so a claim mod's refusal comes first
     * whichever mod loaded first; BuildCraft's Fabric route for {@code onItemUseFirst} runs after the default phase
     * of {@code UseBlockCallback}, where claim mods answer. Without the claim the same click turns it.
     */
    public static void wrenchRotationAsksClaimFirst(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = placeTrapdoor(helper, new BlockPos(2, 2, 3));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack wrench = new ItemStack(BCCoreItems.WRENCH.get());
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, null, pos.south(2))) {
            ServerPlayer player = lease.player();
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            try (WorkClaimMod claim = new WorkClaimMod(pos::equals, pos::equals, p -> false, p -> false)) {
                player.gameMode.useItemOn(player, level, wrench, InteractionHand.MAIN_HAND, hit);
                check(trapdoorFacing(level, pos) == Direction.NORTH,
                        "a claimed trapdoor must not turn under a wrench, faces " + trapdoorFacing(level, pos));
                check(wrench.getDamageValue() == 0, "a refused click must not wear the wrench, has lost "
                        + wrench.getDamageValue());
                check(claim.toolAsks(pos) >= 1, "liveness: the claim must have been asked about the click");
            }
            player.gameMode.useItemOn(player, level, wrench, InteractionHand.MAIN_HAND, hit);
            check(trapdoorFacing(level, pos) == Direction.EAST,
                    "liveness: with no claim the crouching wrench click must turn the trapdoor, faces "
                            + trapdoorFacing(level, pos));
        }
        helper.succeed();
    }

    /**
     * A machine's right-click ask is the right-click event and no more: it does not run the held item's
     * before-the-block use ({@code onItemUseFirst}, which a machine's click never runs on NeoForge), so a crouching
     * fake player's wrench does not turn the block during the ask, and the ask is not answered as a refusal.
     */
    public static void machineClickAskRunsNoUseFirst(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = placeTrapdoor(helper, new BlockPos(2, 2, 3));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack wrench = new ItemStack(BCCoreItems.WRENCH.get());
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, null, pos.south(2))) {
            ServerPlayer player = lease.player();
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            ClickVerdict verdict = Platform.protection().rightClickBlock(player, InteractionHand.MAIN_HAND, hit);
            check(verdict == ClickVerdict.DEFAULT, "an unclaimed click must be allowed as it is, was " + verdict);
            check(trapdoorFacing(level, pos) == Direction.NORTH,
                    "the ask must not turn the trapdoor, which faces " + trapdoorFacing(level, pos));
            check(wrench.getDamageValue() == 0, "the ask must not wear the wrench, has lost " + wrench.getDamageValue());
        }
        helper.succeed();
    }

    /**
     * A machine's break probe at a tube a mining well owns answers as NeoForge's break event does: allowed (no claim
     * refuses it), without asking the tube's player-break guard, which is the block's own answer to a player's break
     * and still refuses that.
     */
    public static void tubeProbeIsNotAPlayerBreak(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        BlockPos tubeRel = new BlockPos(2, 3, 3);
        helper.setBlock(tubeRel.above(), BCFactoryBlocks.MINING_WELL.get());
        helper.setBlock(tubeRel, BCFactoryBlocks.TUBE.get());
        BlockPos tube = helper.absolutePos(tubeRel);
        check(BlockUtil.canMachineBreak(level, tube, new GameProfile(UUID.randomUUID(), "bc_protection_test")),
                "a machine's probe at an owned tube must be allowed, as NeoForge's break event allows it");
        check(level.getBlockState(tube).is(BCFactoryBlocks.TUBE.get()), "the probe must leave the tube in place");
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, null, tube.south(2))) {
            check(!LoaderTestEvents.playerBreakGoesAhead(level, tube, lease.player()),
                    "liveness: a player's break of a tube the well owns is still refused by the tube");
        }
        check(level.getBlockState(tube).is(BCFactoryBlocks.TUBE.get()), "the refused break must leave the tube");
        helper.succeed();
    }
}
