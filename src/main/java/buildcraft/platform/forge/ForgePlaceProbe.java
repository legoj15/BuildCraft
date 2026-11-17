/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;

/^*
 * The "may this machine place a block here?" ask for a machine that places a captured block state without using an item
 * (the Blueprint Builder's {@code level.setBlock}), so Forge's own placement capture never runs for it: the Forge twin of
 * {@code NeoForgePlaceProbe}. It posts the event a player's placement posts, {@link BlockEvent.EntityPlaceEvent}, or
 * {@link BlockEvent.EntityMultiPlaceEvent} for a block that fills two cells (a bed, a door), before anything is placed,
 * and reads the answer off the post: that is how claim and protection mods refuse a placement.
 *
 * <p><b>Two buses.</b> On EventBus 7 an event posts on its own class's bus only, and a bus hands an event to its
 * subclasses' listeners only for an {@code InheritableEvent}; {@code EntityPlaceEvent} is a {@code MutableEvent}, not an
 * inheritable one, so a multi-cell probe posted on the multi-place bus never reaches a listener on the single-place bus
 * (on NeoForge it does, being a subclass). {@link ForgeGameHooks#onBlockPlace} registers on both for that reason.
 *
 * <p>The event is a PROBE: nothing is placed, cancelled or not, so BuildCraft's own placement listeners must not treat
 * it as a block changing and check {@link #isProbe}. Because nothing has been placed yet, the event's placed state is
 * the cell's current one (a player's placement reads the live block, a machine's the snapshot, and the snapshot holds
 * the same state: air, as a rule), the same limit the break probe has; claim mods judge by position and player. Server
 * thread only, like every caller. Use it through {@code BlockUtil#canMachinePlace}, which honours the protection toggle
 * and leases the machine's player.
 *^/
final class ForgePlaceProbe {

    /^* The probe {@link #canPlace} is posting right now (the innermost one, if a listener probes again), or null. *^/
    @Nullable
    private static BlockEvent.EntityPlaceEvent probeInFlight;

    private ForgePlaceProbe() {}

    /^*
     * Asks whether {@code player} may place a block filling {@code cells} (the first is the cell the block is placed
     * in, any others the cells it fills too): posts the placement event a player's placement would and returns
     * {@code true} if it was NOT cancelled. Each cell is "placed against" the block below it, as a block set down on
     * the floor of its cell is.
     *
     * @throws IllegalArgumentException for no cells
     *^/
    static boolean canPlace(ServerLevel level, List<BlockPos> cells, Player player) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("a placement fills at least one cell");
        }
        BlockPos first = cells.get(0);
        BlockEvent.EntityPlaceEvent outer = probeInFlight;
        boolean cancelled;
        if (cells.size() == 1) {
            BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(
                    BlockSnapshot.create(level.dimension(), level, first), level.getBlockState(first.below()), player);
            probeInFlight = event;
            try {
                cancelled = ForgeBus.post(event);
            } finally {
                probeInFlight = outer;
            }
        } else {
            List<BlockSnapshot> snapshots = new ArrayList<>(cells.size());
            for (BlockPos cell : cells) {
                snapshots.add(BlockSnapshot.create(level.dimension(), level, cell));
            }
            BlockEvent.EntityMultiPlaceEvent event = new BlockEvent.EntityMultiPlaceEvent(snapshots,
                    level.getBlockState(first.below()), player);
            probeInFlight = event;
            try {
                cancelled = ForgeBus.post(event);
            } finally {
                probeInFlight = outer;
            }
        }
        return !cancelled;
    }

    /^* True while {@code event} is a permission probe posted by {@link #canPlace} rather than a block being placed. *^/
    static boolean isProbe(Object event) {
        return event != null && event == probeInFlight;
    }
}
*///?}
