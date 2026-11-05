/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import buildcraft.platform.FluidProperties;
import buildcraft.platform.fabric.FabricFlowingFluids;
import buildcraft.platform.fabric.FabricFluidPhysics;

/^*
 * NeoForge's per-type fluid tracking for BuildCraft's fluids ({@link FabricFluidPhysics}): a BuildCraft fluid never
 * feeds vanilla's water current (it is in the water tag, so it would); its own current is summed per fluid, where
 * NeoForge sums one per {@code FluidType}, as vanilla's scan reads each block's height; and the tracked fluids
 * answer NeoForge's water-like, drowning and fall-distance questions.
 *^/
@Mixin(EntityFluidInteraction.class)
abstract class EntityFluidInteractionMixin implements FabricFluidPhysics.Interaction {

    @Shadow
    @Final
    private List<Object> fluidTrackers;

    /^* The currents of the BuildCraft fluids this entity has touched, made on the first (an entity that never touches
     *  one, nearly all of them, carries no map). *^/
    @Unique
    @Nullable
    private Map<FluidProperties, FabricFluidPhysics.Current> buildcraft$currents;
    /^* Whether this scan pushes (vanilla's {@code !ignoreCurrent}), the entity's feet and the scanned box's bottom. *^/
    @Unique
    private boolean buildcraft$pushing;
    @Unique
    private double buildcraft$feetY;
    @Unique
    private double buildcraft$boxMinY;

    /^* Starts a scan where vanilla reads the box it scans: that is the box the pushing test needs, so it is not asked
     *  for a second time. *^/
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getFluidInteractionBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB buildcraft$startScan(Entity entity, Operation<AABB> original,
                                      @Local(argsOnly = true) boolean ignoreCurrent) {
        AABB box = original.call(entity);
        if (buildcraft$currents != null) {
            for (FabricFluidPhysics.Current current : buildcraft$currents.values()) {
                current.reset();
            }
        }
        buildcraft$pushing = !ignoreCurrent && box != null;
        buildcraft$feetY = entity.getBoundingBox().minY;
        buildcraft$boxMinY = box != null ? box.minY : 0.0;
        return box;
    }

    /^* Each fluid block vanilla's scan reads: a BuildCraft fluid that pushes adds its flow to its own current. *^/
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float buildcraft$scanBlock(FluidState state, BlockGetter level, BlockPos pos, Operation<Float> original) {
        float height = original.call(state, level, pos);
        if (buildcraft$pushing) {
            FluidProperties declared = FabricFlowingFluids.declared(state.getType());
            double top = pos.getY() + height;
            if (declared != null && declared.canPushEntity() && top >= buildcraft$boxMinY) {
                if (buildcraft$currents == null) {
                    buildcraft$currents = new IdentityHashMap<>(4);
                }
                buildcraft$currents.computeIfAbsent(declared, p -> new FabricFluidPhysics.Current())
                        .accumulate(top - buildcraft$feetY, state.getFlow(level, pos));
            }
        }
        return height;
    }

    /^* Vanilla picks a fluid's current by tag; a BuildCraft fluid has its own, so it matches none. *^/
    @WrapOperation(method = "getCurrentAccumulatorFor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Holder;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$ownCurrent(Holder<Fluid> fluid, TagKey<Fluid> tag, Operation<Boolean> original) {
        return FabricFlowingFluids.declared(fluid.value()) == null && original.call(fluid, tag);
    }

    @Override
    public boolean buildcraft$inWaterLike() {
        for (Object tracked : fluidTrackers) {
            EntityFluidTrackerAccessor tracker = (EntityFluidTrackerAccessor) tracked;
            if (tracker.buildcraft$height() > 0.0 && FabricFluidPhysics.isWaterLike(tracker.buildcraft$fluid())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$eyeInWaterLike() {
        for (Object tracked : fluidTrackers) {
            EntityFluidTrackerAccessor tracker = (EntityFluidTrackerAccessor) tracked;
            if (tracker.buildcraft$eyesInside() && FabricFluidPhysics.isWaterLike(tracker.buildcraft$fluid())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$inSwimmableDeeperThan(double depth, TagKey<Fluid> floatable) {
        for (Object tracked : fluidTrackers) {
            EntityFluidTrackerAccessor tracker = (EntityFluidTrackerAccessor) tracked;
            if (tracker.buildcraft$height() > depth
                    && FabricFluidPhysics.canSwimIn(tracker.buildcraft$fluid(), floatable)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$inBuildCraftFluid() {
        for (Object tracked : fluidTrackers) {
            EntityFluidTrackerAccessor tracker = (EntityFluidTrackerAccessor) tracked;
            if (tracker.buildcraft$height() > 0.0 && FabricFlowingFluids.declared(tracker.buildcraft$fluid().value()) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$eyeInBoatableWater() {
        for (Object tracked : fluidTrackers) {
            EntityFluidTrackerAccessor tracker = (EntityFluidTrackerAccessor) tracked;
            Holder<Fluid> fluid = tracker.buildcraft$fluid();
            if (tracker.buildcraft$eyesInside() && FabricFlowingFluids.declared(fluid.value()) == null
                    && fluid.is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$inKindOf(FluidProperties fluid) {
        for (Object tracked : fluidTrackers) {
            EntityFluidTrackerAccessor tracker = (EntityFluidTrackerAccessor) tracked;
            if (tracker.buildcraft$height() > 0.0 && FabricFlowingFluids.declared(tracker.buildcraft$fluid().value()) == fluid) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$eyeInDrowningFluid() {
        for (Object tracked : fluidTrackers) {
            EntityFluidTrackerAccessor tracker = (EntityFluidTrackerAccessor) tracked;
            if (tracker.buildcraft$eyesInside() && FabricFluidPhysics.canDrownIn(tracker.buildcraft$fluid())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public float buildcraft$fallDistanceModifier() {
        for (Object tracked : fluidTrackers) {
            EntityFluidTrackerAccessor tracker = (EntityFluidTrackerAccessor) tracked;
            FluidProperties declared = FabricFlowingFluids.declared(tracker.buildcraft$fluid().value());
            if (tracker.buildcraft$height() > 0.0 && declared != null && !declared.waterLike()) {
                return FabricFluidPhysics.FALL_DISTANCE_MODIFIER;
            }
        }
        return 1.0F;
    }

    @Override
    public void buildcraft$applyCurrents(Entity entity) {
        // Nothing to push (the case for nearly every entity on every tick): no lambda to build either.
        if (buildcraft$currents == null || buildcraft$currents.isEmpty()) {
            return;
        }
        buildcraft$currents.forEach((properties, current) ->
                current.applyTo(entity, properties.motionScale().getAsDouble()));
    }
}
*///?}
