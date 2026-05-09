/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.elem;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.IInteractionElement;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.gui.widget.WidgetFluidTank;

/**
 * A GUI element that renders a fluid tank (fill level + overlay) and handles
 * click interaction via the associated {@link WidgetFluidTank} widget.
 */
public class GuiElementFluidTank implements IInteractionElement {

    private final BuildCraftGui gui;
    private final IGuiArea area;
    private final FluidTank tank;
    private final WidgetFluidTank widget;
    private final GuiIcon overlay;

    public GuiElementFluidTank(BuildCraftGui gui, IGuiArea area,
            FluidTank tank,
            WidgetFluidTank widget,
            GuiIcon overlay) {
        this.gui = gui;
        this.area = area;
        this.tank = tank;
        this.widget = widget;
        this.overlay = overlay;
    }

    // --- IGuiArea delegation ---

    @Override
    public double getX() { return area.getX(); }

    @Override
    public double getY() { return area.getY(); }

    @Override
    public double getWidth() { return area.getWidth(); }

    @Override
    public double getHeight() { return area.getHeight(); }

    // --- Rendering ---

    @Override
    public void drawBackground(float partialTicks) {
        if (tank == null) return;

        FluidStack fluid = tank.getFluid();
        if (!fluid.isEmpty() && tank.getCapacity() > 0) {
            GuiGraphics graphics = GuiIcon.getGuiGraphics();
            if (graphics != null) {
                drawFluid(graphics, fluid, tank.getFluidAmount(), tank.getCapacity());
            }
        }

        // Draw overlay (gauge marks)
        if (overlay != null) {
            overlay.drawAt(area);
        }
    }

    private void drawFluid(GuiGraphics graphics, FluidStack fluid, int amount, int capacity) {
        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation stillTexture = fluidExt.getStillTexture(fluid);
        if (stillTexture == null) return;

        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(stillTexture);

        int tintColor = fluidExt.getTintColor(fluid);

        int x = (int) area.getX();
        int y = (int) area.getY();
        int w = (int) area.getWidth();
        int h = (int) area.getHeight();

        // Fill height proportional to amount
        int fillHeight = (int) ((float) amount / capacity * h);
        if (fillHeight <= 0 && amount > 0) fillHeight = 1;

        int fillY = y + h - fillHeight;

        // Compute atlas dimensions from sprite UVs
        int spriteSize = 16;
        float uMin = sprite.getU0();
        float vMin = sprite.getV0();
        float uMax = sprite.getU1();
        float vMax = sprite.getV1();
        int atlasWidth = (int) (spriteSize / (uMax - uMin));
        int atlasHeight = (int) (spriteSize / (vMax - vMin));

        // Enable scissor to clip to the filled area
        graphics.enableScissor(x, fillY, x + w, fillY + fillHeight);

        // Tile the 16x16 sprite to fill the rectangle
        for (int tileY = fillY; tileY < fillY + fillHeight; tileY += spriteSize) {
            for (int tileX = x; tileX < x + w; tileX += spriteSize) {
                int drawW = Math.min(spriteSize, x + w - tileX);
                int drawH = Math.min(spriteSize, fillY + fillHeight - tileY);
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    TextureAtlas.LOCATION_BLOCKS,
                    tileX, tileY,
                    sprite.getU0() * atlasWidth, sprite.getV0() * atlasHeight,
                    drawW, drawH,
                    atlasWidth, atlasHeight,
                    tintColor
                );
            }
        }

        graphics.disableScissor();
    }

    // --- Tooltips ---

    @Override
    public void addToolTips(List<ToolTip> tooltips) {
        if (tank == null) return;
        if (!contains(gui.mouse.getX(), gui.mouse.getY())) return;

        FluidStack fluid = tank.getFluid();
        String text;
        if (fluid.isEmpty()) {
            text = "Empty";
        } else {
            text = fluid.getHoverName().getString() + ": " +
                    fluid.getAmount() + " / " + tank.getCapacity() + " mB";
        }
        tooltips.add(new ToolTip(text));
    }

    // --- Interaction ---

    @Override
    public void onMouseClicked(int button) {
        if (widget != null && contains(gui.mouse.getX(), gui.mouse.getY())) {
            widget.sendClick();
        }
    }
}
