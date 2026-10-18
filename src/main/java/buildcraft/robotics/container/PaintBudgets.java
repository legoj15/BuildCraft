/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.container;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Zone Planner stroke allowances ({@link PaintRateLimiter}) of the players who paint at one planner, by player.
 * The tile holds this, not the menu: a menu lives only as long as the screen is open, so an allowance kept on it was
 * refilled by closing the planner and opening it again, and a client could spend a burst, reopen, and spend another.
 * Held by the planner and keyed by who is painting, the allowance carries across reopenings (the clock keeps refilling
 * it meanwhile) and is not shared between players.
 *
 * <p>Not saved: an allowance is a few seconds' worth of state, and a reloaded planner starts everyone with a full one.
 * An entry is dropped once its allowance has refilled, since a player with none is exactly a new player, so the map
 * holds only the players who painted lately and not every one who ever opened the planner.
 */
public final class PaintBudgets {
    /** The size past which stale entries are swept on the next use. */
    private static final int SWEEP_ABOVE = 16;

    private final Map<UUID, PaintRateLimiter> byPlayer = new HashMap<>();

    /**
     * Takes one stroke from {@code player}'s allowance at game time {@code now}.
     *
     * @return false if they are sending faster than the limit and this stroke should be dropped.
     */
    public boolean tryAcquire(UUID player, long now) {
        if (byPlayer.size() > SWEEP_ABOVE) {
            byPlayer.values().removeIf(limiter -> limiter.isRefilled(now));
        }
        return byPlayer.computeIfAbsent(player, id -> new PaintRateLimiter()).tryAcquire(now);
    }

    /** How many players' allowances are held, for tests. */
    public int size() {
        return byPlayer.size();
    }
}
