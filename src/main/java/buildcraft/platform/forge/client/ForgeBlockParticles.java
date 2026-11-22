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
//? if >=26.3 {
import net.minecraft.core.Direction;
//?}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.client.ClientRegistrationSinks.BlockParticleOverride;

/^*
 * The per-block hit and break particles registered through {@code ClientRegistrationSinks#blockParticles}, asked by
 * {@code ClientLevelMixin} from 1.21.11 up and {@code ParticleEngineMixin} on 1.21.1. Forge does have NeoForge's hook
 * ({@code IClientBlockExtensions}), but it is attached by overriding {@code Block.initializeClient} in the block's own
 * class (there is no registration event), and its 26.3 hit hook is not given the block position BuildCraft's overrides
 * need; so the Fabric route is used: an override returning true replaces vanilla's particles. From 1.21.11 up it is
 * asked first in {@code addDestroyBlockEffect} (not for air); on 26.3 in place of {@code addBreakingParticles} in
 * {@code addBreakingBlockEffects} (after the hit sound), on 1.21.11, 26.1 and 26.2 at the head of Forge's
 * {@code addBreakingBlockEffect(BlockPos, BlockHitResult)}. On 1.21.1 it is asked first in {@code ParticleEngine.destroy}
 * (not for air) and in {@code ParticleEngine.addBlockHitEffects}, Forge's own hit-particle entry point. Before 26.3 the
 * hook shape is the hit result (as on NeoForge before 26.3), which is what is passed.
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

    //? if >=26.3 {
    /^* True when the block's override spawned its own hit particles, so vanilla's are skipped. *^/
    public static boolean addHitEffects(BlockState state, Level level, BlockPos pos, Direction face,
            ParticleEngine engine) {
        BlockParticleOverride particles = OVERRIDES.get(state.getBlock());
        return particles != null && particles.addHitEffects(state, level, pos, face, engine);
    }
    //?} else {
    /^// True when the block's override spawned its own hit particles, so vanilla's are skipped. Before 26.3 the override
    // takes the hit itself, which Forge 61, 64 and 65 hand to ClientLevel#addBreakingBlockEffect(BlockPos, BlockHitResult)
    // and Forge 52 (1.21.1) to ParticleEngine#addBlockHitEffects.
    public static boolean addHitEffects(BlockState state, Level level, net.minecraft.world.phys.HitResult target,
            ParticleEngine engine) {
        BlockParticleOverride particles = OVERRIDES.get(state.getBlock());
        return particles != null && particles.addHitEffects(state, level, target, engine);
    }
    ^///?}
}
*///?}
