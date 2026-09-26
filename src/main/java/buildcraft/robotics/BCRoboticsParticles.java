/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.robotics.particle.RobotEnergyParticleOptions;

/** Robotics particle types. The client providers live in {@code BCRoboticsClient}. */
public class BCRoboticsParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, BCRobotics.MODID);

    /** The robot's red energy exhaust (7.1.x {@code EntityRobotEnergyParticle}). Not limiter-overriding by
     *  type: the robot opts out of the limiter per spawn, because it applies 7.1.x's own particle-setting
     *  thinning ({@link buildcraft.robotics.entity.RobotExhaust}). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<RobotEnergyParticleOptions>> ROBOT_ENERGY =
            PARTICLE_TYPES.register("robot_energy", () -> new ParticleType<RobotEnergyParticleOptions>(false) {
                @Override
                public MapCodec<RobotEnergyParticleOptions> codec() {
                    return RobotEnergyParticleOptions.MAP_CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, RobotEnergyParticleOptions> streamCodec() {
                    return RobotEnergyParticleOptions.STREAM_CODEC;
                }
            });

    public static void init(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
