/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.factory.block.BlockWaterGel;
import buildcraft.factory.block.BlockWaterGel.GelStage;

/**
 * Water gel's per-stage break speed and sounds, as 1.12.2 shipped them ({@code BlockWaterGel.GelStage}: pitch,
 * spreading, hardness), read through the same state-level calls the client makes while a player digs and when the
 * block breaks — {@code BlockState#getDestroyProgress} and NeoForge's position-aware
 * {@code BlockState#getSoundType(level, pos, entity)} — so a missed override (the block falling back to its
 * registered 0.6 strength or its plain slime sound) fails here instead of only in a live client.
 *
 * <p>The expected numbers are 1.12.2's table, restated rather than read back from the enum, so a changed constant
 * fails too. The hardness runs "backwards" on purpose: spreading gel (3) is slow to dig, set gel (0.1) nearly
 * instant — upstream's values, kept for parity.
 */
public final class WaterGelBlockTester {

    private WaterGelBlockTester() {}

    /** 1.12.2's per-stage {pitch, hardness}, in {@link GelStage} order. */
    private static final float[][] UPSTREAM = {
        { 0.3f, 3f }, { 0.4f, 3f }, { 0.6f, 3f }, { 0.8f, 3f }, // SPREAD_0..3
        { 1.0f, 0.6f }, { 1.2f, 0.6f },                         // GELLING_0..1
        { 1.5f, 0.1f },                                         // GEL
    };

    public static void waterGelBreakSpeedAndSoundsPerStage(GameTestHelper helper) {
        helper.assertTrue(GelStage.VALUES.length == UPSTREAM.length,
            "water gel has " + GelStage.VALUES.length + " stages; 1.12.2 had " + UPSTREAM.length);

        BlockPos rel = new BlockPos(2, 2, 2);
        BlockPos abs = helper.absolutePos(rel);
        // Bare-handed survival player standing on the ground: dig speed 1, no airborne /5 penalty.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);

        for (GelStage stage : GelStage.VALUES) {
            float pitch = UPSTREAM[stage.ordinal()][0];
            float hardness = UPSTREAM[stage.ordinal()][1];
            BlockState state = BCFactoryBlocks.WATER_GEL.get().defaultBlockState()
                .setValue(BlockWaterGel.PROP_STAGE, stage);
            helper.setBlock(rel, state);
            BlockState placed = helper.getLevel().getBlockState(abs);
            helper.assertTrue(placed == state, "precondition: " + stage + " placed, got " + placed);

            // Hand-harvestable (1.12.2 Material.CLAY), so vanilla's harvestable branch: speed / hardness / 30.
            float expected = 1f / hardness / 30f;
            float progress = placed.getDestroyProgress(player, helper.getLevel(), abs);
            helper.assertTrue(Math.abs(progress - expected) < 1e-6f,
                stage + ": bare-hand dig progress per tick must be 1/(" + hardness + "*30) = " + expected
                    + ", got " + progress);

            SoundType sound = placed.getSoundType(helper.getLevel(), abs, player);
            helper.assertTrue(sound.getBreakSound() == SoundEvents.SLIME_BLOCK_BREAK
                    && sound.getStepSound() == SoundEvents.SLIME_BLOCK_STEP
                    && sound.getPlaceSound() == SoundEvents.SLIME_BLOCK_PLACE
                    && sound.getHitSound() == SoundEvents.SLIME_BLOCK_HIT
                    && sound.getFallSound() == SoundEvents.SLIME_BLOCK_FALL,
                stage + ": every gel sound must be the slime block's");
            helper.assertTrue(sound.getPitch() == pitch,
                stage + ": sound pitch must be " + pitch + ", got " + sound.getPitch());
            helper.assertTrue(sound.getVolume() == SoundType.SLIME_BLOCK.getVolume(),
                stage + ": sound volume must be the slime block's, got " + sound.getVolume());
        }

        // Spreading gel digs slower than set gel — the ordering a player notices first.
        BlockState spreading = BCFactoryBlocks.WATER_GEL.get().defaultBlockState()
            .setValue(BlockWaterGel.PROP_STAGE, GelStage.SPREAD_0);
        BlockState set = spreading.setValue(BlockWaterGel.PROP_STAGE, GelStage.GEL);
        helper.assertTrue(spreading.getDestroyProgress(player, helper.getLevel(), abs)
                < set.getDestroyProgress(player, helper.getLevel(), abs),
            "spreading gel must take longer to dig than set gel");
        helper.succeed();
    }
}
