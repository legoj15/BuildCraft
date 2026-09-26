/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import buildcraft.robotics.BCRoboticsParticles;

/**
 * Data for the robot's red energy exhaust puff: its size multiplier, which scales both the quad and the
 * lifetime (see {@link buildcraft.robotics.entity.RobotExhaust#particleSize}). Common-side on purpose — the
 * particle TYPE is registry content and must exist on servers; only its renderer is client-only.
 */
public record RobotEnergyParticleOptions(float size) implements ParticleOptions {
    public static final MapCodec<RobotEnergyParticleOptions> MAP_CODEC =
            Codec.FLOAT.fieldOf("size").xmap(RobotEnergyParticleOptions::new, RobotEnergyParticleOptions::size);

    public static final StreamCodec<ByteBuf, RobotEnergyParticleOptions> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(RobotEnergyParticleOptions::new, RobotEnergyParticleOptions::size);

    @Override
    public ParticleType<RobotEnergyParticleOptions> getType() {
        return BCRoboticsParticles.ROBOT_ENERGY.get();
    }
}
