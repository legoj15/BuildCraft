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
 *  down to ZERO, so no hit ever cost the robot anything and it became immune. The guard against that is a floor
 *  of 1 micro-MJ per point of damage; ordinary armour stacks never reach it and are 7.1.x's arithmetic unchanged. */
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
    public void armourNeverCutsTheDebitBelowOneMicroMjPerPoint() {
        Assertions.assertEquals(1L, EntityRobot.damageDebitPerPoint(modded(EntityRobot.MAX_WEARABLES)),
                "eight absurdly protective helmets leave the 1 micro-MJ floor, not zero and not a percentage of the base");
        Assertions.assertEquals(1L, EntityRobot.damageDebitPerPoint(1.0e12),
                "one absurdly protective helmet is floored too");
        Assertions.assertEquals(1L, EntityRobot.damageDebitPerPoint(Double.MAX_VALUE),
                "the floor holds at the extreme of the double range");
    }

    @Test
    public void theFloorOnlyBitesWhereTheArithmeticWouldGoBelowIt() {
        // 2 / (2 + p) of 260,000,000 micro-MJ: p = 259,999,998 leaves exactly 2, p = 519,999,998 exactly 1, and a
        // protection past that rounds toward zero, which the floor lifts back to 1.
        Assertions.assertEquals(2L, EntityRobot.damageDebitPerPoint(259_999_998.0), "two is left as two");
        Assertions.assertEquals(1L, EntityRobot.damageDebitPerPoint(519_999_998.0), "one is left as one");
        Assertions.assertEquals(1L, EntityRobot.damageDebitPerPoint(2_000_000_000.0), "would round to zero, floored to one");
    }

    @Test
    public void thereIsNoPercentageCapAnyMore() {
        // Eight iron helmets halve the debit eight times: 7.1.x's value, 1/256 of the base. The old 90% cap lifted
        // anything below a tenth of the base back up to a tenth, which three iron helmets (1/8) were just clear of.
        double[] eightIron = new double[EntityRobot.MAX_WEARABLES];
        java.util.Arrays.fill(eightIron, 2.0);
        Assertions.assertEquals(BASE / 256, EntityRobot.damageDebitPerPoint(eightIron),
                "eight iron helmets (99.6% off) keep 7.1.x's compounded value");
        Assertions.assertTrue(EntityRobot.damageDebitPerPoint(eightIron) < BASE / 10,
                "and that is below the tenth the old cap would have forced");
        Assertions.assertEquals(BASE / 8, EntityRobot.damageDebitPerPoint(2.0, 2.0, 2.0),
                "three iron helmets (87.5% off) are 7.1.x's compounded value as ever");
    }
}
