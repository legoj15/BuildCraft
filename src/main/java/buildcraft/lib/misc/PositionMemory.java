/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import net.minecraft.core.BlockPos;

/**
 * The store under every "leave that block alone for a while" memory a machine keeps: one note per block position,
 * each carrying what the note is about ({@code V}) and when it ends, kept for a window and up to a capacity set by a
 * {@link Policy}. The layers on top decide what a note means and when it applies:
 *
 * <ul>
 * <li>{@link RefusalMemory} - a protection mod (or the block) refused the machine; the note is the block state it
 *     refused in, and only that state, asked about by the same owner, is still refused.</li>
 * <li>{@code UnreachableBlockCache} - a robot could not fly to the block; the note is where the robot stood, and it
 *     only applies while the robot is near there.</li>
 * <li>{@code WorkRefusals} - a {@link RefusalMemory} plus a count of failures in a row.</li>
 * </ul>
 *
 * <p>{@code BlockClaims} is not one of them: a claim is held by its owner until given back, never windowed, so it keeps
 * its own ledger in the robot registry.
 *
 * <p>Keyed by the packed {@link BlockPos#asLong()} (a large claimed pool can mean thousands of notes, and a fresh
 * position object for a cell finds its note), in the order the notes were made or restarted. A note is dropped when it
 * is read after its window has ended. Transient: never saved. Server thread only, like every caller.
 *
 * @param <V> what a note records about its block
 */
public final class PositionMemory<V> {

    /**
     * How long a memory's notes last and how many it keeps.
     *
     * <ul>
     * <li>A <em>fixed</em> window is the same length for every note, so notes end in the order they were made: the
     *     cap drops the oldest, and {@link #takeExpired} can hand back the due ones without scanning the rest.</li>
     * <li>A <em>spaced</em> window is {@code max(windowTicks, spacingTicks per other live note)}, so re-asking a
     *     fully refused pool of any size averages one probe per {@code spacingTicks} rather than one per tick. It takes
     *     no cap (forgetting live notes early is what makes a pump thrash); past the plain-window pool size each new
     *     note first drops the stale ones, so only live notes stretch the window.</li>
     * </ul>
     *
     * @param windowTicks the window of a fixed policy, the shortest window of a spaced one
     * @param spacingTicks 0 for a fixed window; otherwise how far each other live note stretches a new one's window
     * @param capacity the most notes kept (oldest dropped first); fixed windows only
     */
    public record Policy(long windowTicks, long spacingTicks, int capacity) {
        public Policy {
            if (windowTicks <= 0 || spacingTicks < 0 || capacity <= 0) {
                throw new IllegalArgumentException("bad memory policy " + windowTicks + "/" + spacingTicks + "/"
                        + capacity);
            }
            if (spacingTicks > 0 && capacity != Integer.MAX_VALUE) {
                throw new IllegalArgumentException("a spaced window takes no cap: forgetting live refusals thrashes");
            }
        }

        /** Every note lasts {@code windowTicks}; past {@code capacity} notes the oldest is dropped. */
        public static Policy fixed(long windowTicks, int capacity) {
            return new Policy(windowTicks, 0, capacity);
        }

        /** At least {@code minWindowTicks}, stretched by {@code spacingTicks} per other live note; no cap. */
        public static Policy spaced(long minWindowTicks, long spacingTicks) {
            return new Policy(minWindowTicks, spacingTicks, Integer.MAX_VALUE);
        }

        public boolean isSpaced() {
            return spacingTicks > 0;
        }
    }

    /** Tells a note that no longer holds although its window has not ended (its block has changed). */
    @FunctionalInterface
    public interface StaleTest<V> {
        boolean isStale(long packedPos, V note);
    }

    private record Note<V>(V value, long expiresAt) {}

    private final Policy policy;
    /** Up to this many other live notes a spaced window is the plain {@link Policy#windowTicks}, so counting them
     *  exactly (and pruning the stale ones to do it) only starts above it. */
    private final int plainWindowPool;
    private final Long2ObjectLinkedOpenHashMap<Note<V>> notes = new Long2ObjectLinkedOpenHashMap<>();

    public PositionMemory(Policy policy) {
        this.policy = policy;
        this.plainWindowPool = policy.isSpaced() ? (int) (policy.windowTicks() / policy.spacingTicks()) : 0;
    }

    public Policy policy() {
        return policy;
    }

    /**
     * Notes {@code value} for {@code packedPos} at {@code gameTime}, restarting any earlier note there (it becomes the
     * newest). A spaced policy past its plain-window pool first drops the notes that have expired or that
     * {@code stale} rejects, then sizes the window by the live notes left; past the capacity the oldest is dropped.
     */
    public void put(long packedPos, V value, long gameTime, @Nullable StaleTest<V> stale) {
        notes.remove(packedPos);
        long window = policy.windowTicks();
        if (policy.isSpaced()) {
            if (notes.size() > plainWindowPool) {
                pruneStale(gameTime, stale);
            }
            window = Math.max(window, notes.size() * policy.spacingTicks());
        }
        notes.putAndMoveToLast(packedPos, new Note<>(value, gameTime + window));
        while (notes.size() > policy.capacity()) {
            notes.removeFirst();
        }
    }

    private void pruneStale(long gameTime, @Nullable StaleTest<V> stale) {
        ObjectIterator<Long2ObjectMap.Entry<Note<V>>> it = notes.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<Note<V>> entry = it.next();
            Note<V> note = entry.getValue();
            if (gameTime >= note.expiresAt()
                    || (stale != null && stale.isStale(entry.getLongKey(), note.value()))) {
                it.remove();
            }
        }
    }

    /** The note held for {@code packedPos} while its window runs, else null; a note read after its window ended is
     *  dropped. */
    @Nullable
    public V get(long packedPos, long gameTime) {
        Note<V> note = notes.get(packedPos);
        if (note == null) {
            return null;
        }
        if (gameTime >= note.expiresAt()) {
            notes.remove(packedPos);
            return null;
        }
        return note.value();
    }

    /**
     * Hands back up to {@code limit} positions whose window has ended, restarting each one's window with the note it
     * held so it is not handed back again for another full window. Fixed windows only: notes then end in the order
     * they are kept, so the scan stops at the first one still running and is cheap when nothing is due.
     *
     * @throws IllegalStateException for a spaced policy, whose windows do not end in insertion order
     */
    public List<BlockPos> takeExpired(long gameTime, int limit) {
        if (policy.isSpaced()) {
            throw new IllegalStateException("takeExpired needs a fixed window");
        }
        List<BlockPos> due = new ArrayList<>();
        while (due.size() < limit && !notes.isEmpty()) {
            long key = notes.firstLongKey();
            Note<V> note = notes.get(key);
            if (gameTime < note.expiresAt()) {
                break;
            }
            notes.putAndMoveToLast(key, new Note<>(note.value(), gameTime + policy.windowTicks()));
            due.add(BlockPos.of(key));
        }
        return due;
    }

    /** Drops the note for {@code packedPos}, if any. */
    public void remove(long packedPos) {
        notes.remove(packedPos);
    }

    /** Drops every note. */
    public void clear() {
        notes.clear();
    }

    public boolean isEmpty() {
        return notes.isEmpty();
    }

    /** How many notes are held (expired ones not yet read count). */
    public int size() {
        return notes.size();
    }
}
