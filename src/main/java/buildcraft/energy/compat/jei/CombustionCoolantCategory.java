/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.compat.jei;

import java.util.List;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.energy.BCEnergyItems;
import buildcraft.lib.gui.BCGraphics;
import buildcraft.lib.misc.LocaleUtil;

/**
 * JEI category for the combustion engine's coolants. Two shapes (see {@link CombustionCoolantJei}):
 * a fluid coolant (water) shows its fluid + cooling rate; a solid coolant (ice family) shows the
 * solid item melting into its produced water amount.
 */
public class CombustionCoolantCategory extends AbstractRecipeCategory<CombustionCoolantJei> {
    private static final int WIDTH = 176, HEIGHT = 58;
    private static final int BUCKET = 1000;
    private static final int TEXT_COLOR = 0xFF404040;

    private static final int IN_X = 8, IN_Y = 4, TANK_W = 16, TANK_H = 40;
    private static final int SOLID_Y = 4;
    private static final int OUT_X = 40, OUT_Y = 4;

    public CombustionCoolantCategory(IGuiHelper guiHelper) {
        super(
                EngineFuelJeiTypes.COMBUSTION_COOLANT,
                Component.translatable("gui.jei.category.buildcraftunofficial.combustion_engine_coolant"),
                guiHelper.createDrawableItemLike(BCEnergyItems.ENGINE_IRON.get()),
                WIDTH, HEIGHT
        );
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CombustionCoolantJei recipe, IFocusGroup focuses) {
        if (recipe.isSolid()) {
            builder.addInputSlot(IN_X, SOLID_Y).addItemStacks(List.of(recipe.item()));
            FluidStack water = recipe.fluid();
            if (water != null && !water.isEmpty()) {
                builder.addOutputSlot(OUT_X, OUT_Y)
                        .setFluidRenderer(water.getAmount(), false, TANK_W, TANK_H)
                        //? if >=1.21.10 {
                        .add(water.getFluid(), water.getAmount(), water.getComponentsPatch());
                        //?} else {
                        /*.addFluidStack(water.getFluid(), water.getAmount(), water.getComponentsPatch());*/
                        //?}
            }
        } else {
            FluidStack fluid = recipe.fluid();
            if (fluid != null && !fluid.isEmpty()) {
                builder.addInputSlot(IN_X, IN_Y)
                        .setFluidRenderer(BUCKET, false, TANK_W, TANK_H)
                        //? if >=1.21.10 {
                        .add(fluid.getFluid(), BUCKET, fluid.getComponentsPatch());
                        //?} else {
                        /*.addFluidStack(fluid.getFluid(), BUCKET, fluid.getComponentsPatch());*/
                        //?}
            }
        }
    }

    @Override
    //? if >=26.1 {
    public void draw(CombustionCoolantJei recipe, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                     double mouseX, double mouseY) {
    //?} else {
    /*public void draw(CombustionCoolantJei recipe, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphics graphics,
                     double mouseX, double mouseY) {*/
    //?}
        Font font = Minecraft.getInstance().font;
        BCGraphics g = new BCGraphics(graphics);
        String line;
        if (recipe.isSolid()) {
            line = Component.translatable(
                    "gui.jei.category.buildcraftunofficial.combustion_engine_coolant.melts",
                    recipe.fluid().getAmount()).getString();
        } else {
            line = Component.translatable(
                    "gui.jei.category.buildcraftunofficial.combustion_engine_coolant.cooling",
                    LocaleUtil.formatDouble(recipe.coolingPerMb(), 4)).getString();
        }
        g.text(font, line, IN_X, 48, TEXT_COLOR, false);
    }
}
