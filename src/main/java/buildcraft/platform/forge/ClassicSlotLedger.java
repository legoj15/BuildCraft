/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.VanillaHopperItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

import buildcraft.api.core.BCLog;
import buildcraft.lib.transfer.AdapterCache;
import buildcraft.platform.transfer.BCSnapshotJournal;
import buildcraft.platform.transfer.BCTransactionContext;

/^*
 * The calls a classic item handler's {@code BCItemStorage} view promised inside the open transaction, kept per slot of
 * what the handler is a view of, and replayed in order when the root commits (none if it aborts). The Forge copy of
 * {@code ClassicSlotLedger} in the NeoForge package (docs/forge-port.md: copied, not shared).
 *
 * <p>Forge's patched vanilla block entities answer a capability query with a wrapper over their container
 * ({@code InvWrapper}, a {@code SidedInvWrapper} per side, a hopper's {@code VanillaHopperItemHandler}), and a double
 * chest's two halves each wrap a {@code CompoundContainer} of their own. So two lookups of one chest can be two
 * handlers, and the two halves of a double chest two more. Their promises are still kept together: those wrappers
 * resolve to the container slot they reach (a side's slot mapped through the face, a double chest's slot to its half),
 * and the ledger of that container answers every wrapper. Any other handler is its own container, slot for slot. A
 * provider that hides its container another way keeps one ledger per wrapper.
 *
 * <p>The ledgers are held weakly by their container ({@link AdapterCache}); an open transaction holds the ledger it
 * owes through its journal, so a lookup inside that transaction finds it again.
 *
 * <p><b>Reflection.</b> {@code SidedInvWrapper} keeps its container and side protected with no getter (a Forge class; the
 * shared access transformer names vanilla classes only), so those two fields are read reflectively, and a wrapper that
 * cannot be read is treated as its own container, the limit #148 lifted, not a crash. {@code CompoundContainer}'s two
 * halves are private in vanilla and read directly: the shared access transformer opens them.
 ^/
final class ClassicSlotLedger {
    private static final AdapterCache<Object, ClassicSlotLedger> LEDGERS =
        new AdapterCache<>(owner -> new ClassicSlotLedger());

    @Nullable
    private static final Field SIDED_INV = field(SidedInvWrapper.class, "inv");
    @Nullable
    private static final Field SIDED_SIDE = field(SidedInvWrapper.class, "side");

    /^* One real call owed at the root commit, made on the handler and slot that promised it. ^/
    private record Owed(int slot, boolean insert, ItemStack stack, IItemHandler handler, int handlerSlot) {}

    /^* A slot of a ledger: what a handler's slot reaches. ^/
    record Slot(ClassicSlotLedger ledger, int index) {
        /^* What is already owed here in this direction, or -1 when the slot owes the other direction or another
         *  item: the handler's simulation cannot answer for both at once. ^/
        int owed(boolean insert, ItemStack resource) {
            return ledger.owed(index, insert, resource);
        }

        void promise(IItemHandler handler, int handlerSlot, boolean insert, ItemStack stack,
            BCTransactionContext transaction) {
            ledger.journal.updateSnapshots(transaction);
            ledger.pending.add(new Owed(index, insert, stack, handler, handlerSlot));
        }
    }

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
            // One ledger carries every wrapper's promises on its container: a handler that throws must not drop
            // the rest unlogged.
            for (Owed owed : replay) {
                try {
                    replay(owed);
                } catch (RuntimeException e) {
                    BCLog.logger.error("[platform] {} threw replaying a simulated {} of {} in slot {}: lost",
                        owed.handler, owed.insert ? "insert" : "extract", owed.stack, owed.handlerSlot, e);
                }
            }
        }
    };

    private ClassicSlotLedger() {}

    /^* The ledger slot {@code handler}'s {@code slot} reaches. ^/
    static Slot slotOf(IItemHandler handler, int slot) {
        Class<?> type = handler.getClass();
        if (type == InvWrapper.class || type == VanillaHopperItemHandler.class) {
            return inContainer(((InvWrapper) handler).getInv(), slot);
        }
        if (type == SidedInvWrapper.class && SIDED_INV != null && SIDED_SIDE != null) {
            try {
                WorldlyContainer inv = (WorldlyContainer) SIDED_INV.get(handler);
                int reached = SidedInvWrapper.getSlot(inv, slot, (Direction) SIDED_SIDE.get(handler));
                if (reached >= 0) {
                    return inContainer(inv, reached);
                }
                // A slot past the face's array: the wrapper itself refuses every call on it, so nothing is ever
                // promised there and the per-wrapper ledger below is only ever read empty.
            } catch (IllegalAccessException e) {
                // setAccessible succeeded at class init, so this cannot happen; fall through to the wrapper itself.
            }
        }
        return new Slot(LEDGERS.get(handler), slot);
    }

    // A double chest's capability answers with an InvWrapper over a CompoundContainer of its two halves, one per half.
    private static Slot inContainer(Container container, int slot) {
        while (container instanceof CompoundContainer compound) {
            Container first = compound.container1;
            Container second = compound.container2;
            int firstSize = first.getContainerSize();
            if (slot < firstSize) {
                container = first;
            } else {
                container = second;
                slot -= firstSize;
            }
        }
        return new Slot(LEDGERS.get(container), slot);
    }

    private int owed(int slot, boolean insert, ItemStack resource) {
        int owed = 0;
        for (Owed call : pending) {
            if (call.slot != slot) {
                continue;
            }
            if (call.insert != insert || !ItemStack.isSameItemSameComponents(call.stack, resource)) {
                return -1;
            }
            owed += call.stack.getCount();
        }
        return owed;
    }

    private static void replay(Owed owed) {
        IItemHandler handler = owed.handler;
        int count = owed.stack.getCount();
        if (owed.insert) {
            ItemStack rest = handler.insertItem(owed.handlerSlot, owed.stack.copy(), false);
            if (!rest.isEmpty()) {
                BCLog.logger.warn("[platform] {} refused {} of a simulated insert into slot {}: {} lost",
                    handler, rest.getCount(), owed.handlerSlot, rest);
            }
        } else {
            ItemStack real = handler.extractItem(owed.handlerSlot, count, false);
            if (real.getCount() != count) {
                BCLog.logger.warn("[platform] {} gave {} of a simulated extract of {} from slot {}",
                    handler, real.getCount(), count, owed.handlerSlot);
            }
        }
    }

    @Nullable
    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            BCLog.logger.warn("[platform] cannot read {}.{}; a wrapper that hides it keeps its own ledger",
                owner.getSimpleName(), name, e);
            return null;
        }
    }
}
*///?}
