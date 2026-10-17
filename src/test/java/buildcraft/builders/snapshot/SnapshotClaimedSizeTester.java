/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.BitSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * A snapshot's size is read from the file (or from an upload) and its cell count is that size's product, so a size
 * whose product is negative or does not fit an int must be refused as bad data before anything is sized from it,
 * and a blueprint's cell list must be checked against the size before its cells are allocated.
 */
public class SnapshotClaimedSizeTester {

    private static Blueprint blueprint() {
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(2, 1, 1);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(new SchematicBlockAir());
        blueprint.data = new int[] {0, 0};
        blueprint.computeKey();
        return blueprint;
    }

    private static Template template() {
        Template template = new Template();
        template.size = new BlockPos(2, 1, 1);
        template.facing = Direction.NORTH;
        template.offset = BlockPos.ZERO;
        template.data = new BitSet();
        template.data.set(1);
        template.computeKey();
        return template;
    }

    private static CompoundTag claiming(Snapshot snapshot, BlockPos size) {
        CompoundTag nbt = Snapshot.writeToNBT(snapshot);
        nbt.put("size", NBTUtilBC.writeBlockPos(size));
        return nbt;
    }

    @Test
    public void aWellFormedBlueprintAndTemplateStillRead() throws InvalidInputDataException {
        Blueprint blueprint = blueprint();
        Snapshot readBlueprint = Snapshot.readFromNBT(Snapshot.writeToNBT(blueprint));
        Assertions.assertArrayEquals(blueprint.data, ((Blueprint) readBlueprint).data);
        Template template = template();
        Snapshot readTemplate = Snapshot.readFromNBT(Snapshot.writeToNBT(template));
        Assertions.assertEquals(template.data, ((Template) readTemplate).data);
    }

    @Test
    public void aBlueprintWhoseCellsOutnumberItsListIsRefusedWithoutAllocatingThem() {
        // 1290^3 fits an int: unchecked, this was an 8 GiB int[] before the length check ran.
        CompoundTag nbt = claiming(blueprint(), new BlockPos(1290, 1290, 1290));
        nbt.put("data", new ListTag());
        Assertions.assertThrows(InvalidInputDataException.class, () -> Snapshot.readFromNBT(nbt));
    }

    @Test
    public void aSizeWhoseCellCountOverflowsAnIntIsRefused() {
        // 2048 * 2048 * 1024 = 2^32, which wraps to 0 in int arithmetic and matched an empty data list.
        BlockPos wraps = new BlockPos(2048, 2048, 1024);
        CompoundTag nbt = claiming(blueprint(), wraps);
        nbt.put("data", new ListTag());
        Assertions.assertThrows(InvalidInputDataException.class, () -> Snapshot.readFromNBT(nbt));
        Assertions.assertThrows(InvalidInputDataException.class, () -> Snapshot.readFromNBT(claiming(template(), wraps)));
    }

    @Test
    public void aNegativeSizeIsRefused() {
        BlockPos negative = new BlockPos(-2, 1, 1);
        Assertions.assertThrows(InvalidInputDataException.class, () -> Snapshot.readFromNBT(claiming(blueprint(), negative)));
        Assertions.assertThrows(InvalidInputDataException.class, () -> Snapshot.readFromNBT(claiming(template(), negative)));
    }
}
