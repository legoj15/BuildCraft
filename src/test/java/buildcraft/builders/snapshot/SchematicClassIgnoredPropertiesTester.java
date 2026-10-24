/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.InvalidInputDataException;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * A snapshot's key is a hash of its NBT, and the library refuses an upload whose re-computed key differs from the one
 * it asked for, while the store re-keys a file whose loaded form hashes differently. So what a scan writes must be what
 * a store round-trip writes back. The ignored properties that depend on the block's class (a stair's {@code shape})
 * are applied on a read-back from the place block that was read; a capture that works them out before it knows its
 * place block saved a stair with {@code shape} not ignored, and the same stair read back ignored it.
 */
public class SchematicClassIgnoredPropertiesTester {

    private static List<String> writtenIgnoredProperties(SchematicBlockDefault schematic) {
        return NBTUtilBC.readStringList(schematic.serializeNbt().get("ignoredProperties")).toList();
    }

    private static SchematicBlockDefault readBack(CompoundTag written) throws InvalidInputDataException {
        SchematicBlockDefault read = new SchematicBlockDefault();
        read.deserializeNbt(written);
        return read;
    }

    private static Blueprint oneCell(SchematicBlockDefault schematic) {
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, 1);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(schematic);
        blueprint.data = new int[] {0};
        blueprint.computeKey();
        return blueprint;
    }

    @Test
    public void aCapturedStairIgnoresItsShape() {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
        Assertions.assertTrue(writtenIgnoredProperties(SchematicBlockDefault.ofBareState(stairs, true)).contains("shape"),
            "vanilla recomputes a stair's corner shape from its neighbours, so a capture must not compare it");
        // The negative control: a slab is no stair, and its properties are all placed as captured.
        Assertions.assertEquals(List.of(),
            writtenIgnoredProperties(SchematicBlockDefault.ofBareState(Blocks.OAK_SLAB.defaultBlockState(), true)));
    }

    @Test
    public void aCapturedStairIsWrittenTheSameAfterAStoreRoundTrip() throws InvalidInputDataException {
        SchematicBlockDefault captured = SchematicBlockDefault.ofBareState(Blocks.OAK_STAIRS.defaultBlockState(), true);
        CompoundTag written = captured.serializeNbt();
        Assertions.assertEquals(written, readBack(written).serializeNbt());
    }

    @Test
    public void aCapturedStairKeepsItsSnapshotKeyThroughTheStore() throws InvalidInputDataException {
        Blueprint scanned = oneCell(SchematicBlockDefault.ofBareState(Blocks.OAK_STAIRS.defaultBlockState(), true));
        Snapshot loaded = Snapshot.readFromNBT(Snapshot.writeToNBT(scanned));
        loaded.computeKey();
        Assertions.assertArrayEquals(scanned.key.hash, loaded.key.hash,
            "the library re-keys an upload and the store re-keys a file: a changed hash is refused / re-filed");
    }
}
