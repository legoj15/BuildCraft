/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.misc.LocaleUtil;

/** Ledger that shows the owner (player who placed the block). */
public class LedgerOwnership extends Ledger_Neptune {
    private static final int COLOUR = 0xD1C07D;

    public LedgerOwnership(BuildCraftGui gui, Supplier<GameProfile> ownerSupplier, boolean expandPositive) {
        super(gui, COLOUR, expandPositive);
        this.title = LocaleUtil.localize("gui.owner");

        appendText(() -> {
            GameProfile profile = ownerSupplier.get();
            return profile != null ? profile.name() : "Unknown";
        }, 0xFFFFFF);

        calculateMaxSize();
    }
}
