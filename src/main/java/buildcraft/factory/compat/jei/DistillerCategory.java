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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import buildcraft.lib.gui.BCGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.factory.BCFactoryItems;
import buildcraft.lib.compat.jei.FluidContainerAliases;

/**
 * Single JEI category for the distiller. Each registered
 * {@link IDistillationRecipe} maps 1:1 to one entry — unlike the heat
 * exchanger, no pairing is needed because the recipe interface already carries
 * the full operation (one input fluid, one gas output, one liquid output, plus
 * a per-cycle MJ cost).
 *
 * <p>The layout mirrors {@code GuiDistiller}: input on the left, gas top-right,
 * liquid bottom-right. The MJ cost is rendered as plain text below the tanks
 * because it's a real per-recipe property the runtime enforces — two recipes
 * with identical fluids but different power costs would otherwise be
 * indistinguishable in the JEI view.
 *
 * <p>All three slots are real INPUT/OUTPUT slots so JEI lookups behave the way
 * players expect — pressing U on an input fluid surfaces every recipe that
 * consumes it; pressing R on an output fluid surfaces every recipe that
 * produces it on either side.
 */
public class DistillerCategory extends AbstractRecipeCategory<IDistillationRecipe> {
    // The GUI's outer border and player-inventory section are visual noise in
    // a JEI category, so we crop the background to the inner machine panel:
    // a 170x74 region starting at (3, 4) of distiller.png. Slot positions are
    // shifted by the same (-3, -4) offset so they still land on the tank
    // graphics inside the cropped texture.
    private static final int TEX_U = 3, TEX_V = 4;
    private static final int TEX_W = 170, TEX_H = 74;

    private static final int WIDTH = TEX_W;
    // Extra strip below the cropped texture for the per-recipe power label.
    private static final int HEIGHT = TEX_H + 12;

    // Tank coordinates from GuiDistiller, shifted by (-TEX_U, -TEX_V) to match
    // the cropped texture's origin.
    private static final int TANK_IN_X = 44 - TEX_U, TANK_IN_Y = 23 - TEX_V, TANK_IN_W = 16, TANK_IN_H = 38;
    private static final int TANK_GAS_X = 98 - TEX_U, TANK_GAS_Y = 10 - TEX_V, TANK_GAS_W = 34, TANK_GAS_H = 17;
    private static final int TANK_LIQ_X = 98 - TEX_U, TANK_LIQ_Y = 54 - TEX_V, TANK_LIQ_W = 34, TANK_LIQ_H = 17;

    // Power label sits two pixels below the cropped texture, in the extra
    // strip JEI fills with the recipe-pane background.
    private static final int POWER_X = 4, POWER_Y = TEX_H + 2;
    private static final int POWER_COLOR = 0xFF404040;

    private final IDrawable background;

    public DistillerCategory(IGuiHelper guiHelper) {
        super(
                DistillerRecipeTypes.DISTILLER,
                Component.translatable("gui.jei.category.buildcraftunofficial.distiller"),
                guiHelper.createDrawableItemLike(BCFactoryItems.DISTILLER.get()),
                WIDTH, HEIGHT
        );
        this.background = guiHelper.createDrawable(
                Identifier.parse("buildcraftunofficial:textures/gui/distiller.png"),
                TEX_U, TEX_V, TEX_W, TEX_H);
    }

    @Override
    public void draw(IDistillationRecipe recipe, IRecipeSlotsView slots, BCGraphics graphics,
                     double mouseX, double mouseY) {
        background.draw(graphics);
        // Cast through double so sub-MJ recipes don't render as "0 MJ".
        double mj = recipe.powerRequired() / (double) MjAPI.MJ;
        String powerStr = Component.translatable(
                "gui.jei.category.buildcraftunofficial.distiller.power",
                buildcraft.lib.misc.LocaleUtil.formatDouble(mj, 1),
                buildcraft.lib.misc.LocaleUtil.mjUnit()).getString();
        Font font = Minecraft.getInstance().font;
        graphics.text(font, powerStr, POWER_X, POWER_Y, POWER_COLOR, false);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, IDistillationRecipe recipe, IFocusGroup focuses) {
        FluidStack in = recipe.in();
        if (!in.isEmpty()) {
            builder.addInputSlot(TANK_IN_X, TANK_IN_Y)
                    .setFluidRenderer(in.getAmount(), false, TANK_IN_W, TANK_IN_H)
                    .add(in.getFluid(), in.getAmount(), in.getComponentsPatch());
            FluidContainerAliases.addAliases(builder, in, RecipeIngredientRole.INPUT);
        }
        FluidStack outGas = recipe.outGas();
        if (outGas != null && !outGas.isEmpty()) {
            builder.addOutputSlot(TANK_GAS_X, TANK_GAS_Y)
                    .setFluidRenderer(outGas.getAmount(), false, TANK_GAS_W, TANK_GAS_H)
                    .add(outGas.getFluid(), outGas.getAmount(), outGas.getComponentsPatch());
            FluidContainerAliases.addAliases(builder, outGas, RecipeIngredientRole.OUTPUT);
        }
        FluidStack outLiquid = recipe.outLiquid();
        if (outLiquid != null && !outLiquid.isEmpty()) {
            builder.addOutputSlot(TANK_LIQ_X, TANK_LIQ_Y)
                    .setFluidRenderer(outLiquid.getAmount(), false, TANK_LIQ_W, TANK_LIQ_H)
                    .add(outLiquid.getFluid(), outLiquid.getAmount(), outLiquid.getComponentsPatch());
            FluidContainerAliases.addAliases(builder, outLiquid, RecipeIngredientRole.OUTPUT);
        }
    }
}
