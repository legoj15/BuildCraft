/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client;

import net.minecraft.client.Minecraft;

import buildcraft.core.item.ItemGoggles;

/**
 * Whether the local player is looking through Robot Goggles, for the robot's see-through charge outline. Client side
 * only (it names {@link Minecraft}): {@code EntityRobot} is loaded on the dedicated server, so it reaches this class
 * only behind {@code level().isClientSide()}, and the answer crosses the boundary as a plain {@code boolean} - the
 * same split as {@code StallHighlights.canSeeLocal}, which keeps the client player class out of every common
 * signature.
 */
public final class RobotGogglesView {
    private RobotGogglesView() {}

    /** Whether the local player has Robot Goggles on. False before a world is joined. */
    public static boolean localPlayerWearsGoggles() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && ItemGoggles.isWornBy(mc.player);
    }
}
