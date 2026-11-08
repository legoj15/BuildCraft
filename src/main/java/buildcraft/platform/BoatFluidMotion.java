/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * How a boat moves on a BuildCraft fluid it floats on: the extra drag that makes a sticky fluid slower to sail than
 * water, shared by both loaders (NeoForge calls {@link #tick} from an entity-tick listener, Fabric from a mixin at
 * the end of the boat's {@code tick}), as {@link ThickFluidMotion} is for a wading entity.
 *
 * <p>Whether a boat floats at all is the loader's own question, answered from {@link FluidProperties#supportsBoating}
 * (NeoForge's {@code canBoatInFluid} through the fluid type, Fabric's boat mixin through the declared properties). This
 * class only slows a boat that does: one on a fluid whose {@link FluidProperties#boatSpeed} is below 1, the factor
 * on the boat's top speed (water 1). The fluid is the one at the boat's own block: a floating boat's origin sits
 * below the surface, which is at most 8/9 of a block up, so a boat whose block holds no fluid is in the air (as
 * vanilla's own {@code checkInWater} judges it). A fluid that is not a BuildCraft fluid, or one boats cannot float on,
 * leaves the boat alone.
 *
 * <p><b>The maths.</b> A boat in water multiplies its horizontal speed by 0.9 each tick (vanilla's
 * {@code floatBoat}), then a paddling rider adds an acceleration {@code a}, then the boat moves by the result. Its
 * terminal speed is therefore {@code a / (1 - 0.9)}. This class multiplies the horizontal speed by a further factor
 * {@code k} at the end of the tick, after the boat has moved, so what the next tick starts from is {@code k} times
 * smaller and the terminal speed becomes {@code a / (1 - 0.9 k)}. Making that {@code s} times the water speed means
 * {@code 1 - 0.9 k = (1 - 0.9) / s}, so {@code k = (1 - (1 - 0.9) / s) / 0.9} ({@link #dragKeep}): 0.889 for a
 * boat speed of one half. A boat coasting with no rider covers the same ratio of distance, since it too is a geometric
 * series in {@code 0.9 k} against {@code 0.9}. A speed of 0.1 or less would need {@code k} at or below zero, so
 * {@code k} stops at zero: the slowest this can make a boat, whose speed is then just the paddle's acceleration.
 *
 * <p>The boat's turning ({@code deltaRotation}, private to vanilla's boat) is left alone: the drag is on its
 * speed, not its handling. The maths assumes the boat is afloat: a boat pushed fully under the surface has vanilla's
 * submerged drag of 0.45 instead of 0.9, so while it stays under, the same {@code k} slows it by only about a tenth.
 *
 * <p>It runs on both logical sides every tick of the boat, because a boat a player rides is simulated by that
 * player's client, and the side that does not simulate it holds it at rest, where multiplying changes nothing. A
 * client therefore reads {@link FluidProperties#boatSpeed}, as it does the current's
 * {@link FluidProperties#motionScale}, from the connected server's settings: the energy fluids' options are COMMON
 * config, so the server sends them on join and on reload rather than the client trusting its own file.
 */
public final class BoatFluidMotion {
    /** The horizontal drag vanilla puts on a boat floating in water each tick ({@code floatBoat}'s inverse friction). */
    static final double IN_WATER_DRAG = 0.9D;

    private BoatFluidMotion() {}

    /** The factor to multiply a boat's horizontal speed by at the end of a tick so its top speed is {@code boatSpeed}
     *  times water's: 1 for water's speed or faster, 0 for one too slow to reach. */
    public static double dragKeep(double boatSpeed) {
        if (boatSpeed >= 1.0D) {
            return 1.0D;
        }
        if (boatSpeed <= 0.0D) {
            return 0.0D;
        }
        double keep = (1.0D - (1.0D - IN_WATER_DRAG) / boatSpeed) / IN_WATER_DRAG;
        return Math.max(0.0D, Math.min(1.0D, keep));
    }

    /** The BuildCraft fluid {@code boat} floats in, or null if it is in none that boats float on. */
    @Nullable
    public static FluidProperties floatingIn(Entity boat) {
        BlockPos pos = boat.blockPosition();
        FluidState state = boat.level().getFluidState(pos);
        FluidProperties fluid = state.isEmpty() ? null : Platform.fluids().declared(state.getType());
        return fluid != null && fluid.supportsBoating() ? fluid : null;
    }

    /** Slows {@code boat} for the BuildCraft fluid it floats in, if that fluid's boat speed is below water's. Call it
     *  once at the end of each tick of the boat, on either side. */
    public static void tick(Entity boat) {
        FluidProperties fluid = floatingIn(boat);
        if (fluid == null) {
            return;
        }
        double keep = dragKeep(fluid.boatSpeed().getAsDouble());
        if (keep >= 1.0D) {
            return;
        }
        Vec3 motion = boat.getDeltaMovement();
        boat.setDeltaMovement(motion.x * keep, motion.y, motion.z * keep);
    }
}
