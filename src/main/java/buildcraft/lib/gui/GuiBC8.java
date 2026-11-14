/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import buildcraft.lib.gui.BCGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
//? if >=1.21.10 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
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
public abstract class GuiBC8<C extends AbstractContainerMenu & BCContainer> extends AbstractContainerScreen<C> {

    /** A mouse position far outside every window, for the passes that must see no hover (see {@link #drawTooltipLayer}). */
    private static final int MOUSE_AWAY = -100_000;

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

    // ─── Window rectangle ────────────────────────────────────────────────────
    // Read straight from vanilla's protected fields, which every supported line (and every 26.1.x patch) keeps
    // under the same names. NeoForge's public getGuiLeft/getGuiTop/getXSize/getYSize are deprecated FOR REMOVAL
    // from 26.1.2, and their replacements getLeftPos/getTopPos/getImageWidth/getImageHeight do not exist on
    // 26.1/26.1.1 — which the single 26.1.x jar must still run on — so neither name is safe; the fields are.

    /** Screen x of the window's left edge. */
    public int windowLeft() {
        return leftPos;
    }

    /** Screen y of the window's top edge. */
    public int windowTop() {
        return topPos;
    }

    /** Window width in GUI pixels. */
    public int windowWidth() {
        return imageWidth;
    }

    /** Window height in GUI pixels. */
    public int windowHeight() {
        return imageHeight;
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
        /*super.render(graphics, mouseX, mouseY, partialTicks);
        // 1.21.11: AbstractContainerScreen.render() does NOT set the hovered-slot tooltip — every
        // concrete vanilla container screen calls renderTooltip() itself after super.render(). 26.1
        // sets it inside extractRenderState, so this call is 1.21.11-only.
        renderTooltip(graphics, mouseX, mouseY);*/
        //?}
        // Draw the drag icon AFTER super (which draws slots/items/highlights) using nextStratum()
        // so the drag icon always sorts on top, matching MC's own carried-item rendering.
        //? if >=1.21.10 {
        graphics.nextStratum();
        //?}
        // (1.21.1 GuiGraphics has no stratum system; immediate draw order already sorts the drag icon on top.)
        mainGui.drawDragLayer(bcg);
        mainGui.drawMenuOverlayLayer(bcg);
        // Under a ledger lying over the window the mouse is on the ledger, not on what it covers: the screens' own
        // tooltip hit-tests (tanks, readouts) are shown a mouse far outside the window.
        boolean covered = mainGui.isCovered(mouseX, mouseY);
        drawTooltipLayer(covered ? MOUSE_AWAY : mouseX, covered ? MOUSE_AWAY : mouseY, partialTicks);
        // 1.21.1: draw the single deferred tooltip set during this frame (last-wins, on top),
        // mirroring 26.1.2's engine-level "for next frame" tooltip. Without this, 1.21.1's
        // immediate renderTooltip drew every tooltip set in the frame, overlapping. (>=1.21.10
        // defers in-engine, so no flush is needed there.)
        //? if <1.21.10 {
        /*BCGraphics.flushDeferredTooltip();*/
        //?}
    }

    // Ledgers beside the window draw with the background, before the slots, as they always have. A ledger that lies over
    // the window (the gap beside it is narrower than MIN_OPEN_WIDTH) draws after the slots and items instead, so its text
    // is not hidden under them, and still under the stack the cursor carries: from the carried item's own seam where
    // the node has one (>= 1.21.10), from renderLabels (after the slots, before the carried stack) on 1.21.1.
    //? if >=26.1 {
    @Override
    public void extractCarriedItem(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawCoveringLedgers();
        super.extractCarriedItem(graphics, mouseX, mouseY);
    }
    //?} elif >=1.21.10 {
    /*@Override
    public void renderCarriedItem(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY) {
        drawCoveringLedgers();
        super.renderCarriedItem(graphics, mouseX, mouseY);
    }*/
    //?}

    private void drawCoveringLedgers() {
        if (!mainGui.hasCoveringElement()) {
            return;
        }
        BCGraphics bcg = GuiIcon.getGuiGraphics();
        if (bcg == null) {
            return;
        }
        //? if >=1.21.10 {
        // The slots' items draw after the plain elements of their stratum, so the ledgers need a stratum of their own.
        bcg.nextStratum();
        mainGui.drawCoveringElements();
        //?} else {
        /*// 1.21.1 queues the slots' items and text and draws them when flushed, after the ledger panel (an immediate
        // blit) would be: flush them first so the panel lands on top, then lift the ledgers above the items' depth (z 150,
        // their count text z 200) and below the carried stack (z 232), which is drawn after this. The pose is in the
        // window's frame here; preDrawForeground puts it back to the screen's.
        bcg.raw.flush();
        mainGui.preDrawForeground();
        bcg.pose().translate(0.0F, 0.0F, 210.0F);
        mainGui.drawCoveringElements();
        mainGui.postDrawForeground();
        // flush() turned the depth test back on; render() runs the rest of its labels and floating items with it off.
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();*/
        //?}
    }

    /** The slots under a ledger that lies over the window are not there for the mouse: no hover, tooltip or highlight,
     *  and the keys that act on the hovered slot (Q, the hotbar digits, swap-hands, middle click) leave them alone.
     *  Vanilla finds the hovered slot, and every slot a click, drag or release lands on, through this one test. */
    @Override
    protected boolean isHovering(int left, int top, int width, int height, double mouseX, double mouseY) {
        return !mainGui.isCovered(mouseX, mouseY) && super.isHovering(left, top, width, height, mouseX, mouseY);
    }

    //? if >=1.21.10 {
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
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseXd, double mouseYd, int button) {
        int mouseX = (int) mouseXd;
        int mouseY = (int) mouseYd;
        if (mainGui.onMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseXd, mouseYd, button);
    }

    @Override
    public boolean mouseReleased(double mouseXd, double mouseYd, int button) {
        int mouseX = (int) mouseXd;
        int mouseY = (int) mouseYd;
        mainGui.onMouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseXd, mouseYd, button);
    }

    @Override
    public boolean mouseDragged(double mouseXd, double mouseYd, int button, double dragX, double dragY) {
        int mouseX = (int) mouseXd;
        int mouseY = (int) mouseYd;
        mainGui.onMouseDragged(mouseX, mouseY, button, 0);
        return super.mouseDragged(mouseXd, mouseYd, button, dragX, dragY);
    }*/
    //?}

    // Same signature on every node, so no directive: the wheel goes to the ledgers (a long one scrolls its text)
    // before the screen's own handling.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mainGui.onMouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        // A ledger lying over the window that has nothing to scroll still keeps the wheel from what is under it.
        if (mainGui.isCovered(mouseX, mouseY)) {
            return true;
        }
        return mouseScrolledBelowLedgers(mouseX, mouseY, scrollX, scrollY);
    }

    /** What the wheel does once no ledger took it: vanilla's handling. A screen with a wheel of its own overrides THIS
     *  and calls {@code super} for the rest, never {@link #mouseScrolled}, so the ledgers are offered the event once
     *  and always first. */
    protected boolean mouseScrolledBelowLedgers(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        //? if <1.21.10 {
        /*drawCoveringLedgers();*/
        //?}
        mainGui.preDrawForeground();
        mainGui.drawElementForegrounds(null);
        mainGui.postDrawForeground();
        // drawForegroundLayer() draws titles/labels at GUI-local coords (e.g. (imageWidth - w)/2, 6),
        // so it MUST run in vanilla's GUI-local pose (origin = GUI top-left). preDrawForeground shifts
        // the origin to the SCREEN top-left for BuildCraft's element coordinate system, which would push
        // those title coords into the screen corner — so draw it AFTER postDrawForeground restores the
        // GUI-local pose (matching the pre-refactor "drawn after super.extractLabels" behaviour).
        drawForegroundLayer();
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
