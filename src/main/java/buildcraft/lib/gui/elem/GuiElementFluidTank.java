/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.elem;

import java.util.List;

import net.minecraft.client.Minecraft;
import buildcraft.lib.gui.BCGraphics;
//? if >=1.21.10 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.lib.fluid.BCFluidTank;
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
    private final BCFluidTank tank;
    private final WidgetFluidTank widget;
    private final GuiIcon overlay;

    public GuiElementFluidTank(BuildCraftGui gui, IGuiArea area,
            BCFluidTank tank,
            WidgetFluidTank widget,
            GuiIcon overlay) {
        this.gui = gui;
        this.area = area;
        this.tank = tank;
        this.widget = widget;
        this.overlay = overlay;
    }

    /** Exposed so the JEI handler can read the current contents for U/R recipe lookups. */
    public BCFluidTank getTank() {
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

        FluidStack fluid = tank.getFluidStack(0);
        long capacity = tank.getCapacityMb(0);
        long amount = tank.getAmountMb(0);
        if (!fluid.isEmpty() && capacity > 0 && amount > 0) {
            BCGraphics graphics = GuiIcon.getGuiGraphics();
            if (graphics != null) {
                drawFluid(graphics, fluid, (int) amount, (int) capacity);
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
                //? if >=1.21.10 {
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    TextureAtlas.LOCATION_BLOCKS,
                    tileX, tileY,
                    sprite.getU0() * atlasWidth, sprite.getV0() * atlasHeight,
                    drawW, drawH,
                    atlasWidth, atlasHeight,
                    tintColor
                );
                //?} else {
                /*// regionWidth/regionHeight (drawW, drawH) are REQUIRED: without them the call binds to
                // the blit(...,int textureSize) overload and tintColor is silently consumed as the texture
                // size — garbage UVs + no tint (water rendered invisible, BC fluids dark grey). With them
                // it binds to the (…,int color) region overload: sample drawW×drawH px from the sprite and
                // tint by the fluid colour. Matches the modern path above.
                graphics.blit(
                    TextureAtlas.LOCATION_BLOCKS,
                    tileX, tileY,
                    sprite.getU0() * atlasWidth, sprite.getV0() * atlasHeight,
                    drawW, drawH,
                    drawW, drawH,
                    atlasWidth, atlasHeight,
                    tintColor
                );*/
                //?}
            }
        }

        graphics.disableScissor();
    }

    // --- Tooltips ---

    /**
     * Builds the standard BC tank tooltip lines. For a tank with fluid: line 1 is the fluid's name and
     * line 2 is "&lt;amount&gt; / &lt;capacity&gt; &lt;unit&gt;" in grey, both sides sharing one unit. For
     * an empty tank: a single "Empty &lt;capacity&gt; &lt;unit&gt; Tank" line — no "0 / &lt;capacity&gt;"
     * line, since that line already states the capacity — with the unit kept SINGULAR ("Empty 4 bucket
     * Tank") because it modifies the noun "Tank". The unit follows {@code useFullUnitNames} (mB /
     * millibuckets); when {@code abbreviateLargeNumbers} is on a bucket-scale tank (capacity ≥ 1000 mB)
     * shows the whole readout in buckets — including a sub-bucket amount ("0.2 / 4 buckets"), never a mixed
     * "174 mB / 4 buckets". Shared by the per-screen tank-tooltip renderers and {@link #addToolTips}.
     */
    public static List<net.minecraft.network.chat.Component> buildTankTooltip(FluidStack fluid, int amount, int capacity) {
        List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        if (amount > 0 && fluid != null && !fluid.isEmpty()) {
            lines.add(fluid.getHoverName());
            lines.add(net.minecraft.network.chat.Component
                    .literal(buildcraft.lib.misc.LocaleUtil.localizeFluidTank(amount, capacity))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            // Empty: the "Empty <capacity> Tank" line already states the capacity, so there's no separate
            // "0 / <capacity>" line to add.
            lines.add(net.minecraft.network.chat.Component.translatable("buildcraft.tank.empty",
                    buildcraft.lib.misc.LocaleUtil.localizeFluidCapacity(capacity)));
        }
        return lines;
    }

    @Override
    public void addToolTips(List<ToolTip> tooltips) {
        if (tank == null) return;
        if (!contains(gui.mouse.getX(), gui.mouse.getY())) return;

        FluidStack fluid = tank.getFluidStack(0);
        int capacity = tank.getCapacityMb(0);
        int amount = tank.getAmountMb(0);
        // Mirrors buildTankTooltip: a full tank shows its name + "x / cap" in grey; an empty tank shows
        // only "Empty <capacity> Tank" (no separate 0/x line). Amounts abbreviate to buckets at ≥1000 mB
        // when the setting is on, and the unit follows useFullUnitNames.
        if (fluid.isEmpty() || amount == 0) {
            tooltips.add(new ToolTip(net.minecraft.network.chat.Component
                    .translatable("buildcraft.tank.empty", buildcraft.lib.misc.LocaleUtil.localizeFluidCapacity(capacity))
                    .getString()));
        } else {
            tooltips.add(new ToolTip(
                fluid.getHoverName().getString(),
                net.minecraft.ChatFormatting.GRAY + buildcraft.lib.misc.LocaleUtil.localizeFluidTank(amount, capacity)
            ));
        }
    }

    // --- Interaction ---

    @Override
    public void onMouseClicked(int button) {
        if (widget != null && contains(gui.mouse.getX(), gui.mouse.getY())) {
            widget.sendClick();
        }
    }
}
