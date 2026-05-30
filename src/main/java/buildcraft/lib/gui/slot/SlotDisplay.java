/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui.slot;

import java.util.function.IntFunction;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A display-only slot that shows an item computed by a supplier function.
 * Cannot be interacted with (no pickup, no insertion).
 */
public class SlotDisplay extends Slot {
    private static final Container EMPTY_INVENTORY = new SimpleContainer(0);

    private final IntFunction<ItemStack> getter;
    private final int displayIndex;

    public SlotDisplay(IntFunction<ItemStack> getter, int displayIndex, int x, int y) {
        super(EMPTY_INVENTORY, displayIndex, x, y);
        this.getter = getter;
        this.displayIndex = displayIndex;
    }

    @Override
    public ItemStack getItem() {
        return getter.apply(displayIndex);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public void set(ItemStack stack) {
        // No-op — display only
    }

    @Override
    public ItemStack remove(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean hasItem() {
        return !getItem().isEmpty();
    }

    @Override
    public int getMaxStackSize() {
        return 0;
    }

    /** Display slots are read-only result previews, not tangible inventory. Reporting them as fake
     *  routes vanilla's slot renderer through {@code fakeItem} (null holder), so dynamic models
     *  (clock/compass) draw their static frame instead of the live world time/heading. */
    @Override
    public boolean isFake() {
        return true;
    }
}
