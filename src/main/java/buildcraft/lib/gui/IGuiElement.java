/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import java.util.List;

import com.google.common.collect.ImmutableList;

import buildcraft.lib.gui.elem.ToolTip;
import buildcraft.lib.gui.help.ElementHelpInfo.HelpPosition;
import buildcraft.lib.gui.pos.IGuiArea;

/** Defines an element that can be rendered, that exists inside of a rectangle. */
public interface IGuiElement extends IGuiArea, ITooltipElement, IHelpElement {
    default void drawBackground(float partialTicks) {}

    default void drawForeground(float partialTicks) {}

    default void tick() {}

    /** True while this element lies over the window itself instead of beside it (a ledger the gap beside the window
     *  cannot hold). Such an element draws in a pass of its own above the window's slots and items (see
     *  {@link BuildCraftGui#drawCoveringElements}) and takes the mouse over the area it covers (see
     *  {@link BuildCraftGui#isCovered}); every other element draws with the background, as it always has. */
    default boolean coversWindow() {
        return false;
    }

    @Override
    default void addToolTips(List<ToolTip> tooltips) {}

    @Override
    default void addHelpElements(List<HelpPosition> elements) {}

    default List<IGuiElement> getThisAndChildrenAt(double x, double y) {
        if (contains(x, y)) {
            return ImmutableList.of(this);
        } else {
            return ImmutableList.of();
        }
    }

    default String getDebugInfo(List<String> info) {
        return toString();
    }
}
