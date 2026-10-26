/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;

/**
 * What one look over a stalled build found: every skipped cell is {@link #note noted} with its reason as the walk
 * meets it, and the tally keeps the total per reason, plus the cells to list: at most {@code cap}, the reasons of
 * {@link EnumStallReason} in order (what the player has to fix first), the cells of one reason in the order noted.
 * Memory stays bounded however many cells are noted (each reason holds at most {@code cap} positions). Side-effect
 * free: it touches nothing but itself, so the cap and the ordering are unit-testable without a world.
 */
final class StallTally {
    private static final EnumStallReason[] REASONS = EnumStallReason.values();

    private final int cap;
    private final List<List<BlockPos>> found = new ArrayList<>(REASONS.length);
    private final int[] counts = new int[REASONS.length];

    StallTally(int cap) {
        this.cap = cap;
        for (int i = 0; i < REASONS.length; i++) {
            found.add(new ArrayList<>());
        }
    }

    /** Notes one skipped cell; {@code null} (nothing but an ordinary wait in the way) notes nothing. */
    void note(@Nullable EnumStallReason reason, BlockPos pos) {
        if (reason == null) {
            return;
        }
        counts[reason.ordinal()]++;
        List<BlockPos> list = found.get(reason.ordinal());
        // A reason can need at most the cap in the merged list, so keep no more than that of it.
        if (list.size() < cap) {
            list.add(pos);
        }
    }

    /** How many cells were noted for {@code reason}, the ones past the cap included. */
    int count(EnumStallReason reason) {
        return counts[reason.ordinal()];
    }

    /** The cells to list: at most the cap, in reason order, and in the order noted within a reason. */
    List<StalledCell> cells() {
        List<StalledCell> cells = new ArrayList<>();
        for (EnumStallReason reason : REASONS) {
            for (BlockPos pos : found.get(reason.ordinal())) {
                if (cells.size() >= cap) {
                    return cells;
                }
                cells.add(new StalledCell(pos, reason));
            }
        }
        return cells;
    }
}
