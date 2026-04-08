/* Copyright (c) 2017 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.compat.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Rect2i;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.IGuiElement;
import buildcraft.lib.gui.ledger.Ledger_Neptune;

import mezz.jei.api.gui.handlers.IGuiContainerHandler;

/**
 * Tells JEI about the bounding rectangles of any open BuildCraft ledgers
 * so that JEI's ingredient list is pushed out of the way instead of
 * overlapping with them.
 */
public class BCGuiContainerHandler implements IGuiContainerHandler<GuiBC8<?>> {

    @Override
    public List<Rect2i> getGuiExtraAreas(GuiBC8<?> screen) {
        List<Rect2i> areas = new ArrayList<>();
        for (IGuiElement element : screen.mainGui.shownElements) {
            if (element instanceof Ledger_Neptune ledger) {
                int x = (int) ledger.getX();
                int y = (int) ledger.getY();
                int w = (int) Math.ceil(ledger.getWidth());
                int h = (int) Math.ceil(ledger.getHeight());
                if (w > 0 && h > 0) {
                    areas.add(new Rect2i(x, y, w, h));
                }
            }
        }
        return areas;
    }
}
