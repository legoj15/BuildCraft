package buildcraft.lib.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import buildcraft.lib.gui.elem.ToolTip;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.gui.pos.IGuiPosition;
import buildcraft.lib.gui.pos.MousePosition;
import buildcraft.lib.misc.GuiUtil;

/** A gui element that allows for easy implementation of an actual {@link Screen} class. */
public class BuildCraftGui {

    public final Minecraft mc = Minecraft.getInstance();
    public final Screen gui;
    public final MousePosition mouse = new MousePosition();

    /** The area that encompasses the entire screen. */
    public final IGuiArea screenElement;

    /** The area that most of the GUI elements should be in. */
    public final IGuiArea rootElement;

    /** All of the {@link IGuiElement} which will be drawn by this gui. */
    public final List<IGuiElement> shownElements = new ArrayList<>();
    public IMenuElement currentMenu;

    /** Ledger-style elements. */
    public IGuiPosition lowerLeftLedgerPos, lowerRightLedgerPos;
    private float lastPartialTicks;

    public BuildCraftGui(Screen gui, IGuiArea rootElement) {
        this.gui = gui;
        this.screenElement = GuiUtil.AREA_WHOLE_SCREEN;
        this.rootElement = rootElement;

        lowerLeftLedgerPos = rootElement.offset(0, 5);
        lowerRightLedgerPos = rootElement.getPosition(1, -1).offset(0, 5);
    }

    public BuildCraftGui(Screen gui) {
        this.gui = gui;
        this.screenElement = GuiUtil.AREA_WHOLE_SCREEN;
        this.rootElement = screenElement;

        lowerLeftLedgerPos = screenElement.getPosition(1, -1).offset(-5, 5);
        lowerRightLedgerPos = screenElement.offset(5, 5);
    }

    public static IGuiArea createWindowedArea(AbstractContainerScreen<?> gui) {
        // Old getter names (present on every 26.1.x; 26.1.2 keeps them alongside getLeftPos/...) so one jar runs on all.
        return IGuiArea.create(gui::getGuiLeft, gui::getGuiTop, gui::getXSize, gui::getYSize);
    }

    public final float getLastPartialTicks() {
        return lastPartialTicks;
    }

    public void tick() {
        if (currentMenu != null) {
            currentMenu.tick();
        }
        for (IGuiElement element : shownElements) {
            element.tick();
        }
    }

    public List<IGuiElement> getElementsAt(double x, double y) {
        List<IGuiElement> elements = new ArrayList<>();
        IMenuElement m = currentMenu;
        if (m != null) {
            elements.addAll(m.getThisAndChildrenAt(x, y));
            if (m.shouldFullyOverride()) {
                return elements;
            }
        }
        for (IGuiElement elem : shownElements) {
            elements.addAll(elem.getThisAndChildrenAt(x, y));
        }
        return elements;
    }

    public void drawBackgroundLayer(float partialTicks, int mouseX, int mouseY, Runnable menuBackgroundRenderer) {
        // 1.12.2 parity: explicitly source partialTicks from the MC timer
        // to avoid stale values from the incoming argument
        partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        this.lastPartialTicks = partialTicks;
        mouse.setMousePosition(mouseX, mouseY);
        // Always draw the gate background — even when a variant/menu popup is open.
        // In 1.12.2, the background was always present; the popup simply drew on top with a dark overlay.
        menuBackgroundRenderer.run();
    }

    public void drawElementBackgrounds() {
        for (IGuiElement element : shownElements) {
            if (element != currentMenu) {
                element.drawBackground(lastPartialTicks);
            }
        }
    }

    /**
     * Renders only the drag-layer menu element (shouldFullyOverride=false) at the highest stratum.
     * Must be called from extractRenderState, AFTER super.extractRenderState() has drawn slots and items,
     * so the drag icon sorts on top of inventory items and slot highlights.
     * Call graphics.nextStratum() before invoking this to guarantee correct z-ordering.
     */
    public void drawDragLayer(buildcraft.lib.gui.BCGraphics graphics) {
        IMenuElement m = currentMenu;
        if (m != null && !m.shouldFullyOverride()) {
            m.drawBackground(lastPartialTicks);
            m.drawForeground(lastPartialTicks);
        }
    }

    public void preDrawForeground() {
        buildcraft.lib.gui.BCGraphics graphics = GuiIcon.getGuiGraphics();
        if (graphics != null) {
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) -rootElement.getX(), (float) -rootElement.getY());
        }
    }

    public void postDrawForeground() {
        buildcraft.lib.gui.BCGraphics graphics = GuiIcon.getGuiGraphics();
        if (graphics != null) {
            graphics.pose().popMatrix();
        }
    }

    public void drawElementForegrounds(Runnable menuBackgroundRenderer) {
        for (IGuiElement element : shownElements) {
            if (element != currentMenu) {
                element.drawForeground(lastPartialTicks);
            }
        }

        IMenuElement m = currentMenu;
        if (m != null && m.shouldFullyOverride()) {
            // Draw a dark overlay over the whole GUI to match 1.12.2's darkening effect
            // when the quick-switch variant popup is open.
            buildcraft.lib.gui.BCGraphics graphics = GuiIcon.getGuiGraphics();
            if (graphics != null) {
                int sx = (int) screenElement.getX();
                int sy = (int) screenElement.getY();
                int sw = (int) screenElement.getWidth();
                int sh = (int) screenElement.getHeight();
                graphics.fill(sx, sy, sx + sw, sy + sh, 0xC0101010);
            }
            m.drawBackground(lastPartialTicks);
            m.drawForeground(lastPartialTicks);
        }
        // Non-override menus (e.g. drag) are rendered via drawDragLayer() in extractRenderState instead.

        java.util.List<ToolTip> tooltips = new java.util.ArrayList<>();
        if (m != null && m.shouldFullyOverride()) {
            if (m instanceof buildcraft.lib.gui.ITooltipElement) {
                ((buildcraft.lib.gui.ITooltipElement) m).addToolTips(tooltips);
            }
        } else {
            if (m instanceof buildcraft.lib.gui.ITooltipElement) {
                ((buildcraft.lib.gui.ITooltipElement) m).addToolTips(tooltips);
            }
            for (IGuiElement element : shownElements) {
                if (element instanceof buildcraft.lib.gui.ITooltipElement) {
                    ((buildcraft.lib.gui.ITooltipElement) element).addToolTips(tooltips);
                }
            }
        }

        if (!tooltips.isEmpty()) {
            buildcraft.lib.gui.BCGraphics graphics = GuiIcon.getGuiGraphics();
            if (graphics != null) {
                java.util.List<net.minecraft.util.FormattedCharSequence> comps = new java.util.ArrayList<>();
                for (ToolTip tip : tooltips) {
                    for (String str : tip) {
                        comps.add(net.minecraft.network.chat.Component.literal(str).getVisualOrderText());
                    }
                }
                if (!comps.isEmpty()) {
                    graphics.setTooltipForNextFrame(net.minecraft.client.Minecraft.getInstance().font, comps, (int) mouse.getX(), (int) mouse.getY());
                }
            }
        }    }

    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        mouse.setMousePosition(mouseX, mouseY);

        IMenuElement m = currentMenu;
        if (m != null) {
            m.onMouseClicked(mouseButton);
            if (m.shouldFullyOverride()) {
                return true;
            }
        }

        for (IGuiElement element : shownElements) {
            if (element instanceof IInteractionElement) {
                ((IInteractionElement) element).onMouseClicked(mouseButton);
            }
        }
        return false;
    }

    public void onMouseDragged(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        mouse.setMousePosition(mouseX, mouseY);

        IMenuElement m = currentMenu;
        if (m != null) {
            m.onMouseDragged(clickedMouseButton, timeSinceLastClick);
            if (m.shouldFullyOverride()) {
                return;
            }
        }

        for (IGuiElement element : shownElements) {
            if (element instanceof IInteractionElement) {
                ((IInteractionElement) element).onMouseDragged(clickedMouseButton, timeSinceLastClick);
            }
        }
    }

    public void onMouseReleased(int mouseX, int mouseY, int state) {
        mouse.setMousePosition(mouseX, mouseY);

        IMenuElement m = currentMenu;
        if (m != null) {
            m.onMouseReleased(state);
            if (m.shouldFullyOverride()) {
                return;
            }
        }

        for (IGuiElement element : shownElements) {
            if (element instanceof IInteractionElement) {
                ((IInteractionElement) element).onMouseReleased(state);
            }
        }
    }

    public boolean onKeyTyped(char typedChar, int keyCode) {
        boolean action = false;
        IMenuElement m = currentMenu;
        if (m != null) {
            action = m.onKeyPress(typedChar, keyCode);
            if (action && m.shouldFullyOverride()) {
                return true;
            }
        }

        for (IGuiElement element : shownElements) {
            if (element instanceof IInteractionElement) {
                action |= ((IInteractionElement) element).onKeyPress(typedChar, keyCode);
            }
        }
        return action;
    }
}
