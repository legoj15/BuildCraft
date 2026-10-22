/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The "may this machine place a block here?" ask for a machine that places a captured block state without using an
 * item (the Blueprint Builder's {@code level.setBlock}), so NeoForge's own placement capture never runs for it. It
 * posts the event a player's placement posts — {@link BlockEvent.EntityPlaceEvent}, or
 * {@link BlockEvent.EntityMultiPlaceEvent} for a block that fills two cells (a bed, a door) — before anything is
 * placed, and reads the answer off its cancellation; that is how claim and protection mods refuse a placement.
 *
 * <p>The event is a PROBE: nothing is placed, cancelled or not, so BuildCraft's own placement listeners must not treat
 * it as a block changing and check {@link #isProbe}. Because nothing has been placed yet, the event's placed state is
 * the cell's current one (air, as a rule), the same limit the break probe has; claim mods judge by position and
 * player. Server thread only, like every caller. Use it through {@link BlockUtil#canMachinePlace}, which honours the
 * protection toggle and leases the machine's player.
 */
public final class PlaceEventProbe {

    /** The probe {@link #canPlace} is posting right now (the innermost one, if a listener probes again), or null. */
    @Nullable
    private static BlockEvent probeInFlight;

    private PlaceEventProbe() {}

    /**
     * Asks whether {@code player} may place a block filling {@code cells} (the first is the cell the block is placed
     * in, any others the cells it fills too): posts the placement event a player's placement would and returns
     * {@code true} if it was NOT cancelled. Each cell is "placed against" the block below it, as a block set down on
     * the floor of its cell is.
     *
     * @throws IllegalArgumentException for no cells
     */
    public static boolean canPlace(ServerLevel level, List<BlockPos> cells, Player player) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("a placement fills at least one cell");
        }
        BlockPos first = cells.get(0);
        BlockEvent.EntityPlaceEvent event;
        if (cells.size() == 1) {
            event = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, first),
                    level.getBlockState(first.below()), player);
        } else {
            List<BlockSnapshot> snapshots = new ArrayList<>(cells.size());
            for (BlockPos cell : cells) {
                snapshots.add(BlockSnapshot.create(level.dimension(), level, cell));
            }
            event = new BlockEvent.EntityMultiPlaceEvent(snapshots, level.getBlockState(first.below()), player);
        }
        BlockEvent outer = probeInFlight;
        probeInFlight = event;
        try {
            NeoForge.EVENT_BUS.post(event);
        } finally {
            probeInFlight = outer;
        }
        return !event.isCanceled();
    }

    /** True while {@code event} is a permission probe posted by {@link #canPlace} rather than a block being placed. */
    public static boolean isProbe(BlockEvent event) {
        return event == probeInFlight;
    }
}
