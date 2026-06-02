/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import buildcraft.lib.gui.BCGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.ledger.LedgerHelp;
import buildcraft.lib.gui.pos.IGuiArea;

/**
 * Base screen class for all BuildCraft GUIs.
 * Extends AbstractContainerScreen and delegates element rendering/input to {@link BuildCraftGui}.
 *
 * MC 26.1: render → extractRenderState, renderBg → extractBackground, renderLabels → extractLabels.
 * imageWidth/imageHeight are now final — pass through super constructor.
 */
@SuppressWarnings("this-escape")
public abstract class GuiBC8<C extends ContainerBC_Neptune> extends AbstractContainerScreen<C> {

    public final BuildCraftGui mainGui;

    protected GuiBC8(C container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title);
        IGuiArea rootArea = BuildCraftGui.createWindowedArea(this);
        this.mainGui = new BuildCraftGui(this, rootArea);
    }

    protected GuiBC8(C container, Inventory playerInventory, Component title, int xSize, int ySize) {
        //? if >=26.1 {
        super(container, playerInventory, title, xSize, ySize);
        //?} else {
        /*super(container, playerInventory, title);
        this.imageWidth = xSize;
        this.imageHeight = ySize;*/
        //?}
        IGuiArea rootArea = BuildCraftGui.createWindowedArea(this);
        this.mainGui = new BuildCraftGui(this, rootArea);
    }

    /** Subclasses should add their elements to mainGui.shownElements here. Called from init(). */
    protected abstract void initGuiElements();

    /** Whether to auto-attach the left-side {@link LedgerHelp} after {@link #initGuiElements()} runs.
     *  Override and return false on screens that host a UI element (e.g. vanilla's recipe book)
     *  that would overlap the left-side ledger. Matches 1.12.2's {@code shouldAddHelpLedger()}. */
    protected boolean shouldAddHelpLedger() {
        return true;
    }

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
        if (shouldAddHelpLedger()) {
            mainGui.shownElements.add(new LedgerHelp(mainGui, false));
        }

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

    /** Background layer. 26.1: extractBackground(GuiGraphicsExtractor); 1.21.11: renderBg(GuiGraphics) — abstract, no super. */
    @Override
    //? if >=26.1 {
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
    //?} else {
    /*public void renderBg(net.minecraft.client.gui.GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {*/
    //?}
        BCGraphics bcg = new BCGraphics(graphics);
        GuiIcon.setGuiGraphics(bcg);
        mainGui.drawBackgroundLayer(partialTicks, mouseX, mouseY, () -> {
            drawBackgroundTexture(bcg);
        });
        mainGui.drawElementBackgrounds();
    }

    /** Top-level render. 26.1: extractRenderState(GuiGraphicsExtractor); 1.21.11: render(GuiGraphics). */
    @Override
    //? if >=26.1 {
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
    //?} else {
    /*public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {*/
    //?}
        BCGraphics bcg = new BCGraphics(graphics);
        GuiIcon.setGuiGraphics(bcg);
        //? if >=26.1 {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        //?} else {
        /*super.render(graphics, mouseX, mouseY, partialTicks);*/
        //?}
        // Draw the drag icon AFTER super (which draws slots/items/highlights) using nextStratum()
        // so the drag icon always sorts on top, matching MC's own carried-item rendering.
        graphics.nextStratum();
        mainGui.drawDragLayer(bcg);
        drawTooltipLayer(mouseX, mouseY, partialTicks);
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

    /** Foreground labels. 26.1: extractLabels(GuiGraphicsExtractor); 1.21.11: renderLabels(GuiGraphics). */
    @Override
    //? if >=26.1 {
    protected void extractLabels(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    //?} else {
    /*protected void renderLabels(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY) {*/
    //?}
        // Intentionally no super call: AbstractContainerScreen draws this.title / this.playerInventoryTitle
        // left-aligned, duplicating BuildCraft's centered titles drawn in drawForegroundLayer().
        BCGraphics bcg = new BCGraphics(graphics);
        GuiIcon.setGuiGraphics(bcg);
        mainGui.preDrawForeground();
        drawForegroundLayer();
        mainGui.drawElementForegrounds(null);
        mainGui.postDrawForeground();
    }

    /** Draw custom foreground labels. Subclasses should override this. */
    protected void drawForegroundLayer() {
    }

    /** Draw the background texture. Override this to blit your GUI background. */
    protected void drawBackgroundTexture(BCGraphics graphics) {
        // Default: do nothing. Subclasses blit their texture.
    }

    /** Draw hover tooltips, after the slots/items/drag layer. Subclasses override this instead of
     * the vanilla render method; obtain the graphics via {@link GuiIcon#getGuiGraphics()}. This is
     * the single cross-cliff seam for post-render GUI work — subclasses stay version-agnostic. */
    protected void drawTooltipLayer(int mouseX, int mouseY, float partialTick) {
        // Default: do nothing. Subclasses draw their tank/slot tooltips (and animated overlays).
    }
}
