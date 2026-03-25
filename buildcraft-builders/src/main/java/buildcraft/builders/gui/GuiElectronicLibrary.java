/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.gui;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.builders.container.ContainerElectronicLibrary;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.Snapshot;

public class GuiElectronicLibrary extends AbstractContainerScreen<ContainerElectronicLibrary> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftbuilders:textures/gui/electronic_library.png");
    private static final int SIZE_X = 244, SIZE_Y = 220;

    public GuiElectronicLibrary(ContainerElectronicLibrary container, Inventory playerInv, Component title) {
        super(container, playerInv, title);
        this.imageWidth = SIZE_X;
        this.imageHeight = SIZE_Y;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphicsExtractor GuiGraphicsExtractor, float partialTick, int mouseX, int mouseY) {
        GuiGraphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                leftPos, topPos,
                0f, 0f,
                imageWidth, imageHeight,
                256, 256);

        // Draw snapshot list
        GlobalSavedDataSnapshots snapshots = GlobalSavedDataSnapshots.get(
                GlobalSavedDataSnapshots.Side.CLIENT);
        List<Snapshot.Key> list = snapshots.getList();
        int listX = leftPos + 8;
        int listY = topPos + 22;
        Snapshot.Key selected = menu.tile != null ? menu.tile.selected : null;
        for (int i = 0; i < list.size() && i < 12; i++) {
            Snapshot.Key key = list.get(i);
            boolean isSelected = key.equals(selected);
            if (isSelected) {
                GuiGraphicsExtractor.fill(listX, listY, listX + 154, listY + 8, 0xFF_55_55_55);
            }
            int colour = isSelected ? 0xFF_FF_FA_A0 : 0xFF_E0_E0_E0;
            Snapshot.Header header = key.header;
            String text = header == null ? key.toString() : header.name;
            GuiGraphicsExtractor.text(font, text, listX, listY, colour, false);
            listY += 8;
        }

        // Draw progress bars
        int progressDown = menu.getSyncedProgressDown();
        if (progressDown >= 0) {
            int barWidth = (int) (22 * (progressDown / 50.0f));
            GuiGraphicsExtractor.fill(leftPos + 194, topPos + 58,
                    leftPos + 194 + barWidth, topPos + 58 + 16, 0xFF_40_80_FF);
        }
        int progressUp = menu.getSyncedProgressUp();
        if (progressUp >= 0) {
            int barWidth = (int) (22 * (progressUp / 50.0f));
            GuiGraphicsExtractor.fill(leftPos + 194, topPos + 79,
                    leftPos + 194 + barWidth, topPos + 79 + 16, 0xFF_40_FF_80);
        }
    }

    @Override
    public void render(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        super.render(GuiGraphicsExtractor, mouseX, mouseY, partialTick);
        renderTooltip(GuiGraphicsExtractor, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        // Check if clicked on a snapshot in the list
        GlobalSavedDataSnapshots snapshots = GlobalSavedDataSnapshots.get(
                GlobalSavedDataSnapshots.Side.CLIENT);
        List<Snapshot.Key> list = snapshots.getList();
        int listX = leftPos + 8;
        int listY = topPos + 22;
        for (int i = 0; i < list.size() && i < 12; i++) {
            if (mouseX >= listX && mouseX < listX + 154
                    && mouseY >= listY && mouseY < listY + 8) {
                menu.sendSelectedToServer(list.get(i));
                return true;
            }
            listY += 8;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
