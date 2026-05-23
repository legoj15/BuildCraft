/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.factory.BCFactoryAttachments.OilAndFuelProduction;

/**
 * Pins the {@link OilAndFuelProduction} player-attachment that gates the
 * {@code refine_and_redefine} advancement. Mirrors the wire-colours tester
 * shape: fresh-attachment / per-fluid edge-flag / completion-edge.
 *
 * <p>Like its sibling, the actual advancement award is gated on
 * {@code ServerPlayer} inside {@code AdvancementUtil}, which
 * {@code makeMockPlayer} cannot satisfy — so these tests pin the tracker
 * semantics and leave the final award path for in-client verification.
 */
public class OilAndFuelProductionTester {

    public static void testFreshAttachmentEmpty(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        OilAndFuelProduction data = player.getData(BCFactoryAttachments.OIL_AND_FUEL_PRODUCTION.get());
        helper.assertFalse(data.isComplete(), "Fresh attachment must not report complete");
        for (String name : BCEnergyFluids.BASE_NAMES) {
            int amount = data.get(name);
            if (amount != 0) {
                throw new AssertionError("Fresh attachment counter for " + name + " must be 0, was " + amount);
            }
        }
        helper.succeed();
    }

    public static void testRecordProductionClampsAtTarget(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        OilAndFuelProduction data = player.getData(BCFactoryAttachments.OIL_AND_FUEL_PRODUCTION.get());

        // Overshooting in one call: counter saturates at PER_FLUID_TARGET (16,000) rather
        // than running past it. A single distillation step can produce up to several
        // hundred mB; over hours of runtime this would otherwise overflow.
        String justSaturated = data.recordProduction("oil", OilAndFuelProduction.PER_FLUID_TARGET + 50_000);
        if (!"oil".equals(justSaturated)) {
            throw new AssertionError("Overshoot from zero must report rising-edge saturation; got " + justSaturated);
        }
        int amount = data.get("oil");
        if (amount != OilAndFuelProduction.PER_FLUID_TARGET) {
            throw new AssertionError("Overshoot must clamp at PER_FLUID_TARGET; got " + amount);
        }

        // Adding more after saturation is a no-op (and must NOT re-report the rising
        // edge — the criterion has already been awarded, awarding it again would still
        // be safe but the contract is "one signal per saturation event").
        String repeat = data.recordProduction("oil", 1_000);
        if (repeat != null) {
            throw new AssertionError("recordProduction on saturated counter must return null; got " + repeat);
        }
        helper.succeed();
    }

    public static void testRecordProductionIgnoresUnknownAndNonPositive(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        OilAndFuelProduction data = player.getData(BCFactoryAttachments.OIL_AND_FUEL_PRODUCTION.get());

        // Unknown base name: vanilla water/lava and other-mod fluids hit this path via
        // BCEnergyFluids.getBaseName returning null. Tracker must reject silently — no
        // ArrayIndexOutOfBounds, no false completion.
        if (data.recordProduction("water", 1_000) != null) {
            throw new AssertionError("Unknown base name must not credit any counter");
        }
        if (data.recordProduction(null, 1_000) != null) {
            throw new AssertionError("Null base name must not credit any counter");
        }

        // Non-positive mB (zero or negative) is a no-op. Negative could arise from a
        // bug elsewhere — clamp at 0 rather than silently decrement.
        if (data.recordProduction("oil", 0) != null) {
            throw new AssertionError("Zero mB must not credit");
        }
        if (data.recordProduction("oil", -100) != null) {
            throw new AssertionError("Negative mB must not credit");
        }

        if (data.get("oil") != 0) {
            throw new AssertionError("After only no-op record calls, oil counter must remain 0");
        }
        helper.succeed();
    }

    public static void testCompletionEdgeFiresExactlyOnce(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        OilAndFuelProduction data = player.getData(BCFactoryAttachments.OIL_AND_FUEL_PRODUCTION.get());

        // Crediting in two halves: first half is below target, must return null; second
        // half pushes across the threshold, must return the saturated base name.
        int half = OilAndFuelProduction.PER_FLUID_TARGET / 2;
        for (int i = 0; i < BCEnergyFluids.BASE_NAMES.size(); i++) {
            String name = BCEnergyFluids.BASE_NAMES.get(i);
            String firstHalf = data.recordProduction(name, half);
            if (firstHalf != null) {
                throw new AssertionError("Half saturation must not report rising edge for " + name);
            }
            String secondHalf = data.recordProduction(name, OilAndFuelProduction.PER_FLUID_TARGET - half);
            if (!name.equals(secondHalf)) {
                throw new AssertionError("Crossing the saturation threshold for " + name
                    + " must return its base name; got " + secondHalf);
            }

            // isComplete tracks the aggregate state; only true once every counter saturates.
            boolean expectedComplete = (i == BCEnergyFluids.BASE_NAMES.size() - 1);
            if (data.isComplete() != expectedComplete) {
                throw new AssertionError("isComplete=" + data.isComplete() + " after saturating "
                    + (i + 1) + " of " + BCEnergyFluids.BASE_NAMES.size() + " counters; expected " + expectedComplete);
            }
        }

        // Subsequent calls on any already-saturated counter must keep returning null.
        String tail = data.recordProduction(BCEnergyFluids.BASE_NAMES.get(0), OilAndFuelProduction.PER_FLUID_TARGET);
        if (tail != null) {
            throw new AssertionError("Already-saturated counter must keep returning null on further credit; got " + tail);
        }
        helper.succeed();
    }
}
