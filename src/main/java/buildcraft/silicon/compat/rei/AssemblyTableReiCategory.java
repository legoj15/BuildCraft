/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.rei;

import java.util.List;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.compat.rei.ReiCategory;
import buildcraft.lib.compat.rei.ReiDisplay;
import buildcraft.lib.compat.rei.ReiLayout;
import buildcraft.lib.compat.rei.ReiLayout.Role;
import buildcraft.lib.compat.viewer.MjLabel;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.compat.viewer.AssemblyRecipeEntry;
import buildcraft.silicon.container.AssemblyTableLayout;

/**
 * REI category for the Assembly Table, laid out as JEI's {@code AssemblyTableCategory}: the input grid and energy bar
 * cropped from the table's GUI, the output on a result frame to the right, and the MJ cost under the panel. The facade
 * entries cycle their block slot and their output independently: REI has no focus link to pair them.
 */
public class AssemblyTableReiCategory extends ReiCategory<AssemblyRecipeEntry> {
    public static final CategoryIdentifier<ReiDisplay<AssemblyRecipeEntry>> ID =
            CategoryIdentifier.of("buildcraftunofficial", "assembly_table");

    private static final Identifier TEXTURE = Identifier.parse("buildcraftunofficial:textures/gui/assembly_table.png");

    private static final int TEX_U = AssemblyTableLayout.VIEWER_U, TEX_V = AssemblyTableLayout.VIEWER_V;
    private static final int TEX_W = AssemblyTableLayout.VIEWER_W, TEX_H = AssemblyTableLayout.VIEWER_H;
    private static final int OUTPUT_X = TEX_W + 16, OUTPUT_Y = AssemblyTableLayout.INPUT_Y - TEX_V;
    private static final int POWER_X = 4, POWER_Y = TEX_H + 2;

    public AssemblyTableReiCategory() {
        super(ID, "gui.jei.category.buildcraftunofficial.assembly_table", BCSiliconItems.ASSEMBLY_TABLE.get(),
                OUTPUT_X + 24, TEX_H + 12);
    }

    @Override
    protected void layout(AssemblyRecipeEntry recipe, ReiLayout layout) {
        layout.background(TEXTURE, TEX_U, TEX_V, TEX_W, TEX_H);
        List<List<ItemStack>> inputs = recipe.inputSlots();
        for (int i = 0; i < Math.min(inputs.size(), AssemblyTableLayout.GRID_CELLS); i++) {
            layout.item(AssemblyTableLayout.inputX(i) - TEX_U, AssemblyTableLayout.inputY(i) - TEX_V, Role.INPUT,
                    inputs.get(i));
        }
        layout.result(OUTPUT_X, OUTPUT_Y, recipe.outputs());
        layout.text(POWER_X, POWER_Y, MjLabel.text("gui.jei.category.buildcraftunofficial.assembly_table.power",
                recipe.microJoules()));
    }
}
