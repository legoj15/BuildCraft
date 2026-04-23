/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.gui;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;

import buildcraft.builders.container.ContainerElectronicLibrary;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.Snapshot;

public class GuiElectronicLibrary extends GuiBC8<ContainerElectronicLibrary> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/electronic_library.png");
    private static final int SIZE_X = 244, SIZE_Y = 220;

    // Snapshot list bounds (GUI-local coordinates)
    private static final int LIST_X = 8;
    private static final int LIST_Y = 22;
    private static final int LIST_W = 154;
    private static final int LIST_ROW_H = 8;
    private static final int LIST_MAX_ROWS = 12;

    // Progress bar positions (GUI-local)
    private static final int BAR_DOWN_X = 194, BAR_DOWN_Y = 58;
    private static final int BAR_UP_X = 194, BAR_UP_Y = 79;
    private static final int BAR_W = 22, BAR_H = 16;

    public GuiElectronicLibrary(ContainerElectronicLibrary container, Inventory playerInv, Component title) {
        super(container, playerInv, title, SIZE_X, SIZE_Y);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void initGuiElements() {
        // No additional GUI elements for now
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                leftPos, topPos,
                0f, 0f,
                imageWidth, imageHeight,
                256, 256);

        // Progress bars (absolute screen coords since drawBackgroundTexture is not translated)
        int progressDown = menu.getSyncedProgressDown();
        if (progressDown >= 0) {
            int w = (int) (BAR_W * (progressDown / 50.0f));
            graphics.fill(leftPos + BAR_DOWN_X, topPos + BAR_DOWN_Y,
                    leftPos + BAR_DOWN_X + w, topPos + BAR_DOWN_Y + BAR_H, 0xFF_40_80_FF);
        }
        int progressUp = menu.getSyncedProgressUp();
        if (progressUp >= 0) {
            int w = (int) (BAR_W * (progressUp / 50.0f));
            graphics.fill(leftPos + BAR_UP_X, topPos + BAR_UP_Y,
                    leftPos + BAR_UP_X + w, topPos + BAR_UP_Y + BAR_H, 0xFF_40_FF_80);
        }
    }

    @Override
    protected void drawForegroundLayer() {
        // Called from extractLabels with the matrix in absolute coordinates
        // (preDrawForeground translates back to screen origin).
        GuiGraphicsExtractor graphics = GuiIcon.getGuiGraphics();
        if (graphics == null) return;

        GlobalSavedDataSnapshots snapshots = GlobalSavedDataSnapshots.get(GlobalSavedDataSnapshots.Side.CLIENT);
        List<Snapshot.Key> list = snapshots.getList();
        Snapshot.Key selected = menu.tile != null ? menu.tile.selected : null;

        int rowY = topPos + LIST_Y;
        for (int i = 0; i < list.size() && i < LIST_MAX_ROWS; i++) {
            Snapshot.Key key = list.get(i);
            boolean isSelected = key.equals(selected);
            if (isSelected) {
                graphics.fill(leftPos + LIST_X, rowY,
                        leftPos + LIST_X + LIST_W, rowY + LIST_ROW_H, 0x80_55_55_55);
            }
            int colour = isSelected ? 0xFF_FF_FA_A0 : 0xFF_E0_E0_E0;
            String text = key.header == null ? key.toString() : key.header.name;
            graphics.text(font, text, leftPos + LIST_X, rowY, colour, false);
            rowY += LIST_ROW_H;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        GlobalSavedDataSnapshots snapshots = GlobalSavedDataSnapshots.get(GlobalSavedDataSnapshots.Side.CLIENT);
        List<Snapshot.Key> list = snapshots.getList();
        int rowY = topPos + LIST_Y;
        for (int i = 0; i < list.size() && i < LIST_MAX_ROWS; i++) {
            if (mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_W
                    && mouseY >= rowY && mouseY < rowY + LIST_ROW_H) {
                Snapshot.Key key = list.get(i);
                menu.sendSelectedToServer(key);
                // Optimistic client-side update for immediate visual feedback
                if (menu.tile != null) {
                    menu.tile.selected = key;
                }
                return true;
            }
            rowY += LIST_ROW_H;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
