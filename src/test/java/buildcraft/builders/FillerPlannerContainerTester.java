/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import buildcraft.builders.addon.AddonFillerPlanner;
import buildcraft.builders.container.ContainerFillerPlanner;

/**
 * Guards the Filler Planner's "no player inventory" invariant.
 * <p>
 * The Filler Planner is a pattern-planning screen with no item interaction — its pattern and
 * parameter "slots" are BuildCraft GUI elements, not vanilla {@link net.minecraft.world.inventory.Slot}s.
 * It used to call {@code addFullPlayerInventory(...)}, which left the player's 36 inventory slots
 * dangling in the empty space below the panel: items rendered as slot-less floating icons under a
 * stray "Inventory" label, because the GUI texture has no inventory cells. The fix dropped that
 * call, so the container must now expose ZERO slots. If player inventory is ever re-added, this fails.
 * <p>
 * The matching client-side changes (GUI height trimmed to the 81px panel, help ledger opted out via
 * {@code GuiFillerPlanner.shouldAddHelpLedger()}) are client-only and can't be instantiated
 * headlessly — they need in-client verification.
 */
public class FillerPlannerContainerTester {

    public static void testFillerPlannerHasNoSlots(GameTestHelper helper) {
        Player mockPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        AddonFillerPlanner addon = new AddonFillerPlanner();
        ContainerFillerPlanner container = new ContainerFillerPlanner(1, mockPlayer.getInventory(), addon);

        if (!container.slots.isEmpty()) {
            throw new IllegalStateException(
                "Filler Planner is a pattern-only screen: its container must expose no slots "
                    + "(no player inventory). Found " + container.slots.size() + " slot(s) — "
                    + "did addFullPlayerInventory() get re-added?");
        }

        helper.succeed();
    }
}
