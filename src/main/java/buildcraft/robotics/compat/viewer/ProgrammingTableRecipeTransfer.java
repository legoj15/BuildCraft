/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.viewer;

import net.minecraft.world.entity.player.Player;

import buildcraft.lib.compat.viewer.RecipeTransfer;
import buildcraft.lib.compat.viewer.TransferResult;
import buildcraft.lib.gui.ContainerBC_Neptune;

import buildcraft.robotics.BCRoboticsMenuTypes;
import buildcraft.robotics.container.ContainerProgrammingTable;
import buildcraft.robotics.container.RoboticsTableTransfer;
import buildcraft.robotics.recipe.ProgrammingRecipeJei;
import buildcraft.silicon.tile.TileProgrammingTable;

/**
 * "+" recipe transfer for the Programming Table, shared by JEI and REI: moves a blank board from the player's
 * inventory into the table and selects the option the entry shows. The server does the work
 * ({@link RoboticsTableTransfer#program}); this only asks it for the entry's recipe and board, after the same checks as
 * an advisory answer.
 */
public final class ProgrammingTableRecipeTransfer
        extends RecipeTransfer<ContainerProgrammingTable, ProgrammingRecipeJei> {

    public ProgrammingTableRecipeTransfer() {
        super(ContainerProgrammingTable.class, BCRoboticsMenuTypes.PROGRAMMING_TABLE::get);
    }

    @Override
    protected TransferResult check(ContainerProgrammingTable container, ProgrammingRecipeJei recipe, Player player,
            boolean maxTransfer) {
        TileProgrammingTable table = container.tile;
        String boardId = recipe.boardId();
        if (table == null || boardId == null) {
            return TransferResult.INVALID;
        }
        return RoboticsTableTransfer.program(player.getInventory(), table, recipe.recipeId(), boardId, false);
    }

    @Override
    protected void send(ContainerProgrammingTable container, ProgrammingRecipeJei recipe, Player player,
            boolean maxTransfer) {
        String recipeId = recipe.recipeId();
        String boardId = recipe.boardId();
        container.sendMessage(ContainerBC_Neptune.NET_JEI_TRANSFER_PROGRAMMING, buf -> {
            buf.writeUtf(recipeId);
            buf.writeUtf(boardId);
        });
    }
}
