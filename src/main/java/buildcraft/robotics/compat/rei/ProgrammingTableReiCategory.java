/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.compat.rei;

import java.util.List;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.compat.rei.ReiCategory;
import buildcraft.lib.compat.rei.ReiDisplay;
import buildcraft.lib.compat.rei.ReiLayout;
import buildcraft.lib.compat.rei.ReiLayout.Role;
import buildcraft.lib.compat.viewer.MjLabel;
import buildcraft.robotics.container.ProgrammingTableLayout;
import buildcraft.robotics.recipe.ProgrammingRecipeJei;
import buildcraft.silicon.BCSiliconItems;

/**
 * REI category for the Programming Table, laid out as JEI's {@code ProgrammingTableCategory}: the table's own GUI with
 * the blank board, the programmed board, and the option grid with the cell to click framed. One display per option,
 * so a lookup on a programmed board lands on exactly its entry. Only the two boards are indexed; the grid cells are
 * shown only.
 */
public class ProgrammingTableReiCategory extends ReiCategory<ProgrammingRecipeJei> {
    public static final CategoryIdentifier<ReiDisplay<ProgrammingRecipeJei>> ID =
            CategoryIdentifier.of("buildcraftunofficial", "programming_table");

    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/programming_table.png");

    private static final int TEX_U = ProgrammingTableLayout.VIEWER_U, TEX_V = ProgrammingTableLayout.VIEWER_V;
    private static final int TEX_W = ProgrammingTableLayout.VIEWER_W, TEX_H = ProgrammingTableLayout.VIEWER_H;
    private static final int POWER_X = 4, POWER_Y = TEX_H + 2;

    public ProgrammingTableReiCategory() {
        super(ID, "gui.jei.category.buildcraftunofficial.programming_table", BCSiliconItems.PROGRAMMING_TABLE::get,
                TEX_W, TEX_H + 12);
    }

    @Override
    protected void layout(ProgrammingRecipeJei recipe, ReiLayout layout) {
        layout.background(TEXTURE, TEX_U, TEX_V, TEX_W, TEX_H);
        // The table GUI's own selection frame, under the option's item as in the GUI.
        int cell = recipe.gridIndex();
        layout.texture(TEXTURE, ProgrammingTableLayout.cellX(cell) - TEX_U, ProgrammingTableLayout.cellY(cell) - TEX_V,
                ProgrammingTableLayout.SELECTED_U, ProgrammingTableLayout.SELECTED_V, ProgrammingTableLayout.CELL_SIZE,
                ProgrammingTableLayout.CELL_SIZE);
        layout.item(ProgrammingTableLayout.BOARD_X - TEX_U, ProgrammingTableLayout.BOARD_Y - TEX_V, Role.INPUT,
                recipe.inputs());
        layout.item(ProgrammingTableLayout.RESULT_X - TEX_U, ProgrammingTableLayout.RESULT_Y - TEX_V, Role.OUTPUT,
                List.of(recipe.output()));
        List<ItemStack> grid = recipe.grid();
        for (int i = 0; i < grid.size(); i++) {
            if (!grid.get(i).isEmpty()) {
                layout.item(ProgrammingTableLayout.cellX(i) - TEX_U, ProgrammingTableLayout.cellY(i) - TEX_V,
                        Role.RENDER_ONLY, List.of(grid.get(i)));
            }
        }
        layout.text(POWER_X, POWER_Y, () -> MjLabel.text(
                "gui.jei.category.buildcraftunofficial.programming_table.power",
                recipe.microJoules()));
    }
}
