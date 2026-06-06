/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.list;

import java.util.ArrayList;
import java.util.List;

import buildcraft.lib.gui.BCGraphics;
//? if >=1.21.10 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.lists.ListMatchHandler;
import buildcraft.api.lists.ListMatchHandler.Type;
import buildcraft.api.lists.ListRegistry;

import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.gui.ledger.Ledger_Neptune;
import buildcraft.lib.list.ListHandler;

/** A help-style side ledger that, when expanded and the player hovers over slot 0 of a list line
 * in one-stack (By-Type / By-Material) mode, reveals which tags / capabilities the registered
 * {@link ListMatchHandler}s use to match items against the exemplar. Idle text otherwise reminds
 * the player how to invoke it.
 *
 * <p>Lives in {@code core.list} (next to {@link GuiList} and {@link ContainerList}) rather than in
 * {@code lib.gui.ledger} because it's specific to the list item's GUI layout — it knows about the
 * slot grid coordinates and the {@link ListHandler.Line} model directly.
 */
@SuppressWarnings("this-escape")
public class LedgerListMatch extends Ledger_Neptune {
    private static final Identifier ICON = Identifier.parse("buildcraftunofficial:textures/icons/help.png");

    /** Slot grid origin and pitch — must match {@link ContainerList}'s
     * {@code addSlot(new ListPhantomSlot(..., 8 + slot * 18, 32 + line * 34, ...))}. */
    private static final int SLOT_X0 = 8;
    private static final int SLOT_Y0 = 32;
    private static final int SLOT_PITCH_X = 18;
    private static final int SLOT_PITCH_Y = 34;
    private static final int SLOT_SIZE = 16;

    private final ContainerList container;

    /** Cache of last hovered (line, exemplar item id, mode) so we only rebuild text when state
     * actually changes — drawBackground runs every frame and rebuilding wraps the font on every
     * call. */
    private int cachedLine = -2;
    private String cachedSig = "";

    public LedgerListMatch(BuildCraftGui gui, ContainerList container) {
        // Greenish tint, left-side (matches help ledger orientation).
        super(gui, 0xFF_99_CC_99, false);
        this.container = container;
        this.title = "gui.ledger.list_match";
        appendIdleText();
        calculateMaxSize();
    }

    @Override
    protected void drawIcon(double x, double y, BCGraphics graphics) {
        //? if >=1.21.10 {
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON,
                (int) x, (int) y, 0f, 0f, 16, 16, 16, 16);
        //?} else {
        /*graphics.blit(ICON,
                (int) x, (int) y, 0f, 0f, 16, 16, 16, 16);*/
        //?}
    }

    @Override
    public void drawBackground(float partialTicks) {
        // When closed/closing we don't need to compute hover state.
        if (shouldDrawOpen()) {
            updateHoverContent();
        }
        super.drawBackground(partialTicks);
    }

    private void updateHoverContent() {
        int hoveredLine = getHoveredLineForSlotZero();

        if (hoveredLine < 0 || hoveredLine >= container.lines.length) {
            setIdleIfChanged();
            return;
        }

        ListHandler.Line line = container.lines[hoveredLine];
        if (!line.isOneStackMode()) {
            setIdleIfChanged();
            return;
        }

        ItemStack exemplar = line.getStack(0);
        if (exemplar.isEmpty()) {
            setIdleIfChanged();
            return;
        }

        String sig = hoveredLine + ":" + System.identityHashCode(exemplar.getItem())
                + ":" + line.byType + ":" + line.byMaterial + ":" + line.precise;
        if (sig.equals(cachedSig) && cachedLine == hoveredLine) return;
        cachedSig = sig;
        cachedLine = hoveredLine;

        clearTextEntries();

        boolean any = false;
        if (line.byType) {
            appendText(buildcraft.lib.misc.LocaleUtil.localize("gui.list.match.mode_type"),
                    0xFFFFFF).setDropShadow(true);
            any |= appendHandlerDescriptions(Type.TYPE, exemplar);
        }
        if (line.byMaterial) {
            appendText(buildcraft.lib.misc.LocaleUtil.localize("gui.list.match.mode_material"),
                    0xFFFFFF).setDropShadow(true);
            any |= appendHandlerDescriptions(Type.MATERIAL, exemplar);
        }

        if (!any) {
            appendText(buildcraft.lib.misc.LocaleUtil.localize("gui.list.match.no_handlers"),
                    0xFFAAAA);
            appendText(buildcraft.lib.misc.LocaleUtil.localize("gui.list.match.no_handlers_hint"),
                    0xCCCCCC);
        }

        // The Precise flag only does work in the exact-match (no TYPE / no MATERIAL) path —
        // none of the registered handlers consult `precise` when matching by tag/capability.
        // Surface this so the player doesn't think the depressed button is doing something.
        if (line.precise) {
            appendText(buildcraft.lib.misc.LocaleUtil.localize("gui.list.match.precise_inactive"),
                    0xFFAAAA);
        }

        calculateMaxSize();
    }

    /** @return true if at least one handler claimed the source for this mode. */
    private boolean appendHandlerDescriptions(Type mode, ItemStack exemplar) {
        boolean any = false;
        for (ListMatchHandler handler : ListRegistry.getHandlers()) {
            if (!handler.isValidSource(mode, exemplar)) continue;
            List<String> descriptions = handler.describeMatch(mode, exemplar);
            if (descriptions.isEmpty()) {
                appendText("- " + handler.getClass().getSimpleName(), 0xCCCCCC);
                any = true;
                continue;
            }
            for (String desc : descriptions) {
                appendText("- " + desc, 0xCCCCCC);
            }
            any = true;
        }
        return any;
    }

    private void setIdleIfChanged() {
        if (cachedLine == -1 && "idle".equals(cachedSig)) return;
        cachedLine = -1;
        cachedSig = "idle";
        clearTextEntries();
        appendIdleText();
        calculateMaxSize();
    }

    private void appendIdleText() {
        appendText(buildcraft.lib.misc.LocaleUtil.localize("gui.list.match.idle"), 0xCCCCCC);
    }

    /** @return the line index whose slot-0 the mouse is currently over, or -1 if none. */
    private int getHoveredLineForSlotZero() {
        double mx = gui.mouse.getX() - gui.rootElement.getX();
        double my = gui.mouse.getY() - gui.rootElement.getY();

        // Slot 0 only — narrow x window
        if (mx < SLOT_X0 || mx >= SLOT_X0 + SLOT_SIZE) return -1;

        for (int line = 0; line < container.lines.length; line++) {
            int y = SLOT_Y0 + line * SLOT_PITCH_Y;
            if (my >= y && my < y + SLOT_SIZE) return line;
        }
        return -1;
    }
}
