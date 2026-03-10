/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import buildcraft.lib.gui.BuildCraftGui;

/** Ledger that shows the owner (player who placed the block). */
public class LedgerOwnership extends Ledger_Neptune {
    private static final int COLOUR = 0xD1C07D;

    public LedgerOwnership(BuildCraftGui gui, Supplier<GameProfile> ownerSupplier, boolean expandPositive) {
        super(gui, COLOUR, expandPositive);
        this.title = "gui.owner";

        appendText(() -> {
            GameProfile profile = ownerSupplier.get();
            return profile != null ? profile.name() : "Unknown";
        }, 0xFFFFFF);

        calculateMaxSize();
    }

    @Override
    protected void drawIcon(double x, double y, GuiGraphics graphics) {
        // Draw a simple player-head icon (tan square with dark "face" features)
        int ix = (int) x, iy = (int) y;
        // Head background
        graphics.fill(ix + 2, iy + 1, ix + 14, iy + 15, 0xFFD4A574);
        // Eyes
        graphics.fill(ix + 4, iy + 5, ix + 7, iy + 8, 0xFF3D2817);
        graphics.fill(ix + 9, iy + 5, ix + 12, iy + 8, 0xFF3D2817);
        // Mouth
        graphics.fill(ix + 5, iy + 10, ix + 11, iy + 12, 0xFF3D2817);
    }
}
