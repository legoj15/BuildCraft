/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.Iterator;

import net.minecraft.core.BlockPos;

/**
 * The default block-scanner iterator for {@link PathFindingSearch}: steps through each block of a hollow
 * cube shell, starting at radius 1 and growing to 64, so nearby targets are found before distant ones.
 * Ported from 7.1.x {@code buildcraft.core.lib.utils.BlockScannerExpanding} (the upstream file carried no
 * copyright notice; the logic is carried over 1:1).
 *
 * <p>Yields position DELTAS (as {@link BlockPos}), not absolute positions — {@code PathFindingSearch} adds
 *  each delta to the search start, exactly as 7.1.x did with {@code BlockIndex} deltas.
 */
public class BlockScannerExpanding implements Iterator<BlockPos> {

    private int searchRadius;
    private int searchX;
    private int searchY;
    private int searchZ;

    public BlockScannerExpanding() {
        searchRadius = 1;
        searchX = -1;
        searchY = -1;
        searchZ = -1;
    }

    @Override
    public boolean hasNext() {
        return searchRadius < 64;
    }

    @Override
    public BlockPos next() {
        // Step through each block in a hollow cube of size (searchRadius * 2 - 1); when done, grow the
        // radius and start over.
        BlockPos next = new BlockPos(searchX, searchY, searchZ);

        // Step to the next Y
        if (Math.abs(searchX) == searchRadius || Math.abs(searchZ) == searchRadius) {
            searchY += 1;
        } else {
            searchY += searchRadius * 2;
        }

        if (searchY > searchRadius) {
            // Step to the next Z
            searchY = -searchRadius;
            searchZ += 1;

            if (searchZ > searchRadius) {
                // Step to the next X
                searchZ = -searchRadius;
                searchX += 1;

                if (searchX > searchRadius) {
                    // Step to the next radius
                    searchRadius += 1;
                    searchX = -searchRadius;
                    searchY = -searchRadius;
                    searchZ = -searchRadius;
                }
            }
        }
        return next;
    }
}
