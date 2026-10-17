/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.container;

import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.builders.snapshot.Snapshot;
import buildcraft.lib.misc.HashUtil;

/**
 * The Electronic Library's selection is a key into the <em>client's</em> own library, which the server has no copy of
 * until the upload lands, so the server cannot check that it exists. What it can check is that the key could be one:
 * the selection is stored on the tile, saved with it and forwarded to every player watching the menu.
 */
public class LibrarySelectionTester {

    private static byte[] hash(int length) {
        return new byte[length];
    }

    private static Snapshot.Key withHash(int length) {
        return new Snapshot.Key(new Snapshot.Key(), hash(length));
    }

    private static Snapshot.Key named(Snapshot.Key content, String name) {
        Snapshot.Header header = new Snapshot.Header(content, new UUID(1, 2), new Date(5), name);
        return new Snapshot.Key(content, header);
    }

    @Test
    public void aRealLibraryKeyIsSelectable() {
        Snapshot.Key content = withHash(HashUtil.HASH_BYTES);
        Assertions.assertTrue(ContainerElectronicLibrary.isSelectable(content), "content-only");
        Assertions.assertTrue(ContainerElectronicLibrary.isSelectable(named(content, "house")), "named");
    }

    @Test
    public void aKeyWhoseHashIsNotADigestIsNotSelectable() {
        Assertions.assertFalse(ContainerElectronicLibrary.isSelectable(withHash(0)), "empty");
        Assertions.assertFalse(ContainerElectronicLibrary.isSelectable(withHash(HashUtil.HASH_BYTES - 1)), "short");
        Assertions.assertFalse(ContainerElectronicLibrary.isSelectable(withHash(HashUtil.HASH_BYTES + 1)), "long");
        Assertions.assertFalse(ContainerElectronicLibrary.isSelectable(withHash(30_000)),
            "a packet-sized hash would be saved with the tile and sent to every watcher");
    }

    @Test
    public void aKeyWhoseHeaderIsOutOfShapeIsNotSelectable() {
        Snapshot.Key content = withHash(HashUtil.HASH_BYTES);
        Snapshot.Key badInner = withHash(7);
        Assertions.assertFalse(ContainerElectronicLibrary.isSelectable(
            new Snapshot.Key(content, new Snapshot.Header(badInner, new UUID(1, 2), new Date(5), "house"))),
            "the header's own key must be a digest too");
        Assertions.assertFalse(ContainerElectronicLibrary.isSelectable(
            named(content, "x".repeat(ContainerElectronicLibrary.MAX_SELECTED_NAME_LENGTH + 1))),
            "a name no text box could have produced");
        Assertions.assertTrue(ContainerElectronicLibrary.isSelectable(
            named(content, "x".repeat(ContainerElectronicLibrary.MAX_SELECTED_NAME_LENGTH))), "but the limit itself is fine");
    }
}
