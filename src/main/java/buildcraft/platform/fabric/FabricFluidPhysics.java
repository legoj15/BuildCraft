/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

import buildcraft.platform.FluidProperties;

/^*
 * The entity physics NeoForge reads from a {@code FluidType}, for BuildCraft's fluids on Fabric. Vanilla's entity
 * physics knows only the water and lava tags, and every BuildCraft fluid is in {@code minecraft:water} (the oil's
 * spread rules need that), so without this an entity treats them all as water. With it, as on NeoForge:
 * <ul>
 * <li>an entity is "in water" ({@code Entity.isInWater}, so swimming, bobbing, water movement, the splash and the
 * fall-distance reset) and its eyes are "in water" only in a fluid whose {@link FluidProperties#waterLike} is set
 * (NeoForge's {@code getIsWaterLike} checks in {@code Entity.baseTick} and {@code updateFluidInteraction});</li>
 * <li>a mob's {@code FloatGoal} keeps it afloat only in a fluid it can swim in ({@link FluidProperties#canSwim}),
 * deeper than its jump threshold;</li>
 * <li>it drowns with its eyes in a fluid whose {@link FluidProperties#canDrown} is set ({@code onLivingBreathe});</li>
 * <li>a current pushes it only where {@link FluidProperties#canPushEntity} is set, scaled by that fluid's own
 * {@link FluidProperties#motionScale} rather than water's 0.014 (NeoForge's per-type current accumulators);</li>
 * <li>in a fluid that is not water-like, its fall distance is halved every tick (NeoForge's default
 * {@code fallDistanceModifier});</li>
 * <li>a living entity in a fluid that is not water-like (crude oil, residue) wades through it by
 * {@link buildcraft.platform.ThickFluidMotion}, the movement step the NeoForge fluid type's {@code FluidType.move}
 * takes, and a jumping one off the ground (or on the ground deeper than its jump threshold) rises by NeoForge's
 * {@code jumpInFluid} lift of {@link #FLUID_JUMP_SPEED} a tick ({@code LivingEntityMixin}).</li>
 * </ul>
 * The rest of vanilla's tag queries ({@code getFluidHeight(WATER)}, {@code isEyeInFluid(WATER)} elsewhere) stay tag
 * based, as NeoForge's deprecated tag paths are. Extinguishing is the fluid's own {@code entityInside}.
 *^/
public final class FabricFluidPhysics {
    /^* NeoForge's default {@code FluidType.Properties.fallDistanceModifier}, which no BuildCraft fluid changes. *^/
    public static final float FALL_DISTANCE_MODIFIER = 0.5F;
    /^* NeoForge's {@code jumpInFluid} lift per tick for a fluid that is not water (no swim-speed attribute). *^/
    public static final double FLUID_JUMP_SPEED = 0.04F;

    private FabricFluidPhysics() {}

    /^* What {@code EntityFluidInteractionMixin} adds to an entity's {@code EntityFluidInteraction}. *^/
    public interface Interaction {
        /^* Whether the entity is in a water-like fluid (NeoForge: any tracked type that {@code getIsWaterLike}). *^/
        boolean buildcraft$inWaterLike();

        /^* Whether the entity's eyes are in a water-like fluid. *^/
        boolean buildcraft$eyeInWaterLike();

        /^* Whether the entity is deeper than {@code depth} in a fluid it can swim in (NeoForge's {@code FloatGoal}). *^/
        boolean buildcraft$inSwimmableDeeperThan(double depth);

        /^* Whether the entity is in {@code fluid}'s kind (either form), NeoForge's {@code isInFluidType}. *^/
        boolean buildcraft$inKindOf(FluidProperties fluid);

        /^* Whether the entity's eyes are in a fluid it can drown in. *^/
        boolean buildcraft$eyeInDrowningFluid();

        /^* The fall-distance factor for this tick: {@link #FALL_DISTANCE_MODIFIER} in a BuildCraft fluid that is not
         *  water-like, else 1. *^/
        float buildcraft$fallDistanceModifier();

        /^* Pushes the entity along the currents of the BuildCraft fluids it touched this tick, each by its own
         *  motion scale. *^/
        void buildcraft$applyCurrents(Entity entity);
    }

    /^* Whether a tracked fluid counts as water for the entity: its declared flag for a BuildCraft fluid, else the tag. *^/
    public static boolean isWaterLike(Holder<Fluid> fluid) {
        FluidProperties declared = FabricFlowingFluids.declared(fluid.value());
        return declared != null ? declared.waterLike() : fluid.is(FluidTags.WATER);
    }

    /^* Whether an entity can swim in a tracked fluid: its declared flag for a BuildCraft fluid, else the water tag. *^/
    public static boolean canSwimIn(Holder<Fluid> fluid) {
        FluidProperties declared = FabricFlowingFluids.declared(fluid.value());
        return declared != null ? declared.canSwim() : fluid.is(FluidTags.WATER);
    }

    /^* Whether an entity can drown with its eyes in a tracked fluid: its declared flag, else the water tag. *^/
    public static boolean canDrownIn(Holder<Fluid> fluid) {
        FluidProperties declared = FabricFlowingFluids.declared(fluid.value());
        return declared != null ? declared.canDrown() : fluid.is(FluidTags.WATER);
    }

    /^*
     * One BuildCraft fluid's current this tick, the same sum vanilla's {@code EntityFluidInteraction.CurrentAccumulator}
     * keeps for water: the flow of every touched block (scaled down while the fluid is under 0.4 deep), applied as
     * a push of the fluid's motion scale.
     *^/
    public static final class Current {
        private double height;
        private Vec3 accumulated = Vec3.ZERO;
        private int count;

        public void reset() {
            height = 0.0;
            accumulated = Vec3.ZERO;
            count = 0;
        }

        /^* Adds one block's flow, with the fluid's surface {@code depth} above the entity's feet. *^/
        public void accumulate(double depth, Vec3 flow) {
            height = Math.max(height, depth);
            accumulated = accumulated.add(height < 0.4 ? flow.scale(height) : flow);
            count++;
        }

        public void applyTo(Entity entity, double scale) {
            if (count == 0 || accumulated.lengthSqr() < 1.0E-5F) {
                return;
            }
            Vec3 impulse = entity instanceof Player ? accumulated.scale(1.0 / count) : accumulated.normalize();
            Vec3 movement = entity.getDeltaMovement();
            impulse = impulse.scale(scale);
            if (Math.abs(movement.x) < 0.003 && Math.abs(movement.z) < 0.003 && impulse.length() < 0.0045000000000000005) {
                impulse = impulse.normalize().scale(0.0045000000000000005);
            }
            entity.addDeltaMovement(impulse);
        }
    }
}
*///?}
