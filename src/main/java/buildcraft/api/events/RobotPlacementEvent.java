/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.events;

import net.minecraft.world.entity.player.Player;

/** Fired when a robot program is about to be deployed; cancel to veto the placement. Nothing posts it yet. */
public class RobotPlacementEvent implements ICancellableBCEvent {
    public static final BCEventListeners<RobotPlacementEvent> LISTENERS = new BCEventListeners<>();

    public Player player;
    public String robotProgram;
    private boolean canceled;

    public RobotPlacementEvent(Player player, String robotProgram) {
        this.player = player;
        this.robotProgram = robotProgram;
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
