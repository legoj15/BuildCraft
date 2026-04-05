/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.pos.IGuiArea;

/**
 * Base screen class for all BuildCraft GUIs.
 * Extends AbstractContainerScreen and delegates element rendering/input to {@link BuildCraftGui}.
 *
 * MC 26.1: render → extractRenderState, renderBg → extractBackground, renderLabels → extractLabels.
 * imageWidth/imageHeight are now final — pass through super constructor.
 */
public abstract class GuiBC8<C extends ContainerBC_Neptune> extends AbstractContainerScreen<C> {

    public final BuildCraftGui mainGui;

    protected GuiBC8(C container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title);
        IGuiArea rootArea = BuildCraftGui.createWindowedArea(this);
        this.mainGui = new BuildCraftGui(this, rootArea);
    }

    protected GuiBC8(C container, Inventory playerInventory, Component title, int xSize, int ySize) {
        super(container, playerInventory, title, xSize, ySize);
        IGuiArea rootArea = BuildCraftGui.createWindowedArea(this);
        this.mainGui = new BuildCraftGui(this, rootArea);
    }

    /** Subclasses should add their elements to mainGui.shownElements here. Called from init(). */
    protected abstract void initGuiElements();

    @Override
    protected void init() {
        super.init();

        // Save old ledger instances before re-creating elements.
        java.util.Map<String, buildcraft.lib.gui.ledger.Ledger_Neptune> oldLedgers = new java.util.LinkedHashMap<>();
        for (IGuiElement elem : mainGui.shownElements) {
            if (elem instanceof buildcraft.lib.gui.ledger.Ledger_Neptune ledger) {
                oldLedgers.put(elem.getClass().getName(), ledger);
            }
        }

        IGuiArea rootArea = BuildCraftGui.createWindowedArea(this);
        mainGui.lowerLeftLedgerPos = rootArea.offset(0, 5);
        mainGui.lowerRightLedgerPos = rootArea.getPosition(1, -1).offset(0, 5);
        mainGui.shownElements.clear();
        initGuiElements();

        // Restore full animation state from old ledgers to new ones
        if (!oldLedgers.isEmpty()) {
            for (IGuiElement elem : mainGui.shownElements) {
                if (elem instanceof buildcraft.lib.gui.ledger.Ledger_Neptune ledger) {
                    var oldLedger = oldLedgers.get(elem.getClass().getName());
                    if (oldLedger != null) {
                        ledger.copyAnimationStateFrom(oldLedger);
                    }
                }
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        mainGui.tick();
    }

    /** MC 26.1: extractBackground replaces renderBg. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        GuiIcon.setGuiGraphics(graphics);
        mainGui.drawBackgroundLayer(partialTicks, mouseX, mouseY, () -> {
            drawBackgroundTexture(graphics);
        });
        mainGui.drawElementBackgrounds();
    }

    /** MC 26.1: extractRenderState replaces render. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        GuiIcon.setGuiGraphics(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int button = event.button();
        if (mainGui.onMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int button = event.button();
        mainGui.onMouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int button = event.button();
        mainGui.onMouseDragged(mouseX, mouseY, button, 0);
        return super.mouseDragged(event, dragX, dragY);
    }

    /** MC 26.1: extractLabels replaces renderLabels. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        GuiIcon.setGuiGraphics(graphics);
        mainGui.preDrawForeground();
        drawForegroundLayer();
        mainGui.drawElementForegrounds(null);
        mainGui.postDrawForeground();
    }

    /** Draw custom foreground labels. Subclasses should override this. */
    protected void drawForegroundLayer() {
    }

    /** Draw the background texture. Override this to blit your GUI background. */
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        // Default: do nothing. Subclasses blit their texture.
    }
}
