/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.container.ContainerAutoCraftItems;
import buildcraft.lib.gui.IBCMenuProvider;

public class TileAutoWorkbenchItems extends TileAutoWorkbenchBase implements IBCMenuProvider {
    public TileAutoWorkbenchItems(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(), pos, state, 3, 3);
    }

    @Override
    protected boolean spillsContentsOnRemoval() {
        return true;
    }

    // --- IBCMenuProvider (GUI) ---
    // The tile is the sole MenuProvider (writeClientSideData supplies the pos on every open path,
    // including vanilla's spectator path — this BaseEntityBlock now offers read-only spectator access
    // like its BuildCraft machine siblings and vanilla containers).

    @Override
    public Component getDisplayName() {
        return Component.translatable("tile.autoWorkbenchBlock.name");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerAutoCraftItems(containerId, playerInventory, this);
    }
}
