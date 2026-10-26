/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.rei;

import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import buildcraft.lib.compat.viewer.RecipeTransfer;
import buildcraft.lib.compat.viewer.TransferResult;
import buildcraft.lib.gui.BCContainer;

/**
 * REI's "+" button for one BuildCraft machine category: wraps the machine's shared {@link RecipeTransfer} (the one
 * JEI's handler wraps too), so both viewers check the same things and send the server the same container message. A
 * {@link TransferResult} becomes REI's answer: OK an enabled button (and on a press, the transfer), MISSING and BUSY a
 * greyed button with the same tooltip JEI shows, INVALID no button.
 *
 * @param <C> the machine's container menu type
 * @param <R> the recipe-viewer entry type
 */
public final class ReiRecipeTransferHandler<C extends AbstractContainerMenu & BCContainer, R>
        implements TransferHandler {

    // JEI's tooltips for the same two answers.
    private static final String MISSING_KEY = "gui.jei.transfer.buildcraftunofficial.missing";
    private static final String BUSY_KEY = "gui.jei.transfer.buildcraftunofficial.table_busy";

    private final CategoryIdentifier<ReiDisplay<R>> category;
    private final RecipeTransfer<C, R> core;

    public ReiRecipeTransferHandler(CategoryIdentifier<ReiDisplay<R>> category, RecipeTransfer<C, R> core) {
        this.category = category;
        this.core = core;
    }

    @Override
    public Result handle(Context context) {
        if (!(context.getDisplay() instanceof ReiDisplay<?> display)
                || !display.getCategoryIdentifier().equals(category)) {
            return Result.createNotApplicable();
        }
        AbstractContainerMenu menu = context.getMenu();
        Player player = context.getMinecraft().player;
        if (!core.containerClass().isInstance(menu) || player == null) {
            return Result.createNotApplicable();
        }
        @SuppressWarnings("unchecked") // the category check above fixes the display's recipe type to R
        R recipe = ((ReiDisplay<R>) display).recipe();
        TransferResult result = core.transfer(core.containerClass().cast(menu), recipe, player,
                context.isStackedCrafting(), context.isActuallyCrafting());
        return switch (result) {
            case OK -> Result.createSuccessful().blocksFurtherHandling();
            case MISSING -> Result.createFailed(Component.translatable(MISSING_KEY));
            case BUSY -> Result.createFailed(Component.translatable(BUSY_KEY));
            case INVALID -> Result.createNotApplicable();
        };
    }
}
