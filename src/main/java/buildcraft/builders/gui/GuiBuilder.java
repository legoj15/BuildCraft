/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.elem.GuiElementFluidTank;
import buildcraft.lib.gui.pos.GuiRectangle;

import buildcraft.builders.container.ContainerBuilder;
import buildcraft.builders.tile.TileBuilder;

public class GuiBuilder extends GuiBC8<ContainerBuilder> {
    private static final Identifier TEXTURE_BASE =
            Identifier.parse("buildcraftunofficial:textures/gui/builder.png");
    private static final Identifier TEXTURE_BLUEPRINT =
            Identifier.parse("buildcraftunofficial:textures/gui/builder_blueprint.png");

    private static final int SIZE_X = 176;
    private static final int SIZE_BLUEPRINT_X = 256;
    private static final int SIZE_Y = 222;
    private static final int BLUEPRINT_WIDTH = 87;

    // Matches 1.12.2: overlay is the right-hand 87px of a 256-wide GUI, tank sprite is the
    // 16x47 strip at (0, 54) within builder_blueprint.png.
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_BLUEPRINT_GUI = new GuiIcon(
            TEXTURE_BLUEPRINT,
            SIZE_BLUEPRINT_X - BLUEPRINT_WIDTH,
            0,
            BLUEPRINT_WIDTH,
            SIZE_Y
    );
    private static final GuiIcon ICON_TANK_OVERLAY = new GuiIcon(TEXTURE_BLUEPRINT, 0, 54, 16, 47);

    // Tank rectangles in GUI-local coordinates (1.12.2 layout).
    private static final int TANK_Y = 145;
    private static final int TANK_W = 16;
    private static final int TANK_H = 47;
    private static int tankX(int i) {
        return 179 + i * 18;
    }

    public GuiBuilder(ContainerBuilder container, Inventory playerInv, Component title) {
        super(container, playerInv, title, SIZE_BLUEPRINT_X, SIZE_Y);
        // Player inventory is at y=140 (set in ContainerBuilder). The "Inventory" label goes
        // above it; imageHeight-94 puts it 11px above the first row, matching Minecraft chest.
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void initGuiElements() {
        if (menu.tile == null) return;
        for (int i = 0; i < TileBuilder.TANK_COUNT; i++) {
            final int idx = i;
            mainGui.shownElements.add(new GuiElementFluidTank(
                    mainGui,
                    new GuiRectangle(tankX(idx), TANK_Y, TANK_W, TANK_H).offset(mainGui.rootElement),
                    menu.tile.getTank(idx),
                    idx < menu.widgetTanks.size() ? menu.widgetTanks.get(idx) : null,
                    ICON_TANK_OVERLAY
            ));
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        // Base GUI spans the left 176px; the blueprint overlay fills the right 87px. We always
        // draw both — the overlay carries the tank gauge art and display-slot frames, which
        // should be visible even before a blueprint is loaded so the player can see where
        // they'll appear (1.12.2 drew it unconditionally too).
        ICON_GUI.drawAt(mainGui.rootElement);
        ICON_BLUEPRINT_GUI.drawAt(mainGui.rootElement.offset(SIZE_BLUEPRINT_X - BLUEPRINT_WIDTH, 0));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        // Title — centered over the main section.
        String titleStr = title.getString();
        int titleWidth = font.width(titleStr);
        graphics.text(font, titleStr, (SIZE_X - titleWidth) / 2, 6, 0xFF404040, false);

        // Progress readout — visible only while something is actively being broken/placed.
        int leftToBreak = menu.getSyncedLeftToBreak();
        int leftToPlace = menu.getSyncedLeftToPlace();
        int y = 50;
        if (leftToBreak > 0) {
            graphics.text(font,
                    Component.literal("Break: " + leftToBreak).withStyle(ChatFormatting.DARK_GRAY),
                    10, y, 0xFF404040, false);
            y += 10;
        }
        if (leftToPlace > 0) {
            graphics.text(font,
                    Component.literal("Place: " + leftToPlace).withStyle(ChatFormatting.DARK_GRAY),
                    10, y, 0xFF404040, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        GuiIcon.setGuiGraphics(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }
}
