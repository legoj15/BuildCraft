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

    private static int cachedPlayerId = Integer.MIN_VALUE;
    private static int cachedTick;
    private static boolean cachedWorn;

    /** Whether the local player has Robot Goggles on. False before a world is joined. Every robot asks it once or twice
     *  a frame, and with an accessory mod installed the answer scans that mod's slots, so it is worked out once per
     *  player tick (the slots cannot change in between; the player's own tick, not the level's, which stops under
     *  {@code /tick freeze}). Keyed by entity id, not the player object, so no left world is kept alive. */
    public static boolean localPlayerWearsGoggles() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        if (mc.player.getId() != cachedPlayerId || mc.player.tickCount != cachedTick) {
            cachedPlayerId = mc.player.getId();
            cachedTick = mc.player.tickCount;
            cachedWorn = ItemGoggles.isWornBy(mc.player);
        }
        return cachedWorn;
    }
}
