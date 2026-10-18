/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.robots.IRobotAccess;

/** The per-point battery debit a robot pays for damage, as a function of what it wears
 *  ({@link EntityRobot#damageDebitPerPoint}). The piece-by-piece 7.1.x arithmetic is pinned end to end by the
 *  {@code robot_armour_scales_damage_debit} game test; this is the part that cannot be reached by wearing
 *  vanilla armour: a stack of high-protection modded helmets, whose compounded scaling used to round the debit
 *  down to ZERO, so no hit ever cost the robot anything and it became immune. */
public class RobotDamageDebitTest {

    private static final long BASE = IRobotAccess.DAMAGE_ENERGY_PER_POINT;

    /** Protection 1000: each piece scales the debit by 2 / 1002 (about 0.2%). */
    private static double[] modded(int pieces) {
        double[] protections = new double[pieces];
        java.util.Arrays.fill(protections, 1000.0);
        return protections;
    }

    @Test
    public void aBareRobotPaysTheFullDebit() {
        Assertions.assertEquals(BASE, EntityRobot.damageDebitPerPoint());
    }

    @Test
    public void armourAndFlatWearablesScaleTheDebitAsIn71x() {
        Assertions.assertEquals(BASE * 2 / 4, EntityRobot.damageDebitPerPoint(2.0), "iron helmet halves it");
        Assertions.assertEquals(BASE * 2 / 5, EntityRobot.damageDebitPerPoint(3.0), "diamond helmet: two fifths");
        Assertions.assertEquals(BASE * 7 / 10, EntityRobot.damageDebitPerPoint(-1.0), "a skull takes a flat 30% off");
        Assertions.assertEquals(BASE * 2 / 4 * 2 / 5, EntityRobot.damageDebitPerPoint(2.0, 3.0),
                "pieces compound in the order worn");
    }

    @Test
    public void eightHighProtectionHelmetsDoNotMakeTheRobotImmune() {
        long perPoint = EntityRobot.damageDebitPerPoint(modded(EntityRobot.MAX_WEARABLES));
        Assertions.assertTrue(perPoint > 0,
                "a hit must always cost something: eight modded helmets drove the per-point debit to " + perPoint);
    }

    @Test
    public void armourReducesTheDebitByAtMostNinetyPercent() {
        long floor = BASE / 10;
        Assertions.assertEquals(floor, EntityRobot.damageDebitPerPoint(modded(EntityRobot.MAX_WEARABLES)),
                "the reduction from any stack of armour is capped at 90%");
        Assertions.assertEquals(floor, EntityRobot.damageDebitPerPoint(modded(1)),
                "one absurdly protective helmet is capped too");
    }

    @Test
    public void theCapDoesNotTouchReductionsBelowIt() {
        Assertions.assertEquals(BASE / 8, EntityRobot.damageDebitPerPoint(2.0, 2.0, 2.0),
                "three iron helmets (87.5% off) are below the cap and keep 7.1.x's compounded value");
    }
}
