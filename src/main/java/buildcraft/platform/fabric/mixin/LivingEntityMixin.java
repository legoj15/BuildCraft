/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.hook.BlockParticleEffects;

/^*
 * NeoForge's {@code addLandingEffects} on Fabric: a {@link BlockParticleEffects} block sends its own landing particles
 * in place of vanilla's block-particle burst, with the same particle count.
 *^/
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {

    @WrapOperation(method = "checkFallDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
    private int buildcraft$landingEffects(ServerLevel level, ParticleOptions particle, double x, double y, double z,
                                          int count, double dx, double dy, double dz, double speed,
                                          Operation<Integer> original, @Local(argsOnly = true) BlockState onState,
                                          @Local(argsOnly = true) BlockPos pos) {
        if (onState.getBlock() instanceof BlockParticleEffects block
                && block.spawnLandingEffects(level, pos, (LivingEntity) (Object) this, count)) {
            return 0;
        }
        return original.call(level, particle, x, y, z, count, dx, dy, dz, speed);
    }
}
*///?}
