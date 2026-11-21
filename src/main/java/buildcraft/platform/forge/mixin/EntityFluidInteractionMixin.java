/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import java.util.List;
//? if >=26.3 {

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.Fluid;

import buildcraft.platform.forge.ForgeFluidPhysics;

/^*
 * NeoForge's per-type water-like test for BuildCraft's fluids, as an answer {@code EntityMixin} and {@code FloatGoalMixin}
 * can ask an entity's {@code EntityFluidInteraction} ({@link ForgeFluidPhysics}). Forge keeps vanilla's per-fluid tracker
 * list (one tracker per fluid the entity touches, with its height and whether the eyes are inside) beside its own
 * per-type trackers; the list is what carries the fluid itself, so a water-tag fluid can be told from BuildCraft's.
 *
 * <p>Stands in for NeoForge's {@code FluidType#getIsWaterLike} reads in {@code Entity.baseTick} and
 * {@code updateFluidInteraction}, and for its {@code EntityFluidInteraction#getFallDistanceModifier}.
 *
 * <p><b>Why this injection point is stable.</b> The mixin adds methods and shadows one field; it injects into no
 * method body, so Forge's rewrite of {@code update} (it loops each fluid over a tracker pair) cannot move anything.
 * {@code fluidTrackers} is vanilla's list and Forge leaves it as it was (its own trackers live in
 * {@code fluidTrackersByType}); it is typed {@code List<Object>} because the element class is private.
 *^/
@Mixin(EntityFluidInteraction.class)
abstract class EntityFluidInteractionMixin implements ForgeFluidPhysics.Interaction {
    @Shadow
    @Final
    private List<Object> fluidTrackers;

    @Override
    public boolean buildcraft$inWaterLike() {
        for (Object tracker : fluidTrackers) {
            EntityFluidTrackerAccessor t = (EntityFluidTrackerAccessor) tracker;
            if (t.buildcraft$height() > 0.0 && ForgeFluidPhysics.isWaterLike(t.buildcraft$fluid())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$eyeInWaterLike() {
        for (Object tracker : fluidTrackers) {
            EntityFluidTrackerAccessor t = (EntityFluidTrackerAccessor) tracker;
            if (t.buildcraft$eyesInside() && ForgeFluidPhysics.isWaterLike(t.buildcraft$fluid())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean buildcraft$inSwimmableDeeperThan(double depth, TagKey<Fluid> floatable) {
        for (Object tracker : fluidTrackers) {
            EntityFluidTrackerAccessor t = (EntityFluidTrackerAccessor) tracker;
            if (t.buildcraft$height() > depth && ForgeFluidPhysics.canSwimIn(t.buildcraft$fluid(), floatable)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public float buildcraft$fallDistanceModifier(Entity entity) {
        float smallest = 1.0F;
        for (Object tracker : fluidTrackers) {
            EntityFluidTrackerAccessor t = (EntityFluidTrackerAccessor) tracker;
            if (t.buildcraft$height() > 0.0) {
                Holder<Fluid> fluid = t.buildcraft$fluid();
                smallest = Math.min(smallest, ForgeFluidPhysics.fallDistanceModifier(fluid, entity));
            }
        }
        return smallest;
    }
}
//?} elif >=1.21.10 {
/^import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.Fluid;

import net.minecraftforge.fluids.FluidType;

import buildcraft.platform.FluidProperties;
import buildcraft.platform.forge.ForgeFluidPhysics;

// The 26.1 and 26.2 form (Forge 64 and 65), where vanilla's {@code EntityFluidInteraction} keeps one tracker per fluid
// TAG ({@code trackerByFluid}: water and lava) and Forge adds one per fluid type ({@code trackerByFluidType}); no tracker
// carries its fluid, so the 26.3 form cannot be read here. Instead a fluid that is not water-like is kept out of the water
// tag's tracker when the trackers for a fluid are picked: then every water read of the entity (the tag's height, its
// eyes, {@code isInFluid(WATER)}, so {@code isInWater} and the {@code FloatGoal} / {@code Swim} tests, which on these
// lines read {@code isInWater() && getFluidHeight(WATER)}) answers as NeoForge's per-type water-like check does, and
// {@code FloatGoalMixin} and {@code SwimMixin} are empty here. Forge's per-type trackers are untouched, so currents,
// wading, boating and drowning still come from each fluid's own type.
//
// <p><b>Injection point.</b> The one {@code Fluid.is(TagKey)} call in the private {@code getTrackersFor(Fluid)}, the
// method Forge rewrote to also hand back the per-type tracker (the tag loop it keeps is vanilla's). No ordinal, no
// local. The fall-distance modifier reads the per-type trackers through {@code EntityFluidTrackerAccessor}.
@Mixin(EntityFluidInteraction.class)
abstract class EntityFluidInteractionMixin implements ForgeFluidPhysics.Interaction {
    @Shadow
    @Final
    private Map<FluidType, Object> trackerByFluidType;

    @Shadow
    public abstract boolean isInFluid(TagKey<Fluid> fluid);

    @Shadow
    public abstract boolean isEyeInFluid(TagKey<Fluid> fluid);

    @Shadow
    public abstract double getFluidHeight(TagKey<Fluid> fluid);

    @WrapOperation(method = "getTrackersFor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/Fluid;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean buildcraft$joinsTagTracker(Fluid fluid, TagKey<Fluid> tag, Operation<Boolean> original) {
        return original.call(fluid, tag) && (tag != FluidTags.WATER || ForgeFluidPhysics.isWaterLike(fluid));
    }

    @Override
    public boolean buildcraft$inWaterLike() {
        return isInFluid(FluidTags.WATER);
    }

    @Override
    public boolean buildcraft$eyeInWaterLike() {
        return isEyeInFluid(FluidTags.WATER);
    }

    // Not asked on this line (FloatGoalMixin and SwimMixin are empty here); answered for the interface: a BuildCraft
    // type by its own flag, anything else by the tag's tracker, which exists for the water and lava tags only.
    @Override
    public boolean buildcraft$inSwimmableDeeperThan(double depth, TagKey<Fluid> floatable) {
        for (Map.Entry<FluidType, Object> entry : trackerByFluidType.entrySet()) {
            FluidProperties declared = ForgeFluidPhysics.declared(entry.getKey());
            if (declared != null && declared.canSwim()
                    && ((EntityFluidTrackerAccessor) entry.getValue()).buildcraft$height() > depth) {
                return true;
            }
        }
        return getFluidHeight(floatable) > depth;
    }

    @Override
    public float buildcraft$fallDistanceModifier(Entity entity) {
        float smallest = 1.0F;
        for (Map.Entry<FluidType, Object> entry : trackerByFluidType.entrySet()) {
            if (((EntityFluidTrackerAccessor) entry.getValue()).buildcraft$height() > 0.0) {
                smallest = Math.min(smallest, ForgeFluidPhysics.fallDistanceModifier(entry.getKey(), entity));
            }
        }
        return smallest;
    }
}
^///?} else {
/^import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.world.entity.Entity;

// Forge 1.21.1's entity physics already asks each fluid's type (isInWater is the water type's, the eyes likewise,
// every modded type's fall-distance modifier applies), so the water-like checks this adds on 26.x have no role here.
// Empty, so the mixin config names the same classes on both Forge lines.
@Mixin(Entity.class)
abstract class EntityFluidInteractionMixin {
}
^///?}
*///?}
