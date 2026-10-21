/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

import buildcraft.api.core.InvalidInputDataException;

import buildcraft.builders.snapshot.SchematicBlockFluid;

/**
 * Block states BuildCraft keeps in its own NBT (blueprints, snapshots, facades) must decode whichever
 * spelling of the keys they were written with. Minecraft 26.3 renamed {@code Name}/{@code Properties} to
 * {@code id}/{@code properties} and its reader accepts only the new spelling, so a plain
 * {@code NbtUtils.readBlockState} turns everything saved on 26.2 or earlier into air.
 */
public class BlockStateNbtTester {
    private static final BlockState STAIRS = Blocks.OAK_STAIRS.defaultBlockState()
        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
        .setValue(BlockStateProperties.HALF, Half.TOP);

    /** Hand-built so the expected spelling does not come from the code under test. */
    private static CompoundTag stairsTag(String idKey, String propertiesKey) {
        CompoundTag properties = new CompoundTag();
        properties.putString("facing", "east");
        properties.putString("half", "top");
        properties.putString("shape", "straight");
        properties.putString("waterlogged", "false");
        CompoundTag tag = new CompoundTag();
        tag.putString(idKey, "minecraft:oak_stairs");
        tag.put(propertiesKey, properties);
        return tag;
    }

    @Test
    public void bothKeySpellingsDecodeToTheSameState() {
        Assertions.assertEquals(STAIRS, BlockStateNbt.read(stairsTag("Name", "Properties")),
            "pre-26.3 spelling (Name/Properties) must decode on this node");
        Assertions.assertEquals(STAIRS, BlockStateNbt.read(stairsTag("id", "properties")),
            "26.3 spelling (id/properties) must decode on this node");
    }

    @Test
    public void writtenStateRoundTrips() {
        Assertions.assertEquals(STAIRS, BlockStateNbt.read(NbtUtils.writeBlockState(STAIRS)));
        // A state with no properties writes no properties tag at all on 26.3; keep that path covered.
        BlockState stone = Blocks.STONE.defaultBlockState();
        Assertions.assertEquals(stone, BlockStateNbt.read(NbtUtils.writeBlockState(stone)));
    }

    /**
     * Snapshot hashes are taken over the snapshot's own NBT (the Electronic Library re-keys every upload and refuses
     * one whose hash differs from the key it asked for), so a schematic must spell its block state the same way on
     * every node, or a blueprint saved on 26.2 is refused on 26.3. The canonical spelling is the pre-26.3 one.
     */
    @Test
    public void schematicsWriteTheSameSpellingOnEveryNode() throws InvalidInputDataException {
        CompoundTag saved = new CompoundTag();
        saved.put("blockState", stairsTag("id", "properties"));
        saved.putBoolean("isFlowing", false);
        SchematicBlockFluid schematic = new SchematicBlockFluid();
        schematic.deserializeNbt(saved);
        Assertions.assertEquals(stairsTag("Name", "Properties"),
            NBTUtilBC.getCompound(schematic.serializeNbt(), "blockState"));
    }

    /** One real read site, fed the spelling that is not the running node's. */
    @Test
    public void schematicBlockFluidReadsEitherSpelling() throws InvalidInputDataException {
        BlockState water = Blocks.WATER.defaultBlockState();
        for (String[] keys : new String[][] { { "Name", "Properties" }, { "id", "properties" } }) {
            CompoundTag state = new CompoundTag();
            state.putString(keys[0], "minecraft:water");
            CompoundTag properties = new CompoundTag();
            properties.putString("level", "0");
            state.put(keys[1], properties);
            CompoundTag saved = new CompoundTag();
            saved.put("blockState", state);
            saved.putBoolean("isFlowing", false);

            SchematicBlockFluid schematic = new SchematicBlockFluid();
            schematic.deserializeNbt(saved);
            Assertions.assertEquals(water, schematic.getBlockStateForRender(), "keys " + keys[0] + "/" + keys[1]);
        }
    }
}
