/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link GameProfileUtil#getDisplayName} is what the owner ledger shows. authlib rejects a null profile name, so
 * "this owner has no name" is spelled {@code ""} everywhere (see {@code OwnerData}); the ledger must therefore treat a
 * blank name — not only a null profile — as "unknown", or it renders an empty row.
 */
public class GameProfileUtilTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    public void namedProfileShowsItsName() {
        Assertions.assertEquals("Steve", GameProfileUtil.getDisplayName(new GameProfile(ID, "Steve")));
    }

    @Test
    public void nullProfileHasNoDisplayName() {
        Assertions.assertNull(GameProfileUtil.getDisplayName(null));
    }

    @Test
    public void blankNameHasNoDisplayName() {
        Assertions.assertNull(GameProfileUtil.getDisplayName(new GameProfile(ID, "")));
        Assertions.assertNull(GameProfileUtil.getDisplayName(new GameProfile(ID, "   ")));
    }
}
