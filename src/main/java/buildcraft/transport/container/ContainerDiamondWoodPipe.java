/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.container;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.pipe.IPipeHolder;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.lib.net.PacketBufferBC;

import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond;
import buildcraft.transport.pipe.behaviour.PipeBehaviourWoodDiamond.FilterMode;
import buildcraft.transport.tile.TilePipeHolder;

@SuppressWarnings("this-escape")
public class ContainerDiamondWoodPipe extends ContainerBC_Neptune {
    private static final int NET_FILTER_MODE = 1;

    private final IPipeHolder pipeHolder;
    public final PipeBehaviourWoodDiamond behaviour;

    /** Client-side constructor (from network). */
    public ContainerDiamondWoodPipe(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getBehaviour(playerInv, buf));
    }

    /** Server-side constructor. */
    public ContainerDiamondWoodPipe(int containerId, Inventory playerInv, PipeBehaviourWoodDiamond behaviour) {
        super(BCTransportMenuTypes.DIAMOND_WOOD_PIPE.get(), containerId, playerInv.player);
        this.behaviour = behaviour;
        this.pipeHolder = behaviour.pipe.getHolder();

        // 9 phantom filter slots in a single row
        for (int i = 0; i < 9; i++) {
            addSlot(new SlotPhantom(behaviour.filters, i, 8 + i * 18, 18));
        }

        // Player inventory
        addFullPlayerInventory(8, 79);
    }

    private static PipeBehaviourWoodDiamond getBehaviour(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TilePipeHolder holder && holder.getPipe() != null) {
                if (holder.getPipe().getBehaviour() instanceof PipeBehaviourWoodDiamond wd) {
                    return wd;
                }
            }
        }
        throw new IllegalStateException("No wood-diamond pipe behaviour at " + pos);
    }

    /** Client → server: change filter mode. */
    public void sendNewFilterMode(FilterMode newFilterMode) {
        this.sendMessage(NET_FILTER_MODE, (buffer) -> buffer.writeEnum(newFilterMode));
    }

    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient,
            net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        super.readMessage(id, buffer, isClient, ctx);
        if (id == NET_FILTER_MODE && !isClient) {
            behaviour.filterMode = buffer.readEnum(FilterMode.class);
            behaviour.pipe.getHolder().scheduleNetworkUpdate(
                    buildcraft.api.transport.pipe.IPipeHolder.PipeMessageReceiver.BEHAVIOUR);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return pipeHolder.canPlayerInteract(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // Phantom slots don't support shift-clicking items into them
        return ItemStack.EMPTY;
    }
}
