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
        if (currentMenu == null || !currentMenu.shouldFullyOverride()) {
            menuBackgroundRenderer.run();
        }
    }

    public void drawElementBackgrounds() {
        for (IGuiElement element : shownElements) {
            if (element != currentMenu) {
                element.drawBackground(lastPartialTicks);
            }
        }
    }

    public void preDrawForeground() {
        // PoseStack operations - TODO
    }

    public void postDrawForeground() {
        // PoseStack operations - TODO
    }

    public void drawElementForegrounds(Runnable menuBackgroundRenderer) {
        for (IGuiElement element : shownElements) {
            if (element != currentMenu) {
                element.drawForeground(lastPartialTicks);
            }
        }

        IMenuElement m = currentMenu;
        if (m != null) {
            if (m.shouldFullyOverride() && menuBackgroundRenderer != null) {
                menuBackgroundRenderer.run();
            }
            m.drawBackground(lastPartialTicks);
            m.drawForeground(lastPartialTicks);
        }

        // TODO: draw tooltips
        // GuiUtil.drawVerticallyAppending(mouse, getAllTooltips(), this::drawTooltip);
    }

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
