/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

import net.minecraft.world.item.ItemStack;

/**
 * A {@link BCItemStorage} whose slots can also be overwritten outright, outside any transaction: the twin of
 * NeoForge 1.21.1's {@code IItemHandlerModifiable}. Only 1.21.1 reads it: there its adapter implements the modifiable
 * interface exactly when the storage does, so a foreign container wrapping a BuildCraft inventory (a
 * {@code SlotItemHandler} casts to {@code IItemHandlerModifiable} to {@code set} a slot) finds what it did before the
 * platform layer. The other loaders have no such handler type and ignore it.
 */
public interface BCModifiableItemStorage extends BCItemStorage {
    /** Replaces the stack at {@code index} with {@code stack} (the storage keeps its own copy if it needs one). */
    void setStackInSlot(int index, ItemStack stack);
}
