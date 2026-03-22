/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import buildcraft.lib.gui.BuildCraftGui;

/** Ledger that shows the owner (player who placed the block). */
public class LedgerOwnership extends Ledger_Neptune {
    private static final int COLOUR = 0xE0F0FF;
    // Default Steve skin texture for the player head icon
    private static final Identifier SKIN_TEXTURE = Identifier.parse("textures/entity/player/wide/steve.png");

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
        // Blit the 8x8 face region from the Steve skin texture, scaled to 16x16
        // Skin face is at u=8, v=8 in the 64x64 skin texture
        graphics.blit(RenderPipelines.GUI_TEXTURED, SKIN_TEXTURE,
            (int) x, (int) y, 8f, 8f, 16, 16, 8, 8, 64, 64);
    }
}

