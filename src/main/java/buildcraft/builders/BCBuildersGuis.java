/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;

import buildcraft.builders.addon.AddonFillerPlanner;
import buildcraft.builders.container.ContainerFillerPlanner;

/** Enum to identify which GUI to open. In 1.21.11 this is used with the MenuType system. */
public enum BCBuildersGuis {
    ARCHITECT,
    BUILDER,
    FILLER,
    LIBRARY,
    REPLACER,
    FILLER_PLANNER;

    /** Open a GUI for a player (no block position). For FILLER_PLANNER use {@link #openFillerPlannerGUI}. */
    public void openGUI(Player player) {
        // Most GUIs are opened by the block on right-click via player.openMenu(blockEntity, pos).
        // FILLER_PLANNER is the only one that targets an addon attached to a Volume Box rather than
        // a block entity, so it has its own helper below.
    }

    /** Open a GUI for a player at a specific block position. */
    public void openGUI(Player player, BlockPos pos) {
    }

    /**
     * Open the Filler Planner GUI for the given addon. Server-side only — the client constructor of
     * {@link ContainerFillerPlanner} reads the volume-box UUID + addon slot from the network buffer
     * and looks the addon up in {@link buildcraft.core.marker.volume.ClientVolumeBoxes}.
     */
    public static void openFillerPlannerGUI(Player player, AddonFillerPlanner addon) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (addon == null || addon.volumeBox == null) return;
        java.util.UUID boxId = addon.volumeBox.id;
        buildcraft.core.marker.volume.EnumAddonSlot slot = addon.getSlot();
        sp.openMenu(
            new SimpleMenuProvider(
                (id, inv, p) -> new ContainerFillerPlanner(id, inv, addon),
                Component.translatable("item.buildcraftunofficial.filler_planner")
            ),
            buf -> {
                buf.writeUUID(boxId);
                buf.writeEnum(slot);
            }
        );
    }
}
