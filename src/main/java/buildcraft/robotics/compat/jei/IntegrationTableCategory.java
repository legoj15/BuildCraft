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

import buildcraft.lib.compat.jei.JeiMjLabel;
import buildcraft.lib.gui.BCGraphics;

import buildcraft.silicon.BCSiliconItems;

/**
 * JEI category for the Integration Table, drawn as the table's own GUI (the Programming Table category is the
 * pattern): the item in the centre of the 3x3 ring, the ingredients around it, the preview and the result slot. The
 * table's only recipe today programs robots ({@link IntegrationRecipeCollector}), so "recipe" on a functional robot
 * lands here and "uses" on a board or the blank robot pages through every program.
 *
 * <p>The preview slot repeats the result {@code RENDER_ONLY}, as the table shows it before crafting; it is not
 * indexed. The laser-energy cost is printed under the panel like the other laser tables'.
 */
public class IntegrationTableCategory extends AbstractRecipeCategory<IntegrationRecipeJei> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftunofficial:textures/gui/integration_table.png");

    // Crop the GUI texture (176x191, see GuiIntegrationTable) to the ring, the preview, the result and the power
    // bar, leaving out the title strip and the player inventory.
    private static final int TEX_U = 12, TEX_V = 18;
    private static final int TEX_W = 160, TEX_H = 78;

    // Slot positions from ContainerIntegrationTable, shifted by (-TEX_U, -TEX_V).
    private static final int RING_X = 19 - TEX_U, RING_Y = 24 - TEX_V, RING_PITCH = 25;
    private static final int PREVIEW_X = 101 - TEX_U, PREVIEW_Y = 36 - TEX_V;
    private static final int OUTPUT_X = 138 - TEX_U, OUTPUT_Y = 49 - TEX_V;

    /** Ring cells (column, row) per expansion index, in ContainerIntegrationTable's slot order: reading order,
     *  skipping the centre. */
    private static final int[][] RING_CELLS = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}};

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
    public void draw(IntegrationRecipeJei recipe, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                     double mouseX, double mouseY) {
    //?} else {
    /*public void draw(IntegrationRecipeJei recipe, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphics graphics,
                     double mouseX, double mouseY) {*/
    //?}
        background.draw(graphics);
        JeiMjLabel.draw(new BCGraphics(graphics), POWER_KEY, recipe.microJoules(), POWER_X, POWER_Y);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, IntegrationRecipeJei recipe, IFocusGroup focuses) {
        // addItemStacks throughout: the single-stack forms fork at the JEI 20 cliff (see ProgrammingTableCategory).
        builder.addInputSlot(ringX(1), ringY(1)).addItemStacks(List.of(recipe.center()));
        for (int i = 0; i < Math.min(recipe.expansions().size(), RING_CELLS.length); i++) {
            int[] cell = RING_CELLS[i];
            builder.addInputSlot(ringX(cell[0]), ringY(cell[1])).addItemStacks(List.of(recipe.expansions().get(i)));
        }
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, PREVIEW_X, PREVIEW_Y)
                .addItemStacks(List.of(recipe.outputs().get(0)));
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).addItemStacks(recipe.outputs());
    }

    private static int ringX(int column) {
        return RING_X + column * RING_PITCH;
    }

    private static int ringY(int row) {
        return RING_Y + row * RING_PITCH;
    }
}
