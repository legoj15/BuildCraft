/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import buildcraft.transport.BCTransportAttachments.PluggablesPlaced;
import buildcraft.transport.BCTransportAttachments.PluggablesPlaced.Kind;

/**
 * Pins the {@link PluggablesPlaced} player-attachment that gates the
 * {@code all_plugged_up} advancement. Three behaviours are pinned:
 * <ol>
 *   <li>{@code getData} returns a fresh instance for a new player — mask is zero
 *       and {@code isComplete()} is false.</li>
 *   <li>{@code markPlaced} returns {@code true} only on the first sight of each
 *       kind; subsequent calls with the same kind return {@code false}.</li>
 *   <li>{@code isComplete()} flips to {@code true} exactly on the 8th unique
 *       kind, not before.</li>
 * </ol>
 * The actual advancement award is gated on {@code ServerPlayer} inside
 * {@code AdvancementUtil}, which {@code makeMockPlayer} cannot satisfy; the
 * predicate and the attachment are what these tests guard.
 */
public class PluggablesPlacedTester {

    public static void testFreshAttachmentEmpty(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PluggablesPlaced data = player.getData(BCTransportAttachments.PLUGGABLES_PLACED.get());
        helper.assertFalse(data.isComplete(), "Fresh attachment must not report complete");
        helper.succeed();
    }

    public static void testMarkPlacedReturnsTrueOnlyOnFirstSighting(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PluggablesPlaced data = player.getData(BCTransportAttachments.PLUGGABLES_PLACED.get());

        helper.assertTrue(data.markPlaced(Kind.BLOCKER),
            "First BLOCKER placement must return true");
        helper.assertFalse(data.markPlaced(Kind.BLOCKER),
            "Subsequent BLOCKER placement must return false");
        helper.assertTrue(data.markPlaced(Kind.GATE),
            "First GATE placement must return true");
        helper.assertFalse(data.markPlaced(Kind.GATE),
            "Subsequent GATE placement must return false");
        helper.succeed();
    }

    public static void testCompleteOnlyAfterAllEightKinds(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PluggablesPlaced data = player.getData(BCTransportAttachments.PLUGGABLES_PLACED.get());

        Kind[] kinds = Kind.values();
        // Eight kinds is the contract carried in PluggablesPlaced.ALL_KINDS_MASK and the eight
        // criteria in data/buildcraftunofficial/advancement/all_plugged_up.json. Adding or
        // removing one without updating both will silently break the advancement's progress.
        helper.assertTrue(kinds.length == 8,
            "PluggablesPlaced.Kind must define exactly 8 kinds; saw " + kinds.length);

        for (int i = 0; i < kinds.length - 1; i++) {
            data.markPlaced(kinds[i]);
            helper.assertFalse(data.isComplete(),
                "isComplete must remain false after " + (i + 1) + " of 8 kinds placed");
        }

        data.markPlaced(kinds[kinds.length - 1]);
        helper.assertTrue(data.isComplete(),
            "isComplete must flip true once all 8 kinds have been placed");
        helper.succeed();
    }
}
