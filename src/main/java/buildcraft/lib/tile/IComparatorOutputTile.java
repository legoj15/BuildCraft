/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

/**
 * A tile whose block drives a redstone comparator. The block bases ({@code BlockBCTile_Neptune},
 * {@code BlockBCTile_Directional}) answer {@code getAnalogOutputSignal} from this once, across the 1.21.10
 * signature cliff; a block opts in by overriding {@code hasAnalogOutputSignal} to return {@code true}.
 *
 * <p>The tile must call {@code setChanged()} whenever the level can change (every {@code TileBC_Neptune} item
 * handler already does), which is what makes vanilla notify adjacent comparators.
 */
public interface IComparatorOutputTile {
    /** @return the comparator signal, 0..15. */
    int getComparatorLevel();
}
