/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a machine remembers after a protection mod refused to let it break a block, so it does not ask again (and
 * does not spend power or travel on the block) every cycle.
 *
 * <p>A refusal holds while the block's state is unchanged, for at most {@code retryTicks} on the caller's clock. The
 * state check forgets a refusal the moment the block becomes something else (the new block is a new question); the
 * time limit exists because lifting a claim, or adding the machine owner to it, fires no event a machine could hear:
 * without it a block refused once would be skipped for as long as it stayed the same. Bounded and insertion-ordered,
 * so a machine that meets a great many refusals (a robot working a whole claimed forest) keeps only the latest ones.
 *
 * <p>Transient by design: never persisted, so a reloaded machine simply asks again. Server thread only, like every
 * caller. The clock is whatever monotonic tick count the caller has (a world's game time, or a count of the
 * machine's own ticks) and is passed in on every call.
 */
public final class ProtectionRefusals {

    private record Refusal(BlockState state, long retryAt) {}

    private final int retryTicks;
    private final Map<BlockPos, Refusal> refused;

    /**
     * @param retryTicks how long a refusal holds before the machine may ask again
     * @param capacity the most refusals kept; past it the oldest is dropped
     */
    public ProtectionRefusals(int retryTicks, int capacity) {
        this.retryTicks = retryTicks;
        this.refused = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockPos, Refusal> eldest) {
                return size() > capacity;
            }
        };
    }

    /** Records that the block at {@code pos}, in {@code state}, was just refused; restarts any earlier refusal there. */
    public void remember(BlockPos pos, BlockState state, long now) {
        BlockPos key = pos.immutable();
        refused.remove(key);
        refused.put(key, new Refusal(state, now + retryTicks));
    }

    /**
     * Whether the machine should leave {@code pos} alone: it was refused in this very {@code state} and the retry
     * window has not elapsed. A changed state or an elapsed window forgets the refusal and answers false.
     */
    public boolean isRefused(BlockPos pos, BlockState state, long now) {
        Refusal refusal = refused.get(pos);
        if (refusal == null) {
            return false;
        }
        if (refusal.state().equals(state) && now < refusal.retryAt()) {
            return true;
        }
        refused.remove(pos);
        return false;
    }

    /** Drops the refusal at {@code pos}, if any. */
    public void forget(BlockPos pos) {
        refused.remove(pos);
    }

    /** How many refusals are held (for tests). */
    public int size() {
        return refused.size();
    }
}
