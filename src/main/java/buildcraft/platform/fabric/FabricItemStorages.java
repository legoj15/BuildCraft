/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCTransactionContext;

/^*
 * The two-way adapter between {@link BCItemStorage} and Fabric's {@code Storage<ItemVariant>}: the item kind of
 * {@link FabricStorageAdapter}, which holds the shape and the non-slotted gap. What is item-specific: amounts are
 * counts on both sides (clamped to {@code int}), a foreign slot's capacity is bounded by the item's own stack size, and
 * a whole-storage insert into a BuildCraft inventory stacks onto occupied slots before it fills empty ones.
 ^/
public final class FabricItemStorages extends FabricStorageAdapter<ItemStack, ItemVariant, BCItemStorage> {
    private static final FabricItemStorages KIND = new FabricItemStorages();

    private FabricItemStorages() {}

    public static Storage<ItemVariant> toFabric(BCItemStorage storage) {
        return KIND.toFabricStorage(storage);
    }

    public static BCItemStorage fromFabric(Storage<ItemVariant> storage) {
        return KIND.fromFabricStorage(storage);
    }

    @Override
    boolean isEmpty(ItemStack resource) {
        return resource.isEmpty();
    }

    @Override
    ItemStack empty() {
        return ItemStack.EMPTY;
    }

    @Override
    ItemVariant blank() {
        return ItemVariant.blank();
    }

    @Override
    ItemStack resourceOf(ItemVariant variant) {
        return variant.toStack();
    }

    @Override
    ItemVariant variantOf(ItemStack resource) {
        return ItemVariant.of(resource);
    }

    @Override
    ItemStack stackOf(ItemVariant variant, long amount) {
        int count = toBuildCraft(amount);
        return variant.isBlank() || count <= 0 ? ItemStack.EMPTY : variant.toStack(count);
    }

    @Override
    int toBuildCraft(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
    }

    @Override
    long toFabric(int amount) {
        return amount;
    }

    @Override
    int amountAt(BCItemStorage storage, int index) {
        return storage.getStack(index).getCount();
    }

    @Override
    ItemStack capacityProbe(BCItemStorage storage, int index) {
        return storage.getStack(index);
    }

    @Override
    int boundCapacity(int capacity, ItemStack resource) {
        // Fabric reports one capacity per slot, for what it holds; an item's own stack size still bounds it.
        return resource.isEmpty() ? capacity : Math.min(capacity, resource.getMaxStackSize());
    }

    @Override
    int insertAcross(BCItemStorage storage, ItemStack resource, int amount, BCTransactionContext transaction) {
        int inserted = 0;
        // Stacking order, as BuildCraft's own insertStacking: occupied indices first, then empty ones.
        for (int pass = 0; pass < 2 && inserted < amount; pass++) {
            for (int i = 0; i < storage.size() && inserted < amount; i++) {
                if (storage.getStack(i).isEmpty() == (pass == 1)) {
                    inserted += storage.insert(i, resource, amount - inserted, transaction);
                }
            }
        }
        return inserted;
    }

    @Override
    int extractAcross(BCItemStorage storage, ItemStack resource, int amount, BCTransactionContext transaction) {
        int extracted = 0;
        for (int i = 0; i < storage.size() && extracted < amount; i++) {
            extracted += storage.extract(i, resource, amount - extracted, transaction);
        }
        return extracted;
    }

    @Override
    BCItemStorage slottedView(SlottedStorage<ItemVariant> storage) {
        return new Slotted(storage);
    }

    @Override
    BCItemStorage plainView(Storage<ItemVariant> storage) {
        return new Plain(storage);
    }

    private static final class Slotted extends FromSlotted<ItemStack, ItemVariant> implements BCItemStorage {
        Slotted(SlottedStorage<ItemVariant> storage) {
            super(KIND, storage);
        }
    }

    private static final class Plain extends FromPlain<ItemStack, ItemVariant> implements BCItemStorage {
        Plain(Storage<ItemVariant> storage) {
            super(KIND, storage);
        }
    }
}
*///?}
