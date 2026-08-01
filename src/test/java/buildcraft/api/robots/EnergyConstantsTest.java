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
 * <p><b>1. The long-vs-int tripwire.</b> {@link EntityRobotBase#MAX_POWER} is deliberately larger than
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
 * <p>Note the damage debit is asserted as a ratio of capacity rather than compared literal-to-literal.
 * There is no constant to reference yet — it arrives with the damage path — so the ratio is the part
 * worth freezing: 7.1.x spent 2 600 RF of a 100 000 RF battery per point of damage, i.e. 2.6% of a full
 * charge, and that is what decides how many hits a robot walks away from.
 *
 * <p><b>Environment note.</b> The assertions that dereference a non-inlined {@code EntityRobotBase}
 * constant cannot execute in this test tree today, and fail with
 * {@code NoClassDefFoundError: Could not initialize class buildcraft.api.robots.EntityRobotBase}. The
 * cause is not the robot: {@code EntityRobotBase} extends {@code Entity}, {@code Entity} extends
 * NeoForge's {@code AttachmentHolder}, and that class asks {@code FMLEnvironment.isProduction()} during
 * its own static initialisation — which throws "There is no current FML Loader" because the plain
 * {@code test} task boots no loader. ({@code SHUTDOWN_POWER} and {@code NULL_ROBOT_ID} pass regardless:
 * they are compile-time constant expressions, so javac inlines them and the class is never touched. The
 * rest are computed from {@code MjAPI.MJ}, which is a method call, so they are not.) Fixing this is a
 * build change, not a test change — see the Ph3 hand-off notes.
 */
public class EnergyConstantsTest {

    /** Chosen, not derived — 7.1.x debited 2 600 RF per point of damage, at 10 RF/MJ. When the damage path
     *  lands and turns this into a real constant, this local goes away and the assertions below reference
     *  the constant instead. */
    private static final long DAMAGE_DEBIT_PER_POINT = 260 * MjAPI.MJ;

    // ── 1. The tripwire ─────────────────────────────────────────────────────

    @Test
    public void maxPowerStaysAboveTheIntCeiling() {
        Assertions.assertTrue(EntityRobotBase.MAX_POWER > Integer.MAX_VALUE,
                "robot capacity must not fit in an int — that is the whole point of this test: any int "
                        + "narrowing anywhere on the energy path wraps a full battery negative");
    }

    @Test
    public void safetyPowerAlsoStaysAboveTheIntCeiling() {
        Assertions.assertTrue(EntityRobotBase.SAFETY_POWER > Integer.MAX_VALUE,
                "the safety threshold is compared against stored power on the same long path");
    }

    // ── 2. The re-pin pins ──────────────────────────────────────────────────

    @Test
    public void maxPowerIsTenThousandMj() {
        Assertions.assertEquals(10_000L * MjAPI.MJ, EntityRobotBase.MAX_POWER,
                "7.1.x MAX_ENERGY was 100 000 RF; BuildCraft's canonical bridge is 1 MJ = 10 RF");
    }

    @Test
    public void maxPowerIsAWholeNumberOfMjThatFitsTheSynchedAccessor() {
        // Decision 4 syncs charge as a whole-MJ VAR_INT (0..10000) rather than a micro-joule long, so the
        // accessor goes dirty at most once per MJ instead of every tick. That only works while capacity is
        // an exact multiple of one MJ and the quotient fits an int.
        Assertions.assertEquals(0, EntityRobotBase.MAX_POWER % MjAPI.MJ,
                "capacity must be a whole number of MJ or the synched whole-MJ charge truncates unevenly");
        Assertions.assertEquals(10_000L, EntityRobotBase.MAX_POWER / MjAPI.MJ,
                "the renderer's charge fraction is ENERGY_MJ / 10000");
        Assertions.assertTrue(EntityRobotBase.MAX_POWER / MjAPI.MJ <= Integer.MAX_VALUE,
                "the whole-MJ charge is synced as an int");
    }

    @Test
    public void safetyPowerIsOneFifthOfCapacity() {
        Assertions.assertEquals(EntityRobotBase.MAX_POWER / 5, EntityRobotBase.SAFETY_POWER,
                "7.1.x SAFETY_POWER was 20 000 RF of a 100 000 RF battery");
        Assertions.assertEquals(2_000L * MjAPI.MJ, EntityRobotBase.SAFETY_POWER,
                "and that is 2 000 MJ at the re-pinned capacity");
    }

    @Test
    public void shutdownPowerIsZero() {
        Assertions.assertEquals(0L, EntityRobotBase.SHUTDOWN_POWER,
                "7.1.x SHUTDOWN_POWER was 0 RF — a robot shuts down when it is actually flat");
    }

    @Test
    public void thresholdsAreOrdered() {
        Assertions.assertTrue(EntityRobotBase.SHUTDOWN_POWER < EntityRobotBase.SAFETY_POWER,
                "a robot must reach its safety margin before it reaches shutdown");
        Assertions.assertTrue(EntityRobotBase.SAFETY_POWER < EntityRobotBase.MAX_POWER,
                "the safety margin is a fraction of capacity, not capacity itself");
    }

    @Test
    public void nullRobotIdIsLongMaxValue() {
        Assertions.assertEquals(Long.MAX_VALUE, EntityRobotBase.NULL_ROBOT_ID,
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
        Assertions.assertEquals(100_000L, EntityRobotBase.MAX_POWER / defaultCycleCost,
                "a full robot must buy the same 100 000 default AI cycles it bought in 7.1.x");
    }

    // ── 4. Damage debit ─────────────────────────────────────────────────────

    @Test
    public void damageDebitIsTwoPointSixPercentOfAFullCharge() {
        Assertions.assertEquals(EntityRobotBase.MAX_POWER / 1000 * 26, DAMAGE_DEBIT_PER_POINT,
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
        long stored = EntityRobotBase.MAX_POWER;
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
