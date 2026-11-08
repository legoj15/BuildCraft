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
import org.spongepowered.asm.mixin.injection.Coerce;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
//? if >=26.1 {
import net.minecraft.world.entity.EntityFluidInteraction;
//?} else {
/^import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
^///?}
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.platform.FluidProperties;
import buildcraft.platform.fabric.FabricFlowingFluids;
import buildcraft.platform.fabric.FabricFluidPhysics;

/^*
 * NeoForge's per-type fluid tracking for BuildCraft's fluids ({@link FabricFluidPhysics}): a BuildCraft fluid never
 * feeds vanilla's water current (it is in the water tag, so it would); its own current is summed per fluid, where
 * NeoForge sums one per {@code FluidType}, as vanilla's scan reads each block's height; and the tracked fluids
 * answer NeoForge's water-like, drowning and fall-distance questions. Vanilla 26.3 tracks each fluid apart, so those
 * answers read its trackers; on 26.1 and 26.2, which track per tag, the same scan also fills a
 * {@link FabricFluidPhysics.FluidTracking}. Before 26.1 the scan is the entity's own, once per tag
 * ({@code updateFluidHeightAndDoFluidPushing}, from {@code updateInWaterStateAndDoFluidPushing}), so this targets
 * {@code Entity} and fills the same record from both passes.
 *^/
//? if >=26.1 {
@Mixin(EntityFluidInteraction.class)
//?} else {
/^@Mixin(Entity.class)
^///?}
abstract class EntityFluidInteractionMixin implements FabricFluidPhysics.Interaction {

    //? if >=26.3 {
    @Shadow
    @Final
    private List<Object> fluidTrackers;
    //?} else {
    /^// Each fluid the scan reached this tick, made on the first fluid the entity touches (nearly every entity never
    // does, and carries none).
    @Unique
    @Nullable
    private FabricFluidPhysics.FluidTracking buildcraft$tracking;
    // The column and height of the entity's eyes this scan, as vanilla reads them for its eyes-inside test.
    @Unique
    private int buildcraft$eyeBlockX;
    @Unique
    private int buildcraft$eyeBlockZ;
    @Unique
    private double buildcraft$eyeY;
    // Whether the block the scan just read is a BuildCraft fluid.
    @Unique
    private boolean buildcraft$scanningOwn;
    ^///?}

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

    //? if >=26.1 {
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
        buildcraft$beginTracking(entity);
        return box;
    }

    /^* Each fluid block vanilla's scan reads: a BuildCraft fluid that pushes adds its flow to its own current. *^/
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float buildcraft$scanBlock(FluidState state, BlockGetter level, BlockPos pos, Operation<Float> original) {
        float height = original.call(state, level, pos);
        buildcraft$track(state, pos, height);
        if (buildcraft$pushing) {
            buildcraft$accumulate(state, level, pos, height);
        }
        return height;
    }
    //?} else {
    /^// Starts a scan: the water and lava passes that follow read the same box, the entity's own shrunk by 0.001, and
    // measure each fluid's height from its bottom.
    @Inject(method = "updateInWaterStateAndDoFluidPushing", at = @At("HEAD"))
    private void buildcraft$startScan(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (buildcraft$currents != null) {
            for (FabricFluidPhysics.Current current : buildcraft$currents.values()) {
                current.reset();
            }
        }
        AABB box = entity.getBoundingBox().deflate(0.001);
        buildcraft$pushing = entity.isPushedByFluid();
        buildcraft$feetY = box.minY;
        buildcraft$boxMinY = box.minY;
        buildcraft$beginTracking(entity);
    }

    // Each fluid block a pass reads; only the water pass (every BuildCraft fluid is in the water tag) sums currents.
    @WrapOperation(method = "updateFluidHeightAndDoFluidPushing", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float buildcraft$scanBlock(FluidState state, BlockGetter level, BlockPos pos, Operation<Float> original,
                                       @Local(argsOnly = true) TagKey<Fluid> tag) {
        float height = original.call(state, level, pos);
        buildcraft$track(state, pos, height);
        if (buildcraft$pushing && tag == FluidTags.WATER) {
            buildcraft$accumulate(state, level, pos, height);
        }
        return height;
    }
    ^///?}

    /^* A BuildCraft fluid that pushes adds this block's flow to its own current. *^/
    @Unique
    private void buildcraft$accumulate(FluidState state, BlockGetter level, BlockPos pos, float height) {
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

    //? if >=26.3 {
    // Vanilla 26.3 tracks each fluid itself: nothing to record.
    @Unique
    private void buildcraft$beginTracking(Entity entity) {
    }

    @Unique
    private void buildcraft$track(FluidState state, BlockPos pos, float height) {
    }
    //?} else {
    /^@Unique
    private void buildcraft$beginTracking(Entity entity) {
        if (buildcraft$tracking != null) {
            buildcraft$tracking.reset();
        }
        buildcraft$eyeBlockX = entity.getBlockX();
        buildcraft$eyeBlockZ = entity.getBlockZ();
        buildcraft$eyeY = entity.getEyeY();
    }

    // What vanilla 26.3's per-fluid tracker takes from this block: any fluid whose surface reaches the box.
    @Unique
    private void buildcraft$track(FluidState state, BlockPos pos, float height) {
        double fluidTop = pos.getY() + height;
        buildcraft$scanningOwn = FabricFlowingFluids.declared(state.getType()) != null;
        if (fluidTop >= buildcraft$boxMinY) {
            if (buildcraft$tracking == null) {
                buildcraft$tracking = new FabricFluidPhysics.FluidTracking();
            }
            boolean eyesInside = pos.getX() == buildcraft$eyeBlockX && pos.getZ() == buildcraft$eyeBlockZ
                    && buildcraft$eyeY >= pos.getY() && buildcraft$eyeY <= fluidTop;
            buildcraft$tracking.record(buildcraft$holder(state), fluidTop - buildcraft$feetY, eyesInside);
        }
    }
    ^///?}

    //? if >=26.1 {
    @Unique
    private static Holder<Fluid> buildcraft$holder(FluidState state) {
        return state.typeHolder();
    }
    //?} else {
    /^@Unique
    private static Holder<Fluid> buildcraft$holder(FluidState state) {
        return state.holder();
    }
    ^///?}

    //? if >=26.3 {
    /^* Vanilla picks a fluid's current by tag; a BuildCraft fluid has its own, so it matches none. *^/
    @WrapOperation(method = "getCurrentAccumulatorFor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Holder;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$ownCurrent(Holder<Fluid> fluid, TagKey<Fluid> tag, Operation<Boolean> original) {
        return FabricFlowingFluids.declared(fluid.value()) == null && original.call(fluid, tag);
    }

    @Unique
    private int buildcraft$trackedCount() {
        return fluidTrackers.size();
    }

    @Unique
    private Holder<Fluid> buildcraft$trackedFluid(int index) {
        return ((EntityFluidTrackerAccessor) fluidTrackers.get(index)).buildcraft$fluid();
    }

    @Unique
    private double buildcraft$trackedHeight(int index) {
        return ((EntityFluidTrackerAccessor) fluidTrackers.get(index)).buildcraft$height();
    }

    @Unique
    private boolean buildcraft$trackedEyesInside(int index) {
        return ((EntityFluidTrackerAccessor) fluidTrackers.get(index)).buildcraft$eyesInside();
    }
    //?} elif >=26.1 {
    /^// Vanilla adds every water-tag block's flow to the water tag's tracker; a BuildCraft fluid has its own current.
    // The tracker's height still counts the BuildCraft fluid, as the tag queries do.
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityFluidInteraction$Tracker;accumulateCurrent(Lnet/minecraft/world/phys/Vec3;)V"))
    private void buildcraft$ownCurrent(@Coerce Object tracker, Vec3 flow, Operation<Void> original) {
        if (!buildcraft$scanningOwn) {
            original.call(tracker, flow);
        }
    }

    @Unique
    private int buildcraft$trackedCount() {
        return buildcraft$tracking != null ? buildcraft$tracking.count() : 0;
    }

    @Unique
    private Holder<Fluid> buildcraft$trackedFluid(int index) {
        return buildcraft$tracking.fluid(index);
    }

    @Unique
    private double buildcraft$trackedHeight(int index) {
        return buildcraft$tracking.height(index);
    }

    @Unique
    private boolean buildcraft$trackedEyesInside(int index) {
        return buildcraft$tracking.eyesInside(index);
    }
    ^///?} else {
    /^// Vanilla adds every water-tag block's flow to the water current; a BuildCraft fluid has its own. Its block still
    // counts in the water pass's average (a player's push is the mean over the blocks read) and in the water height,
    // as the tag queries do.
    @WrapOperation(method = "updateFluidHeightAndDoFluidPushing", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getFlow(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 buildcraft$ownCurrent(FluidState state, BlockGetter level, BlockPos pos, Operation<Vec3> original) {
        return FabricFlowingFluids.declared(state.getType()) != null ? Vec3.ZERO : original.call(state, level, pos);
    }

    @Unique
    private int buildcraft$trackedCount() {
        return buildcraft$tracking != null ? buildcraft$tracking.count() : 0;
    }

    @Unique
    private Holder<Fluid> buildcraft$trackedFluid(int index) {
        return buildcraft$tracking.fluid(index);
    }

    @Unique
    private double buildcraft$trackedHeight(int index) {
        return buildcraft$tracking.height(index);
    }

    @Unique
    private boolean buildcraft$trackedEyesInside(int index) {
        return buildcraft$tracking.eyesInside(index);
    }
    ^///?}

    @Override
    public boolean buildcraft$inWaterLike() {
        for (int i = 0; i < buildcraft$trackedCount(); i++) {
            if (buildcraft$trackedHeight(i) > 0.0 && FabricFluidPhysics.isWaterLike(buildcraft$trackedFluid(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$eyeInWaterLike() {
        for (int i = 0; i < buildcraft$trackedCount(); i++) {
            if (buildcraft$trackedEyesInside(i) && FabricFluidPhysics.isWaterLike(buildcraft$trackedFluid(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$inSwimmableDeeperThan(double depth, TagKey<Fluid> floatable) {
        for (int i = 0; i < buildcraft$trackedCount(); i++) {
            if (buildcraft$trackedHeight(i) > depth
                    && FabricFluidPhysics.canSwimIn(buildcraft$trackedFluid(i), floatable)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$inBuildCraftFluid() {
        for (int i = 0; i < buildcraft$trackedCount(); i++) {
            if (buildcraft$trackedHeight(i) > 0.0
                    && FabricFlowingFluids.declared(buildcraft$trackedFluid(i).value()) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$eyeInBoatableWater() {
        for (int i = 0; i < buildcraft$trackedCount(); i++) {
            Holder<Fluid> fluid = buildcraft$trackedFluid(i);
            if (buildcraft$trackedEyesInside(i) && fluid.is(FluidTags.WATER)
                    && FabricFluidPhysics.boatsFloatOn(fluid.value())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$eyeInForeignWater() {
        for (int i = 0; i < buildcraft$trackedCount(); i++) {
            Holder<Fluid> fluid = buildcraft$trackedFluid(i);
            if (buildcraft$trackedEyesInside(i) && FabricFlowingFluids.declared(fluid.value()) == null
                    && fluid.is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$inKindOf(FluidProperties fluid) {
        for (int i = 0; i < buildcraft$trackedCount(); i++) {
            if (buildcraft$trackedHeight(i) > 0.0
                    && FabricFlowingFluids.declared(buildcraft$trackedFluid(i).value()) == fluid) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$eyeInDrowningFluid() {
        for (int i = 0; i < buildcraft$trackedCount(); i++) {
            if (buildcraft$trackedEyesInside(i) && FabricFluidPhysics.canDrownIn(buildcraft$trackedFluid(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public float buildcraft$fallDistanceModifier() {
        for (int i = 0; i < buildcraft$trackedCount(); i++) {
            FluidProperties declared = FabricFlowingFluids.declared(buildcraft$trackedFluid(i).value());
            if (buildcraft$trackedHeight(i) > 0.0 && declared != null && !declared.waterLike()) {
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
