/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client;

import java.util.Locale;

import net.minecraft.client.Minecraft;

import buildcraft.lib.misc.TimeUtil;

/** The language the player has selected, as a {@link Locale}. Client-only: it names {@link Minecraft}, so common
 *  code must reach it only behind a client {@code Dist} check, where it is first loaded. */
public enum ClientLocale {
    ;

    /** The selected language ({@code Options > Language}), or {@link Locale#ROOT} if it cannot be parsed. */
    public static Locale current() {
        return TimeUtil.localeFromLanguageCode(Minecraft.getInstance().getLanguageManager().getSelected());
    }
}
