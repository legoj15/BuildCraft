/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.lib.misc.FakePlayerUtil;

/**
 * Cross-version shims for game-test assertions, plus the one survival block-break driver the drop tests share.
 * <p>
 * MC 1.21.10's {@link GameTestHelper#assertTrue}/{@code assertFalse} take a {@code Component} message;
 * 1.21.11+ / 26.1 take a {@code String}. A gated Stonecutter regex replacement (see build.gradle.kts)
 * routes every {@code assertTrue}/{@code assertFalse} call on a {@code helper} receiver through these on the
 * pre-1.21.11 nodes (1.21.10 wraps the String, 1.21.1 passes it straight on), so the ~15 game-test classes
 * keep passing plain Strings and the divergence lives here.
 * ({@code helper.fail(String)} is left alone — 1.21.10 keeps a String overload of it.)
 */
public final class GameTestUtil {
    // NB: the param is `gth`, not `helper` — the build-script regex rewrites assertTrue/assertFalse calls
    // on a `helper` receiver, so naming it `helper` here would make this method rewrite itself into infinite
    // recursion.
    public static void assertTrue(GameTestHelper gth, boolean condition, String message) {
        //? if >=1.21.11 {
        gth.assertTrue(condition, message);
        //?} elif >=1.21.10 {
        /*gth.assertTrue(condition, net.minecraft.network.chat.Component.literal(message));*/
        //?} else {
        /*gth.assertTrue(condition, message);*/
        //?}
    }

    public static void assertFalse(GameTestHelper gth, boolean condition, String message) {
        //? if >=1.21.11 {
        gth.assertFalse(condition, message);
        //?} elif >=1.21.10 {
        /*gth.assertFalse(condition, net.minecraft.network.chat.Component.literal(message));*/
        //?} else {
        /*gth.assertFalse(condition, message);*/
        //?}
    }

    /**
     * Breaks the block at {@code relativePos} the way {@code ServerPlayerGameMode.destroyBlock} does for a survival
     * player holding {@code heldItem}: {@code playerWillDestroy}, {@code removeBlock}, then {@code playerDestroy}
     * only when the held item can harvest the block. {@code helper.destroyBlock} would skip loot, and the tool gate
     * is exactly what the drop tests exist to check: without that gate the block-self loot would fire for any tool,
     * and the empty-hand tests would fail.
     *
     * <p>The player is a leased {@link FakePlayerUtil} player, not {@code makeMockPlayer}'s bare {@code Player}:
     * 26.3 narrowed {@code Block#playerDestroy} to {@code ServerPlayer}, and the only vanilla {@code ServerPlayer}
     * mock ({@code makeMockServerPlayerInLevel}) is {@code forRemoval}. The player is leased 64 blocks above the
     * block (current and previous-tick position both, since the pick reads the latter), looking level, so
     * {@code player.pick} cannot hit it: the pipe holder's partial-break check
     * ({@code playerWillDestroy}) must fall through to the full break (the click-break tests that need a pick hit
     * use a positioned mock player instead).
     */
    public static void breakAsSurvivalPlayer(GameTestHelper helper, BlockPos relativePos, ItemStack heldItem) {
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(relativePos);
        BlockState state = level.getBlockState(absPos);
        BlockEntity be = level.getBlockEntity(absPos);
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, null, absPos.above(64))) {
            FakePlayer player = lease.player();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, heldItem);
            boolean canHarvest = state.canHarvestBlock(level, absPos, player);
            state.getBlock().playerWillDestroy(level, absPos, state, player);
            level.removeBlock(absPos, false);
            if (canHarvest) {
                state.getBlock().playerDestroy(level, player, absPos, state, be, player.getMainHandItem());
            }
        }
    }

    private GameTestUtil() {}
}
