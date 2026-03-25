/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.list;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.core.item.ItemList_BC8;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.ledger.LedgerHelp;

public class GuiList extends GuiBC8<ContainerList> {
    private static final Identifier TEXTURE_BASE =
        Identifier.parse("buildcraftcore:textures/gui/list_new.png");
    private static final int SIZE_X = 176, SIZE_Y = 191;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE_BASE, 0, 0, SIZE_X, SIZE_Y);
    private static final GuiIcon ICON_ONE_STACK = new GuiIcon(TEXTURE_BASE, 0, 191, 20, 20);

    // Button sprite UV positions on the texture sheet (from 1.12.2 GuiList)
    // All 3 buttons share the same off-state sprite at (176, 16)
    // Each has its own on-state sprite at y=28, spaced 9px apart in x
    private static final int BTN_U_OFF = 176, BTN_V_OFF = 16;
    private static final int BTN_V_ON = 28;
    private static final int BTN_SIZE = 11;
    private static final int BUTTON_COUNT = 3;

    // Toggle button widgets for each line
    private ToggleImageButton[][] toggleButtons;

    public GuiList(ContainerList menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        // Help ledger on the left side (matching 1.12.2)
        mainGui.shownElements.add(new LedgerHelp(mainGui, false));

        // Label text field — bordered, with setResponder for automatic sync
        EditBox labelField = new EditBox(this.font, leftPos + 10, topPos + 10, 156, 12, Component.empty());
        labelField.setMaxLength(32);
        labelField.setBordered(true);
        // Load existing label
        if (menu.getListItemStack().getItem() instanceof ItemList_BC8 listItem) {
            String name = listItem.getLocationName(menu.getListItemStack());
            if (name != null && !name.isEmpty()) {
                labelField.setValue(name);
            }
        }
        labelField.setResponder(newText -> menu.setLabel(newText));
        addRenderableWidget(labelField);

        // Toggle buttons for each line (precise, type, material)
        toggleButtons = new ToggleImageButton[menu.lines.length][BUTTON_COUNT];
        for (int line = 0; line < menu.lines.length; line++) {
            int bOffX = this.leftPos + 8 + 9 * 18 - BUTTON_COUNT * BTN_SIZE;
            int bOffY = this.topPos + 32 + line * 34 + 18;

            for (int btn = 0; btn < BUTTON_COUNT; btn++) {
                final int lineIdx = line;
                final int btnIdx = btn;
                // On-state U: 176 for precise, 185 for type, 194 for material
                int uOn = BTN_U_OFF + btn * 9;
                String tooltipKey = btn == 0 ? "gui.list.nbt" : (btn == 1 ? "gui.list.metadata" : "gui.list.oredict");

                ToggleImageButton button = new ToggleImageButton(
                    bOffX + btn * BTN_SIZE, bOffY, BTN_SIZE, BTN_SIZE,
                    TEXTURE_BASE,
                    BTN_U_OFF, BTN_V_OFF,  // off-state: same for all buttons
                    uOn, BTN_V_ON,          // on-state: unique per button
                    menu.lines[lineIdx].getOption(btnIdx),
                    Component.translatable(tooltipKey),
                    lineIdx, btnIdx
                );
                toggleButtons[line][btn] = button;
                addRenderableWidget(button);
            }
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);

        // Draw one-stack overlay for lines in type/material mode
        for (int i = 0; i < menu.lines.length; i++) {
            if (menu.lines[i].isOneStackMode()) {
                ICON_ONE_STACK.drawAt(leftPos + 6, topPos + 30 + i * 34);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        GuiIcon.setGuiGraphics(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    // --- Toggle image button widget ---

    private class ToggleImageButton extends AbstractWidget {
        private final Identifier texture;
        private final int uOff, vOff, uOn, vOn;
        private boolean toggled;
        private final int lineIdx, btnIdx;

        ToggleImageButton(int x, int y, int w, int h,
                          Identifier texture, int uOff, int vOff, int uOn, int vOn,
                          boolean initialState, Component tooltip,
                          int lineIdx, int btnIdx) {
            super(x, y, w, h, Component.empty());
            this.texture = texture;
            this.uOff = uOff;
            this.vOff = vOff;
            this.uOn = uOn;
            this.vOn = vOn;
            this.toggled = initialState;
            this.lineIdx = lineIdx;
            this.btnIdx = btnIdx;
            setTooltip(Tooltip.create(tooltip));
        }

        void toggle() {
            toggled = !toggled;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (this.isHovered()) {
                menu.switchButton(lineIdx, btnIdx);
                toggle();
                return true;
            }
            return false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int u = toggled ? uOn : uOff;
            int v = toggled ? vOn : vOff;
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    getX(), getY(), (float) u, (float) v,
                    width, height, 256, 256);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
