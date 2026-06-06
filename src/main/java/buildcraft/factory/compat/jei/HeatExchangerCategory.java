/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import buildcraft.lib.gui.BCGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.recipes.IRefineryRecipeManager.ICoolableRecipe;
import buildcraft.api.recipes.IRefineryRecipeManager.IHeatableRecipe;
import buildcraft.factory.BCFactoryItems;
import buildcraft.lib.compat.jei.FluidContainerAliases;

/**
 * Single JEI category for the heat exchanger. Each entry is a complete
 * {@link HeatExchangerRecipePair} (heatable + coolable) so the rendered view
 * mirrors the in-world GUI exactly: cold fluid enters MID-LEFT and the same
 * fluid type, heated, exits TOP-RIGHT; hot fluid enters TOP-LEFT and exits
 * MID-RIGHT cooled. The two flows cross diagonally and the same fluid type
 * appears on each diagonal, matching the runtime routing in
 * {@code TileHeatExchange.craft()} where the heatable's output lands in
 * {@code endSection.tankOutput} (TOP-RIGHT) and the coolable's output lands
 * in {@code startSection.tankOutput} (MID-RIGHT).
 *
 * <p>All four slots are real INPUT/OUTPUT slots, so JEI lookups behave the
 * way players expect — pressing U on lava surfaces every pair where lava is
 * the heat source; pressing R on hot oil surfaces every pair that produces
 * it on either side.
 */
public class HeatExchangerCategory extends AbstractRecipeCategory<HeatExchangerRecipePair> {
    // The GUI's outer border and player-inventory section are visual noise in
    // a JEI category, so we crop the background to the inner machine panel:
    // a 170x84 region starting at (3, 4) of heat_exchanger.png. Slot positions
    // are shifted by the same (-3, -4) offset so they still land on the tank
    // graphics inside the cropped texture.
    private static final int TEX_U = 3, TEX_V = 4;
    private static final int TEX_W = 170, TEX_H = 84;

    private static final int WIDTH = TEX_W;
    private static final int HEIGHT = TEX_H;

    // Tank coordinates from GuiHeatExchange, shifted by (-TEX_U, -TEX_V) to
    // match the cropped texture's origin.
    private static final int START_IN_X = 44 - TEX_U, START_IN_Y = 64 - TEX_V, START_IN_W = 34, START_IN_H = 17;
    private static final int START_OUT_X = 116 - TEX_U, START_OUT_Y = 43 - TEX_V, START_OUT_W = 16, START_OUT_H = 38;
    private static final int END_IN_X = 44 - TEX_U, END_IN_Y = 12 - TEX_V, END_IN_W = 16, END_IN_H = 38;
    private static final int END_OUT_X = 98 - TEX_U, END_OUT_Y = 12 - TEX_V, END_OUT_W = 34, END_OUT_H = 17;

    private final IDrawable background;

    public HeatExchangerCategory(IGuiHelper guiHelper) {
        super(
                HeatExchangerRecipeTypes.PAIR,
                Component.translatable("gui.jei.category.buildcraftunofficial.heat_exchanger"),
                guiHelper.createDrawableItemLike(BCFactoryItems.HEAT_EXCHANGE.get()),
                WIDTH, HEIGHT
        );
        this.background = guiHelper.createDrawable(
                Identifier.parse("buildcraftunofficial:textures/gui/heat_exchanger.png"),
                TEX_U, TEX_V, TEX_W, TEX_H);
    }

    @Override
    //? if >=26.1 {
    public void draw(HeatExchangerRecipePair pair, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                     double mouseX, double mouseY) {
    //?} else {
    /*public void draw(HeatExchangerRecipePair pair, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphics graphics,
                     double mouseX, double mouseY) {*/
    //?}
        background.draw(graphics);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, HeatExchangerRecipePair pair, IFocusGroup focuses) {
        IHeatableRecipe heatable = pair.heatable();
        ICoolableRecipe coolable = pair.coolable();

        // Inputs sit on their owning section's input tank.
        FluidStack hIn = heatable.in();
        if (!hIn.isEmpty()) {
            builder.addInputSlot(START_IN_X, START_IN_Y)
                    .setFluidRenderer(hIn.getAmount(), false, START_IN_W, START_IN_H)
                    //? if >=1.21.10 {
                    .add(hIn.getFluid(), hIn.getAmount(), hIn.getComponentsPatch());
                    //?} else {
                    /*.addFluidStack(hIn.getFluid(), hIn.getAmount(), hIn.getComponentsPatch());*/
                    //?}
            FluidContainerAliases.addAliases(builder, hIn, RecipeIngredientRole.INPUT);
        }
        FluidStack cIn = coolable.in();
        if (!cIn.isEmpty()) {
            builder.addInputSlot(END_IN_X, END_IN_Y)
                    .setFluidRenderer(cIn.getAmount(), false, END_IN_W, END_IN_H)
                    //? if >=1.21.10 {
                    .add(cIn.getFluid(), cIn.getAmount(), cIn.getComponentsPatch());
                    //?} else {
                    /*.addFluidStack(cIn.getFluid(), cIn.getAmount(), cIn.getComponentsPatch());*/
                    //?}
            FluidContainerAliases.addAliases(builder, cIn, RecipeIngredientRole.INPUT);
        }

        // Outputs cross diagonally — matches TileHeatExchange.craft() (c_out =
        // start.tankOutput, h_out = end.tankOutput) and gives the GUI's
        // "fluids trade vertical positions" look: the heated cold-fluid product
        // exits at TOP-RIGHT, and the cooled hot-fluid product exits at MID-RIGHT.
        FluidStack hOut = heatable.out();
        if (hOut != null && !hOut.isEmpty()) {
            builder.addOutputSlot(END_OUT_X, END_OUT_Y)
                    .setFluidRenderer(hOut.getAmount(), false, END_OUT_W, END_OUT_H)
                    //? if >=1.21.10 {
                    .add(hOut.getFluid(), hOut.getAmount(), hOut.getComponentsPatch());
                    //?} else {
                    /*.addFluidStack(hOut.getFluid(), hOut.getAmount(), hOut.getComponentsPatch());*/
                    //?}
            FluidContainerAliases.addAliases(builder, hOut, RecipeIngredientRole.OUTPUT);
        }
        FluidStack cOut = coolable.out();
        if (cOut != null && !cOut.isEmpty()) {
            builder.addOutputSlot(START_OUT_X, START_OUT_Y)
                    .setFluidRenderer(cOut.getAmount(), false, START_OUT_W, START_OUT_H)
                    //? if >=1.21.10 {
                    .add(cOut.getFluid(), cOut.getAmount(), cOut.getComponentsPatch());
                    //?} else {
                    /*.addFluidStack(cOut.getFluid(), cOut.getAmount(), cOut.getComponentsPatch());*/
                    //?}
            FluidContainerAliases.addAliases(builder, cOut, RecipeIngredientRole.OUTPUT);
        }
    }
}
