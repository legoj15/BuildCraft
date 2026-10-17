/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.tile;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.test.ValueIoTestUtil;
import buildcraft.transport.BCTransportBlocks;

/** The pipe holder keeps its owner in {@code ownerUUID}/{@code ownerName}. Reading a save that has the id but no name
 *  used to bake the English word "Unknown" into the profile (and so back into the next save). */
public class TilePipeHolderOwnerNbtTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static TilePipeHolder newHolder() {
        return new TilePipeHolder(BlockPos.ZERO, BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState());
    }

    private static CompoundTag save(TilePipeHolder holder) {
        return ValueIoTestUtil.write(holder::writeData);
    }

    @Test
    public void namedOwnerSurvivesASaveLoadCycle() {
        CompoundTag stored = ValueIoTestUtil.write(out -> {
            out.putString("ownerUUID", ID.toString());
            out.putString("ownerName", "Steve");
        });
        TilePipeHolder holder = newHolder();
        holder.readData(ValueIoTestUtil.inputOf(stored));

        TilePipeHolder reloaded = newHolder();
        reloaded.readData(ValueIoTestUtil.inputOf(save(holder)));

        GameProfile owner = reloaded.getOwner();
        Assertions.assertNotNull(owner);
        Assertions.assertEquals(ID, GameProfileUtil.getId(owner));
        Assertions.assertEquals("Steve", GameProfileUtil.getName(owner));
    }

    @Test
    public void storedIdWithoutNameDoesNotInventAnEnglishName() {
        CompoundTag onlyId = ValueIoTestUtil.write(out -> out.putString("ownerUUID", ID.toString()));
        TilePipeHolder holder = newHolder();

        holder.readData(ValueIoTestUtil.inputOf(onlyId));

        GameProfile owner = holder.getOwner();
        Assertions.assertNotNull(owner);
        Assertions.assertEquals(ID, GameProfileUtil.getId(owner));
        Assertions.assertEquals("", GameProfileUtil.getName(owner));
        Assertions.assertNotEquals("Unknown", ValueIoTestUtil.stringOr(save(holder), "ownerName", ""),
                "the load -> save cycle must not bake a literal English name into the save");
    }

    @Test
    public void absentOwnerStaysAbsent() {
        TilePipeHolder holder = newHolder();

        holder.readData(ValueIoTestUtil.inputOf(new CompoundTag()));

        Assertions.assertNull(holder.getOwner());
        Assertions.assertFalse(save(holder).contains("ownerUUID"));
    }
}
