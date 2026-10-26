/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.client.render;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import buildcraft.lib.client.render.MarkerRenderer;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;

import buildcraft.builders.snapshot.EnumStallReason;
import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.core.item.ItemGoggles;

/**
 * Who sees the outlines of the cells a stalled Builder is skipping, and in what colour. Client side only (the laser
 * types are client assets); {@code BCBuildersEventDist} reaches it only from its render events.
 */
public final class StallHighlights {
    private StallHighlights() {
    }

    /**
     * Whether {@code player} sees the stalled-cell outlines: while a Marker Wrench or Volume Wrench is in either hand
     * (the tools that already show the build's volumes), or BC Goggles are worn on the head. The one place that says
     * so: render code asks it and nothing else, so a change to who sees them (Goggles becoming wearable, a new tool)
     * is a change here.
     */
    public static boolean canSee(Player player) {
        return MarkerRenderer.isHoldingMarkerWrench(player)
            || player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ItemGoggles;
    }

    /** The outline a reason is drawn with: red for what the player has to change, yellow for supplies, blue for fluid
     *  (the colours of the power beams: low, medium and full). */
    public static LaserType laserFor(EnumStallReason reason) {
        return switch (reason.hue()) {
            case RED -> BuildCraftLaserManager.POWER_LOW;
            case YELLOW -> BuildCraftLaserManager.POWER_MED;
            case BLUE -> BuildCraftLaserManager.POWER_FULL;
        };
    }
}
