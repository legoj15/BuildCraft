/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.Iterator;
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
 * {@link IZone#getRandomBlockPos(java.util.Random)}).
 *
 * <p>{@code IZone} predates the level's {@link RandomSource} and still draws from a {@link Random}, so the
 * constructor seeds one from the level's source — each search gets its own zone distribution.
 */
public class BlockScannerZoneRandom implements Iterator<BlockPos> {

    private final Random rand;
    private final IZone zone;
    private final BlockPos origin;

    public BlockScannerZoneRandom(BlockPos origin, RandomSource levelRandom, IZone zone) {
        this.origin = origin;
        this.rand = new Random(levelRandom.nextLong());
        this.zone = zone;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public BlockPos next() {
        BlockPos block = zone.getRandomBlockPos(rand);
        return block.subtract(origin);
    }
}
