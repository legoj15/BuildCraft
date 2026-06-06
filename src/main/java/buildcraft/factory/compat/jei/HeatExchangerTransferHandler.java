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
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.container.ContainerHeatExchange;
import buildcraft.lib.compat.jei.JeiTransferUtil;
import buildcraft.lib.gui.ContainerBC_Neptune;

/**
 * JEI "+" recipe transfer for the Heat Exchanger: moves a filled bucket of the heatable input into
 * container slot 0 (hot in) and a bucket of the coolable input into slot 1 (cold in), which the
 * tile drains into its tanks. Enabled only when the player holds both buckets.
 */
public class HeatExchangerTransferHandler implements IRecipeTransferHandler<ContainerHeatExchange, HeatExchangerRecipePair> {
    private final IRecipeTransferHandlerHelper helper;

    public HeatExchangerTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends ContainerHeatExchange> getContainerClass() {
        return ContainerHeatExchange.class;
    }

    @Override
    public Optional<MenuType<ContainerHeatExchange>> getMenuType() {
        return Optional.of(BCFactoryMenuTypes.HEAT_EXCHANGE.get());
    }

    @Override
    public RecipeType<HeatExchangerRecipePair> getRecipeType() {
        return HeatExchangerRecipeTypes.PAIR;
    }

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(
            ContainerHeatExchange container,
            HeatExchangerRecipePair pair,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {

        // Slot→tank mapping (see TileHeatExchange's drain logic): container slot 0 → END.tankInput
        // drains the HOT fluid (coolable.in() — the fluid being cooled); slot 1 → START.tankInput
        // drains the COLD fluid (heatable.in() — the fluid being heated). "heatable"/"coolable"
        // name the role of the fluid, which is the opposite of its temperature, so the buckets must
        // cross relative to those names.
        Item slot0Bucket = bucketOf(pair.coolable().in()); // hot in
        Item slot1Bucket = bucketOf(pair.heatable().in()); // cold in
        if (slot0Bucket == Items.AIR || slot1Bucket == Items.AIR) {
            return missing();
        }

        Inventory inv = player.getInventory();
        // Usually two different fluids; if a pair ever shared one, two buckets are needed.
        if (slot0Bucket == slot1Bucket) {
            if (JeiTransferUtil.countMatching(inv, new ItemStack(slot0Bucket)) < 2) return missing();
        } else if (JeiTransferUtil.countMatching(inv, new ItemStack(slot0Bucket)) < 1
                || JeiTransferUtil.countMatching(inv, new ItemStack(slot1Bucket)) < 1) {
            return missing();
        }

        if (doTransfer) {
            String slot0Id = BuiltInRegistries.ITEM.getKey(slot0Bucket).toString();
            String slot1Id = BuiltInRegistries.ITEM.getKey(slot1Bucket).toString();
            container.sendMessage(ContainerBC_Neptune.NET_JEI_TRANSFER_BUCKETS, buf -> {
                buf.writeVarInt(2);
                buf.writeVarInt(0); // END.tankInput (hot in) ← coolable
                buf.writeUtf(slot0Id);
                buf.writeVarInt(1); // START.tankInput (cold in) ← heatable
                buf.writeUtf(slot1Id);
            });
        }
        return null;
    }

    private static Item bucketOf(FluidStack fluid) {
        return (fluid == null || fluid.isEmpty()) ? Items.AIR : fluid.getFluid().getBucket();
    }

    private IRecipeTransferError missing() {
        return helper.createUserErrorWithTooltip(
                Component.translatable("gui.jei.transfer.buildcraftunofficial.missing"));
    }
}
