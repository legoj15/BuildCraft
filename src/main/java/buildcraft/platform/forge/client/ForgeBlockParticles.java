/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge {
/*import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
//? if >=1.21.10 {
import net.minecraft.core.Direction;
//?}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.client.ClientRegistrationSinks.BlockParticleOverride;

/^*
 * The per-block hit and break particles registered through {@code ClientRegistrationSinks#blockParticles}, asked by
 * {@code ClientLevelMixin} on 26.3 and {@code ParticleEngineMixin} on 1.21.1. Forge does have NeoForge's hook
 * ({@code IClientBlockExtensions}), but it is attached by overriding {@code Block.initializeClient} in the block's own
 * class (there is no registration event), and its 26.3 hit hook is not given the block position BuildCraft's overrides
 * need; so the Fabric route is used: an override returning true replaces vanilla's particles. On 26.3 it is asked first
 * in {@code addDestroyBlockEffect} (not for air) and in place of {@code addBreakingParticles} in
 * {@code addBreakingBlockEffects} (after the hit sound); on 1.21.1 first in {@code ParticleEngine.destroy} (not for air)
 * and in {@code ParticleEngine.addBlockHitEffects}, Forge's own hit-particle entry point, where the 1.21.1 hook shape
 * (the hit result, as on NeoForge 1.21.1) is what is passed.
 *^/
public final class ForgeBlockParticles {

    private static final Map<Block, BlockParticleOverride> OVERRIDES = new ConcurrentHashMap<>();

    private ForgeBlockParticles() {}

    static void register(Block block, BlockParticleOverride particles) {
        OVERRIDES.put(block, particles);
    }

    /^* True when the block's override spawned its own break particles, so vanilla's are skipped. *^/
    public static boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine engine) {
        BlockParticleOverride particles = OVERRIDES.get(state.getBlock());
        return particles != null && particles.addDestroyEffects(state, level, pos, engine);
    }

    /^* True when the block's override spawned its own hit particles, so vanilla's are skipped. *^/
    //? if >=1.21.10 {
    public static boolean addHitEffects(BlockState state, Level level, BlockPos pos, Direction face,
            ParticleEngine engine) {
        BlockParticleOverride particles = OVERRIDES.get(state.getBlock());
        return particles != null && particles.addHitEffects(state, level, pos, face, engine);
    }
    //?} else {
    /^public static boolean addHitEffects(BlockState state, Level level, net.minecraft.world.phys.HitResult target,
            ParticleEngine engine) {
        BlockParticleOverride particles = OVERRIDES.get(state.getBlock());
        return particles != null && particles.addHitEffects(state, level, target, engine);
    }
    ^///?}
}
*///?}
