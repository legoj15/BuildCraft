/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.pipe.behaviour;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;

public class PipeBehaviourWoodPower extends PipeBehaviour {

    public PipeBehaviourWoodPower(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourWoodPower(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
    }

    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourWoodPower);
    }

    public int getTextureIndex(Direction face) {
        if (face == null) {
            return 0;
        }
        if (pipe.getConnectedPipe(face) != null) {
            return 0;
        }
        BlockEntity tile = pipe.getConnectedTile(face);
        if (tile == null) {
            return 0;
        }
        if (pipe.getFlow() instanceof buildcraft.transport.pipe.flow.PipeFlowRedstoneFlux) {
            net.neoforged.neoforge.transfer.energy.EnergyHandler handler = pipe.getHolder().getCapabilityFromPipe(face, net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK);
            if (handler == null) return 1;
            
            // Exclude Redstone Engine (FE Consumer) from generating an extraction plug
            if (tile instanceof buildcraft.energy.tile.TileEngineFE) return 0;
            // Include MJ Dynamo (FE Producer) to correctly generate an extraction plug
            if (tile instanceof buildcraft.energy.tile.TileDynamoMJ) return 1;
            
            // Fallback for foreign blocks by performing a fast insertion check
            try (net.neoforged.neoforge.transfer.transaction.Transaction tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                if (handler.insert(1, tx) > 0) return 0;
            }
            return 1;
        } else {
            // Check if neighbour can receive MJ — use NeoForge capability lookup
            IMjReceiver recv = pipe.getHolder().getCapabilityFromPipe(face, MjAPI.CAP_RECEIVER);
            return recv == null ? 1 : recv.canReceive() ? 0 : 1;
        }
    }
}
