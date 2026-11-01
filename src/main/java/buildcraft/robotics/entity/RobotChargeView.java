/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import net.minecraft.util.Mth;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.IRobotAccess;

/**
 * How a robot's charge reads through Robot Goggles: the outline colour, red when the battery is empty to green when
 * it is full. Pure math (no entity, no client class) so it is testable and loads on a dedicated server; the entity
 * asks it for its team colour and the renderer asks it for the charge bar.
 *
 * <p>The ramp is 7.2.x's {@code RenderRobot} one: {@code (1 - charge, charge, 0)}, a straight blend of the red and
 * green channels with nothing in between added, so a half-full robot is a dim olive rather than a bright yellow.
 */
public final class RobotChargeView {
    /** Whole MJ at a full battery: {@code MAX_POWER} over one MJ, never a hard-coded 10 000, so a capacity re-pin
     *  keeps both the charge bar and the outline honest. */
    private static final long FULL_MJ = IRobotAccess.MAX_POWER / MjAPI.MJ;

    private RobotChargeView() {}

    /** Stored charge as a 0..1 fraction of a full battery, from the synced whole-MJ figure. */
    public static float chargeFraction(int energyMj) {
        if (FULL_MJ <= 0) {
            return 0F;
        }
        return Mth.clamp(energyMj / (float) FULL_MJ, 0F, 1F);
    }

    /** The outline colour for a charge fraction, {@code 0xRRGGBB} (no alpha, the shape {@code getTeamColor}
     *  returns): {@code 0xFF0000} at 0, {@code 0x00FF00} at 1, red falling and green rising linearly between. */
    public static int outlineRgb(float chargeFraction) {
        float charge = Mth.clamp(chargeFraction, 0F, 1F);
        int red = Math.round((1F - charge) * 255F);
        int green = Math.round(charge * 255F);
        return (red << 16) | (green << 8);
    }

    /** {@link #outlineRgb(float)} for a robot's synced whole-MJ charge. */
    public static int outlineRgbForEnergy(int energyMj) {
        return outlineRgb(chargeFraction(energyMj));
    }
}
