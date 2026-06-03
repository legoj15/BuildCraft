/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.compat.jei;

import java.util.Optional;

import javax.annotation.Nullable;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.recipes.IRefineryRecipeManager.IDistillationRecipe;
import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.container.ContainerDistiller;
import buildcraft.lib.compat.jei.JeiTransferUtil;
import buildcraft.lib.gui.ContainerBC_Neptune;

/**
 * JEI "+" recipe transfer for the Distiller: moves a filled bucket of the recipe's input fluid from
 * the player's inventory into the machine's input container slot (slot 0), which the tile then
 * drains into its input tank. Enabled only when the player holds such a bucket (fluid sitting in a
 * tank or pipe can't transfer — there's no item to move).
 */
public class DistillerTransferHandler implements IRecipeTransferHandler<ContainerDistiller, IDistillationRecipe> {
    private final IRecipeTransferHandlerHelper helper;

    public DistillerTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends ContainerDistiller> getContainerClass() {
        return ContainerDistiller.class;
    }

    @Override
    public Optional<MenuType<ContainerDistiller>> getMenuType() {
        return Optional.of(BCFactoryMenuTypes.DISTILLER.get());
    }

    @Override
    public IRecipeType<IDistillationRecipe> getRecipeType() {
        return DistillerRecipeTypes.DISTILLER;
    }

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(
            ContainerDistiller container,
            IDistillationRecipe recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {

        FluidStack in = recipe.in();
        Item bucket = (in == null || in.isEmpty()) ? Items.AIR : in.getFluid().getBucket();
        if (bucket == Items.AIR || JeiTransferUtil.countMatching(player.getInventory(), new ItemStack(bucket)) < 1) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("gui.jei.transfer.buildcraftunofficial.missing"));
        }

        if (doTransfer) {
            String bucketId = BuiltInRegistries.ITEM.getKey(bucket).toString();
            container.sendMessage(ContainerBC_Neptune.NET_JEI_TRANSFER_BUCKETS, buf -> {
                buf.writeVarInt(1);
                buf.writeVarInt(0); // input container slot
                buf.writeUtf(bucketId);
            });
        }
        return null;
    }
}
