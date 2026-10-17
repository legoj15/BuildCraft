/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.robots;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.EnumPipePart;

/** The saved resource ids keep the side they were taken on: a block claim or a request slot on one face of a
 *  pipe is not the same claim as one on another face, so a reload that folded every side into the centre merged
 *  claims that were distinct (and made a station's request slot equal to its sibling's). */
public class ResourceIdSideNbtTest {

    @Test
    public void aBlockIdKeepsItsSide() {
        ResourceIdBlock id = new ResourceIdBlock(new BlockPos(3, 64, -7));
        id.side = EnumPipePart.NORTH;

        CompoundTag nbt = new CompoundTag();
        id.writeToNbt(nbt);
        ResourceId loaded = ResourceId.load(nbt);

        Assertions.assertEquals(id, loaded, "the block claim must reload as the same claim, side included");
        Assertions.assertSame(EnumPipePart.NORTH, ((ResourceIdBlock) loaded).side);
    }

    @Test
    public void aRequestIdKeepsItsSideAndSlot() {
        ResourceIdRequest id = new ResourceIdRequest();
        id.pos = new BlockPos(1, 2, 3);
        id.side = EnumPipePart.UP;

        CompoundTag nbt = new CompoundTag();
        id.writeToNbt(nbt);
        ResourceId loaded = ResourceId.load(nbt);

        Assertions.assertEquals(id, loaded, "the request id must reload as the same id");
        Assertions.assertSame(EnumPipePart.UP, ((ResourceIdRequest) loaded).side);
    }

    @Test
    public void differentSidesStayDifferentAfterAReload() {
        ResourceIdBlock down = new ResourceIdBlock(new BlockPos(5, 5, 5));
        down.side = EnumPipePart.DOWN;
        ResourceIdBlock east = new ResourceIdBlock(new BlockPos(5, 5, 5));
        east.side = EnumPipePart.EAST;

        CompoundTag downNbt = new CompoundTag();
        down.writeToNbt(downNbt);
        CompoundTag eastNbt = new CompoundTag();
        east.writeToNbt(eastNbt);

        Assertions.assertNotEquals(ResourceId.load(downNbt), ResourceId.load(eastNbt),
                "claims on different faces of one block are distinct claims");
    }
}
