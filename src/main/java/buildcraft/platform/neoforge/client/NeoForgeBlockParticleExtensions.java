/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge.client;

//? if neoforge {
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

//? if <26.3 {
/*import net.minecraft.world.phys.HitResult;

import org.jspecify.annotations.Nullable;
*///?}

import buildcraft.platform.client.ClientRegistrationSinks.BlockParticleOverride;

/** A block's {@link IClientBlockExtensions}: its hit and break particles, from a {@link BlockParticleOverride}. */
final class NeoForgeBlockParticleExtensions implements IClientBlockExtensions {
    private final BlockParticleOverride particles;

    NeoForgeBlockParticleExtensions(BlockParticleOverride particles) {
        this.particles = particles;
    }

    // 26.3 drops the HitResult argument: vanilla passes the position and face being hit instead.
    @Override
    //? if >=26.3 {
    public boolean addHitEffects(BlockState state, Level level, BlockPos pos, net.minecraft.core.Direction face,
            ParticleEngine manager) {
        return particles.addHitEffects(state, level, pos, face, manager);
    }
    //?} else {
    /*public boolean addHitEffects(BlockState state, Level level,
            @Nullable HitResult target, ParticleEngine manager) {
        return particles.addHitEffects(state, level, target, manager);
    }*/
    //?}

    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
        return particles.addDestroyEffects(state, level, pos, manager);
    }
}
//?}
