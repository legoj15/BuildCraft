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
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.compat.jei.JeiMjLabel;
import buildcraft.lib.gui.BCGraphics;

import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.tile.TileProgrammingTable;

/**
 * JEI category for the Programming Table, drawn as the table's own GUI (the Assembly Table category is the pattern):
 * the board slot top-left, the programmed board below it, and the whole 6x4 option grid with the cell to click
 * highlighted — the grid is how the table is actually operated, so showing it answers "where do I click" as well as
 * "what does it make". Each entry is one option ({@link ProgrammingRecipeCollector}), so JEI "recipe" on a Miner
 * board lands on exactly the Miner entry, and "uses" on a blank board pages through every program.
 *
 * <p>Only the board and the result are JEI inputs/outputs. The grid cells are {@code RENDER_ONLY}: they show
 * tooltips but are not indexed, so they don't make every entry a "use" of every board. The laser-energy cost spans
 * almost two orders of magnitude between boards, so it is printed under the panel like the Assembly Table's.
 */
public class ProgrammingTableCategory extends AbstractRecipeCategory<ProgrammingRecipeJei> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftunofficial:textures/gui/programming_table.png");

    // Crop the GUI texture (176x207, see GuiProgrammingTable) to the board slots, the option grid and the power bar,
    // leaving out the title strip and the player inventory.
    private static final int TEX_U = 3, TEX_V = 27;
    private static final int TEX_W = 169, TEX_H = 86;

    // Slot positions from ContainerProgrammingTable, shifted by (-TEX_U, -TEX_V).
    private static final int INPUT_X = 8 - TEX_U, INPUT_Y = 36 - TEX_V;
    private static final int OUTPUT_X = 8 - TEX_U, OUTPUT_Y = 90 - TEX_V;
    private static final int GRID_X = 43 - TEX_U, GRID_Y = 36 - TEX_V;
    private static final int SLOT_PITCH = 18;

    // The table GUI's selection frame (the same texture cell GuiProgrammingTable draws).
    private static final int SELECTED_U = 196, SELECTED_V = 1, SELECTED_SIZE = 16;

    private static final int POWER_X = 4, POWER_Y = TEX_H + 2;
    private static final String POWER_KEY = "gui.jei.category.buildcraftunofficial.programming_table.power";

    private static final int WIDTH = TEX_W;
    private static final int HEIGHT = TEX_H + 12;

    private final IDrawable background;
    private final IDrawable selected;

    public ProgrammingTableCategory(IGuiHelper guiHelper) {
        super(
                ProgrammingRecipeJeiTypes.PROGRAMMING,
                Component.translatable("gui.jei.category.buildcraftunofficial.programming_table"),
                guiHelper.createDrawableItemLike(BCSiliconItems.PROGRAMMING_TABLE.get()),
                WIDTH, HEIGHT
        );
        this.background = guiHelper.createDrawable(TEXTURE, TEX_U, TEX_V, TEX_W, TEX_H);
        this.selected = guiHelper.createDrawable(TEXTURE, SELECTED_U, SELECTED_V, SELECTED_SIZE, SELECTED_SIZE);
    }

    @Override
    //? if >=26.1 {
    public void draw(ProgrammingRecipeJei recipe, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                     double mouseX, double mouseY) {
    //?} else {
    /*public void draw(ProgrammingRecipeJei recipe, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphics graphics,
                     double mouseX, double mouseY) {*/
    //?}
        background.draw(graphics);
        // JEI draws slot contents after this, so the frame sits under the item, as in the table's own GUI.
        selected.draw(graphics, cellX(recipe.gridIndex()), cellY(recipe.gridIndex()));
        JeiMjLabel.draw(new BCGraphics(graphics), POWER_KEY, recipe.microJoules(), POWER_X, POWER_Y);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ProgrammingRecipeJei recipe, IFocusGroup focuses) {
        // addItemStacks (not addItemStack/add) throughout: the single-stack forms fork at the JEI 20 cliff —
        // addItemStack is deprecated for removal on 20+, and its add(ItemStack) successor is absent on 1.21.1's 19.x.
        builder.addInputSlot(INPUT_X, INPUT_Y).addItemStacks(recipe.inputs());
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).addItemStacks(List.of(recipe.output()));

        List<ItemStack> grid = recipe.grid();
        for (int i = 0; i < grid.size(); i++) {
            ItemStack option = grid.get(i);
            if (!option.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.RENDER_ONLY, cellX(i), cellY(i)).addItemStacks(List.of(option));
            }
        }
    }

    private static int cellX(int index) {
        return GRID_X + (index % TileProgrammingTable.OPTION_COLS) * SLOT_PITCH;
    }

    private static int cellY(int index) {
        return GRID_Y + (index / TileProgrammingTable.OPTION_COLS) * SLOT_PITCH;
    }
}
