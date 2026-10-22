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
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * A stand-in for a claim/protection mod that guards what a hoe and a seed do rather than what a pickaxe does: a
 * listener on the right-click-block event (a hoe about to till dirt), the block-placement event (a seed being
 * planted) and the item right-click event (an item used in the air) that records every event at the positions it
 * watches and cancels the ones at positions it refuses - exactly how a claim mod refuses a hoe's till and turns a
 * seed's placement into a FAIL. The till is guarded through the right-click because that is the ask every node
 * shares: on 26.3 a hoe's till posts no tool-modification event. Each kind of event has its own refusal rule, so a
 * claim that guards only one of them can be stood up: what one ask does not stop, the others must.
 * {@link ClaimProtectionMod} is the same for block breaks. Register it in a try-with-resources so it is always
 * unregistered, and watch only the positions of one arena cell so concurrent tests in other cells are never refused
 * or counted.
 *
 * <p>Positions are those the events carry: the CLICKED (to-be-tilled) block for a right-click, the block PLACED
 * (the crop cell, above the soil) for a placement, the block the using player STANDS in for an item right-click.
 */
public final class WorkClaimMod implements AutoCloseable {

    private final Map<BlockPos, Integer> toolAsks = new HashMap<>();
    private final Map<BlockPos, Integer> placeAsks = new HashMap<>();
    private final Map<BlockPos, Integer> itemAsks = new HashMap<>();
    private final Consumer<PlayerInteractEvent.RightClickBlock> toolListener;
    private final Consumer<BlockEvent.EntityPlaceEvent> placeListener;
    private final Consumer<PlayerInteractEvent.RightClickItem> itemListener;

    /**
     * A claim that refuses right-clicks and placements alike (and lets item uses through).
     *
     * @param watched the positions whose events are counted; events elsewhere are ignored
     * @param refused of the watched positions, the ones whose right-click and placement events are cancelled
     */
    public WorkClaimMod(Predicate<BlockPos> watched, Predicate<BlockPos> refused) {
        this(watched, refused, refused, pos -> false);
    }

    /**
     * @param watched the positions whose events are counted; events elsewhere are ignored
     * @param clickRefused of the watched positions, the ones whose right-click-block events are cancelled
     * @param placeRefused of the watched positions, the ones whose placement events are cancelled
     * @param itemRefused of the watched positions, the ones a player stands in when their item right-click event is
     *        cancelled
     */
    public WorkClaimMod(Predicate<BlockPos> watched, Predicate<BlockPos> clickRefused,
            Predicate<BlockPos> placeRefused, Predicate<BlockPos> itemRefused) {
        toolListener = event -> {
            BlockPos pos = event.getPos();
            if (watched.test(pos)) {
                toolAsks.merge(pos.immutable(), 1, Integer::sum);
                if (clickRefused.test(pos)) {
                    event.setCanceled(true);
                }
            }
        };
        placeListener = event -> {
            BlockPos pos = event.getPos();
            if (watched.test(pos)) {
                placeAsks.merge(pos.immutable(), 1, Integer::sum);
                if (placeRefused.test(pos)) {
                    event.setCanceled(true);
                }
            }
        };
        itemListener = event -> {
            BlockPos pos = event.getPos();
            if (watched.test(pos)) {
                itemAsks.merge(pos.immutable(), 1, Integer::sum);
                if (itemRefused.test(pos)) {
                    event.setCanceled(true);
                }
            }
        };
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, toolListener);
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, placeListener);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, itemListener);
    }

    /** How many tool uses (right-clicks) were attempted at {@code pos}. */
    public int toolAsks(BlockPos pos) {
        return toolAsks.getOrDefault(pos, 0);
    }

    /** How many tool uses were attempted at all the watched positions. */
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

    /** How many item uses in the air were attempted by a player standing at {@code pos}. */
    public int itemAsks(BlockPos pos) {
        return itemAsks.getOrDefault(pos, 0);
    }

    /** How many item uses in the air were attempted by a player standing at any of the watched positions. */
    public int totalItemAsks() {
        return itemAsks.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public void close() {
        NeoForge.EVENT_BUS.unregister(toolListener);
        NeoForge.EVENT_BUS.unregister(placeListener);
        NeoForge.EVENT_BUS.unregister(itemListener);
    }
}
