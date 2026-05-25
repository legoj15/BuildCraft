/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.lang.reflect.Field;
import java.util.List;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

import buildcraft.builders.BCBuildersBlocks;

/**
 * Pins the wiring around the two new Builder advancements ({@code paving_the_way} and
 * {@code start_of_something_big}). These tests can only verify the predicates and the NBT
 * shape, not the final advancement award — {@code GameTestHelper.makeMockPlayer} returns a
 * plain {@code Player}, not a {@code ServerPlayer}, and {@code AdvancementUtil.unlockAdvancement}
 * short-circuits silently when handed a non-server player (see the limitation note in
 * {@code CLAUDE.md}). The award step itself needs in-client verification.
 */
public class BuilderAdvancementsTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    private static void setField(TileBuilder tile, String name, Object value) {
        try {
            Field f = TileBuilder.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(tile, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Test reflection failed for field '" + name + "'", e);
        }
    }

    private static Object getField(TileBuilder tile, String name) {
        try {
            Field f = TileBuilder.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(tile);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Test reflection failed for field '" + name + "'", e);
        }
    }

    /** Single-position builder (no path-marker chain consumed) doesn't qualify — paving is path-themed.
     *  This is the common case for a builder placed without a path-marker chain in front of it. */
    public static void testPavingTheWayRejectsSinglePositionBuilder(GameTestHelper helper) {
        BlockPos builderPos = new BlockPos(1, 2, 1);
        helper.setBlock(builderPos, BCBuildersBlocks.BUILDER.get());
        TileBuilder tile = helper.getBlockEntity(builderPos, TileBuilder.class);

        assertTrue(tile.path == null, "fresh builder has no path");
        // basePoses defaults to a single-position list pointing at the block in front; the predicate
        // must say no because there's no path to have paved.
        assertTrue(!tile.shouldGrantPavingTheWay(),
                "shouldGrantPavingTheWay must be false for a builder with no path");
        helper.succeed();
    }

    /** Multi-segment path, builder mid-way through: predicate is false until the last position. */
    public static void testPavingTheWayRejectsMidPath(GameTestHelper helper) {
        BlockPos builderPos = new BlockPos(1, 2, 1);
        helper.setBlock(builderPos, BCBuildersBlocks.BUILDER.get());
        TileBuilder tile = helper.getBlockEntity(builderPos, TileBuilder.class);

        tile.path = ImmutableList.of(
                new BlockPos(10, 64, 20),
                new BlockPos(15, 64, 20),
                new BlockPos(20, 64, 20));
        // Synthesise a 3-position basePoses with the cursor at index 1 (middle).
        List<BlockPos> bp = ImmutableList.of(
                new BlockPos(10, 64, 20),
                new BlockPos(15, 64, 20),
                new BlockPos(20, 64, 20));
        setField(tile, "basePoses", new java.util.ArrayList<>(bp));
        setField(tile, "currentBasePosIndex", 1);

        assertTrue(!tile.shouldGrantPavingTheWay(),
                "shouldGrantPavingTheWay must be false while mid-path (index 1 of 3)");
        helper.succeed();
    }

    /** Multi-segment path, cursor on the last position: predicate fires. */
    public static void testPavingTheWayAcceptsAtLastPosition(GameTestHelper helper) {
        BlockPos builderPos = new BlockPos(1, 2, 1);
        helper.setBlock(builderPos, BCBuildersBlocks.BUILDER.get());
        TileBuilder tile = helper.getBlockEntity(builderPos, TileBuilder.class);

        tile.path = ImmutableList.of(
                new BlockPos(10, 64, 20),
                new BlockPos(15, 64, 20),
                new BlockPos(20, 64, 20));
        List<BlockPos> bp = ImmutableList.of(
                new BlockPos(10, 64, 20),
                new BlockPos(15, 64, 20),
                new BlockPos(20, 64, 20));
        setField(tile, "basePoses", new java.util.ArrayList<>(bp));
        setField(tile, "currentBasePosIndex", 2);

        assertTrue(tile.shouldGrantPavingTheWay(),
                "shouldGrantPavingTheWay must be true at the final base position of a multi-segment path");
        helper.succeed();
    }

    /**
     * BIG_STRUCTURE_THRESHOLD is the public contract from spec ("1024 blocks or more"). If this
     * number ever drifts in code, the test reminds the author to update the user-facing docs /
     * advancement description / changelog. Treat the constant as part of the API surface.
     */
    public static void testBigStructureThresholdMatchesSpec(GameTestHelper helper) {
        assertTrue(TileBuilder.BIG_STRUCTURE_THRESHOLD == 1024L,
                "BIG_STRUCTURE_THRESHOLD must be 1024 (the spec'd value) — got " + TileBuilder.BIG_STRUCTURE_THRESHOLD);
        helper.succeed();
    }

    /**
     * Builder NBT round-trip pins the new persistent advancement state (counter + both latches +
     * the wasDoneLastTick edge memory). Without persistence, a chunk unload mid-build would
     * either lose progress toward start_of_something_big (counter resets to 0, slow path-marker
     * builds would never reach the threshold) or re-grant a freshly-loaded latched advancement.
     */
    public static void testNbtRoundTripPreservesAdvancementState(GameTestHelper helper) {
        BlockPos builderPos = new BlockPos(1, 2, 1);
        helper.setBlock(builderPos, BCBuildersBlocks.BUILDER.get());
        TileBuilder tile = helper.getBlockEntity(builderPos, TileBuilder.class);

        // Seed non-default values for every persisted advancement field.
        setField(tile, "bigStructureCellsBuilt", 800L);
        setField(tile, "pavingTheWayGranted", Boolean.TRUE);
        setField(tile, "startOfSomethingBigGranted", Boolean.FALSE);
        setField(tile, "wasDoneLastTick", Boolean.TRUE);

        ServerLevel level = helper.getLevel();
        CompoundTag tag = tile.saveCustomOnly(level.registryAccess());
        // Clobber the live fields, then reload from the tag to prove the values came from NBT.
        setField(tile, "bigStructureCellsBuilt", 0L);
        setField(tile, "pavingTheWayGranted", Boolean.FALSE);
        setField(tile, "startOfSomethingBigGranted", Boolean.TRUE);
        setField(tile, "wasDoneLastTick", Boolean.FALSE);
        tile.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));

        assertTrue(((Long) getField(tile, "bigStructureCellsBuilt")) == 800L,
                "bigStructureCellsBuilt must round-trip — got " + getField(tile, "bigStructureCellsBuilt"));
        assertTrue(((Boolean) getField(tile, "pavingTheWayGranted")),
                "pavingTheWayGranted must round-trip as true");
        assertTrue(!((Boolean) getField(tile, "startOfSomethingBigGranted")),
                "startOfSomethingBigGranted must round-trip as false");
        assertTrue(((Boolean) getField(tile, "wasDoneLastTick")),
                "wasDoneLastTick must round-trip as true");
        helper.succeed();
    }

    /** Default values on a brand-new Builder: counter 0, both latches false, edge-memory false. */
    public static void testFreshBuilderAdvancementStateIsZero(GameTestHelper helper) {
        BlockPos builderPos = new BlockPos(1, 2, 1);
        helper.setBlock(builderPos, BCBuildersBlocks.BUILDER.get());
        TileBuilder tile = helper.getBlockEntity(builderPos, TileBuilder.class);

        assertTrue(((Long) getField(tile, "bigStructureCellsBuilt")) == 0L,
                "fresh builder bigStructureCellsBuilt must be 0");
        assertTrue(!((Boolean) getField(tile, "pavingTheWayGranted")),
                "fresh builder pavingTheWayGranted must be false");
        assertTrue(!((Boolean) getField(tile, "startOfSomethingBigGranted")),
                "fresh builder startOfSomethingBigGranted must be false");
        assertTrue(!((Boolean) getField(tile, "wasDoneLastTick")),
                "fresh builder wasDoneLastTick must be false");
        helper.succeed();
    }
}
