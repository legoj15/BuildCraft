/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.help;

import java.util.List;

import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.misc.LocaleUtil;

/** Defines some information used when displaying help text about a specific GUI element. */
public class ElementHelpInfo {
    public final String title;
    public final int colour;
    public final String[] localeKeys;
    public final boolean isPreTranslated;

    public ElementHelpInfo(String title, int colour, String... localeKeys) {
        this.title = title;
        this.colour = colour;
        this.localeKeys = localeKeys;
        this.isPreTranslated = false;
    }

    public ElementHelpInfo(String title, int colour, boolean isPreTranslated, String... localeKeys) {
        this.title = title;
        this.colour = colour;
        this.localeKeys = localeKeys;
        this.isPreTranslated = isPreTranslated;
    }

    public static ElementHelpInfo preTranslated(String title, int colour, String... lines) {
        return new ElementHelpInfo(title, colour, true, lines);
    }

    /** True when {@code other} would show exactly the same text: a screen that rebuilds its entry every frame (a live
     *  rate) offers a new instance each time, and only a change in what it says should refresh the help ledger. */
    public boolean sameContent(ElementHelpInfo other) {
        return other != null && (other == this || (colour == other.colour && isPreTranslated == other.isPreTranslated
                && java.util.Objects.equals(title, other.title) && java.util.Arrays.equals(localeKeys, other.localeKeys)));
    }

    public final HelpPosition target(IGuiArea target) {
        return new HelpPosition(this, target);
    }

    /** Stores an {@link ElementHelpInfo} information, as well as the target area which the help element relates to. */
    public static final class HelpPosition {
        public final ElementHelpInfo info;
        public final IGuiArea target;

        private HelpPosition(ElementHelpInfo info, IGuiArea target) {
            this.info = info;
            this.target = target;
        }
    }
}
