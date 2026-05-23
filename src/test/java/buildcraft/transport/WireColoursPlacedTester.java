/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;

import buildcraft.transport.BCTransportAttachments.WireColoursPlaced;

/**
 * Pins the {@link WireColoursPlaced} player-attachment that gates the
 * {@code colorful_electrician} advancement. Three behaviours are pinned:
 * <ol>
 *   <li>{@code getData} returns a fresh instance for a new player — mask is zero
 *       and {@code isComplete()} is false.</li>
 *   <li>{@code markPlaced} returns {@code true} only on the first sight of each
 *       colour; subsequent calls with the same colour return {@code false}.</li>
 *   <li>{@code isComplete()} flips to {@code true} exactly on the 16th unique
 *       colour, not before.</li>
 * </ol>
 * The actual advancement award is gated on {@code ServerPlayer} inside
 * {@code AdvancementUtil}, which {@code makeMockPlayer} cannot satisfy; the
 * predicate and the attachment are what these tests guard.
 */
public class WireColoursPlacedTester {

    public static void testFreshAttachmentEmpty(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        WireColoursPlaced data = player.getData(BCTransportAttachments.WIRE_COLOURS_PLACED.get());
        helper.assertFalse(data.isComplete(), "Fresh attachment must not report complete");
        helper.succeed();
    }

    public static void testMarkPlacedReturnsTrueOnlyOnFirstSighting(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        WireColoursPlaced data = player.getData(BCTransportAttachments.WIRE_COLOURS_PLACED.get());

        helper.assertTrue(data.markPlaced(DyeColor.RED),
            "First RED placement must return true");
        helper.assertFalse(data.markPlaced(DyeColor.RED),
            "Subsequent RED placement must return false");
        helper.assertTrue(data.markPlaced(DyeColor.BLUE),
            "First BLUE placement must return true");
        helper.assertFalse(data.markPlaced(DyeColor.BLUE),
            "Subsequent BLUE placement must return false");
        helper.succeed();
    }

    public static void testCompleteOnlyAfterAllSixteenColours(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        WireColoursPlaced data = player.getData(BCTransportAttachments.WIRE_COLOURS_PLACED.get());

        DyeColor[] colours = DyeColor.values();
        // 16 dye colours is a hard contract of Minecraft's DyeColor enum; if this ever changes
        // the attachment's bitmask width (WireColoursPlaced.ALL_COLOURS_MASK) needs revisiting.
        helper.assertTrue(colours.length == 16,
            "DyeColor must define exactly 16 colours; saw " + colours.length);

        for (int i = 0; i < colours.length - 1; i++) {
            data.markPlaced(colours[i]);
            helper.assertFalse(data.isComplete(),
                "isComplete must remain false after " + (i + 1) + " of 16 colours placed");
        }

        data.markPlaced(colours[colours.length - 1]);
        helper.assertTrue(data.isComplete(),
            "isComplete must flip true once all 16 colours have been placed");
        helper.succeed();
    }
}
