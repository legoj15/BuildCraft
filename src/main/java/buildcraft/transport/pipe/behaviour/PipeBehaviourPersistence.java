/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.pipe.behaviour;

import javax.annotation.Nullable;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;

/** Save-dirtying for pipe behaviours that keep player-editable state (filter inventories, colour assignments,
 * modes, facings, limits). A behaviour saves through the holder tile ({@code pipe -> beh}), so an edit only reaches
 * disk if the tile's chunk is flagged unsaved; call {@link #markDirty} from every edit path (inventory change
 * callback, GUI message handler, wrench click, gate action) instead of relying on the pipe's per-tick
 * {@code blockEntityChanged}.
 *
 * <p>That per-tick call is a perf hot spot, but it cannot simply go: it is what saves the evolving flow state (items
 * and fluid in transit, power buffers, the stripes pipe's battery), none of which has a dirty mark of its own. It
 * would only be safe to narrow once each flow reports its own changes; until then it also hides a missing edit mark,
 * which is why {@code PipeFilterPersistenceTester} clears the chunk's unsaved flag and checks each edit path before
 * any tick can run. */
public final class PipeBehaviourPersistence {
    private PipeBehaviourPersistence() {}

    /** Flags the pipe's block entity changed so the next save writes the edit. Server side only (the client copy
     * never saves), and a no-op for a detached behaviour (JUnit builds them with a null pipe). Uses
     * {@code setChanged()}: this is a real, user-driven change, as opposed to the per-tick flow churn that
     * {@code TilePipeHolder.tick} dirties through {@code blockEntityChanged} alone. */
    public static void markDirty(@Nullable IPipe pipe) {
        if (pipe == null) {
            return;
        }
        IPipeHolder holder = pipe.getHolder();
        if (holder == null) {
            return;
        }
        Level level = holder.getPipeWorld();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockEntity tile = holder.getPipeTile();
        if (tile != null) {
            tile.setChanged();
        }
    }
}
