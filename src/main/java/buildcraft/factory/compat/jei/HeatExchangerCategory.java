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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.recipes.IRefineryRecipeManager.ICoolableRecipe;
import buildcraft.api.recipes.IRefineryRecipeManager.IHeatableRecipe;
import buildcraft.factory.BCFactoryItems;

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
    private static final int WIDTH = 176;
    private static final int HEIGHT = 86;

    // Tank coordinates match GuiHeatExchange exactly.
    private static final int START_IN_X = 44, START_IN_Y = 64, START_IN_W = 34, START_IN_H = 17;
    private static final int START_OUT_X = 116, START_OUT_Y = 43, START_OUT_W = 16, START_OUT_H = 38;
    private static final int END_IN_X = 44, END_IN_Y = 12, END_IN_W = 16, END_IN_H = 38;
    private static final int END_OUT_X = 98, END_OUT_Y = 12, END_OUT_W = 34, END_OUT_H = 17;

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
                0, 0, WIDTH, HEIGHT);
    }

    @Override
    public void draw(HeatExchangerRecipePair pair, IRecipeSlotsView slots, GuiGraphicsExtractor graphics,
                     double mouseX, double mouseY) {
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
                    .add(hIn.getFluid(), hIn.getAmount(), hIn.getComponentsPatch());
            addBucketAlias(builder, hIn, RecipeIngredientRole.INPUT);
        }
        FluidStack cIn = coolable.in();
        if (!cIn.isEmpty()) {
            builder.addInputSlot(END_IN_X, END_IN_Y)
                    .setFluidRenderer(cIn.getAmount(), false, END_IN_W, END_IN_H)
                    .add(cIn.getFluid(), cIn.getAmount(), cIn.getComponentsPatch());
            addBucketAlias(builder, cIn, RecipeIngredientRole.INPUT);
        }

        // Outputs cross diagonally — matches TileHeatExchange.craft() (c_out =
        // start.tankOutput, h_out = end.tankOutput) and gives the GUI's
        // "fluids trade vertical positions" look: the heated cold-fluid product
        // exits at TOP-RIGHT, and the cooled hot-fluid product exits at MID-RIGHT.
        FluidStack hOut = heatable.out();
        if (hOut != null && !hOut.isEmpty()) {
            builder.addOutputSlot(END_OUT_X, END_OUT_Y)
                    .setFluidRenderer(hOut.getAmount(), false, END_OUT_W, END_OUT_H)
                    .add(hOut.getFluid(), hOut.getAmount(), hOut.getComponentsPatch());
            addBucketAlias(builder, hOut, RecipeIngredientRole.OUTPUT);
        }
        FluidStack cOut = coolable.out();
        if (cOut != null && !cOut.isEmpty()) {
            builder.addOutputSlot(START_OUT_X, START_OUT_Y)
                    .setFluidRenderer(cOut.getAmount(), false, START_OUT_W, START_OUT_H)
                    .add(cOut.getFluid(), cOut.getAmount(), cOut.getComponentsPatch());
            addBucketAlias(builder, cOut, RecipeIngredientRole.OUTPUT);
        }
    }

    /**
     * Register the fluid's bucket item as an invisible lookup ingredient with
     * the same role as the fluid slot. The recipe view stays clean (the bucket
     * isn't drawn — the fluid renderer keeps its tank shape), but pressing R/U
     * on a bucket of cool oil now surfaces this recipe alongside the bare-fluid
     * lookup, which matches what a survival-mode player actually has in hand.
     */
    private static void addBucketAlias(IRecipeLayoutBuilder builder, FluidStack stack, RecipeIngredientRole role) {
        Item bucket = stack.getFluid().getBucket();
        if (bucket != null && bucket != Items.AIR) {
            builder.addInvisibleIngredients(role).add(bucket);
        }
    }
}
