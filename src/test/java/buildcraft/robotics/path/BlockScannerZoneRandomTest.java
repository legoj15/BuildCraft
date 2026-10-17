/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IZone;
import buildcraft.robotics.zone.ZonePlan;

/** {@link BlockScannerZoneRandom}: the zone-biased random scanner a robot with a work zone searches with. A zone
 *  that has nothing to sample from (an empty {@link ZonePlan} reports a null pick) must end the scan rather than
 *  throw on every tick, and the height it samples must be the robot level's whole build range (the 26.x worlds run
 *  from -64 to 319), not 7.1.x's fixed 0..254. */
public class BlockScannerZoneRandomTest {

    private static final BlockPos ORIGIN = new BlockPos(3, 70, -5);

    @Test
    public void aZoneWithNothingToSampleEndsTheScan() {
        IZone empty = new IZone() {
            @Override
            public double distanceTo(BlockPos pos) {
                return Double.MAX_VALUE;
            }

            @Override
            public double distanceToSquared(BlockPos pos) {
                return Double.MAX_VALUE;
            }

            @Override
            public boolean contains(Vec3 point) {
                return false;
            }

            @Override
            public BlockPos getRandomBlockPos(Random rand) {
                return null;
            }
        };
        BlockScannerZoneRandom scanner = new BlockScannerZoneRandom(ORIGIN, RandomSource.create(1L), empty, -64, 319);

        Assertions.assertFalse(scanner.hasNext(), "a zone with nothing to pick from has nothing to scan");
    }

    @Test
    public void anEmptyPlanEndsTheScan() {
        BlockScannerZoneRandom scanner = new BlockScannerZoneRandom(ORIGIN, RandomSource.create(1L), new ZonePlan(),
                -64, 319);

        Assertions.assertFalse(scanner.hasNext());
    }

    @Test
    public void samplesSpanTheGivenBuildRange() {
        ZonePlan plan = new ZonePlan();
        plan.set(10, 10, true);
        BlockScannerZoneRandom scanner = new BlockScannerZoneRandom(ORIGIN, RandomSource.create(7L), plan, -64, 319);

        boolean below = false;
        boolean above = false;
        for (int i = 0; i < 4000; i++) {
            Assertions.assertTrue(scanner.hasNext());
            BlockPos pos = scanner.next().offset(ORIGIN);
            Assertions.assertEquals(10, pos.getX(), "the column is the zone's");
            Assertions.assertEquals(10, pos.getZ(), "the column is the zone's");
            Assertions.assertTrue(pos.getY() >= -64 && pos.getY() <= 319, "y within the build range: " + pos);
            below |= pos.getY() < 0;
            above |= pos.getY() > 254;
        }
        Assertions.assertTrue(below, "the scan reaches below y=0");
        Assertions.assertTrue(above, "the scan reaches above 7.1.x's y=254");
    }
}
