/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.jei;

import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
//? if >=26.2 {
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
//?}
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import buildcraft.lib.gui.BCContainer;

/**
 * The one skeleton of every BuildCraft JEI "+" recipe-transfer handler that moves real items into a machine's
 * container: the Programming, Integration and Assembly Tables (items) and the Distiller and Heat Exchanger
 * (buckets). A subclass names its JEI recipe type and supplies the two machine-specific halves; this base owns
 * everything that is the same for all of them — the handler plumbing, the order of the two halves, and answering JEI
 * with the outcome.
 *
 * <p>JEI calls {@code transferRecipe} constantly with {@code doTransfer = false} to decide whether the "+" button
 * is enabled, and once with {@code true} when it is pressed. So the checks are the machine's own and the two halves
 * stay apart:
 * <ul>
 * <li>{@link #check} validates the request against the client's copy of the player inventory and the machine's
 *     slots and changes nothing. It is advisory only, because the client's copy can lag;</li>
 * <li>{@link #send}, run only when the press came with {@code doTransfer} and {@link #check} said
 *     {@link TransferResult#OK}, sends the container message that makes the server re-run the same checks for real
 *     against its own state.</li>
 * </ul>
 * The {@link TransferResult} {@link #check} returns is JEI's answer: {@code OK} enables the button, anything else
 * becomes the matching JEI error (a "missing" or "busy" tooltip on a greyed button, or an internal error for a
 * request the server would not know).
 *
 * @param <C> the machine's container menu type
 * @param <R> the JEI recipe type
 */
public abstract class AbstractTransferHandler<C extends AbstractContainerMenu & BCContainer, R>
        implements IRecipeTransferHandler<C, R> {

    private final IRecipeTransferHandlerHelper helper;
    private final Class<C> containerClass;
    private final Supplier<MenuType<C>> menuType;

    protected AbstractTransferHandler(IRecipeTransferHandlerHelper helper, Class<C> containerClass,
            Supplier<MenuType<C>> menuType) {
        this.helper = helper;
        this.containerClass = containerClass;
        this.menuType = menuType;
    }

    @Override
    public Class<? extends C> getContainerClass() {
        return containerClass;
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return Optional.of(menuType.get());
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

    //? if >=26.2 {
    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(IRecipeTransferContext<R, C> context, boolean doTransfer) {
        return transfer(context.getContainer(), context.getRecipe(), context.getPlayer(), context.isMaxTransfer(),
                doTransfer);
    }
    //?}

    // JEI 30.30+ (26.2) deprecates this for removal in favour of the context overload above but keeps it
    // abstract, so it must stay; it is also the only entry point on older JEI builds.
    @Override
    @Nullable
    @SuppressWarnings("removal")
    public IRecipeTransferError transferRecipe(C container, R recipe, IRecipeSlotsView recipeSlots,
            Player player, boolean maxTransfer, boolean doTransfer) {
        return transfer(container, recipe, player, maxTransfer, doTransfer);
    }

    @Nullable
    private IRecipeTransferError transfer(C container, R recipe, Player player, boolean maxTransfer,
            boolean doTransfer) {
        TransferResult result = check(container, recipe, player, maxTransfer);
        if (result == TransferResult.OK && doTransfer) {
            send(container, recipe, player, maxTransfer);
        }
        return switch (result) {
            case OK -> null;
            case MISSING -> helper.createUserErrorWithTooltip(
                    Component.translatable("gui.jei.transfer.buildcraftunofficial.missing"));
            case BUSY -> helper.createUserErrorWithTooltip(
                    Component.translatable("gui.jei.transfer.buildcraftunofficial.table_busy"));
            case INVALID -> helper.createInternalError();
        };
    }
}
