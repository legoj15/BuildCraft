/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import java.util.List;
//? if >=26.1 {

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
//?} else {
/^import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.world.entity.Entity;

// Forge 1.21.1's and 1.21.11's (Forge 52, 61) entity physics already asks each fluid's type (isInWater is the water
// type's, the eyes likewise, every modded type's fall-distance modifier applies), so the water-like checks this adds on
// 26.x have no role here.
// Empty, so the mixin config names the same classes on every Forge line.
@Mixin(Entity.class)
abstract class EntityFluidInteractionMixin {
}
^///?}
*///?}
