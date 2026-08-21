/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.Iterator;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The random block-scanner iterator for {@link PathFindingSearch}: an unbounded stream of uniformly
 * random cells inside a sphere of {@code maxDistance} around the search start (polar/azimuth sampling,
 * as 7.1.x). Ported from 7.1.x {@code buildcraft.core.lib.utils.BlockScannerRandom} (the upstream file
 * carried no copyright notice; the logic is carried over 1:1).
 *
 * <p>Yields position DELTAS (as {@link BlockPos}), not absolute positions — {@code PathFindingSearch} adds
 * each delta to the search start.
 */
public class BlockScannerRandom implements Iterator<BlockPos> {

    private final RandomSource rand;
    private final int maxDistance;

    public BlockScannerRandom(RandomSource rand, int maxDistance) {
        this.rand = rand;
        this.maxDistance = maxDistance;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public BlockPos next() {
        double radius = rand.nextFloat() * maxDistance;
        float polarAngle = rand.nextFloat() * 2.0F * (float) Math.PI;
        float azimuthAngle = rand.nextFloat() * (float) Math.PI;

        int searchX = (int) (radius * Mth.cos(polarAngle) * Mth.sin(azimuthAngle));
        int searchY = (int) (radius * Mth.cos(azimuthAngle));
        int searchZ = (int) (radius * Mth.sin(polarAngle) * Mth.sin(azimuthAngle));

        return new BlockPos(searchX, searchY, searchZ);
    }
}
