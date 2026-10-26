/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

import java.util.List;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import buildcraft.lib.compat.viewer.MjLabel;
import buildcraft.lib.gui.BCGraphics;

import buildcraft.robotics.compat.viewer.IntegrationRecipeCollector;
import buildcraft.robotics.compat.viewer.IntegrationRecipeEntry;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.container.IntegrationTableLayout;

/**
 * JEI category for the Integration Table, drawn as the table's own GUI (the Programming Table category is the
 * pattern): the item in the centre of the 3x3 ring, the ingredients around it, the preview and the result slot. The
 * table's only recipe today programs robots ({@link IntegrationRecipeCollector}), so "recipe" on a functional robot
 * lands here and "uses" on a board or the blank robot pages through every program.
 *
 * <p>The preview slot repeats the result {@code RENDER_ONLY}, as the table shows it before crafting; it is not
 * indexed. The laser-energy cost is printed under the panel like the other laser tables'.
 */
public class IntegrationTableCategory extends AbstractRecipeCategory<IntegrationRecipeEntry> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftunofficial:textures/gui/integration_table.png");

    // The layout's viewer crop of the GUI texture: the ring, the preview, the result and the power bar.
    // Package-visible so TableLayoutTest can check the crop still frames everything the layout puts in it.
    static final int TEX_U = IntegrationTableLayout.VIEWER_U, TEX_V = IntegrationTableLayout.VIEWER_V;
    static final int TEX_W = IntegrationTableLayout.VIEWER_W, TEX_H = IntegrationTableLayout.VIEWER_H;

    // Slot positions from IntegrationTableLayout, shifted by (-TEX_U, -TEX_V).
    private static final int PREVIEW_X = IntegrationTableLayout.PREVIEW_X - TEX_U,
            PREVIEW_Y = IntegrationTableLayout.PREVIEW_Y - TEX_V;
    private static final int OUTPUT_X = IntegrationTableLayout.RESULT_X - TEX_U,
            OUTPUT_Y = IntegrationTableLayout.RESULT_Y - TEX_V;

    private static final int POWER_X = 4, POWER_Y = TEX_H + 2;
    private static final String POWER_KEY = "gui.jei.category.buildcraftunofficial.integration_table.power";

    private static final int WIDTH = TEX_W;
    private static final int HEIGHT = TEX_H + 12;

    private final IDrawable background;

    public IntegrationTableCategory(IGuiHelper guiHelper) {
        super(
                IntegrationRecipeJeiTypes.INTEGRATION,
                Component.translatable("gui.jei.category.buildcraftunofficial.integration_table"),
                guiHelper.createDrawableItemLike(BCSiliconItems.INTEGRATION_TABLE.get()),
                WIDTH, HEIGHT
        );
        this.background = guiHelper.createDrawable(TEXTURE, TEX_U, TEX_V, TEX_W, TEX_H);
    }

    @Override
    //? if >=26.1 {
    public void draw(IntegrationRecipeEntry recipe, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                     double mouseX, double mouseY) {
    //?} else {
    /*public void draw(IntegrationRecipeEntry recipe, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphics graphics,
                     double mouseX, double mouseY) {*/
    //?}
        background.draw(graphics);
        MjLabel.draw(new BCGraphics(graphics), POWER_KEY, recipe.microJoules(), POWER_X, POWER_Y);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, IntegrationRecipeEntry recipe, IFocusGroup focuses) {
        // addItemStacks throughout: the single-stack forms fork at the JEI 20 cliff (see ProgrammingTableCategory).
        builder.addInputSlot(ringX(IntegrationTableLayout.CENTRE_COL), ringY(IntegrationTableLayout.CENTRE_ROW))
                .addItemStacks(List.of(recipe.center()));
        // Expansion i sits in the menu's ring slot i (reading order, centre skipped).
        for (int i = 0; i < Math.min(recipe.expansions().size(), IntegrationTableLayout.RING_SLOTS); i++) {
            builder.addInputSlot(ringX(IntegrationTableLayout.ringColumn(i)), ringY(IntegrationTableLayout.ringRow(i)))
                    .addItemStacks(List.of(recipe.expansions().get(i)));
        }
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, PREVIEW_X, PREVIEW_Y)
                .addItemStacks(List.of(recipe.outputs().get(0)));
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).addItemStacks(recipe.outputs());
    }

    private static int ringX(int column) {
        return IntegrationTableLayout.ringX(column) - TEX_U;
    }

    private static int ringY(int row) {
        return IntegrationTableLayout.ringY(row) - TEX_V;
    }
}
