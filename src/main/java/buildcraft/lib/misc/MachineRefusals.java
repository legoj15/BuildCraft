/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * How many clicks a machine's fake player has had refused, kept per player. A stripes pipe tries several handlers on
 * one stack, and the handler API answers only handled or not: the dispatcher reads {@link #count} before and after
 * each handler, and a handler whose click was refused (a claim mod cancelled the right-click, the item gate stopped
 * the item, break protection said no) must end the dispatch the way a refused click ends a player's, instead of
 * leaving the next handler to click a neighbouring block the claim was never asked about.
 *
 * <p>The tally belongs to the player whose click it was, not to the process: a refusal for another player (an
 * addon's nested dispatch) cannot end this player's dispatch. Entries are weak, so a detached player's goes with it.
 *
 * <p>{@link BlockUtil}'s machine-click helpers record their own refusals, and so do the stripes minecart handler for a
 * refused attack and the stripes shears handler for a
 * refused break. The one refusal no helper sees is a mob interaction: {@code Player#interactOn} answers a cancelled
 * entity-interaction event with whatever result the cancelling listener left, the default PASS (the same answer as a
 * mob that ignores the item) or a consuming one (the same answer as a mob that took the item), so
 * {@link #onEntityInteract} records it from the event itself.
 *
 * <p>A placement a protection mod refuses through NeoForge's placement capture (the item's use places the block, the
 * cancelled placement event takes it back) reaches the clicker as the item's FAIL, the same answer as a block item
 * with no room. A caller that must tell the two apart (the Filler, which parks a refused cell) runs the click inside
 * {@link #countingPlacements}, and {@link #onEntityPlace} records the cancelled placement for that player. Outside
 * that scope a cancelled placement is not counted: a stripes pipe's dispatch deliberately goes on to its next click
 * after one (see {@link BlockUtil.ItemUse#PASSED}).
 */
@EventBusSubscriber(modid = "buildcraftunofficial")
public final class MachineRefusals {

    private static final Map<Player, AtomicLong> TALLIES = Collections.synchronizedMap(new WeakHashMap<>());

    /** The machine player {@link #countingPlacements} is counting cancelled placements for right now, or null. */
    @Nullable
    private static Player placingFor;

    private MachineRefusals() {}

    /** Clicks refused for {@code player} so far; only differences mean anything. */
    public static long count(Player player) {
        AtomicLong tally = TALLIES.get(player);
        return tally == null ? 0 : tally.get();
    }

    /** Notes one click refused for {@code player}. */
    public static void record(Player player) {
        TALLIES.computeIfAbsent(player, key -> new AtomicLong()).incrementAndGet();
    }

    /**
     * A protection mod cancelling a machine player's interaction with a mob. Last priority and even when cancelled,
     * so every listener at an earlier priority has had its say; a real player's refused click needs no tally, nothing
     * asks it. A listener that cancels at this same LOWEST priority and is registered after this one goes unseen
     * (the event bus runs a priority's listeners in registration order); protection mods cancel earlier than that.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() && event.getEntity() instanceof FakePlayer machine) {
            record(machine);
        }
    }

    /**
     * Runs {@code use} (a click by {@code player}) with the placements it makes counted: a placement event for
     * {@code player} that a protection mod cancels is {@link #record recorded} as a refusal, so a caller comparing
     * {@link #count} before and after sees it. Server thread only; nests (the inner scope wins while it runs).
     */
    public static boolean countingPlacements(Player player, BooleanSupplier use) {
        Player outer = placingFor;
        placingFor = player;
        try {
            return use.getAsBoolean();
        } finally {
            placingFor = outer;
        }
    }

    /**
     * A protection mod cancelling a placement by the player {@link #countingPlacements} is counting for. Last priority
     * and even when cancelled, like {@link #onEntityInteract}; a permission probe ({@link PlaceEventProbe}) places
     * nothing and answers its own caller, so it is not counted.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        Player counting = placingFor;
        if (counting != null && event.isCanceled() && event.getEntity() == counting
                && !PlaceEventProbe.isProbe(event)) {
            record(counting);
        }
    }
}
