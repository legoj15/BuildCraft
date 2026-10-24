/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.builders.BCBuildersItems;
import buildcraft.lib.misc.HashUtil;

/**
 * The server's snapshot store is a pool every player's uploads write into and nothing lists, so a request for a
 * hash must be backed by something the asking player can show: a used snapshot item referencing that hash, in
 * their inventory or the menu they have open. That is the only thing a legitimate client ever asks with (the
 * tooltip overlay, the architect table and the replacer all pass an item's header key), and it is the Electronic
 * Library's own rule — it shows a player their own files, and its downloads go to the players watching the
 * machine an item was inserted into. These tests pin the predicate that decides what counts as showing.
 */
public class SnapshotRequestVisibilityTester extends VanillaSetupBaseTester {

    private static byte[] hash(int fill) {
        byte[] hash = new byte[HashUtil.HASH_BYTES];
        java.util.Arrays.fill(hash, (byte) fill);
        return hash;
    }

    private static ItemStack usedBlueprint(byte[] hash) {
        Snapshot.Key key = new Snapshot.Key(new Snapshot.Key(), hash);
        Snapshot.Header header = new Snapshot.Header(key, UUID.randomUUID(), new Date(0), "A build");
        return BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(header);
    }

    @Test
    public void aUsedSnapshotItemReferencesItsOwnHash() {
        Assertions.assertTrue(SnapshotRequestPayload.itemReferencesSnapshot(usedBlueprint(hash(1)), hash(1)));
    }

    @Test
    public void aUsedSnapshotItemDoesNotReferenceAnotherHash() {
        // The theft case: holding one build's item must not unlock a different build's content.
        Assertions.assertFalse(SnapshotRequestPayload.itemReferencesSnapshot(usedBlueprint(hash(1)), hash(2)));
    }

    @Test
    public void nothingElseReferencesAnyHash() {
        // A clean (unused) blueprint carries no header; stone is not a snapshot item at all; empty is empty.
        Assertions.assertFalse(SnapshotRequestPayload.itemReferencesSnapshot(
            new ItemStack(BCBuildersItems.BLUEPRINT_CLEAN.get()), hash(1)));
        Assertions.assertFalse(SnapshotRequestPayload.itemReferencesSnapshot(new ItemStack(Items.STONE), hash(1)));
        Assertions.assertFalse(SnapshotRequestPayload.itemReferencesSnapshot(ItemStack.EMPTY, hash(1)));
    }

    @Test
    public void aHandCarriedHashOfTheWrongLengthMatchesNothing() {
        // The wire format allows any byte array; the items carry 32-byte hashes, so a stub or truncated one is
        // nobody's proof. (Same length but different bytes is the case above.)
        Assertions.assertFalse(SnapshotRequestPayload.itemReferencesSnapshot(usedBlueprint(hash(1)),
            new byte[] {1}));
        Assertions.assertFalse(SnapshotRequestPayload.itemReferencesSnapshot(usedBlueprint(hash(1)), null));
    }
}
