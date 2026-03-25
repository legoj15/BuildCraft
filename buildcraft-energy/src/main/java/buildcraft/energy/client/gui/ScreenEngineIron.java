/* Copyright (c) 2017 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.client.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

import buildcraft.energy.container.ContainerEngineIron;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.ledger.LedgerEngine;
import buildcraft.lib.gui.ledger.LedgerHelp;
import buildcraft.lib.gui.ledger.LedgerOwnership;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.misc.LocaleUtil;

/**
 * Screen (GUI) for the combustion engine. Displays 3 fluid tanks
 * (fuel, coolant, residue) with actual fluid textures, glass overlays, and tooltips.
 */
public class ScreenEngineIron extends GuiBC8<ContainerEngineIron> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftenergy:textures/gui/combustion_engine_gui.png");
    private static final int SIZE_X = 176, SIZE_Y = 177;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_TANK_OVERLAY = new GuiIcon(TEXTURE, 176, 0, 16, 60);

    // Tank positions relative to the GUI top-left (matching 1.12.2)
    private static final int TANK_WIDTH = 16, TANK_HEIGHT = 60;
    private static final int TANK_FUEL_X = 26, TANK_FUEL_Y = 18;
    private static final int TANK_COOLANT_X = 80, TANK_COOLANT_Y = 18;
    private static final int TANK_RESIDUE_X = 134, TANK_RESIDUE_Y = 18;

    public ScreenEngineIron(ContainerEngineIron menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        if (menu.engine != null) {
            // Ownership ledger on the right side (on top, matching 1.12.2 standardLedgerInit order)
            mainGui.shownElements.add(new LedgerOwnership(mainGui,
                () -> menu.engine != null ? menu.engine.getOwner() : null,
                true
            ));

            // Power ledger on the right side (below ownership)
            mainGui.shownElements.add(new LedgerEngine(mainGui,
                menu::getSyncedCurrentOutput,
                menu::getSyncedPower,
                menu::getSyncedHeat,
                menu::getSyncedPowerStage,
                menu::isSyncedBurning,
                true
            ));

            // Help ledger on the left side (interactive — highlights tanks on hover)
            mainGui.shownElements.add(new LedgerHelp(mainGui, false));

            // Register help elements for each tank so the ledger can highlight them
            mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(TANK_FUEL_X, TANK_FUEL_Y, TANK_WIDTH, TANK_HEIGHT).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.tank.title.tankFuel", 0xFF_FF_33_33,
                    "buildcraft.help.tank.generic", "buildcraft.help.tank.fuel")
            ));
            mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(TANK_COOLANT_X, TANK_COOLANT_Y, TANK_WIDTH, TANK_HEIGHT).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.tank.title.tankCoolant", 0xFF_55_55_FF,
                    "buildcraft.help.tank.generic", "buildcraft.help.tank.coolant")
            ));
            mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(TANK_RESIDUE_X, TANK_RESIDUE_Y, TANK_WIDTH, TANK_HEIGHT).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.tank.title.tankResidue", 0xFF_AA_33_AA,
                    "buildcraft.help.tank.generic", "buildcraft.help.tank.residue")
            ));
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);

        // Draw fluid tank textures with glass overlay
        drawFluidTank(graphics, TANK_FUEL_X, TANK_FUEL_Y,
            menu.getSyncedFuelFluid(), menu.getSyncedFuelAmount(), TileEngineIron_BC8.MAX_FLUID);
        drawFluidTank(graphics, TANK_COOLANT_X, TANK_COOLANT_Y,
            menu.getSyncedCoolantFluid(), menu.getSyncedCoolantAmount(), TileEngineIron_BC8.MAX_FLUID);
        drawFluidTank(graphics, TANK_RESIDUE_X, TANK_RESIDUE_Y,
            menu.getSyncedResidueFluid(), menu.getSyncedResidueAmount(), TileEngineIron_BC8.MAX_FLUID);
    }

    /**
     * Draw a fluid tank with the actual fluid texture, filled proportionally,
     * then draw the glass overlay on top.
     */
    private void drawFluidTank(GuiGraphicsExtractor graphics, int x, int y, Fluid fluid, int amount, int maxAmount) {
        int drawX = (int) mainGui.rootElement.getX() + x;
        int drawY = (int) mainGui.rootElement.getY() + y;

        // Draw fluid texture if there's any fluid
        if (amount > 0 && maxAmount > 0 && fluid != null && fluid != Fluids.EMPTY) {
            int fillHeight = (int) ((float) amount / maxAmount * TANK_HEIGHT);
            if (fillHeight > 0) {
                drawFluidTexture(graphics, drawX, drawY + (TANK_HEIGHT - fillHeight),
                    TANK_WIDTH, fillHeight, fluid);
            }
        }

        // Draw glass overlay on top of the fluid
        ICON_TANK_OVERLAY.drawAt(drawX, drawY);
    }

    /**
     * Render the fluid's still texture tiled into the given rectangle.
     * Uses NeoForge's IClientFluidTypeExtensions for the texture location
     * and tint color (fluid textures are grayscale; color comes from tinting).
     */
    private void drawFluidTexture(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Fluid fluid) {
        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(fluid);
        Identifier stillTexture = fluidExt.getStillTexture();
        if (stillTexture == null) return;

        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
            .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(stillTexture);

        // Get the fluid's tint color (ARGB packed int)
        int tintColor = fluidExt.getTintColor();

        // Enable scissor to clip to the tank area
        graphics.enableScissor(x, y, x + width, y + height);

        // Tile the 16x16 sprite to fill the rectangle
        int spriteSize = 16;
        float uMin = sprite.getU0();
        float vMin = sprite.getV0();
        float uMax = sprite.getU1();
        float vMax = sprite.getV1();
        int atlasWidth = (int) (spriteSize / (uMax - uMin));
        int atlasHeight = (int) (spriteSize / (vMax - vMin));

        for (int tileY = y; tileY < y + height; tileY += spriteSize) {
            for (int tileX = x; tileX < x + width; tileX += spriteSize) {
                int drawW = Math.min(spriteSize, x + width - tileX);
                int drawH = Math.min(spriteSize, y + height - tileY);
                // Use the blit overload with a color parameter for tinting
                graphics.blit(
                    net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
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

    @Override
    protected void renderLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String str = LocaleUtil.localize("tile.engineIron.name");
        int strWidth = font.width(str);
        int titleX = (imageWidth - strWidth) / 2;
        graphics.text(font, str, titleX, 6, 0xFF404040, false);
        graphics.text(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        GuiIcon.setGuiGraphics(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);

        // Draw tank tooltips
        renderTankTooltip(graphics, mouseX, mouseY, TANK_FUEL_X, TANK_FUEL_Y,
            menu.getSyncedFuelFluid(), menu.getSyncedFuelAmount(), TileEngineIron_BC8.MAX_FLUID);
        renderTankTooltip(graphics, mouseX, mouseY, TANK_COOLANT_X, TANK_COOLANT_Y,
            menu.getSyncedCoolantFluid(), menu.getSyncedCoolantAmount(), TileEngineIron_BC8.MAX_FLUID);
        renderTankTooltip(graphics, mouseX, mouseY, TANK_RESIDUE_X, TANK_RESIDUE_Y,
            menu.getSyncedResidueFluid(), menu.getSyncedResidueAmount(), TileEngineIron_BC8.MAX_FLUID);
    }

    /**
     * Show a tooltip when hovering over a tank area.
     * Line 1: Fluid name (if tank has fluid)
     * Line 2: "X / Y mB" in gray
     */
    private void renderTankTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                    int tankX, int tankY, Fluid fluid, int amount, int maxAmount) {
        int absX = leftPos + tankX;
        int absY = topPos + tankY;
        if (mouseX >= absX && mouseX < absX + TANK_WIDTH
            && mouseY >= absY && mouseY < absY + TANK_HEIGHT) {

            List<Component> lines = new java.util.ArrayList<>();
            if (fluid != null && fluid != Fluids.EMPTY && amount > 0) {
                lines.add(fluid.getFluidType().getDescription());
            }
            lines.add(Component.literal(amount + " / " + maxAmount + " mB")
                .withStyle(net.minecraft.ChatFormatting.GRAY));

            // Convert Component list to ClientTooltipComponent list for 1.21.11 API
            List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> tooltipLines =
                lines.stream()
                    .map(c -> net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                        .create(c.getVisualOrderText()))
                    .collect(java.util.stream.Collectors.toList());

            graphics.setTooltipForNextFrame(font, tooltipLines, mouseX, mouseY,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                null);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        // Check if click is within any of the 3 tank areas
        if (isTankClicked(mouseX, mouseY, TANK_FUEL_X, TANK_FUEL_Y)) {
            menu.widgetFuel.sendClick();
            return true;
        }
        if (isTankClicked(mouseX, mouseY, TANK_COOLANT_X, TANK_COOLANT_Y)) {
            menu.widgetCoolant.sendClick();
            return true;
        }
        if (isTankClicked(mouseX, mouseY, TANK_RESIDUE_X, TANK_RESIDUE_Y)) {
            menu.widgetResidue.sendClick();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean isTankClicked(double mouseX, double mouseY, int tankX, int tankY) {
        int absX = leftPos + tankX;
        int absY = topPos + tankY;
        return mouseX >= absX && mouseX < absX + TANK_WIDTH
            && mouseY >= absY && mouseY < absY + TANK_HEIGHT;
    }
}
