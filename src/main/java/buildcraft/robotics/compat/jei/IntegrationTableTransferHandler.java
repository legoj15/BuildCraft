/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}
import net.minecraft.world.entity.player.Player;

import buildcraft.lib.compat.jei.AbstractTransferHandler;
import buildcraft.lib.compat.jei.TransferResult;
import buildcraft.lib.gui.ContainerBC_Neptune;

import buildcraft.robotics.container.RoboticsTableTransfer;
import buildcraft.robotics.recipe.IntegrationRecipeJei;
import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.container.ContainerIntegrationTable;
import buildcraft.silicon.tile.TileIntegrationTable;

/**
 * JEI "+" recipe transfer for the Integration Table: moves a blank robot into the centre slot and the entry's board
 * into a ring slot. The server does the work ({@link RoboticsTableTransfer#integrate}); the recipe carries no other
 * state, so the request is just the board's program.
 */
public class IntegrationTableTransferHandler
        extends AbstractTransferHandler<ContainerIntegrationTable, IntegrationRecipeJei> {

    public IntegrationTableTransferHandler(IRecipeTransferHandlerHelper helper) {
        super(helper, ContainerIntegrationTable.class, BCSiliconMenuTypes.INTEGRATION_TABLE::get);
    }

    @Override
    //? if >=1.21.10 {
    public IRecipeType<IntegrationRecipeJei> getRecipeType() {
    //?} else {
    /*public RecipeType<IntegrationRecipeJei> getRecipeType() {*/
    //?}
        return IntegrationRecipeJeiTypes.INTEGRATION;
    }

    @Override
    protected TransferResult check(ContainerIntegrationTable container, IntegrationRecipeJei recipe, Player player,
            boolean maxTransfer) {
        TileIntegrationTable table = container.tile;
        String boardId = recipe.boardId();
        if (table == null || boardId == null) {
            return TransferResult.INVALID;
        }
        return RoboticsTableTransfer.integrate(player.getInventory(), table, boardId, false);
    }

    @Override
    protected void send(ContainerIntegrationTable container, IntegrationRecipeJei recipe, Player player,
            boolean maxTransfer) {
        String boardId = recipe.boardId();
        container.sendMessage(ContainerBC_Neptune.NET_JEI_TRANSFER_INTEGRATION, buf -> buf.writeUtf(boardId));
    }
}
