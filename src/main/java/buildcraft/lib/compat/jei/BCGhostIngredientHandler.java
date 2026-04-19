/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.slot.IPhantomSlot;

/**
 * A reusable JEI ghost ingredient handler for BuildCraft GUIs.
 * Allows dragging ingredients from JEI's sidebar directly onto phantom/blueprint
 * slots. Each phantom slot that implements {@link IPhantomSlot} becomes a valid
 * drop target.
 *
 * <p>When an ingredient is dropped, a custom container message
 * ({@link ContainerBC_Neptune#NET_GHOST_SLOT_SET}) is sent to the server to
 * update the slot contents.
 *
 * @param <T> the GUI screen type
 */
public class BCGhostIngredientHandler<T extends GuiBC8<?>> implements IGhostIngredientHandler<T> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();

        // Only accept ItemStack ingredients
        if (!(ingredient.getIngredient() instanceof ItemStack)) {
            return targets;
        }

        ContainerBC_Neptune container = gui.getMenu();
        for (int i = 0; i < container.slots.size(); i++) {
            Slot slot = container.slots.get(i);
            if (slot instanceof IPhantomSlot) {
                final int slotIndex = i;
                int x = gui.getLeftPos() + slot.x;
                int y = gui.getTopPos() + slot.y;
                targets.add(new PhantomSlotTarget<>(container, slotIndex, x, y));
            }
        }

        return targets;
    }

    @Override
    public void onComplete() {
        // Nothing to clean up
    }

    /**
     * A drop target for a single phantom slot. When the ingredient is dropped,
     * a network message is sent to the server to set the slot contents.
     */
    private static class PhantomSlotTarget<I> implements Target<I> {
        private final ContainerBC_Neptune container;
        private final int slotIndex;
        private final Rect2i area;

        PhantomSlotTarget(ContainerBC_Neptune container, int slotIndex, int x, int y) {
            this.container = container;
            this.slotIndex = slotIndex;
            // Standard slot size is 16x16, positioned 1px inside the 18x18 slot border
            this.area = new Rect2i(x, y, 16, 16);
        }

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack) {
                // Send the item's registry name to the server.
                // The server will construct a count-1 ItemStack from it.
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).toString();
                container.sendMessage(ContainerBC_Neptune.NET_GHOST_SLOT_SET, buf -> {
                    buf.writeShort(slotIndex);
                    buf.writeUtf(itemId);
                });
            }
        }
    }
}
