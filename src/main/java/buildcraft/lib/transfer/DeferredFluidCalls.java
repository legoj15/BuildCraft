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
import buildcraft.platform.transfer.BCSnapshotJournal;
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
 * once. (Two views of one handler keep separate queues; each lookup builds its own view.)
 */
public final class DeferredFluidCalls {
    /** One direction of the handler: how much a call of {@code stack} (its amount included) moves. */
    @FunctionalInterface
    public interface Call {
        int apply(BCFluidStack stack, boolean simulate);
    }

    private record Owed(boolean fill, BCFluidStack stack) {}

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

    /** {@code handler} names the handler in log lines; the calls reach it. */
    public DeferredFluidCalls(Object handler, Call fill, Call drain) {
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
