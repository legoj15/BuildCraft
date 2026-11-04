/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * A block that draws its own sprint and landing particles (NeoForge's {@code addRunningEffects} and
 * {@code addLandingEffects}).
 */
public interface BlockParticleEffects {
    /** {@code entity} sprints over the block at {@code pos}; true when the block spawned the particles itself. */
    boolean spawnRunningEffects(Level level, BlockPos pos, Entity entity);

    /** {@code entity} lands on the block at {@code pos}; true when the block sent the {@code count} particles itself. */
    boolean spawnLandingEffects(ServerLevel level, BlockPos pos, LivingEntity entity, int count);
}
