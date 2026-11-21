/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge && >=26.1 {
/*import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;

import buildcraft.platform.FluidProperties;
import buildcraft.platform.Platform;

/^*
 * NeoForge's "is this fluid type water-like" flag for BuildCraft's fluids on MinecraftForge, which has no such flag. Every
 * BuildCraft fluid is in {@code minecraft:water} (the oil's spread rules need that), and Forge's entity physics keeps
 * vanilla's tag-based water state beside its own per-type tracking, so without this an entity treats crude oil and residue
 * as water: it is "in water" ({@code Entity.isInWater}: swimming, bobbing, the splash, the fall-distance reset), its eyes are
 * "in water", and a mob floats in it. With it, as on NeoForge:
 * <ul>
 * <li>an entity is in water, and its eyes are in water, only in a water-tag fluid that is not a BuildCraft fluid declared
 * not water-like ({@link #isWaterLike});</li>
 * <li>a mob's {@code FloatGoal} and {@code Swim} behaviour keep it afloat in a BuildCraft fluid only if the fluid
 * {@link FluidProperties#canSwim} (Forge's own patch also ORs in {@code canSwimInFluidType}, which already answers
 * from the same flag);</li>
 * <li>in a fluid that is not water-like, an entity's fall distance is scaled by the fluid type's own modifier (Forge
 * applies only lava's: NeoForge's {@code EntityFluidInteraction#getFallDistanceModifier}, 0.5 by default).</li>
 * </ul>
 * What Forge already answers from the fluid type and needs nothing here: the current that pushes an entity (its
 * per-type accumulators, scaled by {@link ForgeFlowingFluids.Type#motionScale}), wading ({@code FluidType#move}),
 * boating and drowning. The rest of vanilla's tag queries ({@code getFluidHeight(WATER)}) stay tag based, as NeoForge's
 * deprecated tag paths are.
 *
 * <p>The answers read vanilla's per-fluid trackers of the entity's {@code EntityFluidInteraction}, through
 * {@code EntityFluidInteractionMixin}, which implements {@link Interaction}.
 *^/
public final class ForgeFluidPhysics {
    private ForgeFluidPhysics() {}

    /^* The {@link Interaction} of {@code entity}: its {@code EntityFluidInteraction}, which the mixin extends. ^/
    public static Interaction interaction(Entity entity) {
        return (Interaction) entity.getFluidInteraction();
    }

    /^* What {@code EntityFluidInteractionMixin} adds to an entity's {@code EntityFluidInteraction}. ^/
    public interface Interaction {
        /^* Whether the entity is in a water-like fluid (NeoForge: any tracked type that {@code getIsWaterLike}). ^/
        boolean buildcraft$inWaterLike();

        /^* Whether the entity's eyes are in a water-like fluid. ^/
        boolean buildcraft$eyeInWaterLike();

        /^* Whether the entity is deeper than {@code depth} in a fluid it can swim in (NeoForge's {@code FloatGoal}): a
         *  BuildCraft fluid by its own flag, any other by {@code floatable}, the tag the goal asks about. ^/
        boolean buildcraft$inSwimmableDeeperThan(double depth, TagKey<Fluid> floatable);

        /^* The fall-distance factor for this tick: the smallest modifier among the BuildCraft fluids the entity is in that
         *  are not water-like (their type's {@code getFallDistanceModifier}), else 1. ^/
        float buildcraft$fallDistanceModifier(Entity entity);
    }

    /^* The properties of a BuildCraft fluid (either form), or null for any other fluid. ^/
    @Nullable
    static FluidProperties declared(Fluid fluid) {
        return Platform.fluids().declared(fluid);
    }

    /^* Whether a tracked fluid counts as water for the entity: its declared flag for a BuildCraft fluid, else the tag. ^/
    public static boolean isWaterLike(Holder<Fluid> fluid) {
        FluidProperties declared = declared(fluid.value());
        return declared != null ? declared.waterLike() : fluid.is(FluidTags.WATER);
    }

    /^* Whether an entity can swim in a tracked fluid: its declared flag for a BuildCraft fluid, else whether it is in
     *  {@code floatable}, the tag the asking goal reads (vanilla's own test, which a datapack may have widened). ^/
    public static boolean canSwimIn(Holder<Fluid> fluid, TagKey<Fluid> floatable) {
        FluidProperties declared = declared(fluid.value());
        return declared != null ? declared.canSwim() : fluid.is(floatable);
    }

    /^* The fall-distance modifier of a tracked fluid for {@code entity}: its type's for a BuildCraft fluid that is not
     *  water-like, else 1 (no damping). ^/
    public static float fallDistanceModifier(Holder<Fluid> fluid, Entity entity) {
        FluidProperties declared = declared(fluid.value());
        return declared != null && !declared.waterLike()
                ? entity.getFluidFallDistanceModifier(fluid.value().getFluidType())
                : 1.0F;
    }
}
*///?}
