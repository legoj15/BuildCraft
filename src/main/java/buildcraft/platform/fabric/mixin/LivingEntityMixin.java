/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import buildcraft.platform.FluidProperties;
import buildcraft.platform.ThickFluidMotion;
import buildcraft.platform.fabric.FabricFlowingFluids;
import buildcraft.platform.fabric.FabricFluidPhysics;
import buildcraft.platform.fabric.FabricGameHooks;
import buildcraft.platform.fabric.FabricVanillaExtensions;
import buildcraft.platform.hook.BlockParticleEffects;
import buildcraft.platform.hook.PositionalSoundBlock;

/^*
 * NeoForge's {@code addLandingEffects} on Fabric: a {@link BlockParticleEffects} block sends its own landing particles
 * in place of vanilla's block-particle burst, with the same particle count. Also NeoForge's {@code LivingDropsEvent}
 * ({@link FabricGameHooks#onLivingDrops}): {@code dropAllDeathLoot} collects its drops (here, in
 * {@code EntityMixin}, {@code EntityEquipmentMixin} and {@code InventoryMixin}, NeoForge's capture sites), shows them to
 * the listeners, then puts them in the level. And NeoForge's {@code onLivingBreathe} test for BuildCraft's fluids
 * and {@code travel} ({@link FabricFluidPhysics}): the entity drowns with its eyes in a fluid it can drown in, and
 * wades through one that is not water-like by {@link ThickFluidMotion}, as the NeoForge fluid type's {@code move},
 * then takes vanilla's ridden-mount lift; a rider is thrown off a mount only with its eyes in a water-tag fluid that
 * is not BuildCraft's.
 *^/
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {

    @Shadow
    protected boolean jumping;

    @Shadow
    protected abstract double getEffectiveGravity();

    @Shadow
    private void floatInLiquidWhileRidden(TagKey<Fluid> fluidTag) {}

    /^* The BuildCraft fluid that is not water-like this entity travels in this tick (NeoForge's {@code travel} takes
     *  {@code travelInFluid} for it, which hands it to the type's {@code move}), or null. *^/
    @Unique
    private FluidProperties buildcraft$thickFluid() {
        LivingEntity self = (LivingEntity) (Object) this;
        FabricFluidPhysics.Interaction interaction =
                (FabricFluidPhysics.Interaction) ((EntityAccessor) self).buildcraft$fluidInteraction();
        // The entity's own fluid tracking says first whether any BuildCraft fluid is near: the answer for nearly
        // every entity on every tick, and no block lookup for it (vanilla's travel makes its own right after).
        if (!interaction.buildcraft$inBuildCraftFluid()) {
            return null;
        }
        FluidState state = self.level().getFluidState(self.blockPosition());
        FluidProperties declared = FabricFlowingFluids.declared(state.getType());
        if (declared != null && ThickFluidMotion.isThick(declared) && !self.isInWater() && !self.isInLava()
                && self.isAffectedByFluids() && !self.canStandOnFluid(state)
                && interaction.buildcraft$inKindOf(declared)) {
            return declared;
        }
        return null;
    }

    /^* NeoForge's {@code travel} in a BuildCraft fluid that is not water-like: the type's {@code move}, which is
     *  {@link ThickFluidMotion}. A jumping entity off the ground first rises as NeoForge's {@code jumpInFluid} lifts it
     *  (vanilla's {@code aiStep} jumps only from the ground in a fluid it does not know as water). *^/
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void buildcraft$travelInFluidType(Vec3 input, CallbackInfo ci) {
        FluidProperties thick = buildcraft$thickFluid();
        if (thick == null) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (jumping && !self.onGround()) {
            self.setDeltaMovement(self.getDeltaMovement().add(0.0, FabricFluidPhysics.FLUID_JUMP_SPEED, 0.0));
        }
        ThickFluidMotion.travel(self, thick, input, getEffectiveGravity());
        // Vanilla's travelInFluid ends with this lift for a ridden can_float_while_ridden mount in any water-tag
        // fluid, after the type's move on NeoForge 26.3, so a ridden horse floats in crude oil there too (#142).
        floatInLiquidWhileRidden(FluidTags.ENTITY_FLOATABLE);
        ci.cancel();
    }

    /^* NeoForge's {@code aiStep} jump on the ground in a thick fluid deeper than the jump threshold: a
     *  {@code jumpInFluid} lift rather than a jump from the ground. *^/
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;jumpFromGround()V"))
    private void buildcraft$jumpInThickFluid(LivingEntity self, Operation<Void> original) {
        if (buildcraft$thickFluid() != null
                && self.getFluidHeight(FluidTags.WATER) > self.getFluidJumpThreshold()) {
            self.setDeltaMovement(self.getDeltaMovement().add(0.0, FabricFluidPhysics.FLUID_JUMP_SPEED, 0.0));
            return;
        }
        original.call(self);
    }

    @WrapOperation(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$eyeInDrowningFluid(LivingEntity self, TagKey<Fluid> tag, Operation<Boolean> original) {
        return tag == FluidTags.WATER
                ? ((FabricFluidPhysics.Interaction) ((EntityAccessor) self).buildcraft$fluidInteraction())
                        .buildcraft$eyeInDrowningFluid()
                : original.call(self, tag);
    }

    /^* NeoForge's {@code canBeRiddenUnderFluidType}: a rider is thrown off a {@code dismounts_underwater} mount only
     *  with its eyes in a water-tag fluid that is not BuildCraft's, never in a BuildCraft fluid (vanilla asks inside
     *  its eye-in-water branch, which the tag-based test lets crude oil reach). Another mod's water-tag fluid has no
     *  fluid type on Fabric and counts as water here, as it does for boats. *^/
    @WrapOperation(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;dismountsUnderwater()Z"))
    private boolean buildcraft$dismountsInWaterOnly(Entity vehicle, Operation<Boolean> original) {
        return original.call(vehicle)
                && ((FabricFluidPhysics.Interaction) ((EntityAccessor) (Object) this).buildcraft$fluidInteraction())
                        .buildcraft$eyeInForeignWater();
    }

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

    /^* NeoForge's position-aware fall sound ({@code IBlockExtension#playFallSound}), for the block vanilla just read. *^/
    @WrapOperation(method = "playBlockFallSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType buildcraft$fallSound(BlockState state, Operation<SoundType> original) {
        if (!(state.getBlock() instanceof PositionalSoundBlock)) {
            return original.call(state);
        }
        LivingEntity self = (LivingEntity) (Object) this;
        BlockPos pos = new BlockPos(Mth.floor(self.getX()), Mth.floor(self.getY() - 0.2F), Mth.floor(self.getZ()));
        return FabricVanillaExtensions.positionalSoundType(state, self.level(), pos, self);
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
