/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

import net.minecraft.world.item.ItemStack;

/**
 * An indexed item storage, BuildCraft's view of an inventory: BuildCraft's own handlers implement it, and
 * {@link TransferAccess} hands out a view of any other mod's inventory. Its shape is NeoForge's
 * {@code ResourceHandler<ItemResource>} with {@code int} amounts and {@link ItemStack}s standing in for resources: a
 * "resource" argument names an item plus its components, and its count is ignored. The members are
 * {@link BCStorage}'s: indices are slots, amounts are item counts, an empty slot holds {@link ItemStack#EMPTY}, and the
 * capacity asked for an empty resource is an estimate.
 *
 * <p>Changes happen only through {@link #insert} and {@link #extract}, inside a transaction; a storage that changes
 * state journals it with a {@link BCSnapshotJournal}, so an aborted transaction leaves it as it was.
 */
public interface BCItemStorage extends BCStorage<ItemStack> {}
