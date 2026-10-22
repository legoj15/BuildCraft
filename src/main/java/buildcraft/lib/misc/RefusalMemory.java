/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a machine remembers about the answers protection mods (and refusing blocks) gave it, so it neither asks again
 * nor spends power or travel on a block every cycle. The one memory every block-breaking, block-placing or
 * fluid-draining machine keeps; each picks a {@link Policy}, and they are all here:
 *
 * <table>
 * <caption>Refusal policy per machine (pinned by {@code RefusalMemoryOwnerAndPermitTest})</caption>
 * <tr><th>Machine</th><th>Policy</th><th>Window</th><th>Cap</th><th>Owner key</th><th>Permit used for</th></tr>
 * <tr><td>Quarry (frame-volume cells)</td><td>{@link #QUARRY_FRAME}</td><td>fixed 200 ticks; refused cells are
 *     re-probed as they come due ({@link #takeExpired}), even by an idle quarry</td><td>none: the cells held are
 *     cells of the frame box</td><td>tile owner</td><td>-</td></tr>
 * <tr><td>Builder and Filler placements</td><td>{@link #BUILDER_PLACE}</td><td>fixed 200 ticks; a refused cell is
 *     parked (skipped when queuing placements) and asked again once its window ends</td><td>none: the cells held
 *     are cells of the build box</td><td>tile owner</td><td>-</td></tr>
 * <tr><td>Stripes pipe</td><td>{@link #STRIPES}</td><td>fixed 200 ticks</td><td>4 (it faces one block at a
 *     time)</td><td>pipe owner</td><td>the block the current break cycle charges</td></tr>
 * <tr><td>Robot search boards (lumberjack, miner, ...)</td><td>{@link #ROBOT_SEARCH}</td><td>fixed 1200 ticks</td>
 *     <td>256, oldest dropped (a claimed forest)</td><td>home-station owner</td><td>-</td></tr>
 * <tr><td>Pump; robot pump board</td><td>{@link #DRAIN}</td><td>spaced: 6000 ticks, stretched by 20 per other
 *     live refusal</td><td>none (see below)</td><td>tile owner; home-station owner</td><td>Pump: the cell the
 *     current bucket charges</td></tr>
 * </table>
 *
 * <p><b>Refusals.</b> A refusal holds while the block's state is unchanged, until its window ends. The state check
 * forgets it the moment the block becomes something else (a new block is a new question); the window exists because
 * lifting a claim, or trusting the machine's owner in it, fires no event a machine could hear.
 * <ul>
 * <li>A <em>fixed</em> window is the same length for every refusal, so entries end in the order they were made: an
 *     optional cap drops the oldest, and {@link #takeExpired} can hand back the due ones without scanning the rest.</li>
 * <li>A <em>spaced</em> window is {@code max(windowTicks, spacingTicks per other live refusal)}, so re-asking a fully
 *     refused pool of any size averages one probe per {@code spacingTicks} rather than one per tick. It takes no cap:
 *     forgetting live refusals early is exactly what makes a pump thrash (the next rebuild re-queues the forgotten
 *     cells and probes one per tick, forever); the map only holds cells this machine reached and was refused. Only
 *     live refusals stretch the window: past the plain-window pool size each new refusal first drops the entries that
 *     have expired, and those whose cell has changed (read through a {@link StateLookup}; a cell it cannot read is
 *     kept).</li>
 * </ul>
 *
 * <p><b>The permit.</b> Besides refusals the memory holds at most one standing "yes": the block (position and state) a
 * machine was cleared to work on for the cycle it is charging, so it need not ask on every tick of that cycle. It is
 * about that block only: re-pointing the machine, or the block vanishing or being replaced, means it does not match
 * and the machine asks again before it spends anything. A refusal of the permitted block revokes it.
 *
 * <p><b>The owner key.</b> Every answer is about the identity the machine asked as ({@link MachineIdentity}: its named
 * owner, else "[BuildCraft]"). Every call names the owner; when it is a different identity from the one the memory
 * holds answers for (a robot relinked to another player's station), the memory is emptied first, permit included.
 *
 * <p><b>Clock and lifetime.</b> The clock is the level's game time everywhere; the pure overloads take it as an
 * argument so the logic is testable without a world. The {@link Level} overloads read state and clock from the world
 * without loading chunks: a cell in an unloaded chunk is not remembered and not reported as refused. Transient by
 * design: never saved, so a reloaded machine simply asks once more. Server thread only, like every caller.
 */
public final class RefusalMemory {

    /**
     * How long a machine's refusals last and how many it keeps.
     *
     * @param windowTicks the window of a fixed policy, the shortest window of a spaced one
     * @param spacingTicks 0 for a fixed window; otherwise how far each other live refusal stretches a new one's window
     * @param capacity the most refusals kept (oldest dropped first); fixed windows only
     */
    public record Policy(long windowTicks, long spacingTicks, int capacity) {
        public Policy {
            if (windowTicks <= 0 || spacingTicks < 0 || capacity <= 0) {
                throw new IllegalArgumentException("bad refusal policy " + windowTicks + "/" + spacingTicks + "/"
                        + capacity);
            }
            if (spacingTicks > 0 && capacity != Integer.MAX_VALUE) {
                throw new IllegalArgumentException("a spaced window takes no cap: forgetting live refusals thrashes");
            }
        }

        /** Every refusal lasts {@code windowTicks}; past {@code capacity} entries the oldest is dropped. */
        public static Policy fixed(long windowTicks, int capacity) {
            return new Policy(windowTicks, 0, capacity);
        }

        /** At least {@code minWindowTicks}, stretched by {@code spacingTicks} per other live refusal; no cap. */
        public static Policy spaced(long minWindowTicks, long spacingTicks) {
            return new Policy(minWindowTicks, spacingTicks, Integer.MAX_VALUE);
        }

        public boolean isSpaced() {
            return spacingTicks > 0;
        }
    }

    /** The quarry's frame-volume cells: see the class table. */
    public static final Policy QUARRY_FRAME = Policy.fixed(200, Integer.MAX_VALUE);
    /** The Builder's and the Filler's refused placements: see the class table. */
    public static final Policy BUILDER_PLACE = Policy.fixed(200, Integer.MAX_VALUE);
    /** The stripes pipe: see the class table. */
    public static final Policy STRIPES = Policy.fixed(200, 4);
    /** The robot search boards: see the class table. */
    public static final Policy ROBOT_SEARCH = Policy.fixed(1200, 256);
    /** The pump and the robot pump board: five minutes, stretched by a second per other live refusal. */
    public static final Policy DRAIN = Policy.spaced(6000, 20);

    /** A cell's current state, for telling which remembered refusals are stale. */
    @FunctionalInterface
    public interface StateLookup {
        /** The state at {@code pos} now, or null when it cannot be read cheaply (an unloaded chunk): such a refusal
         *  is kept, since it may well still hold. */
        @Nullable
        BlockState stateAt(BlockPos pos);
    }

    private record Refusal(BlockState state, long expiresAt) {}

    private final Policy policy;
    /** Up to this many other live refusals a spaced window is the plain {@link Policy#windowTicks}, so counting them
     *  exactly (and pruning the stale ones to do it) only starts above it. */
    private final int plainWindowPool;
    /** Keyed on {@link BlockPos#asLong()} (a large claimed pool can mean thousands of entries), in the order the
     *  refusals were made or restarted. */
    private final Long2ObjectLinkedOpenHashMap<Refusal> refused = new Long2ObjectLinkedOpenHashMap<>();
    /** The identity every answer held was given to; null until the first call names one. */
    @Nullable
    private UUID ownerId;
    @Nullable
    private BlockPos permitPos;
    @Nullable
    private BlockState permitState;

    public RefusalMemory(Policy policy) {
        this.policy = policy;
        this.plainWindowPool = policy.isSpaced() ? (int) (policy.windowTicks() / policy.spacingTicks()) : 0;
    }

    public Policy policy() {
        return policy;
    }

    /** Empties the memory if {@code owner} acts as someone other than the identity its answers were given to. */
    private void keyOn(@Nullable GameProfile owner) {
        UUID id = GameProfileUtil.getId(MachineIdentity.of(owner));
        if (!Objects.equals(id, ownerId)) {
            refused.clear();
            revokePermit();
            ownerId = id;
        }
    }

    // --- Refusals ---

    /** Records that {@code pos}, holding {@code state}, was refused to {@code owner} at {@code gameTime}; restarts any
     *  earlier refusal there. Only expiry tells a stale entry here; prefer the {@link StateLookup} overload (or the
     *  {@link Level} one) where the world can be read. */
    public void remember(BlockPos pos, BlockState state, long gameTime, @Nullable GameProfile owner) {
        remember(pos, state, gameTime, owner, null);
    }

    /** {@link #remember(BlockPos, BlockState, long, GameProfile)}, telling stale entries of a spaced window by
     *  {@code lookup} as well as by expiry. */
    public void remember(BlockPos pos, BlockState state, long gameTime, @Nullable GameProfile owner,
            @Nullable StateLookup lookup) {
        keyOn(owner);
        long key = pos.asLong();
        refused.remove(key);
        if (permitPos != null && permitPos.asLong() == key) {
            revokePermit();
        }
        long window = policy.windowTicks();
        if (policy.isSpaced()) {
            if (refused.size() > plainWindowPool) {
                pruneStale(gameTime, lookup);
            }
            window = Math.max(window, refused.size() * policy.spacingTicks());
        }
        refused.putAndMoveToLast(key, new Refusal(state, gameTime + window));
        while (refused.size() > policy.capacity()) {
            refused.removeFirst();
        }
    }

    /** Drops every entry that has expired, or whose cell {@code lookup} reports as holding another state. */
    private void pruneStale(long gameTime, @Nullable StateLookup lookup) {
        ObjectIterator<Long2ObjectMap.Entry<Refusal>> it = refused.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<Refusal> entry = it.next();
            Refusal refusal = entry.getValue();
            if (gameTime >= refusal.expiresAt()) {
                it.remove();
            } else if (lookup != null) {
                BlockState now = lookup.stateAt(BlockPos.of(entry.getLongKey()));
                if (now != null && !now.equals(refusal.state())) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Whether the machine should leave {@code pos} alone: it was refused to {@code owner} in this very {@code state}
     * and the window has not ended. A changed state or an ended window forgets the refusal and answers false.
     */
    public boolean isRefused(BlockPos pos, BlockState state, long gameTime, @Nullable GameProfile owner) {
        keyOn(owner);
        long key = pos.asLong();
        Refusal refusal = refused.get(key);
        if (refusal == null) {
            return false;
        }
        if (refusal.state().equals(state) && gameTime < refusal.expiresAt()) {
            return true;
        }
        refused.remove(key);
        return false;
    }

    /** {@link #remember(BlockPos, BlockState, long, GameProfile, StateLookup)} for the cell's current state and the
     *  world's clock. A cell in an unloaded chunk is not read (that would load it) and so not remembered: it has not
     *  been seen to refuse. Other remembered cells in unloaded chunks are likewise left unread, only aged out. */
    public void remember(Level level, BlockPos pos, @Nullable GameProfile owner) {
        if (!level.isLoaded(pos)) {
            return;
        }
        remember(pos, level.getBlockState(pos), level.getGameTime(), owner,
                cell -> level.isLoaded(cell) ? level.getBlockState(cell) : null);
    }

    /** {@link #isRefused(BlockPos, BlockState, long, GameProfile)} against the world's current state and clock. A cell
     *  in an unloaded chunk is not read (that would load it) and is reported as not refused, its entry left as it was
     *  to be judged once the cell is loaded. */
    public boolean isRefused(Level level, BlockPos pos, @Nullable GameProfile owner) {
        keyOn(owner);
        return !refused.isEmpty() && level.isLoaded(pos)
                && isRefused(pos, level.getBlockState(pos), level.getGameTime(), owner);
    }

    /**
     * For a machine that has to notice a lifted claim without any block being asked about: hands back up to
     * {@code limit} positions whose window has ended, so the caller can probe them (is the block mineable now?)
     * instead of waiting for its own work to reach them. Each position returned has its window restarted in the state
     * it was refused in, so it is not handed back again for another full window; a caller that finds the block
     * mineable now calls {@link #forget} (or goes on to break it). Fixed windows only: entries then end in the order
     * they are kept, so the scan stops at the first one still running and is cheap when nothing is due.
     *
     * @throws IllegalStateException for a spaced policy, whose windows do not end in insertion order
     */
    public List<BlockPos> takeExpired(long gameTime, int limit) {
        if (policy.isSpaced()) {
            throw new IllegalStateException("takeExpired needs a fixed window");
        }
        List<BlockPos> due = new ArrayList<>();
        while (due.size() < limit && !refused.isEmpty()) {
            long key = refused.firstLongKey();
            Refusal refusal = refused.get(key);
            if (gameTime < refusal.expiresAt()) {
                break;
            }
            refused.putAndMoveToLast(key, new Refusal(refusal.state(), gameTime + policy.windowTicks()));
            due.add(BlockPos.of(key));
        }
        return due;
    }

    /** {@link #takeExpired(long, int)} on the world's clock. */
    public List<BlockPos> takeExpired(Level level, int limit) {
        return refused.isEmpty() ? List.of() : takeExpired(level.getGameTime(), limit);
    }

    /** Drops the refusal at {@code pos}, if any. */
    public void forget(BlockPos pos) {
        refused.remove(pos.asLong());
    }

    // --- The permit ---

    /** Records that {@code owner} may work on {@code pos} while it holds {@code state}: the one standing "yes",
     *  replacing any earlier one. */
    public void permit(BlockPos pos, BlockState state, @Nullable GameProfile owner) {
        keyOn(owner);
        permitPos = pos.immutable();
        permitState = state;
    }

    /** Whether {@code owner} was cleared to work on {@code pos} in exactly this {@code state}. */
    public boolean isPermitted(BlockPos pos, BlockState state, @Nullable GameProfile owner) {
        keyOn(owner);
        return permitPos != null && permitPos.equals(pos) && state.equals(permitState);
    }

    /** The position of the standing permit, or null when there is none. */
    @Nullable
    public BlockPos permittedPos() {
        return permitPos;
    }

    /** Ends the standing permit, if any: the next cycle asks again. */
    public void revokePermit() {
        permitPos = null;
        permitState = null;
    }

    // --- Whole memory ---

    /** Drops every refusal and the permit. */
    public void clear() {
        refused.clear();
        revokePermit();
    }

    /** Whether no refusal is held (the permit does not count). */
    public boolean isEmpty() {
        return refused.isEmpty();
    }

    /** How many refusals are held. */
    public int size() {
        return refused.size();
    }
}
