/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * How a living entity moves through a BuildCraft fluid that is not water-like (crude oil, residue): a slow wade, the
 * movement step NeoForge's {@code FluidType.move} hands to the fluid (on 26.1.2 and later its {@code travelInFluid}
 * moves such a fluid neither as water nor as lava, so without this an entity in crude oil cannot move at all), and
 * the same step on Fabric ({@code LivingEntityMixin}), so both loaders agree.
 *
 * <p>It is vanilla's water and lava step with the fluid's own numbers. The swim acceleration is water's and lava's
 * 0.02, scaled by the fluid's {@link FluidProperties#motionScale} against water's 0.014 (the sticky-oil option halves
 * that scale, so it slows a wading entity as well as the current, as 1.12.2's sticky oil did with its web slowdown).
 * The horizontal drag runs from water's 0.8 at water's viscosity to lava's 0.5 at lava's and beyond. An entity sinks
 * by vanilla water's pull ({@code gravity / 16}) times water's density over the fluid's, so a denser fluid lets it
 * sink more slowly, never slower than vanilla's creep of 0.003 blocks a tick (smaller vertical speeds are zeroed every
 * tick). It floats only where its {@code FloatGoal} or a jump lifts it, which follow {@link FluidProperties#canSwim}.
 */
public final class ThickFluidMotion {
    /** Water's current scalar ({@code FluidType}'s default {@code motionScale}). */
    public static final double WATER_MOTION_SCALE = 0.014D;
    /** Vanilla's swim acceleration in water and lava. */
    static final float SWIM_SPEED = 0.02F;
    /** Water's and lava's horizontal drag, and the viscosities they belong to. */
    static final double WATER_DRAG = 0.8D;
    static final double LAVA_DRAG = 0.5D;
    static final int WATER_VISCOSITY = 1000;
    static final int LAVA_VISCOSITY = 6000;
    /** Vanilla's vertical drag in water and shallow lava. */
    static final double VERTICAL_DRAG = 0.8D;
    /** The smallest vertical speed vanilla keeps across a tick ({@code LivingEntity.aiStep} zeroes anything less). */
    static final double MIN_SINK = 0.003D;

    private ThickFluidMotion() {}

    /** Whether {@link #travel} moves an entity in this fluid, rather than the loader's water step. */
    public static boolean isThick(FluidProperties fluid) {
        return !fluid.waterLike();
    }

    /** The per-tick horizontal drag in a fluid of this viscosity. */
    public static double horizontalDrag(int viscosity) {
        double thickness = (viscosity - WATER_VISCOSITY) / (double) (LAVA_VISCOSITY - WATER_VISCOSITY);
        thickness = Math.max(0.0D, Math.min(1.0D, thickness));
        return WATER_DRAG + (LAVA_DRAG - WATER_DRAG) * thickness;
    }

    /** The swim acceleration in a fluid of this current scalar: water's and lava's, scaled by it against water's. */
    public static float swimSpeed(double motionScale) {
        double scale = Math.max(0.0D, Math.min(1.0D, motionScale / WATER_MOTION_SCALE));
        return (float) (SWIM_SPEED * scale);
    }

    /** The extra downward pull per tick in a fluid of this density: vanilla water's at water's density, less in a
     *  denser one, lava's ({@code gravity / 4}) at most, which a fluid of a quarter of water's density or lighter
     *  (and one with no density, or lighter than air) reaches. */
    public static double sinkPull(int density, double gravity) {
        double waterPull = gravity / 16.0D;
        if (density <= 0) {
            return gravity / 4.0D;
        }
        return Math.min(gravity / 4.0D, waterPull * 1000.0D / density);
    }

    /**
     * Moves {@code entity} one tick through {@code fluid} by the input {@code input} (strafe, up, forward) under
     * {@code gravity} (the entity's effective gravity this tick).
     */
    public static void travel(LivingEntity entity, FluidProperties fluid, Vec3 input, double gravity) {
        boolean falling = entity.getDeltaMovement().y <= 0.0D;
        double oldY = entity.getY();
        entity.moveRelative(swimSpeed(fluid.motionScale().getAsDouble()), input);
        entity.move(MoverType.SELF, entity.getDeltaMovement());

        Vec3 movement = entity.getDeltaMovement();
        if (entity.horizontalCollision && entity.onClimbable()) {
            movement = new Vec3(movement.x, 0.2D, movement.z);
        }
        double drag = horizontalDrag(fluid.viscosity());
        double y = movement.y * VERTICAL_DRAG;
        if (gravity != 0.0D && !entity.isSprinting()) {
            y -= sinkPull(fluid.density(), gravity);
            if (falling && y < 0.0D && y > -MIN_SINK) {
                y = -MIN_SINK;
            }
        }
        movement = new Vec3(movement.x * drag, y, movement.z * drag);
        // Vanilla's jumpOutOfFluid: a wall with room above lets the entity climb out over the edge.
        if (entity.horizontalCollision
                && entity.isFree(movement.x, movement.y + 0.6F - entity.getY() + oldY, movement.z)) {
            movement = new Vec3(movement.x, 0.3F, movement.z);
        }
        entity.setDeltaMovement(movement);
    }
}
