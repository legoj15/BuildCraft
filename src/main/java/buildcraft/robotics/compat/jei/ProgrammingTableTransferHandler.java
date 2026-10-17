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

import buildcraft.lib.gui.ContainerBC_Neptune;

import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.robotics.container.ContainerProgrammingTable;
import buildcraft.robotics.container.RoboticsTableTransfer;
import buildcraft.robotics.recipe.ProgrammingRecipeJei;
import buildcraft.silicon.tile.TileProgrammingTable;

/**
 * JEI "+" recipe transfer for the Programming Table: moves a blank board from the player's inventory into the
 * table and selects the option the JEI entry shows. The server does the work ({@link RoboticsTableTransfer#program});
 * this only asks it for the entry's recipe and board, after the same checks as an advisory answer.
 */
public class ProgrammingTableTransferHandler
        extends AbstractTableTransferHandler<ContainerProgrammingTable, ProgrammingRecipeJei> {

    public ProgrammingTableTransferHandler(IRecipeTransferHandlerHelper helper) {
        super(helper, ContainerProgrammingTable.class, BCRoboticsMenuTypes.PROGRAMMING_TABLE::get);
    }

    @Override
    //? if >=1.21.10 {
    public IRecipeType<ProgrammingRecipeJei> getRecipeType() {
    //?} else {
    /*public RecipeType<ProgrammingRecipeJei> getRecipeType() {*/
    //?}
        return ProgrammingRecipeJeiTypes.PROGRAMMING;
    }

    @Override
    protected RoboticsTableTransfer.Result execute(ContainerProgrammingTable container, ProgrammingRecipeJei recipe,
            Player player, boolean doTransfer) {
        TileProgrammingTable table = container.tile;
        String recipeId = recipe.recipeId();
        String boardId = recipe.boardId();
        if (table == null || boardId == null) {
            return RoboticsTableTransfer.Result.INVALID;
        }
        RoboticsTableTransfer.Result result =
                RoboticsTableTransfer.program(player.getInventory(), table, recipeId, boardId, false);
        if (result == RoboticsTableTransfer.Result.OK && doTransfer) {
            container.sendMessage(ContainerBC_Neptune.NET_JEI_TRANSFER_PROGRAMMING, buf -> {
                buf.writeUtf(recipeId);
                buf.writeUtf(boardId);
            });
        }
        return result;
    }
}
