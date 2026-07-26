/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

import buildcraft.energy.BCEnergyConfig;

/**
 * The {@link LiquidBlock} backing every BuildCraft energy fluid. Identical to the vanilla
 * block except that <b>searing</b> (heat-tier 2) fluids vent rising steam off their exposed
 * surface — the same white {@link ParticleTypes#CLOUD} billow BuildCraft already uses for
 * Heat Exchanger steam.
 *
 * <p>(The Minecraft 26.2 {@code NOXIOUS_GAS} wisp was the original inspiration, but it reads
 * sulfur-yellow — its colour is baked into the vanilla sprite and the particle hardcodes a
 * white vertex tint, so a {@code SimpleParticleType} caller can't recolour it. {@code CLOUD}
 * is natively white and exists on every node, so it both looks right and needs no version
 * directive.)
 *
 * <p>The effect is purely cosmetic and client-side (driven by {@link #animateTick}, the
 * random display tick). It honours the {@code searingFluidSteam} config toggle and only
 * fires where the block above is air, so steam rises off the top of a pool rather than
 * inside the column.
 *
 * <p><b>Heat is baked in at registration</b> ({@code heat >= 2 -> searing}) so the per-tick
 * path is a couple of cheap field reads — no {@code BCEnergyFluids.getHeat} scan in the
 * client render loop.
 */
public class BCFluidBlock extends LiquidBlock {

    /** 1-in-N chance per display-tick sample to vent a wisp. Keeps the surface lively but not a fog. */
    private static final int STEAM_SPAWN_CHANCE = 8;

    private final boolean searing;

    public BCFluidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties, int heat) {
        super(fluid, properties);
        this.searing = heat >= 2;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);

        if (!searing) {
            return;
        }
        // Null-guarded: fluid registration runs in a static initializer that can precede config load.
        if (BCEnergyConfig.searingFluidSteam == null || !BCEnergyConfig.searingFluidSteam.get()) {
            return;
        }
        // Only vent off an exposed surface, so steam rises into open air, not into a covering block.
        if (!level.getBlockState(pos.above()).isAir()) {
            return;
        }
        if (random.nextInt(STEAM_SPAWN_CHANCE) != 0) {
            return;
        }

        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + random.nextDouble();
        // CLOUD is friction-damped with no gravity, so give it a gentle upward push to read as
        // rising steam (the Heat Exchanger drives the same particle harder for its steam jet).
        double rise = 0.04 + random.nextDouble() * 0.04;
        level.addParticle(ParticleTypes.CLOUD, x, y, z, 0.0, rise, 0.0);
    }
}
