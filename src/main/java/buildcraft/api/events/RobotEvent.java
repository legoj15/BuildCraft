/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.events;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;


import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import buildcraft.api.robots.EntityRobotBase;

public abstract class RobotEvent extends Event {
    public final EntityRobotBase robot;

    public RobotEvent(EntityRobotBase robot) {
        this.robot = robot;
    }

    /** Cancel to veto a robot being placed into the world from its item. */
    public static class Place extends RobotEvent implements ICancellableEvent {
        public final Player player;

        public Place(EntityRobotBase robot, Player player) {
            super(robot);
            this.player = player;
        }
    }

    /** Cancel to suppress the default right-click interaction handling on a robot. */
    public static class Interact extends RobotEvent implements ICancellableEvent {
        public final Player player;
        public final ItemStack item;

        public Interact(EntityRobotBase robot, Player player, ItemStack item) {
            super(robot);
            this.player = player;
            this.item = item;
        }
    }

    /** Cancel to prevent a robot from being dismantled back into its item. */
    public static class Dismantle extends RobotEvent implements ICancellableEvent {
        public final Player player;

        public Dismantle(EntityRobotBase robot, Player player) {
            super(robot);
            this.player = player;
        }
    }
}

