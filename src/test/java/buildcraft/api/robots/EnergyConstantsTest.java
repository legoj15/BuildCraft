/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.robots;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.mj.MjAPI;

/**
 * Pins the robot energy scale (robotics Ph3, Decision 3).
 *
 * <p>Two separate jobs live in this file.
 *
 * <p><b>1. The long-vs-int tripwire.</b> {@link IRobotAccess#MAX_POWER} is deliberately larger than
 * {@code Integer.MAX_VALUE}. Every robot energy value is micro-MJ in a {@code long}; the moment someone
 * narrows one to an {@code int} — a cast, an {@code int} field, an {@code int} accessor — a full battery
 * wraps negative and the robot reads as broken rather than charged. The assertion below is the cheapest
 * possible guard on that, and it only keeps its point while the capacity stays above the int ceiling.
 *
 * <p><b>2. The re-pin pins.</b> The API as first ported was internally inconsistent: capacity implied
 * 1 RF = 0.05 MJ while {@link AIRobot#getPowerCost()} implied 1 RF = 0.1 MJ, so a modern robot would have
 * run half as long as its 7.1.x self on the same nominal charge. Ph3 re-pinned capacity onto BuildCraft's
 * canonical 1 MJ = 10 RF bridge. These are {@code public static final long} compile-time constants that
 * inline into any addon jar, so they must not drift again silently — each assertion below states the
 * 7.1.x quantity it was converted from, and the derived ones (safety margin, damage debit, run length)
 * fail as a set if anyone re-pins capacity without carrying the rest along.
 *
 * <p>Note the damage debit is asserted BOTH against its constant and as a ratio of capacity. The ratio is
 * the part worth freezing: 7.1.x spent 2 600 RF of a 100 000 RF battery per point of damage, i.e. 2.6% of
 * a full charge, and that is what decides how many hits a robot walks away from.
 *
 * <p><b>Why these read {@code IRobotAccess} and not {@code EntityRobotBase}.</b> The constants deliberately
 * live on the interface. When this file was written the {@code test} task booted no FML loader, so
 * dereferencing a constant on the entity class-loaded {@code EntityRobotBase} -> {@code Entity} ->
 * NeoForge's {@code AttachmentHolder} and died with a {@code NoClassDefFoundError} that had nothing to do
 * with energy. The FML-JUnit environment (see {@link buildcraft.FmlJunitEnvironmentTest}) has since made
 * that class-load legal, but the constants stay on the interface: an addon should not need an Entity
 * class-load to read a number, and {@code EntityRobotBase} inherits all of them, so nothing about the
 * entity's own surface changed.
 */
public class EnergyConstantsTest {

    /** The expected debit, spelled out independently of the constant it is checked against, so a change to
     *  {@link IRobotAccess#DAMAGE_ENERGY_PER_POINT} has to be a deliberate edit in two places. */
    private static final long DAMAGE_DEBIT_PER_POINT = 260 * MjAPI.MJ;

    // ── 1. The tripwire ─────────────────────────────────────────────────────

    @Test
    public void maxPowerStaysAboveTheIntCeiling() {
        Assertions.assertTrue(IRobotAccess.MAX_POWER > Integer.MAX_VALUE,
                "robot capacity must not fit in an int — that is the whole point of this test: any int "
                        + "narrowing anywhere on the energy path wraps a full battery negative");
    }

    @Test
    public void safetyPowerIsCarriedOnTheSameLongPathAsCapacity() {
        // This test used to assert SAFETY_POWER > Integer.MAX_VALUE, which cannot ever hold alongside the two
        // pins below it: a fifth of 10 000 MJ is 2.0e9 micro-MJ and the int ceiling is 2.147e9, so the safety
        // margin misses it by about 7%. (Nothing caught the contradiction while it was written, because the
        // whole file was failing on a class-loading error before it reached any arithmetic.) The PROPERTY it
        // was reaching for is real and is kept here: the safety margin is only ever compared against stored
        // power, whose ceiling is MAX_POWER, so that comparison happens in long arithmetic or not at all.
        Assertions.assertTrue(IRobotAccess.MAX_POWER > Integer.MAX_VALUE,
                "the value SAFETY_POWER gets compared against is a stored charge, which tops out at capacity");
        Assertions.assertTrue(IRobotAccess.SAFETY_POWER * 2 > Integer.MAX_VALUE,
                "and the margin itself must be a long: narrow it to an int and this doubling wraps negative, "
                        + "which is precisely the failure mode the tripwire exists to catch");
    }

    // ── 2. The re-pin pins ──────────────────────────────────────────────────

    @Test
    public void maxPowerIsTenThousandMj() {
        Assertions.assertEquals(10_000L * MjAPI.MJ, IRobotAccess.MAX_POWER,
                "7.1.x MAX_ENERGY was 100 000 RF; BuildCraft's canonical bridge is 1 MJ = 10 RF");
    }

    @Test
    public void maxPowerIsAWholeNumberOfMjThatFitsTheSynchedAccessor() {
        // Decision 4 syncs charge as a whole-MJ VAR_INT (0..10000) rather than a micro-joule long, so the
        // accessor goes dirty at most once per MJ instead of every tick. That only works while capacity is
        // an exact multiple of one MJ and the quotient fits an int.
        Assertions.assertEquals(0, IRobotAccess.MAX_POWER % MjAPI.MJ,
                "capacity must be a whole number of MJ or the synched whole-MJ charge truncates unevenly");
        Assertions.assertEquals(10_000L, IRobotAccess.MAX_POWER / MjAPI.MJ,
                "the renderer's charge fraction is ENERGY_MJ / 10000");
        Assertions.assertTrue(IRobotAccess.MAX_POWER / MjAPI.MJ <= Integer.MAX_VALUE,
                "the whole-MJ charge is synced as an int");
    }

    @Test
    public void safetyPowerIsOneFifthOfCapacity() {
        Assertions.assertEquals(IRobotAccess.MAX_POWER / 5, IRobotAccess.SAFETY_POWER,
                "7.1.x SAFETY_POWER was 20 000 RF of a 100 000 RF battery");
        Assertions.assertEquals(2_000L * MjAPI.MJ, IRobotAccess.SAFETY_POWER,
                "and that is 2 000 MJ at the re-pinned capacity");
    }

    @Test
    public void shutdownPowerIsZero() {
        Assertions.assertEquals(0L, IRobotAccess.SHUTDOWN_POWER,
                "7.1.x SHUTDOWN_POWER was 0 RF — a robot shuts down when it is actually flat");
    }

    @Test
    public void thresholdsAreOrdered() {
        Assertions.assertTrue(IRobotAccess.SHUTDOWN_POWER < IRobotAccess.SAFETY_POWER,
                "a robot must reach its safety margin before it reaches shutdown");
        Assertions.assertTrue(IRobotAccess.SAFETY_POWER < IRobotAccess.MAX_POWER,
                "the safety margin is a fraction of capacity, not capacity itself");
    }

    @Test
    public void nullRobotIdIsLongMaxValue() {
        Assertions.assertEquals(Long.MAX_VALUE, IRobotAccess.NULL_ROBOT_ID,
                "the registry's 'no robot' sentinel — ids are handed out ascending from Long.MIN_VALUE, so "
                        + "the top of the range is the one value that can never be a real id");
    }

    // ── 3. Internal consistency: capacity vs the AI's per-cycle cost ────────

    @Test
    public void aFullBatteryBuysExactlyOneHundredThousandAiCycles() {
        // This is the inconsistency the re-pin fixed. AIRobot's default cost is MJ/10 — i.e. 1 RF on the
        // canonical bridge — so a full 7.1.x robot bought 100 000 default cycles. Under the old
        // 5000-MJ capacity it bought half that. If either constant moves alone, this fails.
        long defaultCycleCost = new AIRobot(null).getPowerCost();
        Assertions.assertEquals(MjAPI.MJ / 10, defaultCycleCost,
                "AIRobot's default power cost is one tenth of an MJ — 1 RF at 10 RF/MJ");
        Assertions.assertEquals(100_000L, IRobotAccess.MAX_POWER / defaultCycleCost,
                "a full robot must buy the same 100 000 default AI cycles it bought in 7.1.x");
    }

    // ── 4. Damage debit ─────────────────────────────────────────────────────

    @Test
    public void damageDebitConstantMatchesTheDamagePath() {
        Assertions.assertEquals(DAMAGE_DEBIT_PER_POINT, IRobotAccess.DAMAGE_ENERGY_PER_POINT,
                "the damage path debits IRobotAccess.DAMAGE_ENERGY_PER_POINT per point — it is the one "
                        + "constant a robot's survivability is written in");
    }

    @Test
    public void damageDebitIsTwoPointSixPercentOfAFullCharge() {
        Assertions.assertEquals(IRobotAccess.MAX_POWER / 1000 * 26, DAMAGE_DEBIT_PER_POINT,
                "7.1.x spent 2 600 RF of a 100 000 RF battery per damage point — keep the ratio, not the "
                        + "absolute number, so a future capacity re-pin carries the debit with it");
        Assertions.assertEquals(260L * MjAPI.MJ, DAMAGE_DEBIT_PER_POINT,
                "and at the current capacity that ratio is 260 MJ");
    }

    @Test
    public void aFullyChargedRobotSurvivesExactlyThirtyEightDamagePoints() {
        // The survival rule is deliberately STRICT: a robot lives while `stored - debit > 0`, so landing on
        // exactly zero destroys it. 7.1.x behaved this way and the drop/convert-to-items path depends on a
        // dead robot actually being dead rather than sitting at a flat battery. Counting hits is the honest
        // way to pin both the rule and the ratio at once.
        long stored = IRobotAccess.MAX_POWER;
        int survived = 0;
        while (stored - DAMAGE_DEBIT_PER_POINT > 0) {
            stored -= DAMAGE_DEBIT_PER_POINT;
            survived++;
        }
        Assertions.assertEquals(38, survived,
                "a full robot walks away from 38 points of damage and dies on the 39th");
        Assertions.assertTrue(stored > 0,
                "and it is still holding charge when it dies — death is the strict > 0 rule, not an empty battery");
    }

    @Test
    public void aRobotOnExactlyTheDebitAmountDies() {
        long stored = DAMAGE_DEBIT_PER_POINT;
        Assertions.assertFalse(stored - DAMAGE_DEBIT_PER_POINT > 0,
                "landing on exactly zero destroys the robot — the rule is strictly greater than zero, and "
                        + "relaxing it to >= silently makes robots immortal at zero charge");
    }
}
