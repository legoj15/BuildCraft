/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat.jei;

import java.util.Optional;

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

import buildcraft.lib.compat.viewer.RecipeTransfer;
import buildcraft.lib.compat.viewer.TransferResult;
import buildcraft.lib.gui.BCContainer;

/**
 * The JEI face of every BuildCraft "+" recipe-transfer handler that moves real items into a machine's container: the
 * Programming, Integration and Assembly Tables (items) and the Distiller and Heat Exchanger (buckets). A subclass names
 * its JEI recipe type and hands over the machine's viewer-neutral {@link RecipeTransfer} core, which REI's handler
 * wraps too; this base owns the JEI plumbing and answering JEI with the outcome.
 *
 * <p>JEI calls {@code transferRecipe} constantly with {@code doTransfer = false} to decide whether the "+" button
 * is enabled, and once with {@code true} when it is pressed; {@link RecipeTransfer#transfer} keeps the check and the
 * send apart for both. The {@link TransferResult} it returns is JEI's answer: {@code OK} enables the button, anything
 * else becomes the matching JEI error (a "missing" or "busy" tooltip on a greyed button, or an internal error for a
 * request the server would not know).
 *
 * @param <C> the machine's container menu type
 * @param <R> the JEI recipe type
 */
public abstract class AbstractTransferHandler<C extends AbstractContainerMenu & BCContainer, R>
        implements IRecipeTransferHandler<C, R> {

    private final IRecipeTransferHandlerHelper helper;
    private final RecipeTransfer<C, R> core;

    protected AbstractTransferHandler(IRecipeTransferHandlerHelper helper, RecipeTransfer<C, R> core) {
        this.helper = helper;
        this.core = core;
    }

    @Override
    public Class<? extends C> getContainerClass() {
        return core.containerClass();
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return Optional.of(core.menuType());
    }

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
        return switch (core.transfer(container, recipe, player, maxTransfer, doTransfer)) {
            case OK -> null;
            case MISSING -> helper.createUserErrorWithTooltip(
                    Component.translatable("gui.jei.transfer.buildcraftunofficial.missing"));
            case ONLY_PROGRAMMED -> helper.createUserErrorWithTooltip(
                    Component.translatable("gui.jei.transfer.buildcraftunofficial.only_programmed"));
            case BUSY -> helper.createUserErrorWithTooltip(
                    Component.translatable("gui.jei.transfer.buildcraftunofficial.inventory_full"));
            case INVALID -> helper.createInternalError();
        };
    }
}
