/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.jei;

import java.util.List;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import buildcraft.lib.gui.BCGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.mj.MjAPI;

import buildcraft.silicon.BCSiliconItems;

/**
 * Single JEI category for the Assembly Table. Each {@link AssemblyRecipeJei}
 * entry maps 1:1 to one displayed recipe — chipsets, gates, lenses, plugs, and
 * the gate copier each contribute one entry per output, while the entire
 * facade family collapses into one cycling entry to avoid spamming the JEI
 * index with thousands of near-identical variants.
 *
 * <p>The layout mirrors {@code GuiAssemblyTable}: a 3×4 input grid on the left,
 * a power bar (drawn statically in the cropped background, no JEI element),
 * and a single output slot on the right. The MJ cost is rendered as plain text
 * below the panel — assembly recipes vary in cost (500 MJ for a lens, 80,000
 * for a Gold AND Gate) so the number is a load-bearing recipe property the
 * player needs to see.
 */
public class AssemblyTableCategory extends AbstractRecipeCategory<AssemblyRecipeJei> {
    // Crop the GUI to just the input grid + power bar. The 3×4 column of output
    // display slots in the real GUI is omitted because it shows recipe-state
    // overlays at runtime, not crafting outputs — JEI's output slot is its own
    // thing on the right, drawn with a vanilla output-slot background.
    private static final int TEX_U = 3, TEX_V = 27;
    private static final int TEX_W = 89, TEX_H = 86;

    // Slot positions inside the cropped texture. Original GUI inputs start at
    // (8, 36) and the power bar at (86, 36); shift both by (-TEX_U, -TEX_V).
    private static final int SLOT_X = 8 - TEX_U;   // 5
    private static final int SLOT_Y = 36 - TEX_V;  // 9
    private static final int SLOT_PITCH = 18;
    private static final int MAX_INPUT_SLOTS = 12;

    // Output sits to the right of the cropped texture, aligned with the first
    // input row. Uses JEI's standard 26×26 output-slot background drawable.
    private static final int OUTPUT_X = TEX_W + 16;  // 105
    private static final int OUTPUT_Y = SLOT_Y;      // 9

    // Power label sits two pixels below the cropped texture, in the extra
    // strip JEI fills with the recipe-pane background.
    private static final int POWER_X = 4, POWER_Y = TEX_H + 2;
    private static final int POWER_COLOR = 0xFF404040;

    private static final int WIDTH = OUTPUT_X + 24;  // 129
    private static final int HEIGHT = TEX_H + 12;    // 98

    private final IDrawable background;

    public AssemblyTableCategory(IGuiHelper guiHelper) {
        super(
                AssemblyRecipeJeiTypes.ASSEMBLY,
                Component.translatable("gui.jei.category.buildcraftunofficial.assembly_table"),
                guiHelper.createDrawableItemLike(BCSiliconItems.ASSEMBLY_TABLE.get()),
                WIDTH, HEIGHT
        );
        this.background = guiHelper.createDrawable(
                Identifier.parse("buildcraftunofficial:textures/gui/assembly_table.png"),
                TEX_U, TEX_V, TEX_W, TEX_H);
    }

    @Override
    //? if >=26.1 {
    public void draw(AssemblyRecipeJei recipe, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                     double mouseX, double mouseY) {
    //?} else {
    /*public void draw(AssemblyRecipeJei recipe, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphics graphics,
                     double mouseX, double mouseY) {*/
    //?}
        background.draw(graphics);
        // Cast through double so sub-MJ recipes don't render as "0 MJ".
        double mj = recipe.microJoules() / (double) MjAPI.MJ;
        String powerStr = Component.translatable(
                "gui.jei.category.buildcraftunofficial.assembly_table.power",
                buildcraft.lib.misc.LocaleUtil.formatDouble(mj, 1),
                buildcraft.lib.misc.LocaleUtil.mjUnit()).getString();
        Font font = Minecraft.getInstance().font;
        new buildcraft.lib.gui.BCGraphics(graphics).text(font, powerStr, POWER_X, POWER_Y, POWER_COLOR, false);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AssemblyRecipeJei recipe, IFocusGroup focuses) {
        List<List<ItemStack>> inputs = recipe.inputSlots();
        int inputCount = Math.min(inputs.size(), MAX_INPUT_SLOTS);
        IRecipeSlotBuilder[] inputSlotBuilders = new IRecipeSlotBuilder[inputCount];
        for (int i = 0; i < inputCount; i++) {
            List<ItemStack> slot = inputs.get(i);
            if (slot.isEmpty()) continue;
            int col = i % 3;
            int row = i / 3;
            int x = SLOT_X + col * SLOT_PITCH;
            int y = SLOT_Y + row * SLOT_PITCH;
            inputSlotBuilders[i] = builder.addInputSlot(x, y).addItemStacks(slot);
        }

        IRecipeSlotBuilder outputSlotBuilder = null;
        if (!recipe.outputs().isEmpty()) {
            outputSlotBuilder = builder.addOutputSlot(OUTPUT_X, OUTPUT_Y)
                    .setOutputSlotBackground()
                    .addItemStacks(recipe.outputs());
        }

        // Focus-link the cycling block slot to the output slot for the facade
        // recipes: without this, a focus on a specific block (e.g. JEI "see
        // uses" on leaves) narrows only the input slot, leaving the output to
        // cycle through every facade and pair leaves with the wrong variant.
        int linkIdx = recipe.focusLinkInputIndex();
        if (linkIdx >= 0 && linkIdx < inputCount
                && inputSlotBuilders[linkIdx] != null
                && outputSlotBuilder != null) {
            builder.createFocusLink(inputSlotBuilders[linkIdx], outputSlotBuilder);
        }
    }
}
