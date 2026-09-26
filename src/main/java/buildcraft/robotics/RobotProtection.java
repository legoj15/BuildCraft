/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.BlockUtil;

/**
 * Who a robot acts for when protection mods are asked. A robot has no owner of its own; it works for its home
 * (linked) station, whose owner is whoever placed the pipe the station sits on. So a robot flying out of its owner's
 * base is refused wherever that owner would be — the same rule as the Quarry and Builder, which ask as the player
 * who placed them. A robot with no home station asks as the generic "[BuildCraft]" identity, as 7.1.x's robots
 * always did.
 */
public final class RobotProtection {
    private RobotProtection() {}

    /** The robot's home station owner, or null when it has no home station or the station has no owner. */
    @Nullable
    public static GameProfile ownerOf(IRobotAccess robot) {
        DockingStation home = robot.getLinkedStation();
        return home == null ? null : home.getOwner();
    }

    /** Whether protection mods let this robot break the block at {@code pos}; see {@link BlockUtil#canMachineBreak}. */
    public static boolean canBreak(IRobotAccess robot, ServerLevel level, BlockPos pos) {
        return BlockUtil.canMachineBreak(level, pos, ownerOf(robot));
    }
}
