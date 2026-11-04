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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.fabric.FabricGameHooks;
import buildcraft.platform.hook.BlockParticleEffects;

/^*
 * NeoForge's {@code addLandingEffects} on Fabric: a {@link BlockParticleEffects} block sends its own landing particles
 * in place of vanilla's block-particle burst, with the same particle count. Also NeoForge's {@code LivingDropsEvent}
 * ({@link FabricGameHooks#onLivingDrops}): {@code dropAllDeathLoot} collects its drops (here, in
 * {@code EntityMixin}, {@code EntityEquipmentMixin} and {@code InventoryMixin}, NeoForge's capture sites), shows them to
 * the listeners, then puts them in the level.
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

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void buildcraft$startDropCapture(ServerLevel level, DamageSource source, CallbackInfo ci) {
        FabricGameHooks.startDropCapture((LivingEntity) (Object) this);
    }

    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void buildcraft$postDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        FabricGameHooks.finishDropCapture((LivingEntity) (Object) this, source);
    }

    @WrapOperation(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean buildcraft$captureDrop(Level level, Entity drop, Operation<Boolean> original) {
        return FabricGameHooks.captureDrop((LivingEntity) (Object) this, (ItemEntity) drop) || original.call(level, drop);
    }
}
*///?}
