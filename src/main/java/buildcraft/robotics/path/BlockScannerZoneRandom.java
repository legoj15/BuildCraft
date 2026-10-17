/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import buildcraft.api.core.IZone;

/**
 * The zone-biased block-scanner iterator for {@link PathFindingSearch}: an unbounded stream of uniform
 * random cells inside the robot's work {@link IZone}, reported as DELTAS from the search origin so
 * {@code PathFindingSearch} can add them to the start as it does for every scanner. Ported from 7.1.x
 * {@code buildcraft.core.lib.utils.BlockScannerZoneRandom} (the upstream file carried no copyright notice;
 * the logic is carried over 1:1, with {@code zone.getRandomBlockIndex(rand)} mapped onto the modern
 * {@link IZone#getRandomBlockPos(Random, int, int)}).
 *
 * <p>Two departures from 7.1.x: the height a column-only zone (a {@code ZonePlan}) is sampled at is the robot
 * level's build range {@code minY..maxY}, not the old fixed 0..254; and a zone with nothing to pick from (its sample
 * is null) ends the scan instead of throwing on every tick.
 *
 * <p>{@code IZone} predates the level's {@link RandomSource} and still draws from a {@link Random}, so the
 * constructor seeds one from the level's source — each search gets its own zone distribution.
 */
public class BlockScannerZoneRandom implements Iterator<BlockPos> {

    private final Random rand;
    private final IZone zone;
    private final BlockPos origin;
    private final int minY;
    private final int maxY;
    /** The sample {@link #hasNext} drew and {@link #next} has not handed out yet. */
    private BlockPos pending;
    private boolean exhausted;

    /** @param minY the robot level's lowest buildable y, inclusive
     *  @param maxY the robot level's highest buildable y, inclusive */
    public BlockScannerZoneRandom(BlockPos origin, RandomSource levelRandom, IZone zone, int minY, int maxY) {
        this.origin = origin;
        this.rand = new Random(levelRandom.nextLong());
        this.zone = zone;
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    public boolean hasNext() {
        if (pending == null && !exhausted) {
            pending = zone.getRandomBlockPos(rand, minY, maxY);
            exhausted = pending == null;
        }
        return pending != null;
    }

    @Override
    public BlockPos next() {
        if (!hasNext()) {
            throw new NoSuchElementException("the zone has nothing to sample");
        }
        BlockPos block = pending;
        pending = null;
        return block.subtract(origin);
    }
}
