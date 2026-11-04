/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.hook.BlockParticleEffects;

/^* NeoForge's {@code addRunningEffects} on Fabric: a {@link BlockParticleEffects} block draws its own sprint particles. *^/
@Mixin(Entity.class)
abstract class EntityMixin {

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void buildcraft$runningEffects(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        // The block vanilla reads next, at the same position.
        BlockPos pos = self.getOnPosLegacy();
        BlockState state = self.level().getBlockState(pos);
        if (state.getBlock() instanceof BlockParticleEffects block && block.spawnRunningEffects(self.level(), pos, self)) {
            ci.cancel();
        }
    }
}
*///?}
