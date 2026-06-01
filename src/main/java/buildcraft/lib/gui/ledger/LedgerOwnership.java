/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.ledger;

import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import buildcraft.lib.gui.BCGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import buildcraft.lib.gui.BuildCraftGui;

/** Ledger that shows the owner (player who placed the block).
 *  Renders the owner's actual skin face, matching 1.12.2's SpriteUtil.getFaceSprite(). */
@SuppressWarnings("this-escape")
public class LedgerOwnership extends Ledger_Neptune {
    private static final int COLOUR = 0xE0F0FF;
    /** Fallback skin for when no owner profile is available. */
    private static final Identifier STEVE_SKIN = Identifier.parse("textures/entity/player/wide/steve.png");

    private final Supplier<GameProfile> ownerSupplier;

    public LedgerOwnership(BuildCraftGui gui, Supplier<GameProfile> ownerSupplier, boolean expandPositive) {
        super(gui, COLOUR, expandPositive);
        this.ownerSupplier = ownerSupplier;
        this.title = "gui.owner";

        // 1.12.2 used colour 0 (black) for the owner name text
        appendText(() -> {
            GameProfile profile = ownerSupplier.get();
            return profile != null ? profile.name() : "Unknown";
        }, 0x000000);

        calculateMaxSize();
    }

    @Override
    protected void drawIcon(double x, double y, BCGraphics graphics) {
        Identifier skinTexture = getSkinTexture(ownerSupplier.get());

        // Draw the 8x8 face region from the 64x64 skin texture, scaled to 16x16.
        // The face pixels are at UV (8, 8) → (16, 16) in the skin texture.
        graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture,
            (int) x, (int) y, 8f, 8f, 16, 16, 8, 8, 64, 64);

        // Draw the hat overlay layer on top (matching 1.12.2 SpriteUtil.getFaceOverlaySprite()).
        // The hat overlay pixels are at UV (40, 8) → (48, 16) in the skin texture.
        graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture,
            (int) x, (int) y, 40f, 8f, 16, 16, 8, 8, 64, 64);
    }

    /** Get the skin texture Identifier for the given player profile.
     *  Tries the network connection first (for online players), then falls back to Steve. */
    private static Identifier getSkinTexture(GameProfile profile) {
        if (profile == null || profile.id() == null) {
            return STEVE_SKIN;
        }
        try {
            // Look up the player in the connection — this has their loaded skin
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                PlayerInfo info = connection.getPlayerInfo(profile.id());
                if (info != null) {
                    var skin = info.getSkin();
                    var bodyTex = skin.body();
                    if (bodyTex != null) {
                        return bodyTex.id();
                    }
                }
            }
        } catch (Exception e) {
            // Fall through to default
        }
        return STEVE_SKIN;
    }
}
