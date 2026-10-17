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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FakePlayerUtil;

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

    /**
     * Leases the fake player this robot acts as in the world: the home station owner's (so claim mods judge the owner
     * and advancements a used item grants reach them), or the generic "[BuildCraft]" player when the robot has no
     * owner. Use with try-with-resources, like every {@link FakePlayerUtil.Lease}.
     */
    public static FakePlayerUtil.Lease lease(IRobotAccess robot, ServerLevel level) {
        return FakePlayerUtil.lease(level, ownerOf(robot), null);
    }

    /**
     * Puts the robot's tool in the leased fake player's main hand, the way a ticking player holds it: vanilla's
     * destroy-speed maths and every claim or tool listener read the player's hand, not the stack an event carries.
     * The lease takes the tool back out of the hand when it closes; the stack is never copied, so wear the use puts
     * on it lands on the robot's own tool.
     */
    public static void holdInMainHand(FakePlayer player, ItemStack tool) {
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        //? if >=1.21.10 {
        // 1.21.10 split the player's equipment table off the Inventory: setItemInHand now writes the table, while
        // Player.getDestroySpeed still reads inventory.getSelectedItem(). Real players bridge the two while
        // ticking; a never-ticked fake player bridges nowhere, and without this line a robot mined at hand speed -
        // the tool's speed never entered the formula at all (found by the Ph9 break-progress game test).
        player.getInventory().setSelectedItem(tool);
        //?}
    }

    /** Whether protection mods let this robot break the block at {@code pos}; see {@link BlockUtil#canMachineBreak}. */
    public static boolean canBreak(IRobotAccess robot, ServerLevel level, BlockPos pos) {
        return BlockUtil.canMachineBreak(level, pos, ownerOf(robot));
    }
}
