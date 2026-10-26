/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.compat.rei;

import java.util.List;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;

import net.minecraft.resources.Identifier;

import buildcraft.lib.compat.rei.ReiCategory;
import buildcraft.lib.compat.rei.ReiDisplay;
import buildcraft.lib.compat.rei.ReiLayout;
import buildcraft.lib.compat.rei.ReiLayout.Role;
import buildcraft.lib.compat.viewer.MjLabel;
import buildcraft.robotics.recipe.IntegrationRecipeJei;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.container.IntegrationTableLayout;

/**
 * REI category for the Integration Table, laid out as JEI's {@code IntegrationTableCategory}: the table's own GUI with
 * the item in the centre of the ring, the ingredients around it, the preview (shown only) and the result.
 */
public class IntegrationTableReiCategory extends ReiCategory<IntegrationRecipeJei> {
    public static final CategoryIdentifier<ReiDisplay<IntegrationRecipeJei>> ID =
            CategoryIdentifier.of("buildcraftunofficial", "integration_table");

    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/integration_table.png");

    private static final int TEX_U = IntegrationTableLayout.VIEWER_U, TEX_V = IntegrationTableLayout.VIEWER_V;
    private static final int TEX_W = IntegrationTableLayout.VIEWER_W, TEX_H = IntegrationTableLayout.VIEWER_H;
    private static final int POWER_X = 4, POWER_Y = TEX_H + 2;

    public IntegrationTableReiCategory() {
        super(ID, "gui.jei.category.buildcraftunofficial.integration_table", BCSiliconItems.INTEGRATION_TABLE.get(),
                TEX_W, TEX_H + 12);
    }

    @Override
    protected void layout(IntegrationRecipeJei recipe, ReiLayout layout) {
        layout.background(TEXTURE, TEX_U, TEX_V, TEX_W, TEX_H);
        layout.item(ringX(IntegrationTableLayout.CENTRE_COL), ringY(IntegrationTableLayout.CENTRE_ROW), Role.INPUT,
                List.of(recipe.center()));
        // Expansion i sits in the menu's ring slot i (reading order, centre skipped).
        for (int i = 0; i < Math.min(recipe.expansions().size(), IntegrationTableLayout.RING_SLOTS); i++) {
            layout.item(ringX(IntegrationTableLayout.ringColumn(i)), ringY(IntegrationTableLayout.ringRow(i)),
                    Role.INPUT, List.of(recipe.expansions().get(i)));
        }
        layout.item(IntegrationTableLayout.PREVIEW_X - TEX_U, IntegrationTableLayout.PREVIEW_Y - TEX_V,
                Role.RENDER_ONLY, List.of(recipe.outputs().get(0)));
        layout.item(IntegrationTableLayout.RESULT_X - TEX_U, IntegrationTableLayout.RESULT_Y - TEX_V, Role.OUTPUT,
                recipe.outputs());
        layout.text(POWER_X, POWER_Y, MjLabel.text("gui.jei.category.buildcraftunofficial.integration_table.power",
                recipe.microJoules()));
    }

    private static int ringX(int column) {
        return IntegrationTableLayout.ringX(column) - TEX_U;
    }

    private static int ringY(int row) {
        return IntegrationTableLayout.ringY(row) - TEX_V;
    }
}
