/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.transfer;

import java.util.ArrayList;
import java.util.List;

import buildcraft.api.core.BCLog;
import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.BCTransactionContext;

/**
 * The real calls a {@code BCFluidStorage} view owes a fluid handler that can only simulate (a classic NeoForge
 * {@code IFluidHandler} on 1.21.1, another mod's robot-API {@code IFluidHandlerAdv}): each fill or drain is answered
 * by a simulation and queued, the queue replays in order when the root transaction commits, and an abort drops what
 * it queued.
 *
 * <p>The handler's simulations cannot see the queue, so a second fill (or drain) of a fluid in one root transaction
 * would be promised again out of the handler's untouched state, and the replay would then move less than the view
 * reported. Each simulation therefore asks for what this transaction already owes of that fluid plus the new amount
 * and answers only the difference: the queued calls never add up to more than one call of their total would move.
 * A fill offered while a fill of another fluid is queued is refused, since the handler cannot be asked about both at
 * once. (A lookup hands out the view already in use for a handler, so callers share its queue; a transaction that
 * queued a call keeps its view in use until it closes, and a view nobody holds any more is replaced by a new one with
 * its own.)
 *
 * <p>A replayed call that moves less than its simulation promised is only logged, which a move between two storages
 * cannot afford: the other side already committed. {@link #drainInto} and {@link #fillFrom} make such a move outside
 * any transaction in the order the classic handlers needed before the platform layer (both halves simulated, then
 * the handler's real call, then the other side's, and what that side cannot take handed back), so a handler whose
 * real call breaks its own simulation cannot create fluid, and a hand-back it refuses is logged, never silent.
 */
public final class DeferredFluidCalls {
    /** A storage view whose fluid calls are owed to a handler that can only simulate. */
    public interface Owner {
        DeferredFluidCalls deferredCalls();
    }

    /** One direction of the handler: how much a call of {@code stack} (its amount included) moves. */
    @FunctionalInterface
    public interface Call {
        int apply(BCFluidStack stack, boolean simulate);
    }

    private record Owed(boolean fill, BCFluidStack stack) {}

    // An open transaction holds the journal, and through it this object; holding the owning view here keeps that
    // view in use for the transaction's whole life, so a lookup of the same handler inside it gets this queue again
    // instead of a fresh view with nothing owed.
    @SuppressWarnings("unused")
    private final Owner owner;
    private final Object handler;
    private final Call fill;
    private final Call drain;
    private final List<Owed> pending = new ArrayList<>();
    private final BCSnapshotJournal<Integer> journal = new BCSnapshotJournal<>() {
        @Override
        protected Integer createSnapshot() {
            return pending.size();
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            pending.subList(snapshot, pending.size()).clear();
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            List<Owed> replay = new ArrayList<>(pending);
            pending.clear();
            for (Owed owed : replay) {
                int done = (owed.fill ? fill : drain).apply(owed.stack.copy(), false);
                if (done != owed.stack.getAmount()) {
                    BCLog.logger.warn("[transfer] {} {} {} of a simulated {} of {}", handler,
                        owed.fill ? "took" : "gave", done, owed.fill ? "fill" : "drain", owed.stack);
                }
            }
        }
    };

    /**
     * {@code owner} is the view this queue belongs to; {@code handler} names the handler in log lines; the calls
     * reach it.
     */
    public DeferredFluidCalls(Owner owner, Object handler, Call fill, Call drain) {
        this.owner = owner;
        this.handler = handler;
        this.fill = fill;
        this.drain = drain;
    }

    /** Promises up to {@code amount} of {@code resource} into the handler, replayed at the root commit. */
    public int fill(BCFluidStack resource, int amount, BCTransactionContext transaction) {
        return promise(true, resource, amount, transaction);
    }

    /** Promises up to {@code amount} of {@code resource} out of the handler, replayed at the root commit. */
    public int drain(BCFluidStack resource, int amount, BCTransactionContext transaction) {
        return promise(false, resource, amount, transaction);
    }

    /**
     * Moves up to {@code amount} of {@code resource} out of the handler into index 0 of {@code into}, now and
     * outside any transaction; {@code execute} false only simulates. Returns the mB that reached {@code into}.
     */
    public int drainInto(BCFluidStorage into, BCFluidStack resource, int amount, boolean execute) {
        checkNothingOwed();
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        int extractable = drain.apply(resource.copyWithAmount(amount), true);
        if (extractable <= 0 || insertNow(into, resource, extractable, false) < extractable) {
            return 0;
        }
        if (!execute) {
            return extractable;
        }
        int extracted = drain.apply(resource.copyWithAmount(extractable), false);
        if (extracted <= 0) {
            return 0;
        }
        int filled = insertNow(into, resource, extracted, true);
        if (filled < extracted) {
            warnShortHandBack(handler, extracted - filled,
                fill.apply(resource.copyWithAmount(extracted - filled), false), resource);
        }
        return filled;
    }

    /**
     * Moves up to {@code amount} of {@code resource} out of index 0 of {@code from} into the handler, now and
     * outside any transaction; {@code execute} false only simulates. Returns the mB the handler took.
     */
    public int fillFrom(BCFluidStorage from, BCFluidStack resource, int amount, boolean execute) {
        checkNothingOwed();
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        int extractable = extractNow(from, resource, amount, false);
        if (extractable <= 0 || fill.apply(resource.copyWithAmount(extractable), true) < extractable) {
            return 0;
        }
        if (!execute) {
            return extractable;
        }
        int extracted = extractNow(from, resource, extractable, true);
        if (extracted <= 0) {
            return 0;
        }
        int filled = fill.apply(resource.copyWithAmount(extracted), false);
        if (filled < extracted) {
            warnShortHandBack(from, extracted - filled, insertNow(from, resource, extracted - filled, true), resource);
        }
        return filled;
    }

    /** A hand-back the giving side would not fully re-take loses the rest: say so, as a short replay does. */
    private static void warnShortHandBack(Object to, int owed, int taken, BCFluidStack resource) {
        if (taken != owed) {
            BCLog.logger.warn("[transfer] {} took back {} of the {} of {} it gave", to, taken, owed, resource);
        }
    }

    private void checkNothingOwed() {
        if (!pending.isEmpty()) {
            throw new IllegalStateException("A direct fluid move needs no open transaction, but " + handler
                + " is owed " + pending);
        }
    }

    // The BuildCraft side's own root transaction, so a call made inside an open one fails here instead of moving
    // fluid that transaction could not take back.
    private static int insertNow(BCFluidStorage storage, BCFluidStack resource, int amount, boolean execute) {
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int moved = storage.insert(0, resource, amount, tx);
            if (execute) {
                tx.commit();
            }
            return moved;
        }
    }

    private static int extractNow(BCFluidStorage storage, BCFluidStack resource, int amount, boolean execute) {
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int moved = storage.extract(0, resource, amount, tx);
            if (execute) {
                tx.commit();
            }
            return moved;
        }
    }

    private int promise(boolean isFill, BCFluidStack resource, int amount, BCTransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        long owed = 0;
        for (Owed call : pending) {
            if (call.fill != isFill) {
                continue;
            }
            if (BCFluidStack.isSameFluidSameComponents(call.stack, resource)) {
                owed += call.stack.getAmount();
            } else if (isFill) {
                return 0;
            }
        }
        int asked = (int) Math.min(owed + amount, Integer.MAX_VALUE);
        int answered = (isFill ? fill : drain).apply(resource.copyWithAmount(asked), true);
        int moved = (int) Math.min(amount, Math.max(0, answered - owed));
        if (moved > 0) {
            journal.updateSnapshots(transaction);
            pending.add(new Owed(isFill, resource.copyWithAmount(moved)));
        }
        return moved;
    }
}
