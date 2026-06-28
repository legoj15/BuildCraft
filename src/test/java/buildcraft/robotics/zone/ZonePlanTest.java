/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * Pure-JUnit characterization of {@link ZonePlan}: the chunk-keyed mosaic of {@link ZoneChunk}s that backs one dye
 * layer of the Zone Planner. Exercises set/get, negative-coordinate bucketing ({@code >> 4} / {@code & 0xF}),
 * empty-chunk pruning, {@code distanceToSquared}, and the NBT/ByteBuf round-trips that the map-streaming network and
 * tile persistence rely on. {@code ZonePlan} keys chunks by packed {@code long}, so it pulls in no vanilla type
 * needing a registry bootstrap — these run as plain JUnit on every node.
 */
public class ZonePlanTest {

    private static Set<String> cells(ZonePlan plan) {
        Set<String> out = new HashSet<>();
        for (int[] p : plan.getAll()) {
            out.add(p[0] + "," + p[1]);
        }
        return out;
    }

    @Test
    public void testSetGetSingleBlock() {
        ZonePlan plan = new ZonePlan();
        Assertions.assertFalse(plan.get(10, 20), "fresh plan is empty");

        plan.set(10, 20, true);
        Assertions.assertTrue(plan.get(10, 20));
        Assertions.assertFalse(plan.get(20, 10), "x/z are not interchangeable");
    }

    @Test
    public void testNegativeCoordinateBucketing() {
        ZonePlan plan = new ZonePlan();

        // -1 lives in chunk -1 at local 15 (arithmetic >>4 = -1, & 0xF = 15).
        plan.set(-1, -1, true);
        Assertions.assertTrue(plan.get(-1, -1), "negative block round-trips through >>4 / &0xF");
        Assertions.assertFalse(plan.get(0, 0), "the positive neighbour in chunk 0 is untouched");
        Assertions.assertTrue(plan.hasChunk(-1, -1), "it bucketed into chunk (-1, -1)");

        // -16 is the first column of chunk -1; -17 falls into chunk -2 at local 15.
        plan.set(-16, -16, true);
        plan.set(-17, -17, true);
        Assertions.assertTrue(plan.hasChunk(-1, -1));
        Assertions.assertTrue(plan.hasChunk(-2, -2));
        Assertions.assertTrue(plan.get(-16, -16));
        Assertions.assertTrue(plan.get(-17, -17));
    }

    @Test
    public void testEmptyChunkIsPruned() {
        ZonePlan plan = new ZonePlan();
        plan.set(5, 5, true);
        Assertions.assertTrue(plan.hasChunk(0, 0));

        plan.set(5, 5, false);
        Assertions.assertFalse(plan.hasChunk(0, 0), "clearing the last cell prunes the chunk entirely");
        Assertions.assertTrue(plan.getChunkMapping().isEmpty());
    }

    @Test
    public void testSettingFalseOnMissingChunkIsNoOp() {
        ZonePlan plan = new ZonePlan();
        Assertions.assertDoesNotThrow(() -> plan.set(100, 100, false));
        Assertions.assertTrue(plan.getChunkMapping().isEmpty(), "no phantom chunk is created");
    }

    @Test
    public void testFillThenClearOneCellLeaves255InThatChunk() {
        ZonePlan plan = new ZonePlan();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                plan.set(x, z, true);
            }
        }
        plan.set(5, 9, false);

        Assertions.assertEquals(255, cells(plan).size(), "the flip fix holds through the ZonePlan layer too");
        Assertions.assertTrue(plan.get(15, 15), "the corner column survives demotion");
        Assertions.assertFalse(plan.get(5, 9));
    }

    @Test
    public void testFillThenClearWholeChunkPrunes() {
        ZonePlan plan = new ZonePlan();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                plan.set(x, z, true);
            }
        }
        Assertions.assertTrue(plan.hasChunk(0, 0));

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                plan.set(x, z, false);
            }
        }
        Assertions.assertFalse(plan.hasChunk(0, 0), "emptying a promoted chunk still prunes it");
    }

    @Test
    public void testGetAllReportsExactlyTheSetBlocks() {
        ZonePlan plan = new ZonePlan();
        plan.set(0, 0, true);
        plan.set(31, 47, true);
        plan.set(-5, -9, true);

        Assertions.assertEquals(Set.of("0,0", "31,47", "-5,-9"), cells(plan));
    }

    @Test
    public void testGetWithOffsetTranslatesEveryCell() {
        ZonePlan plan = new ZonePlan();
        plan.set(0, 0, true);
        plan.set(1, 0, true);

        ZonePlan shifted = plan.getWithOffset(100, 200);
        Assertions.assertEquals(Set.of("100,200", "101,200"), cells(shifted));
        Assertions.assertEquals(Set.of("0,0", "1,0"), cells(plan), "the source plan is unchanged");
    }

    @Test
    public void testCopyConstructorIsDeep() {
        ZonePlan original = new ZonePlan();
        original.set(3, 3, true);

        ZonePlan copy = new ZonePlan(original);
        original.set(8, 8, true);

        Assertions.assertEquals(Set.of("3,3"), cells(copy), "the copy is snapshotted, not aliased");
    }

    @Test
    public void testDistanceToSquaredUsesChunkCentres() {
        ZonePlan plan = new ZonePlan();
        plan.set(0, 0, true); // chunk (0,0), centre at block (8, 8)

        Assertions.assertEquals(0.0, plan.distanceToSquared(new BlockPos(8, 64, 8)), 1e-9,
                "distance to the chunk centre is zero");
        Assertions.assertEquals(128.0, plan.distanceToSquared(new BlockPos(0, 64, 0)), 1e-9,
                "8^2 + 8^2 from the chunk centre");
    }

    @Test
    public void testDistanceToSquaredTakesTheNearestChunk() {
        ZonePlan plan = new ZonePlan();
        plan.set(0, 0, true);      // centre (8, 8)
        plan.set(1000, 1000, true); // far away

        Assertions.assertEquals(0.0, plan.distanceToSquared(new BlockPos(8, 64, 8)), 1e-9,
                "the minimum over chunks wins");
    }

    @Test
    public void testDistanceToSquaredOnEmptyPlanIsMaxValue() {
        Assertions.assertEquals(Double.MAX_VALUE, new ZonePlan().distanceToSquared(BlockPos.ZERO), 0.0);
    }

    @Test
    public void testContainsFloorsTheVector() {
        ZonePlan plan = new ZonePlan();
        plan.set(3, 7, true);

        Assertions.assertTrue(plan.contains(new Vec3(3.9, 64, 7.1)), "(3.9, 7.1) floors into the set cell (3, 7)");
        Assertions.assertFalse(plan.contains(new Vec3(3.9, 64, 8.1)), "(_, 8.1) floors to z=8, which is clear");
    }

    @Test
    public void testNbtRoundTripAcrossMultipleChunks() {
        ZonePlan before = build(new int[][] { {0, 0}, {31, 47}, {-5, -9}, {-100, 250} });

        CompoundTag tag = new CompoundTag();
        before.writeToNBT(tag);

        ZonePlan after = new ZonePlan();
        after.readFromNBT(tag);

        Assertions.assertEquals(cells(before), cells(after), "every marked block survives the NBT round-trip");
    }

    @Test
    public void testByteBufRoundTripAcrossMultipleChunks() {
        ZonePlan before = build(new int[][] { {0, 0}, {17, 3}, {-1, -1}, {200, -200} });

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        before.writeToByteBuf(buf);

        ZonePlan after = new ZonePlan().readFromByteBuf(buf);

        Assertions.assertEquals(cells(before), cells(after), "every marked block survives the ByteBuf round-trip");
        Assertions.assertEquals(0, buf.readableBytes(), "the reader consumes exactly what the writer produced");
    }

    @Test
    public void testRandomBlockPosLandsInsideTheZone() {
        ZonePlan plan = build(new int[][] { {5, 5}, {-30, 40} });

        Random rand = new Random(99L);
        for (int i = 0; i < 50; i++) {
            BlockPos p = plan.getRandomBlockPos(rand);
            Assertions.assertNotNull(p);
            Assertions.assertTrue(plan.get(p.getX(), p.getZ()),
                    "a random pick is a block that is actually inside the zone");
        }
    }

    @Test
    public void testRandomBlockPosOnEmptyPlanIsNull() {
        Assertions.assertNull(new ZonePlan().getRandomBlockPos(new Random(0L)));
    }

    private static ZonePlan build(int[][] blocks) {
        ZonePlan plan = new ZonePlan();
        for (int[] b : blocks) {
            plan.set(b[0], b[1], true);
        }
        // sanity: the inputs are distinct
        List<int[]> all = plan.getAll();
        Assertions.assertEquals(blocks.length, all.size(), "test inputs must be distinct cells");
        return plan;
    }
}
