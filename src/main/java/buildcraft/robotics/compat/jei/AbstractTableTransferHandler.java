/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.jei;

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

import buildcraft.robotics.container.RoboticsTableTransfer;

/**
 * Shared base for the JEI "+" recipe-transfer handlers of the two robotics tables. Each subclass names its JEI
 * recipe type and turns a recipe into a {@link RoboticsTableTransfer} request; this base owns everything that is the
 * same for both — the handler plumbing, and answering JEI with the outcome of the server's own checks.
 *
 * <p>The checks are the server's: {@link RoboticsTableTransfer} run with {@code doTransfer = false} against the
 * client's copy of the player inventory and table slots decides whether the button is enabled (advisory only), and
 * pressing it sends the container message that makes the server re-run them for real. Neither table has a
 * per-press quantity, so shift-click ({@code maxTransfer}) means the same as a plain click.
 *
 * @param <C> the table's container menu type
 * @param <R> the JEI recipe type
 */
public abstract class AbstractTableTransferHandler<C extends AbstractContainerMenu & BCContainer, R>
        implements IRecipeTransferHandler<C, R> {

    protected final IRecipeTransferHandlerHelper helper;
    private final Class<C> containerClass;
    private final Supplier<MenuType<C>> menuType;

    protected AbstractTableTransferHandler(IRecipeTransferHandlerHelper helper, Class<C> containerClass,
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
     * Validates the transfer of {@code recipe} into {@code container} against the player's inventory and, when
     * {@code doTransfer} and the validation passed, sends the container message that does it for real.
     */
    protected abstract RoboticsTableTransfer.Result execute(C container, R recipe, Player player, boolean doTransfer);

    //? if >=26.2 {
    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(IRecipeTransferContext<R, C> context, boolean doTransfer) {
        return transfer(context.getContainer(), context.getRecipe(), context.getPlayer(), doTransfer);
    }
    //?}

    // JEI 30.30+ (26.2) deprecates this for removal in favour of the context overload above but keeps it
    // abstract, so it must stay; it is also the only entry point on older JEI builds.
    @Override
    @Nullable
    @SuppressWarnings("removal")
    public IRecipeTransferError transferRecipe(C container, R recipe, IRecipeSlotsView recipeSlots,
            Player player, boolean maxTransfer, boolean doTransfer) {
        return transfer(container, recipe, player, doTransfer);
    }

    @Nullable
    private IRecipeTransferError transfer(C container, R recipe, Player player, boolean doTransfer) {
        return switch (execute(container, recipe, player, doTransfer)) {
            case OK -> null;
            case MISSING -> helper.createUserErrorWithTooltip(
                    Component.translatable("gui.jei.transfer.buildcraftunofficial.missing"));
            case BUSY -> helper.createUserErrorWithTooltip(
                    Component.translatable("gui.jei.transfer.buildcraftunofficial.table_busy"));
            case INVALID -> helper.createInternalError();
        };
    }
}
