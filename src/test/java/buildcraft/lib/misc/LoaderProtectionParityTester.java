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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import buildcraft.core.BCCoreConfig;
import buildcraft.core.BCCoreItems;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.test.DropArenaUtil;
import buildcraft.lib.test.DropArenaUtil.Drops;
import buildcraft.lib.test.LoaderTestEvents;
import buildcraft.lib.test.WorkClaimMod;

import buildcraft.platform.Platform;
import buildcraft.platform.event.PlatformProtection.ClickVerdict;

/**
 * What a claim, a machine's ask and a refused placement do must not depend on the loader: each test here pins an
 * answer NeoForge gives through its own events, which the Fabric route (BuildCraft's Fabric API callbacks, its
 * Common Protection API asks and its placement capture) must give too.
 */
public class LoaderProtectionParityTester {

    private static void requireProtectionChecksOn() {
        check(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
    }

    /**
     * A machine's door placement that a claim refuses is put back whole, with the machine keeping its door and no door
     * lying about: putting one half back breaks the other through its shape update, and that break must not pop a
     * door (NeoForge restores in reverse with its restoringBlockSnapshots drop guard; the Fabric capture restores the
     * same way, and suppresses drops while it does). Otherwise every refused click would mint a door.
     */
    public static void refusedDoorPlacementDropsNothing(GameTestHelper helper) {
        requireProtectionChecksOn();
        BlockPos floorRel = new BlockPos(2, 1, 3);
        BlockPos lowerRel = floorRel.above();
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(floorRel);
        BlockPos lower = floor.above();
        BlockPos upper = lower.above();
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(floorRel, Blocks.STONE);
            ItemStack door = new ItemStack(Items.OAK_DOOR);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
            int[] asks = { 0 };
            try (LoaderTestEvents.Observer claim = LoaderTestEvents.claimPlacements((placer, at) -> {
                        boolean ours = at.equals(lower) || at.equals(upper);
                        if (ours) {
                            asks[0]++;
                        }
                        return ours;
                    });
                    FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, null, lower.south(2))) {
                ServerPlayer player = lease.player();
                player.setItemInHand(InteractionHand.MAIN_HAND, door);
                BlockUtil.ItemUse use = BlockUtil.tryUseItemOnBlock(level, player, InteractionHand.MAIN_HAND, door, hit);
                check(asks[0] >= 1, "liveness: the door must have been placed and the claim asked about it (use "
                        + use + ")");
                check(use != BlockUtil.ItemUse.USED, "a refused door placement must not count as used, was " + use);
            }
            check(level.getBlockState(lower).isAir() && level.getBlockState(upper).isAir(),
                    "the refused door must be put back to air, lower " + level.getBlockState(lower) + ", upper "
                            + level.getBlockState(upper));
            check(door.getCount() == 1, "the machine keeps its door when the placement is refused, has "
                    + door.getCount());
        }, Drops.near(lowerRel, 4.0).absent(Items.OAK_DOOR));
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
