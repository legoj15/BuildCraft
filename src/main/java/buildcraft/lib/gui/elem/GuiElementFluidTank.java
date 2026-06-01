/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.elem;

import java.util.List;

import net.minecraft.client.Minecraft;
import buildcraft.lib.gui.BCGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

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
@SuppressWarnings("deprecation")
public class GuiElementFluidTank implements IInteractionElement {

    private final BuildCraftGui gui;
    private final IGuiArea area;
    private final net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler tank;
    private final WidgetFluidTank widget;
    private final GuiIcon overlay;

    public GuiElementFluidTank(BuildCraftGui gui, IGuiArea area,
            net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler tank,
            WidgetFluidTank widget,
            GuiIcon overlay) {
        this.gui = gui;
        this.area = area;
        this.tank = tank;
        this.widget = widget;
        this.overlay = overlay;
    }

    /** Exposed so the JEI handler can read the current contents for U/R recipe lookups. */
    public net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler getTank() {
        return tank;
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

        net.neoforged.neoforge.transfer.fluid.FluidResource fluid = tank.getResource(0);
        long capacity = tank.getCapacityAsLong(0, net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY);
        long amount = tank.getAmountAsLong(0);
        if (!fluid.isEmpty() && capacity > 0 && amount > 0) {
            BCGraphics graphics = GuiIcon.getGuiGraphics();
            if (graphics != null) {
                drawFluid(graphics, fluid.toStack((int) amount), (int) amount, (int) capacity);
            }
        }

        // Draw overlay (gauge marks)
        if (overlay != null) {
            overlay.drawAt(area);
        }
    }

    private void drawFluid(BCGraphics graphics, FluidStack fluid, int amount, int capacity) {
        Identifier stillTexture = buildcraft.lib.misc.FluidUtilBC.getFluidTexture(fluid);
        if (stillTexture == null) return;

        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(stillTexture);

        int tintColor = buildcraft.lib.misc.FluidUtilBC.getFluidColor(fluid);

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

        net.neoforged.neoforge.transfer.fluid.FluidResource fluid = tank.getResource(0);
        long capacity = tank.getCapacityAsLong(0, net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY);
        long amount = tank.getAmountAsLong(0);
        // 1.12.2 shape: first line = fluid name (or "Empty"), second line = "x / cap mB" in
        // gray. Section sign §7 = ChatFormatting.GRAY, which the vanilla tooltip renderer picks
        // up on per-line strings.
        String name = fluid.isEmpty() || amount == 0 ? "Empty" : fluid.toStack(1).getHoverName().getString();
        tooltips.add(new ToolTip(
            name,
            net.minecraft.ChatFormatting.GRAY + (amount + " / " + capacity + " mB")
        ));
    }

    // --- Interaction ---

    @Override
    public void onMouseClicked(int button) {
        if (widget != null && contains(gui.mouse.getX(), gui.mouse.getY())) {
            widget.sendClick();
        }
    }
}
