/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.pos.IGuiArea;

/**
 * Base screen class for all BuildCraft GUIs.
 * Extends AbstractContainerScreen and delegates element rendering/input to {@link BuildCraftGui}.
 */
public abstract class GuiBC8<C extends ContainerBC_Neptune> extends AbstractContainerScreen<C> {

    public final BuildCraftGui mainGui;

    protected GuiBC8(C container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title);
        IGuiArea rootArea = BuildCraftGui.createWindowedArea(this);
        this.mainGui = new BuildCraftGui(this, rootArea);
    }

    protected GuiBC8(C container, Inventory playerInventory, Component title, int xSize, int ySize) {
        super(container, playerInventory, title);
        this.imageWidth = xSize;
        this.imageHeight = ySize;
        IGuiArea rootArea = BuildCraftGui.createWindowedArea(this);
        this.mainGui = new BuildCraftGui(this, rootArea);
    }

    /** Subclasses should add their elements to mainGui.shownElements here. Called from init(). */
    protected abstract void initGuiElements();

    @Override
    protected void init() {
        super.init();
        mainGui.shownElements.clear();
        initGuiElements();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        mainGui.tick();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        GuiIcon.setGuiGraphics(graphics);
        mainGui.drawBackgroundLayer(partialTicks, mouseX, mouseY, () -> {
            drawBackgroundTexture(graphics);
        });
        mainGui.drawElementBackgrounds();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        GuiIcon.setGuiGraphics(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
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
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Default: let subclasses override to draw custom labels.
        // If not overridden, vanilla draws this.title and playerInventoryTitle.
    }

    /** Draw the background texture. Override this to blit your GUI background. */
    protected void drawBackgroundTexture(GuiGraphics graphics) {
        // Default: do nothing. Subclasses blit their texture.
    }
}
