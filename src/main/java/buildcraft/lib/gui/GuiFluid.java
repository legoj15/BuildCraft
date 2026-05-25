/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.lib.misc.FluidUtilBC;

/**
 * An {@link ISimpleDrawable} that renders a {@link FluidStack} as a 16x16 fluid icon —
 * uses the fluid's still-texture from the block atlas and tints it with the fluid's
 * dynamic colour, mirroring how {@link GuiStack} renders an item icon.
 *
 * <p>Like {@link GuiStack}/{@link GuiIcon}/{@link buildcraft.lib.client.guide.font.MinecraftFont},
 * this draws via a static {@link GuiGraphicsExtractor} context that the surrounding
 * Screen pushes once per frame.
 */
@SuppressWarnings("deprecation")
public class GuiFluid implements ISimpleDrawable {
    private final FluidStack stack;

    /** The GuiGraphicsExtractor context — set by the surrounding Screen each frame. */
    private static GuiGraphicsExtractor currentGraphics;

    /** Set the GuiGraphicsExtractor context for all GuiFluid rendering. */
    public static void setGuiGraphics(GuiGraphicsExtractor graphics) {
        currentGraphics = graphics;
    }

    public GuiFluid(FluidStack stack) {
        this.stack = stack;
    }

    public FluidStack getStack() {
        return stack;
    }

    @Override
    public void drawAt(double x, double y) {
        if (currentGraphics == null || stack == null || stack.isEmpty()) {
            return;
        }
        Identifier stillTexture = FluidUtilBC.getFluidTexture(stack);
        if (stillTexture == null) return;
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
            .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(stillTexture);
        if (sprite == null) return;
        int color = FluidUtilBC.getFluidColor(stack);
        currentGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, (int) x, (int) y, 16, 16, color);
    }
}
