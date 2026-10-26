/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.viewer;

import java.util.function.Supplier;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import buildcraft.lib.gui.BCContainer;

/**
 * The one viewer-neutral core of every BuildCraft "+" recipe transfer that moves real items into a machine's container:
 * the Programming, Integration and Assembly Tables (items) and the Distiller and Heat Exchanger (buckets). JEI's
 * {@code AbstractTransferHandler} and REI's {@code ReiRecipeTransferHandler} both wrap one of these, so the two viewers
 * check the same things and send the server the same container message.
 *
 * <p>A viewer asks constantly whether its "+" button is enabled (no transfer) and once more when it is pressed. So the
 * checks are the machine's own and the two halves stay apart:
 * <ul>
 * <li>{@link #check} validates the request against the client's copy of the player inventory and the machine's
 *     slots and changes nothing. It is advisory only, because the client's copy can lag;</li>
 * <li>{@link #send}, run only when the press came with {@code doTransfer} and {@link #check} said
 *     {@link TransferResult#OK}, sends the container message that makes the server re-run the same checks for real
 *     against its own state.</li>
 * </ul>
 * {@link #transfer} runs the two in that order and returns the {@link TransferResult}, which each viewer turns into
 * its own answer (an enabled button, a "missing" or "busy" tooltip on a greyed one, or an internal error).
 *
 * @param <C> the machine's container menu type
 * @param <R> the recipe-viewer entry type
 */
public abstract class RecipeTransfer<C extends AbstractContainerMenu & BCContainer, R> {

    private final Class<C> containerClass;
    private final Supplier<MenuType<C>> menuType;

    protected RecipeTransfer(Class<C> containerClass, Supplier<MenuType<C>> menuType) {
        this.containerClass = containerClass;
        this.menuType = menuType;
    }

    /** The machine's container class: a viewer offers the transfer only on a screen whose menu is one of these. */
    public final Class<C> containerClass() {
        return containerClass;
    }

    public final MenuType<C> menuType() {
        return menuType.get();
    }

    /**
     * Whether {@code recipe} can be transferred into {@code container} from {@code player}'s inventory as the
     * client sees both — the same checks the server runs when the message arrives. Must not change anything.
     *
     * @param maxTransfer whether the press was a shift-click, asking for everything available rather than one
     *                    recipe's worth; the robotics tables and the bucket machines have no quantity and ignore it
     */
    protected abstract TransferResult check(C container, R recipe, Player player, boolean maxTransfer);

    /**
     * Sends the container message that makes the server do the transfer. Called only after {@link #check} returned
     * {@link TransferResult#OK} for the same arguments, and only for a real press.
     */
    protected abstract void send(C container, R recipe, Player player, boolean maxTransfer);

    /** Checks, and on a real press that checked {@link TransferResult#OK} sends; returns what the check said. */
    public final TransferResult transfer(C container, R recipe, Player player, boolean maxTransfer,
            boolean doTransfer) {
        TransferResult result = check(container, recipe, player, maxTransfer);
        if (result == TransferResult.OK && doTransfer) {
            send(container, recipe, player, maxTransfer);
        }
        return result;
    }
}
