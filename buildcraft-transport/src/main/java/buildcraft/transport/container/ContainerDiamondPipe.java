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

import buildcraft.transport.BCTransportMenuTypes;
import buildcraft.transport.pipe.behaviour.PipeBehaviourDiamond;
import buildcraft.transport.tile.TilePipeHolder;

public class ContainerDiamondPipe extends ContainerBC_Neptune {
    private final IPipeHolder pipeHolder;
    private final PipeBehaviourDiamond behaviour;

    /** Client-side constructor (from network). */
    public ContainerDiamondPipe(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getBehaviour(playerInv, buf));
    }

    /** Server-side constructor. */
    public ContainerDiamondPipe(int containerId, Inventory playerInv, PipeBehaviourDiamond behaviour) {
        super(BCTransportMenuTypes.DIAMOND_PIPE.get(), containerId, playerInv.player);
        this.behaviour = behaviour;
        this.pipeHolder = behaviour.pipe.getHolder();

        // 6 rows × 9 cols of phantom filter slots
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new SlotPhantom(behaviour.filters, x + y * 9, 8 + x * 18, 18 + y * 18));
            }
        }

        // Player inventory (3 rows + hotbar)
        addFullPlayerInventory(8, 140);
    }

    private static PipeBehaviourDiamond getBehaviour(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TilePipeHolder holder && holder.getPipe() != null) {
                if (holder.getPipe().getBehaviour() instanceof PipeBehaviourDiamond diamond) {
                    return diamond;
                }
            }
        }
        throw new IllegalStateException("No diamond pipe behaviour at " + pos);
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
