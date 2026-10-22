/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import buildcraft.lib.misc.BreakEventCompat;

/**
 * A stand-in for a claim/protection mod: a break listener that records every block-break event at one position and,
 * when {@code refuse} is set, cancels them all — exactly how claim mods refuse a machine's break probe. Register it
 * in a try-with-resources so it is always unregistered, and filter on one position so concurrent tests in other arena
 * cells are never refused or counted.
 */
public final class ClaimProtectionMod implements AutoCloseable {

    /** The player each break event at the watched position was posted for, in order. */
    public final List<Player> askedBy = new ArrayList<>();
    /** Where that player stood (its block) when it asked, in order: the player is shared and moves on afterwards. */
    public final List<BlockPos> askedFrom = new ArrayList<>();
    private final Object token;

    public ClaimProtectionMod(BlockPos pos, boolean refuse) {
        token = BreakEventCompat.onBreak(event -> {
            if (event.getPos().equals(pos)) {
                Player asker = BreakEventCompat.playerOf(event);
                askedBy.add(asker);
                askedFrom.add(asker == null ? null : asker.blockPosition());
                if (refuse) {
                    BreakEventCompat.cancel(event);
                }
            }
        });
    }

    /** How many times machines asked about the watched position. */
    public int asks() {
        return askedBy.size();
    }

    @Override
    public void close() {
        BreakEventCompat.removeListener(token);
    }
}
