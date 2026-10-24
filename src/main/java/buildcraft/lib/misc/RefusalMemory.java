/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a machine remembers about the answers protection mods (and refusing blocks) gave it, so it neither asks again
 * nor spends power or travel on a block every cycle. Every block-breaking, block-placing or fluid-draining machine
 * keeps one, each with a {@link PositionMemory.Policy}, and they are all here, except the Mining Well and Pump tube:
 * it asks about a cell only when its column grows past it (once per mined block), which is rarer than any window
 * here, so it remembers nothing.
 *
 * <table>
 * <caption>Refusal policy per machine (pinned by {@code RefusalMemoryOwnerAndPermitTest})</caption>
 * <tr><th>Machine</th><th>Policy</th><th>Window</th><th>Cap</th><th>Owner key</th><th>Permit used for</th></tr>
 * <tr><td>Quarry (frame-volume cells: blocks it may not break, frame blocks it may not place)</td>
 *     <td>{@link #QUARRY_FRAME}</td><td>fixed 200 ticks; refused cells are re-probed as they come due
 *     ({@link #takeExpired}), even by an idle quarry</td><td>none: the cells held are cells of the frame box</td>
 *     <td>tile owner</td><td>-</td></tr>
 * <tr><td>Builder and Filler placements</td><td>{@link #BUILDER_PLACE}</td><td>fixed 200 ticks; a refused cell is
 *     parked (skipped when queuing placements) and asked again once its window ends</td><td>none: the cells held
 *     are cells of the build box</td><td>tile owner</td><td>-</td></tr>
 * <tr><td>Stripes pipe</td><td>{@link #STRIPES}</td><td>fixed 200 ticks</td><td>4 (it faces one block at a
 *     time)</td><td>pipe owner</td><td>the block the current break cycle charges</td></tr>
 * <tr><td>Robot search boards (lumberjack, miner, ...)</td><td>{@link #ROBOT_SEARCH}</td><td>fixed 1200 ticks</td>
 *     <td>256, oldest dropped (a claimed forest)</td><td>robot's acting identity and home-station owner</td>
 *     <td>-</td></tr>
 * <tr><td>Pump; robot pump board</td><td>{@link #DRAIN}</td><td>spaced: 6000 ticks, stretched by 20 per other
 *     live refusal</td><td>none (see below)</td><td>tile owner; robot's acting identity and home-station
 *     owner</td><td>Pump: the cell the
 *     current bucket charges</td></tr>
 * </table>
 *
 * <p><b>Refusals.</b> A refusal holds while the block's state is unchanged, until its window ends. The state check
 * forgets it the moment the block becomes something else (a new block is a new question); the window exists because
 * lifting a claim, or trusting the machine's owner in it, fires no event a machine could hear. The refusals are kept
 * in a {@link PositionMemory}, the store this memory and the robots' unreachable-block cache share (robot block
 * claims are owner-held, never windowed, and stay outside it: see {@code BlockClaims}), whose policy sets the window
 * and cap:
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
 * holds answers for, the memory is emptied first, permit included. A robot answers to two players, the identity it
 * acts as and its home station's owner, both of whom must permit it (see {@code RobotProtection}), so its calls name
 * the pair: relinked to another player's station, or acting as another player, it starts afresh. A single owner is
 * the pair of that owner with itself, so a robot whose two players are one has the key a one-owner machine has.
 * Each identity is resolved once per profile rather than on every call: a search asking about hundreds of candidates
 * for one owner passes the same profile objects each time.
 *
 * <p><b>Clock and lifetime.</b> The clock is the level's game time everywhere; the pure overloads take it as an
 * argument so the logic is testable without a world. The {@link Level} overloads read state and clock from the world
 * without loading chunks: a cell in an unloaded chunk is not remembered and not reported as refused. Transient by
 * design: never saved, so a reloaded machine simply asks once more. Server thread only, like every caller.
 */
public final class RefusalMemory {

    /** The quarry's frame-volume cells: see the class table. */
    public static final PositionMemory.Policy QUARRY_FRAME = PositionMemory.Policy.fixed(200, Integer.MAX_VALUE);
    /** The Builder's and the Filler's refused placements: see the class table. */
    public static final PositionMemory.Policy BUILDER_PLACE = PositionMemory.Policy.fixed(200, Integer.MAX_VALUE);
    /** The stripes pipe: see the class table. */
    public static final PositionMemory.Policy STRIPES = PositionMemory.Policy.fixed(200, 4);
    /** The robot search boards: see the class table. */
    public static final PositionMemory.Policy ROBOT_SEARCH = PositionMemory.Policy.fixed(1200, 256);
    /** The pump and the robot pump board: five minutes, stretched by a second per other live refusal. */
    public static final PositionMemory.Policy DRAIN = PositionMemory.Policy.spaced(6000, 20);

    /** A cell's current state, for telling which remembered refusals are stale. */
    @FunctionalInterface
    public interface StateLookup {
        /** The state at {@code pos} now, or null when it cannot be read cheaply (an unloaded chunk): such a refusal
         *  is kept, since it may well still hold. */
        @Nullable
        BlockState stateAt(BlockPos pos);
    }

    /** Stands for "no owner named yet": distinct from every profile, and from null (an unowned machine). */
    private static final Object NO_OWNER_YET = new Object();

    /** The refused cells, each noted with the state it was refused in. */
    private final PositionMemory<BlockState> refused;
    /** The identity the machine acted as for every answer held; null until the first call names one. */
    @Nullable
    private UUID actorId;
    /** The identity that also had to permit every answer held (the same as {@link #actorId} for a machine that answers
     *  to one owner); null until the first call names one. */
    @Nullable
    private UUID ownerId;
    /** The profiles {@link #actorId} and {@link #ownerId} were last resolved from, or {@link #NO_OWNER_YET}. */
    @Nullable
    private Object actorResolvedFrom = NO_OWNER_YET;
    @Nullable
    private Object ownerResolvedFrom = NO_OWNER_YET;
    @Nullable
    private BlockPos permitPos;
    @Nullable
    private BlockState permitState;

    public RefusalMemory(PositionMemory.Policy policy) {
        this.refused = new PositionMemory<>(policy);
    }

    public PositionMemory.Policy policy() {
        return refused.policy();
    }

    /** Empties the memory if {@code owner} acts as someone other than the identity its answers were given to. The
     *  profile object passed last time is the same identity again (a profile's id and name never change), so only a
     *  different object is resolved. */
    private void keyOn(@Nullable GameProfile owner) {
        keyOn(owner, owner);
    }

    /** {@link #keyOn(GameProfile)} for a machine that acts as {@code actor} and needs {@code owner}'s leave too. */
    private void keyOn(@Nullable GameProfile actor, @Nullable GameProfile owner) {
        if (actor == actorResolvedFrom && owner == ownerResolvedFrom) {
            return;
        }
        actorResolvedFrom = actor;
        ownerResolvedFrom = owner;
        UUID actorNow = GameProfileUtil.getId(MachineIdentity.of(actor));
        UUID ownerNow = actor == owner ? actorNow : GameProfileUtil.getId(MachineIdentity.of(owner));
        if (!Objects.equals(actorNow, actorId) || !Objects.equals(ownerNow, ownerId)) {
            refused.clear();
            revokePermit();
            actorId = actorNow;
            ownerId = ownerNow;
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
        remember(pos, state, gameTime, owner, owner, lookup);
    }

    /** {@link #remember(BlockPos, BlockState, long, GameProfile, StateLookup)} for a machine that acted as
     *  {@code actor} and needed {@code owner}'s leave too (a robot: see the owner key in the class Javadoc). */
    public void remember(BlockPos pos, BlockState state, long gameTime, @Nullable GameProfile actor,
            @Nullable GameProfile owner, @Nullable StateLookup lookup) {
        keyOn(actor, owner);
        long key = pos.asLong();
        if (permitPos != null && permitPos.asLong() == key) {
            revokePermit();
        }
        refused.put(key, state, gameTime, lookup == null ? null : (cell, refusedIn) -> {
            BlockState now = lookup.stateAt(BlockPos.of(cell));
            return now != null && !now.equals(refusedIn);
        });
    }

    /**
     * Whether the machine should leave {@code pos} alone: it was refused to {@code owner} in this very {@code state}
     * and the window has not ended. A changed state or an ended window forgets the refusal and answers false.
     */
    public boolean isRefused(BlockPos pos, BlockState state, long gameTime, @Nullable GameProfile owner) {
        return isRefused(pos, state, gameTime, owner, owner);
    }

    /** {@link #isRefused(BlockPos, BlockState, long, GameProfile)} for a machine that acts as {@code actor} and needs
     *  {@code owner}'s leave too. */
    public boolean isRefused(BlockPos pos, BlockState state, long gameTime, @Nullable GameProfile actor,
            @Nullable GameProfile owner) {
        keyOn(actor, owner);
        long key = pos.asLong();
        BlockState refusedIn = refused.get(key, gameTime);
        if (refusedIn == null) {
            return false;
        }
        if (refusedIn.equals(state)) {
            return true;
        }
        refused.remove(key);
        return false;
    }

    /** {@link #remember(BlockPos, BlockState, long, GameProfile, StateLookup)} for the cell's current state and the
     *  world's clock. A cell in an unloaded chunk is not read (that would load it) and so not remembered: it has not
     *  been seen to refuse. Other remembered cells in unloaded chunks are likewise left unread, only aged out. */
    public void remember(Level level, BlockPos pos, @Nullable GameProfile owner) {
        remember(level, pos, owner, owner);
    }

    /** {@link #remember(Level, BlockPos, GameProfile)} for a machine that acted as {@code actor} and needed
     *  {@code owner}'s leave too. */
    public void remember(Level level, BlockPos pos, @Nullable GameProfile actor, @Nullable GameProfile owner) {
        if (!level.isLoaded(pos)) {
            return;
        }
        remember(pos, level.getBlockState(pos), level.getGameTime(), actor, owner,
                cell -> level.isLoaded(cell) ? level.getBlockState(cell) : null);
    }

    /** {@link #isRefused(BlockPos, BlockState, long, GameProfile)} against the world's current state and clock. A cell
     *  in an unloaded chunk is not read (that would load it) and is reported as not refused, its entry left as it was
     *  to be judged once the cell is loaded. */
    public boolean isRefused(Level level, BlockPos pos, @Nullable GameProfile owner) {
        return isRefused(level, pos, owner, owner);
    }

    /** {@link #isRefused(Level, BlockPos, GameProfile)} for a machine that acts as {@code actor} and needs
     *  {@code owner}'s leave too. */
    public boolean isRefused(Level level, BlockPos pos, @Nullable GameProfile actor, @Nullable GameProfile owner) {
        keyOn(actor, owner);
        return !refused.isEmpty() && level.isLoaded(pos)
                && isRefused(pos, level.getBlockState(pos), level.getGameTime(), actor, owner);
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
        return refused.takeExpired(gameTime, limit);
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
