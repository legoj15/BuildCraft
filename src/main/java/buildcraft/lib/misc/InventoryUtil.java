/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.transport.IInjectable;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeFlow;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.lib.tile.item.IBCItemHandler;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.lib.transfer.ItemStorageUtil;
import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.TransferAccess;

public class InventoryUtil {
    /** Extracts all items from the handler and adds them to the given list. */
    public static void addAll(IBCItemHandler handler, List<ItemStack> list) {
        ItemStorageUtil.addAll(handler, list);
    }

    /** Attempts to add the given stack to the best acceptor, in this order:
     * {@link IInjectable} instances (BuildCraft pipes),
     * item storages on adjacent blocks (chests, hoppers, etc.),
     * and finally dropping it on the ground. */
    public static void addToBestAcceptor(Level level, BlockPos pos, @Nullable Direction ignore, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return;
        stack = addToRandomInjectable(level, pos, ignore, stack);
        stack = addToRandomInventory(level, pos, stack);
        if (!stack.isEmpty()) {
            drop(level, pos, stack);
        }
    }

    /** Look around the tile given in parameter in all 6 positions, tries to add the items to a random injectable tile
     * around. Will make sure that the location from which the items are coming from (identified by the ignore parameter)
     * isn't used again so that entities don't go backwards. Returns the leftover stack. */
    @Nonnull
    public static ItemStack addToRandomInjectable(Level level, BlockPos pos, @Nullable Direction ignore, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        List<Direction> toTry = new ArrayList<>(6);
        Collections.addAll(toTry, Direction.values());
        Collections.shuffle(toTry);

        for (Direction face : toTry) {
            if (face == ignore) continue;
            if (stack.isEmpty()) return ItemStack.EMPTY;

            BlockPos adjPos = pos.relative(face);
            BlockEntity tile = level.getBlockEntity(adjPos);
            if (tile == null) continue;

            // Check if the adjacent tile is a pipe holder with an injectable flow
            IInjectable injectable = getInjectable(tile, face.getOpposite());
            if (injectable == null) continue;

            stack = injectable.injectItem(stack, true, face.getOpposite(), null, 0);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        return stack;
    }

    /** Tries to get an {@link IInjectable} from the given tile entity, checking its pipe flow
     * for the CAP_INJECTABLE capability (matching 1.12.2 ItemTransactorHelper.getInjectable). */
    @Nullable
    private static IInjectable getInjectable(BlockEntity tile, Direction face) {
        if (tile instanceof IPipeHolder holder) {
            var pipe = holder.getPipe();
            if (pipe != null) {
                PipeFlow flow = pipe.getFlow();
                if (flow != null) {
                    Object result = flow.getCapability(PipeApi.CAP_INJECTABLE, face);
                    if (result instanceof IInjectable injectable) {
                        return injectable;
                    }
                }
            }
        }
        return null;
    }

    /** Tries to insert the stack into adjacent inventories (chests, hoppers, etc), found through the platform
     * layer's item lookup.
     * @return The leftover stack that could not be inserted. */
    @Nonnull
    public static ItemStack addToRandomInventory(Level level, BlockPos pos, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        List<Direction> toTry = new ArrayList<>(6);
        Collections.addAll(toTry, Direction.values());
        Collections.shuffle(toTry);

        int remaining = stack.getCount();

        for (Direction face : toTry) {
            if (remaining <= 0) return ItemStack.EMPTY;

            BlockPos adjPos = pos.relative(face);
            // Query the adjacent block for its item storage on the side facing us
            BCItemStorage handler = TransferAccess.findItem(level, adjPos, face.getOpposite());
            if (handler == null) continue;

            // insertStacking prefers filled slots first, then empty — passing null opens and auto-commits a transaction
            int inserted = ItemStorageUtil.insertStacking(handler, stack, remaining, null);
            remaining -= inserted;
        }

        if (remaining <= 0) return ItemStack.EMPTY;
        return stack.copyWithCount(remaining);
    }

    /**
     * Comparator signal from how full the counted slots of {@code inv} are: the 7.1.x
     * {@code BlockBuildCraft.getComparatorInputOverride} formula (vanilla's container formula restricted to the
     * slots {@code slotCounts} accepts). Each counted slot contributes its fill fraction against
     * min(slot limit, item max stack size); the average maps to 0..14, plus 1 when any counted slot holds
     * anything. No counted slot reads 0 — 7.1.x divided by zero there.
     */
    public static int getComparatorLevel(ItemHandlerSimple inv, IntPredicate slotCounts) {
        int counted = 0;
        boolean anyItem = false;
        float fill = 0;
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            if (!slotCounts.test(slot)) {
                continue;
            }
            counted++;
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                anyItem = true;
                int limit = Math.max(1, Math.min(inv.getSlotLimit(slot), stack.getMaxStackSize()));
                fill += Math.min(1f, (float) stack.getCount() / limit);
            }
        }
        if (counted == 0) {
            return 0;
        }
        return Mth.floor(fill / counted * 14.0F) + (anyItem ? 1 : 0);
    }

    /** Drops the stack as an item entity at the given position. */
    public static void drop(Level level, BlockPos pos, @Nonnull ItemStack stack) {
        if (!stack.isEmpty()) {
            Block.popResource(level, pos, stack);
        }
    }
}
