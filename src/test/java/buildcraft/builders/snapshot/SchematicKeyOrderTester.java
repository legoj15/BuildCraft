/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * A snapshot's key is a hash of its NBT, and the Electronic Library re-keys every upload in the receiving JVM and
 * refuses one whose hash differs from the key it asked for. A schematic's {@code canBeReplacedWithBlocks} is a set of
 * {@link Block}s, which hash by identity, so writing it in set order made the hash differ between JVMs (a client and
 * its dedicated server, or one game before and after a restart) for every block with two or more replaceable blocks
 * (pistons, any rule whose place block differs). The list must be written in one canonical order.
 */
public class SchematicKeyOrderTester {

    @Test
    public void canBeReplacedWithBlocksIsWrittenInRegistryIdOrder() {
        SchematicBlockDefault schematic = new SchematicBlockDefault();
        schematic.blockState = Blocks.PISTON.defaultBlockState();
        schematic.placeBlock = Blocks.PISTON;
        schematic.tileRotation = Rotation.NONE;
        // Eight blocks: set order matching id order by chance is 1 in 40320.
        List<Block> blocks = List.of(Blocks.STONE, Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.MOVING_PISTON,
            Blocks.PISTON_HEAD, Blocks.AIR, Blocks.DIRT, Blocks.OAK_LOG);
        schematic.canBeReplacedWithBlocks.addAll(blocks);

        List<String> written = NBTUtilBC.readStringList(schematic.serializeNbt().get("canBeReplacedWithBlocks")).toList();
        List<String> sorted = blocks.stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).toString()).sorted().toList();
        Assertions.assertEquals(sorted, written);
    }
}
