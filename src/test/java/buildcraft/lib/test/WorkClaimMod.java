/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * A stand-in for a claim/protection mod that guards what a hoe and a seed do rather than what a pickaxe does: a
 * listener on the tool-modification event (a hoe tilling dirt) and the block-placement event (a seed being planted)
 * that records every event at the positions it watches and cancels the ones at positions it refuses - exactly how a
 * claim mod turns a hoe's till into a PASS and a seed's placement into a FAIL. {@link ClaimProtectionMod} is the
 * same for block breaks. Register it in a try-with-resources so it is always unregistered, and watch only the
 * positions of one arena cell so concurrent tests in other cells are never refused or counted.
 *
 * <p>Positions are those the events carry: the TILLED block for a tool modification, the block PLACED (the crop
 * cell, above the soil) for a placement.
 */
public final class WorkClaimMod implements AutoCloseable {

    private final Map<BlockPos, Integer> toolAsks = new HashMap<>();
    private final Map<BlockPos, Integer> placeAsks = new HashMap<>();
    private final Consumer<BlockEvent.BlockToolModificationEvent> toolListener;
    private final Consumer<BlockEvent.EntityPlaceEvent> placeListener;

    /**
     * @param watched the positions whose events are counted; events elsewhere are ignored
     * @param refused of the watched positions, the ones whose events are cancelled
     */
    public WorkClaimMod(Predicate<BlockPos> watched, Predicate<BlockPos> refused) {
        toolListener = event -> {
            BlockPos pos = event.getPos();
            if (watched.test(pos)) {
                // A simulated event is only a question (a probe); the till itself fires the real one.
                if (!event.isSimulated()) {
                    toolAsks.merge(pos.immutable(), 1, Integer::sum);
                }
                if (refused.test(pos)) {
                    event.setCanceled(true);
                }
            }
        };
        placeListener = event -> {
            BlockPos pos = event.getPos();
            if (watched.test(pos)) {
                placeAsks.merge(pos.immutable(), 1, Integer::sum);
                if (refused.test(pos)) {
                    event.setCanceled(true);
                }
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.BlockToolModificationEvent.class, toolListener);
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, placeListener);
    }

    /** How many (non-simulated) tool modifications were attempted at {@code pos}. */
    public int toolAsks(BlockPos pos) {
        return toolAsks.getOrDefault(pos, 0);
    }

    /** How many tool modifications were attempted at all the watched positions. */
    public int totalToolAsks() {
        return toolAsks.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** How many placements were attempted at {@code pos}. */
    public int placeAsks(BlockPos pos) {
        return placeAsks.getOrDefault(pos, 0);
    }

    /** How many placements were attempted at all the watched positions. */
    public int totalPlaceAsks() {
        return placeAsks.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public void close() {
        NeoForge.EVENT_BUS.unregister(toolListener);
        NeoForge.EVENT_BUS.unregister(placeListener);
    }
}
