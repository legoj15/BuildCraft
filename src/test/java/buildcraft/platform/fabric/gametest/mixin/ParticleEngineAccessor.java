/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.gametest.mixin;

//? if fabric {
/*import java.util.Queue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;

/^*
 * The particles spawned since the engine's last tick (the game-test mod only), so the client game test can see what a
 * block's break and hit effects spawned before they join the engine's groups.
 *^/
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {

    @Accessor("particlesToAdd")
    Queue<Particle> buildcraft$particlesToAdd();
}
*///?}
