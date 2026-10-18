/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.container;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure-JUnit pins for {@link PaintBudgets}, the Zone Planner stroke allowances kept on the tile by player: one
 * allowance per player however many times they open the screen, none shared between players, and none held for a
 * player who has not painted lately.
 */
public class PaintBudgetsTest {

    private static int spend(PaintBudgets budgets, UUID player, long now, int attempts) {
        int ok = 0;
        for (int i = 0; i < attempts; i++) {
            if (budgets.tryAcquire(player, now)) {
                ok++;
            }
        }
        return ok;
    }

    @Test
    public void testOnePlayerKeepsOneAllowanceHoweverOftenTheyAsk() {
        PaintBudgets budgets = new PaintBudgets();
        UUID player = UUID.randomUUID();
        Assertions.assertEquals(PaintRateLimiter.BURST, spend(budgets, player, 1000, PaintRateLimiter.BURST + 40),
                "the first screen spends the burst");
        // The same player again (a reopened screen asks the same budgets with the same id): nothing is refilled.
        Assertions.assertEquals(0, spend(budgets, player, 1001, 5), "a reopened screen starts with what the last left");
        Assertions.assertEquals(1, spend(budgets, player, 1001 + PaintRateLimiter.TICKS_PER_STROKE, 5),
                "and the clock refills it as it always did");
    }

    @Test
    public void testPlayersHaveAllowancesOfTheirOwn() {
        PaintBudgets budgets = new PaintBudgets();
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        spend(budgets, one, 1000, PaintRateLimiter.BURST);
        Assertions.assertFalse(budgets.tryAcquire(one, 1000));
        Assertions.assertEquals(PaintRateLimiter.BURST, spend(budgets, two, 1000, PaintRateLimiter.BURST + 5),
                "another player's allowance is full");
    }

    @Test
    public void testPlayersWhoStoppedPaintingAreForgotten() {
        PaintBudgets budgets = new PaintBudgets();
        for (int i = 0; i < 200; i++) {
            Assertions.assertTrue(budgets.tryAcquire(UUID.randomUUID(), 1000));
        }
        Assertions.assertTrue(budgets.size() > 16, "baseline: they are held while their allowance is not full");

        long later = 1000 + PaintRateLimiter.BURST * PaintRateLimiter.TICKS_PER_STROKE;
        Assertions.assertTrue(budgets.tryAcquire(UUID.randomUUID(), later));
        Assertions.assertEquals(1, budgets.size(),
                "once refilled, an allowance is the same as none, so only the player painting now is held");
    }

    @Test
    public void testForgettingAPlayerNeverGivesThemMoreThanKeepingThem() {
        PaintBudgets budgets = new PaintBudgets();
        UUID player = UUID.randomUUID();
        spend(budgets, player, 1000, PaintRateLimiter.BURST);
        for (int i = 0; i < 40; i++) {
            budgets.tryAcquire(UUID.randomUUID(), 1000); // enough others to trigger a sweep
        }
        Assertions.assertFalse(budgets.tryAcquire(player, 1001),
                "a player with an empty allowance is not a candidate for the sweep");
    }
}
