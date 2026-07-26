/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.pipe.behaviour;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;

import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipe.ConnectedType;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventFluid;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;

public class PipeBehaviourClay extends PipeBehaviour {
    public PipeBehaviourClay(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourClay(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
    }

    @PipeEventHandler
    public void orderSides(PipeEventItem.SideCheck ordering) {
        for (Direction face : Direction.values()) {
            ConnectedType type = pipe.getConnectedType(face);
            if (type == ConnectedType.TILE) {
                ordering.increasePriority(face, 100);
            }
        }
    }

    @PipeEventHandler
    public void orderSides(PipeEventFluid.SideCheck ordering) {
        for (Direction face : Direction.values()) {
            ConnectedType type = pipe.getConnectedType(face);
            if (type == ConnectedType.TILE) {
                ordering.increasePriority(face, 100);
            }
        }
    }
}
