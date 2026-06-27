/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.events;

import net.minecraft.world.entity.player.Player;


import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fired when a robot program is about to be deployed; cancel to veto the placement. */
public class RobotPlacementEvent extends Event implements ICancellableEvent {
    public Player player;
    public String robotProgram;

    public RobotPlacementEvent(Player player, String robotProgram) {
        this.player = player;
        this.robotProgram = robotProgram;
    }

}

