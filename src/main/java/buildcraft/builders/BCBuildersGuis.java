/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/** Enum to identify which GUI to open. In 1.21.11 this is used with the MenuType system. */
public enum BCBuildersGuis {
    ARCHITECT,
    BUILDER,
    FILLER,
    LIBRARY,
    REPLACER,
    FILLER_PLANNER;

    /** Open a GUI for a player (no block position). */
    public void openGUI(Player player) {
        // TODO: In 1.21.11, implement via MenuProvider and player.openMenu()
        // For now this is a stub — the Filler Planner's GUI will be wired up
        // when the full container/screen registration is completed.
    }

    /** Open a GUI for a player at a specific block position. */
    public void openGUI(Player player, BlockPos pos) {
        // TODO: implement via MenuProvider
    }
}
