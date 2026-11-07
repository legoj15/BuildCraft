/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.client.ClientRegistrationSinks.BlockParticleOverride;

/^*
 * The per-block hit and break particles registered through {@code ClientRegistrationSinks#blockParticles}. Fabric API
 * 26.3 has no hook for these, so {@code ClientLevelMixin} asks here where NeoForge's patched {@code ClientLevel} asks
 * the block's {@code IClientBlockExtensions}: at the start of {@code addDestroyBlockEffect} (not for air) and in place
 * of {@code addBreakingParticles} inside {@code addBreakingBlockEffects} (after the hit sound; vanilla reaches that call
 * only when the block's render shape is not INVISIBLE, and that call spawns vanilla's terrain
 * particles). As on NeoForge, an override returning true replaces vanilla's particles.
 *^/
public final class FabricBlockParticles {

    private static final Map<Block, BlockParticleOverride> OVERRIDES = new ConcurrentHashMap<>();

    private FabricBlockParticles() {}

    static void register(Block block, BlockParticleOverride particles) {
        OVERRIDES.put(block, particles);
    }

    /^* True when the block's override spawned its own break particles, so vanilla's are skipped. *^/
    public static boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine engine) {
        BlockParticleOverride particles = OVERRIDES.get(state.getBlock());
        return particles != null && particles.addDestroyEffects(state, level, pos, engine);
    }

    /^* True when the block's override spawned its own hit particles, so vanilla's are skipped. *^/
    public static boolean addHitEffects(BlockState state, Level level, BlockPos pos, Direction face,
            ParticleEngine engine) {
        BlockParticleOverride particles = OVERRIDES.get(state.getBlock());
        return particles != null && particles.addHitEffects(state, level, pos, face, engine);
    }
}
*///?}
