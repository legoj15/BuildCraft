/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

import buildcraft.robotics.BCRoboticsParticles;
import buildcraft.robotics.entity.RobotExhaust;

/**
 * Data for the robot's red energy exhaust puff: its size multiplier, which scales both the quad and the
 * lifetime (see {@link buildcraft.robotics.entity.RobotExhaust#particleSize}). Common-side on purpose — the
 * particle TYPE is registry content and must exist on servers; only its renderer is client-only.
 *
 * <p>The size is untrusted input here: {@code /particle} and any addon packet reach this record without going
 * through {@code RobotExhaust.particleSize}, and an unbounded size means a screen-filling puff that lives for
 * days on every client in range. So, like vanilla's {@code ScalableParticleOptionsBase}, the JSON codec rejects
 * sizes outside {@code [MIN_SIZE, MAX_PARTICLE_SIZE]} (a clear command error) and the constructor clamps, which
 * also bounds the network path; NaN falls back to the normal size of 1.
 */
public record RobotEnergyParticleOptions(float size) implements ParticleOptions {
    /** Vanilla's scalable-particle floor; small enough never to matter, positive so the quad never inverts. */
    public static final float MIN_SIZE = 0.01F;

    private static final Codec<Float> SIZE = Codec.FLOAT.validate(v -> v >= MIN_SIZE
            && v <= RobotExhaust.MAX_PARTICLE_SIZE ? DataResult.success(v)
                    : DataResult.error(() -> "Robot exhaust size must be within [" + MIN_SIZE + ";"
                            + RobotExhaust.MAX_PARTICLE_SIZE + "]: " + v));

    public static final MapCodec<RobotEnergyParticleOptions> MAP_CODEC =
            SIZE.fieldOf("size").xmap(RobotEnergyParticleOptions::new, RobotEnergyParticleOptions::size);

    public static final StreamCodec<ByteBuf, RobotEnergyParticleOptions> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(RobotEnergyParticleOptions::new, RobotEnergyParticleOptions::size);

    public RobotEnergyParticleOptions {
        // Mth.clamp passes NaN straight through, so it needs its own fallback.
        size = Float.isNaN(size) ? 1F : Mth.clamp(size, MIN_SIZE, RobotExhaust.MAX_PARTICLE_SIZE);
    }

    @Override
    public ParticleType<RobotEnergyParticleOptions> getType() {
        return BCRoboticsParticles.ROBOT_ENERGY.get();
    }
}
