/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.test.ValueIoTestUtil;
import buildcraft.lib.tile.AbstractBCBlockEntity;

/**
 * The Filler's owner. {@code TileFiller} used to keep a private {@code owner} that shadowed
 * {@code AbstractBCBlockEntity.owner}: the inherited {@code setOwner} (and the inherited, shared
 * {@code ownerUUID}/{@code ownerName} save) changed one field while {@code getOwner()} read the other, so generic
 * machine code could never give a Filler an owner. The Filler now uses the inherited field and the inherited save; the
 * old private {@code owner} child (name/uuid) is still READ so saves from before the change keep their owner.
 */
public class TileFillerOwnerNbtTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static TileFiller newFiller() {
        return new TileFiller(BlockPos.ZERO, BCBuildersBlocks.FILLER.get().defaultBlockState());
    }

    private static CompoundTag save(TileFiller filler) {
        return ValueIoTestUtil.write(filler::writeData);
    }

    @Test
    public void setOwnerThroughTheBaseClassIsWhatGetOwnerReturns() {
        TileFiller filler = newFiller();
        GameProfile owner = new GameProfile(ID, "Steve");

        AbstractBCBlockEntity asBase = filler;
        asBase.setOwner(owner);

        Assertions.assertEquals(owner, filler.getOwner(), "the Filler's getOwner() must see what setOwner stored");
        Assertions.assertEquals(owner, asBase.getOwner(), "the base-class view must agree");

        asBase.setOwner(null);
        Assertions.assertNull(filler.getOwner(), "clearing through setOwner must clear getOwner()");
    }

    @Test
    public void ownerSurvivesASaveLoadCycleInTheStandardShape() {
        TileFiller filler = newFiller();
        filler.setOwner(new GameProfile(ID, "Steve"));

        CompoundTag saved = save(filler);
        Assertions.assertEquals(ID.toString(), ValueIoTestUtil.stringOr(saved, "ownerUUID", ""),
                "the owner is saved under the standard ownerUUID key: " + saved);
        Assertions.assertEquals("Steve", ValueIoTestUtil.stringOr(saved, "ownerName", ""));
        Assertions.assertFalse(saved.contains("owner"), "the retired private owner child is no longer written");

        TileFiller reloaded = newFiller();
        reloaded.readData(ValueIoTestUtil.inputOf(saved));

        GameProfile owner = reloaded.getOwner();
        Assertions.assertNotNull(owner, "the owner survives the round trip");
        Assertions.assertEquals(ID, GameProfileUtil.getId(owner));
        Assertions.assertEquals("Steve", GameProfileUtil.getName(owner));
        Assertions.assertEquals(owner, ((AbstractBCBlockEntity) reloaded).getOwner(), "the base-class view agrees");
    }

    @Test
    public void aSaveInTheOldPrivateOwnerShapeStillLoadsItsOwner() {
        CompoundTag oldSave = save(newFiller());
        CompoundTag oldOwner = new CompoundTag();
        oldOwner.putString("name", "Alex");
        oldOwner.putString("uuid", ID.toString());
        oldSave.put("owner", oldOwner);

        TileFiller filler = newFiller();
        filler.readData(ValueIoTestUtil.inputOf(oldSave));

        GameProfile owner = filler.getOwner();
        Assertions.assertNotNull(owner, "an owner saved in the old child shape must still load");
        Assertions.assertEquals(ID, GameProfileUtil.getId(owner));
        Assertions.assertEquals("Alex", GameProfileUtil.getName(owner));

        // Loading, then saving, upgrades the shape; the next load reads the standard keys.
        CompoundTag upgraded = save(filler);
        Assertions.assertEquals(ID.toString(), ValueIoTestUtil.stringOr(upgraded, "ownerUUID", ""));
        Assertions.assertFalse(upgraded.contains("owner"));
        TileFiller again = newFiller();
        again.readData(ValueIoTestUtil.inputOf(upgraded));
        Assertions.assertEquals(owner, again.getOwner());
    }

    @Test
    public void aStandardShapeOwnerWinsOverAStaleOldChild() {
        CompoundTag mixed = save(newFiller());
        mixed.putString("ownerUUID", ID.toString());
        mixed.putString("ownerName", "Steve");
        CompoundTag stale = new CompoundTag();
        stale.putString("name", "Stale");
        stale.putString("uuid", "99999999-2222-3333-4444-555555555555");
        mixed.put("owner", stale);

        TileFiller filler = newFiller();
        filler.readData(ValueIoTestUtil.inputOf(mixed));

        Assertions.assertEquals(new GameProfile(ID, "Steve"), filler.getOwner());
    }

    @Test
    public void anOldChildWithNoUsableIdLeavesTheFillerOwnerless() {
        for (String uuid : new String[] {"", "not-a-uuid"}) {
            CompoundTag oldSave = save(newFiller());
            CompoundTag oldOwner = new CompoundTag();
            oldOwner.putString("name", "Nobody");
            oldOwner.putString("uuid", uuid);
            oldSave.put("owner", oldOwner);

            TileFiller filler = newFiller();
            Assertions.assertDoesNotThrow(() -> filler.readData(ValueIoTestUtil.inputOf(oldSave)),
                    "an unusable old owner id must not break loading (uuid='" + uuid + "')");
            Assertions.assertNull(filler.getOwner(), "uuid='" + uuid + "'");
        }
    }

    @Test
    public void absentOwnerStaysAbsent() {
        TileFiller filler = newFiller();

        filler.readData(ValueIoTestUtil.inputOf(new CompoundTag()));

        Assertions.assertNull(filler.getOwner());
        CompoundTag saved = save(filler);
        Assertions.assertFalse(saved.contains("ownerUUID"));
        Assertions.assertFalse(saved.contains("owner"));
    }
}
