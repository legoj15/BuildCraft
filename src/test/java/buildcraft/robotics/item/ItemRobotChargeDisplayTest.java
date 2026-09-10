/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.robots.EntityRobotBase;

/**
 * Pins the pure computations behind the robot's charge readouts — the icon's eye-decal tint
 * ({@code chargeEyeTintArgb}, consumed by {@code RobotChargeTintSource}), the inventory bar's
 * colour and width ({@code chargeTintArgb} / {@code chargeBarWidth}) — so the at-a-glance display
 * cannot silently drift. The eye must stay 7.1.x-faithful: always red, its intensity the charge
 * fraction (upstream faded the decal's alpha; here the red is multiplied toward black, because
 * item quads bake onto an alpha-cutout sheet). The bar and tooltip keep upstream's readout ramp
 * (green text at high charge — which upstream had, and the eyes never did). The JSON side of the
 * display is pinned by {@code RoboticsItemIconCoverageTester}; only the math is pinned here, which
 * is why this file needs no stacks, no registry and no FML boot.
 */
public class ItemRobotChargeDisplayTest {

    private static final long MAX = EntityRobotBase.MAX_POWER;

    // ── Bar width ───────────────────────────────────────────────────────────

    @Test
    public void barWidthSpansTheVanillaZeroToThirteenRange() {
        Assertions.assertEquals(0, ItemRobot.chargeBarWidth(0L), "no charge draws no fill");
        Assertions.assertEquals(13, ItemRobot.chargeBarWidth(MAX), "a full charge draws the whole bar");
        Assertions.assertEquals(6, ItemRobot.chargeBarWidth(MAX / 2), "half charge floors to 6 of 13 px");
        Assertions.assertEquals(12, ItemRobot.chargeBarWidth(MAX - 1), "just-under-full must stay under 13");
    }

    @Test
    public void barWidthClampsCorruptCharges() {
        Assertions.assertEquals(0, ItemRobot.chargeBarWidth(-5L), "negative blobs draw no fill, not garbage");
        Assertions.assertEquals(13, ItemRobot.chargeBarWidth(MAX * 2), "overcharged blobs clamp to full");
    }

    // ── The tint ramp (inventory bar; the tooltip text uses the same thresholds) ──

    /** The five ramp colours, as ChatFormatting renders them — kept literal so a ChatFormatting
     *  reorder or a "harmless" palette tweak shows up here instead of retinting every robot icon. */
    private static final int DARK_RED = 0xFFAA0000;
    private static final int RED = 0xFFFF5555;
    private static final int GOLD = 0xFFFFAA00;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int GREEN = 0xFF55FF55;

    @Test
    public void barColourFollowsTheSevenOneXReadoutRamp() {
        Assertions.assertEquals(DARK_RED, ItemRobot.chargeTintArgb(0L), "0% is the dark red 'no charge' colour");
        Assertions.assertEquals(DARK_RED, ItemRobot.chargeTintArgb(MAX * 19 / 100), "19% is still dark red");
        Assertions.assertEquals(RED, ItemRobot.chargeTintArgb(MAX * 20 / 100), "20% crosses to red");
        Assertions.assertEquals(GOLD, ItemRobot.chargeTintArgb(MAX * 30 / 100), "30% crosses to gold");
        Assertions.assertEquals(YELLOW, ItemRobot.chargeTintArgb(MAX * 50 / 100), "50% crosses to yellow");
        Assertions.assertEquals(GREEN, ItemRobot.chargeTintArgb(MAX * 80 / 100), "80% crosses to green");
        Assertions.assertEquals(GREEN, ItemRobot.chargeTintArgb(MAX), "full is green");
    }

    @Test
    public void barColourClampsCorruptChargesAndStaysOpaque() {
        Assertions.assertEquals(GREEN, ItemRobot.chargeTintArgb(MAX * 2), "overcharged blobs clamp to full green");
        Assertions.assertEquals(DARK_RED, ItemRobot.chargeTintArgb(-1L), "negative blobs clamp to empty dark red");
        for (long energy : new long[] {0L, MAX / 3, MAX / 2, MAX - 1, MAX, MAX * 2}) {
            Assertions.assertEquals(0xFF000000, ItemRobot.chargeTintArgb(energy) & 0xFF000000,
                    "the tint must be fully opaque at every charge or the bar renders translucent");
        }
    }

    // ── The eye decal tint (always red, intensity = charge — 7.1.x's fading decal) ──

    @Test
    public void eyeTintIsRedAtTheChargeFraction() {
        Assertions.assertEquals(0xFF000000, ItemRobot.chargeEyeTintArgb(0L),
                "0% multiplies the red decal to black — upstream's alpha-0 read, minus the alpha");
        Assertions.assertEquals(0xFF7F7F7F, ItemRobot.chargeEyeTintArgb(MAX / 2),
                "half charge is a half-strength neutral multiplier");
        Assertions.assertEquals(0xFFFFFFFF, ItemRobot.chargeEyeTintArgb(MAX),
                "full charge is white: the decal art's own pure red, untouched");
        Assertions.assertEquals(63, ItemRobot.chargeEyeTintArgb(MAX / 4) & 0xFF,
                "a quarter charge is a quarter multiplier (255/4 floors to 63)");
    }

    @Test
    public void eyeTintIsNeutralGrayscaleAndOpaque() {
        for (long energy : new long[] {0L, MAX / 5, MAX / 4, MAX / 2, MAX - 1, MAX, MAX * 2}) {
            int tint = ItemRobot.chargeEyeTintArgb(energy);
            int r = tint >> 16 & 0xFF;
            int g = tint >> 8 & 0xFF;
            int b = tint & 0xFF;
            Assertions.assertEquals(r, g, "the eye tint must be neutral (r=g) or it recolors the red decal");
            Assertions.assertEquals(g, b, "the eye tint must be neutral (g=b) or it recolors the red decal");
            Assertions.assertEquals(0xFF, tint >>> 24, "the eye tint must stay opaque — the cutout sheet "
                    + "discards semi-transparent fragments outright");
        }
    }

    @Test
    public void eyeTintClampsCorruptCharges() {
        Assertions.assertEquals(0xFF000000, ItemRobot.chargeEyeTintArgb(-1L), "negative blobs clamp to dark");
        Assertions.assertEquals(0xFFFFFFFF, ItemRobot.chargeEyeTintArgb(MAX * 2), "overcharged blobs clamp to full");
    }
}
