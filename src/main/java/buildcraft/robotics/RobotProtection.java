/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics;

import java.util.Objects;

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
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.MachineIdentity;

/**
 * Who a robot is when protection mods are asked. A robot answers to two players: whoever deployed it (placed it on a
 * station from an item; {@link IRobotAccess#getDeployer}) and the owner of its home (linked) station, who is whoever
 * placed the pipe the station sits on. Any player may home a robot on any free station (as in 7.1.x), so neither
 * player alone may vouch for what the robot does: it ACTS as its deployer (the leased fake player, so claim mods judge,
 * and audit logs and advancements attribute to, the player who put the robot to work), and every protection question
 * it asks must be answered yes for BOTH the deployer and the station's owner. A robot flying out of its owner's base
 * is refused wherever that owner would be, like the Quarry and Builder, which ask as the player who placed them; and
 * a robot homed on someone else's station is refused wherever its own deployer would be. When the two are the same
 * player (same UUID) the question is asked once: these probes post a real protection event per block inside robot
 * search loops.
 *
 * <p>A robot with no recorded deployer (one saved before deployers were recorded) acts and asks as its station's owner
 * alone, exactly as every robot did before. A robot with no home station (or whose station's owner has no name) has
 * the generic "[BuildCraft]" identity on the station side, as 7.1.x's robots always did; {@link MachineIdentity} is
 * the one rule for that, on both sides.
 *
 * <p>Two shapes of guard follow from this. A site that asks a probe ({@link #canBreak}) asks it for both identities.
 * A site that acts through the leased player's own events (a planting, a tool use, a stripes handler) gets the
 * deployer's say from those events, and asks the closest probe for the station's owner first
 * ({@link #stationOwnerCanPlace}, {@link #stationOwnerCanBreak}).
 */
public final class RobotProtection {
    private RobotProtection() {}

    /** The robot's home station owner, or null when it has no home station or the station has no owner. */
    @Nullable
    public static GameProfile ownerOf(IRobotAccess robot) {
        DockingStation home = robot.getLinkedStation();
        return home == null ? null : home.getOwner();
    }

    /** The identity this robot acts as: its deployer when one is recorded, else its home station's owner, each
     *  resolved through {@link MachineIdentity} (an unnamed profile or none is "[BuildCraft]"). */
    public static GameProfile identityOf(IRobotAccess robot) {
        GameProfile deployer = robot.getDeployer();
        return MachineIdentity.of(deployer != null ? deployer : ownerOf(robot));
    }

    /** The station side of the pair: the home station's named owner, else "[BuildCraft]". */
    public static GameProfile stationIdentityOf(IRobotAccess robot) {
        return MachineIdentity.of(ownerOf(robot));
    }

    /** Whether the station's owner is a different player from the one the robot acts as, so it must be asked on its
     *  own. Compared by UUID: a profile's name can change between sessions, and two profile objects can name one
     *  player. */
    public static boolean stationOwnerAsksSeparately(IRobotAccess robot) {
        return !Objects.equals(GameProfileUtil.getId(identityOf(robot)),
                GameProfileUtil.getId(stationIdentityOf(robot)));
    }

    /**
     * Leases the fake player this robot acts as in the world: its deployer's ({@link #identityOf}; the station owner's
     * for a robot with no recorded deployer), so claim mods judge that player and advancements a used item grants reach
     * them. Use with try-with-resources, like every {@link FakePlayerUtil.Lease}. Acting through the lease answers only
     * for that player: a caller first asks the station's owner too, through {@link #canBreak} or a
     * {@code stationOwnerCan*} probe.
     */
    public static FakePlayerUtil.Lease lease(IRobotAccess robot, ServerLevel level) {
        return FakePlayerUtil.lease(level, identityOf(robot), null);
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

    /** Whether protection mods let this robot break the block at {@code pos}: they must permit both the identity it
     *  acts as and its home station's owner (asked once when they are the same player); see
     *  {@link BlockUtil#canMachineBreak}. */
    public static boolean canBreak(IRobotAccess robot, ServerLevel level, BlockPos pos) {
        return BlockUtil.canMachineBreak(level, pos, identityOf(robot)) && stationOwnerCanBreak(robot, level, pos);
    }

    /** The station owner's half of a break, for a site whose own events already ask as the acting identity: true
     *  when the station's owner is that same player, else {@link BlockUtil#canMachineBreak} for the owner. */
    public static boolean stationOwnerCanBreak(IRobotAccess robot, ServerLevel level, BlockPos pos) {
        return !stationOwnerAsksSeparately(robot) || BlockUtil.canMachineBreak(level, pos, stationIdentityOf(robot));
    }

    /** The station owner's half of setting something down in {@code pos}, for a site whose own events (a planting's
     *  placement, a stripes handler's) already ask as the acting identity: true when the station's owner is that same
     *  player, else {@link BlockUtil#canMachinePlace} for the owner. */
    public static boolean stationOwnerCanPlace(IRobotAccess robot, ServerLevel level, BlockPos pos) {
        return !stationOwnerAsksSeparately(robot) || BlockUtil.canMachinePlace(level, pos, stationIdentityOf(robot));
    }
}
