/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import java.util.List;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
//? if >=26.1 {
import net.minecraft.world.entity.EntityFluidInteraction;
//?}
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import buildcraft.platform.fabric.FabricFluidPhysics;
import buildcraft.platform.fabric.FabricGameHooks;
import buildcraft.platform.fabric.FabricVanillaExtensions;
import buildcraft.platform.hook.BlockParticleEffects;
import buildcraft.platform.hook.PositionalSoundBlock;

/^*
 * NeoForge's {@code addRunningEffects} on Fabric: a {@link BlockParticleEffects} block draws its own sprint particles.
 * Also NeoForge's {@code captureDrops} ({@link FabricGameHooks.DropCapture}): while the entity's death collects its
 * drops, what {@code spawnAtLocation} drops is collected instead of put in the level. And NeoForge's fluid-type
 * checks in {@code baseTick} and {@code updateFluidInteraction} for BuildCraft's fluids ({@link FabricFluidPhysics}):
 * in water and eyes in water mean a water-like fluid, each fluid's own current pushes, and a fluid that is not
 * water-like damps the fall distance. A {@link PositionalSoundBlock} steps with its own sound, also as the muffled
 * half of a player's step on a carpet or snow layer ({@link FabricVanillaExtensions.StepSoundPositions}).
 *^/
@Mixin(Entity.class)
abstract class EntityMixin implements FabricGameHooks.DropCapture, FabricVanillaExtensions.StepSoundPositions {

    //? if >=1.21.10 {
    @Shadow
    public double fallDistance;
    //?} else {
    /^@Shadow
    public float fallDistance; // a float on 1.21.1
    ^///?}

    @Shadow
    public abstract boolean isInWater();

    @Shadow
    public abstract boolean isInLava();

    @Shadow
    public abstract boolean isPushedByFluid();

    @Shadow
    protected abstract BlockPos getOnPos(float offset);

    @Unique
    @Nullable
    private List<ItemEntity> buildcraft$capturedDrops;

    /^* The primary (carpet, snow layer) and muffled positions of the step sound being played, or null. *^/
    @Unique
    @Nullable
    private BlockPos buildcraft$primaryStepPos;

    @Unique
    @Nullable
    private BlockPos buildcraft$muffledStepPos;

    //? if >=26.1 {
    @WrapOperation(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$eyeInWater(Entity self, TagKey<Fluid> tag, Operation<Boolean> original) {
        return tag == FluidTags.WATER
                ? FabricFluidPhysics.interaction(self).buildcraft$eyeInWaterLike()
                : original.call(self, tag);
    }

    @WrapOperation(method = "updateFluidInteraction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityFluidInteraction;isInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$inWater(EntityFluidInteraction interaction, TagKey<Fluid> tag,
                                       Operation<Boolean> original) {
        return tag == FluidTags.WATER
                ? ((FabricFluidPhysics.Interaction) interaction).buildcraft$inWaterLike()
                : original.call(interaction, tag);
    }

    @Inject(method = "updateFluidInteraction", at = @At("RETURN"))
    private void buildcraft$applyFluidCurrents(CallbackInfoReturnable<Boolean> cir) {
        if (isPushedByFluid()) {
            FabricFluidPhysics.interaction((Entity) (Object) this).buildcraft$applyCurrents((Entity) (Object) this);
        }
    }
    //?} else {
    /^// Before 26.1 the eyes-in-water flag is set in updateFluidOnEyes and the in-water flag from the water pass of the
    // entity's own scan; both answer for a water-like fluid instead of the water tag. Vanilla's (and NeoForge's) eye
    // flag reads the previous tick's eye test; this one reads this tick's scan, a tick sooner.
    @WrapOperation(method = "updateFluidOnEyes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$eyeInWater(Entity self, TagKey<Fluid> tag, Operation<Boolean> original) {
        return tag == FluidTags.WATER
                ? FabricFluidPhysics.interaction(self).buildcraft$eyeInWaterLike()
                : original.call(self, tag);
    }

    @WrapOperation(method = "updateInWaterStateAndDoWaterCurrentPushing", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z"))
    private boolean buildcraft$inWater(Entity self, TagKey<Fluid> tag, double motionScale,
                                       Operation<Boolean> original) {
        boolean touching = original.call(self, tag, motionScale);
        return tag == FluidTags.WATER ? FabricFluidPhysics.interaction(self).buildcraft$inWaterLike() : touching;
    }

    @Inject(method = "updateInWaterStateAndDoFluidPushing", at = @At("RETURN"))
    private void buildcraft$applyFluidCurrents(CallbackInfoReturnable<Boolean> cir) {
        if (isPushedByFluid()) {
            FabricFluidPhysics.interaction((Entity) (Object) this).buildcraft$applyCurrents((Entity) (Object) this);
        }
    }
    ^///?}

    /^* NeoForge's {@code fallDistance *= getFallDistanceModifier}, after the lava halving and before the void check. *^/
    @Inject(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;checkBelowWorld()V"))
    private void buildcraft$fluidFallDistance(CallbackInfo ci) {
        if (!isInLava() && !isInWater()) {
            fallDistance *= FabricFluidPhysics.interaction((Entity) (Object) this).buildcraft$fallDistanceModifier();
        }
    }

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void buildcraft$runningEffects(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        // The block vanilla reads next, at the same position: the body of its deprecated getOnPosLegacy().
        BlockPos pos = getOnPos(0.2F);
        BlockState state = self.level().getBlockState(pos);
        if (state.getBlock() instanceof BlockParticleEffects block && block.spawnRunningEffects(self.level(), pos, self)) {
            ci.cancel();
        }
    }

    //? if >=1.21.10 {
    @WrapOperation(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean buildcraft$captureDrop(ServerLevel level, Entity drop, Operation<Boolean> original) {
        return FabricGameHooks.captureDrop((Entity) (Object) this, (ItemEntity) drop) || original.call(level, drop);
    }
    //?} else {
    /^@WrapOperation(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean buildcraft$captureDrop(net.minecraft.world.level.Level level, Entity drop,
            Operation<Boolean> original) {
        return FabricGameHooks.captureDrop((Entity) (Object) this, (ItemEntity) drop) || original.call(level, drop);
    }
    ^///?}

    /^* NeoForge's position-aware step sound ({@code IBlockExtension#playStepSound}). *^/
    @WrapOperation(method = "playStepSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType buildcraft$stepSound(BlockState state, Operation<SoundType> original,
                                           @Local(argsOnly = true) BlockPos pos) {
        Entity self = (Entity) (Object) this;
        return state.getBlock() instanceof PositionalSoundBlock
                ? FabricVanillaExtensions.positionalSoundType(state, self.level(), pos, self)
                : original.call(state);
    }

    /^* The two halves of NeoForge's position-aware combination step ({@code playCombinationStepSounds}). *^/
    @WrapOperation(method = "playCombinationStepSounds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType buildcraft$combinationStepSound(BlockState state, Operation<SoundType> original) {
        return stepSoundAt(state, buildcraft$primaryStepPos, original);
    }

    @WrapOperation(method = "playMuffledStepSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType buildcraft$muffledStepSound(BlockState state, Operation<SoundType> original) {
        return stepSoundAt(state, buildcraft$muffledStepPos, original);
    }

    /^* Without a position (a caller other than {@code Player.playStepSound}), the state's own sound. *^/
    @Unique
    private SoundType stepSoundAt(BlockState state, @Nullable BlockPos pos, Operation<SoundType> original) {
        Entity self = (Entity) (Object) this;
        return pos != null && state.getBlock() instanceof PositionalSoundBlock
                ? FabricVanillaExtensions.positionalSoundType(state, self.level(), pos, self)
                : original.call(state);
    }

    @Override
    public void buildcraft$setStepSoundPositions(@Nullable BlockPos primary, @Nullable BlockPos muffled) {
        buildcraft$primaryStepPos = primary;
        buildcraft$muffledStepPos = muffled;
    }

    @Override
    @Nullable
    public List<ItemEntity> buildcraft$capturedDrops() {
        return buildcraft$capturedDrops;
    }

    @Override
    public void buildcraft$setCapturedDrops(@Nullable List<ItemEntity> drops) {
        buildcraft$capturedDrops = drops;
    }
}
*///?}
