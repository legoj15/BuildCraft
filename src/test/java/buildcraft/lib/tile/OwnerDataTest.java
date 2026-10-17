/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.tile;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.test.ValueIoTestUtil;

/**
 * The standard {@code ownerUUID}/{@code ownerName} codec. Its read side used to substitute the English word
 * {@code "Unknown"} for a missing name, and that word was then baked into the profile — written back to disk on the
 * next save and shown verbatim to every player whatever their language. A missing name now reads as an EMPTY name, and
 * the owner ledger (which already owns the localised {@code gui.owner.unknown} text) renders the blank as "unknown".
 */
public class OwnerDataTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static CompoundTag written(GameProfile owner) {
        return ValueIoTestUtil.write(out -> OwnerData.writeOwner(out, owner));
    }

    @Test
    public void namedOwnerRoundTripsExactly() {
        GameProfile back = OwnerData.readOwner(ValueIoTestUtil.inputOf(written(new GameProfile(ID, "Steve"))));

        Assertions.assertNotNull(back);
        Assertions.assertEquals(ID, GameProfileUtil.getId(back));
        Assertions.assertEquals("Steve", GameProfileUtil.getName(back));
    }

    @Test
    public void blankNamedOwnerRoundTripsBlankAndKeepsItsId() {
        GameProfile back = OwnerData.readOwner(ValueIoTestUtil.inputOf(written(new GameProfile(ID, ""))));

        Assertions.assertNotNull(back, "an owner with an id but no name is still an owner");
        Assertions.assertEquals(ID, GameProfileUtil.getId(back));
        Assertions.assertEquals("", GameProfileUtil.getName(back));
    }

    @Test
    public void storedIdWithoutNameDoesNotInventAnEnglishName() {
        CompoundTag onlyId = ValueIoTestUtil.write(out -> out.putString("ownerUUID", ID.toString()));

        GameProfile back = OwnerData.readOwner(ValueIoTestUtil.inputOf(onlyId));

        Assertions.assertNotNull(back);
        Assertions.assertEquals(ID, GameProfileUtil.getId(back));
        Assertions.assertEquals("", GameProfileUtil.getName(back),
                "a missing name must stay empty so the ledger can localise it, not become the English word");
    }

    @Test
    public void resavingAnOwnerThatHadNoNameNeverPersistsEnglish() {
        CompoundTag onlyId = ValueIoTestUtil.write(out -> out.putString("ownerUUID", ID.toString()));
        GameProfile loaded = OwnerData.readOwner(ValueIoTestUtil.inputOf(onlyId));

        CompoundTag resaved = written(loaded);

        Assertions.assertEquals(ID.toString(), ValueIoTestUtil.stringOr(resaved, "ownerUUID", "<absent>"));
        Assertions.assertNotEquals("Unknown", ValueIoTestUtil.stringOr(resaved, "ownerName", ""),
                "the load -> save cycle must not bake a literal English name into the save");
    }

    @Test
    public void absentOrUnparseableIdReadsAsNoOwner() {
        Assertions.assertNull(OwnerData.readOwner(ValueIoTestUtil.inputOf(new CompoundTag())));
        CompoundTag garbage = ValueIoTestUtil.write(out -> out.putString("ownerUUID", "not-a-uuid"));
        Assertions.assertNull(OwnerData.readOwner(ValueIoTestUtil.inputOf(garbage)));
    }
}
