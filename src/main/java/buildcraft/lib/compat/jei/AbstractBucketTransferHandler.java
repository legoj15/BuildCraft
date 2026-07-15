/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.jei;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.lib.gui.BCContainer;
import buildcraft.lib.gui.ContainerBC_Neptune;

/**
 * Shared base for the JEI "+" recipe-transfer handlers that move filled buckets of a recipe's input
 * fluids from the player's inventory into a machine's container slots (Distiller, Heat Exchanger) —
 * the tile then drains those buckets into its input tanks. Enabled only when the player is holding the
 * required buckets (fluid already in a tank or pipe has no item to move).
 *
 * <p>Subclasses supply the JEI {@code getRecipeType()} and the recipe → (slot, bucket) mapping via
 * {@link #getRequiredBuckets}; this base performs the availability check and the
 * {@link ContainerBC_Neptune#NET_JEI_TRANSFER_BUCKETS} network send.
 *
 * @param <C> the machine's container menu type (a {@link BCContainer})
 * @param <R> the JEI recipe type
 */
public abstract class AbstractBucketTransferHandler<C extends AbstractContainerMenu & BCContainer, R>
        implements IRecipeTransferHandler<C, R> {

    protected final IRecipeTransferHandlerHelper helper;
    private final Class<C> containerClass;
    private final Supplier<MenuType<C>> menuType;

    protected AbstractBucketTransferHandler(IRecipeTransferHandlerHelper helper, Class<C> containerClass,
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
     * The bucket to place into each of the machine's container slots for this recipe. A returned
     * {@link BucketSlot} carrying {@link Items#AIR} (or an empty list) means the recipe has no
     * transferable bucket → the transfer is rejected with a "missing buckets" tooltip.
     */
    protected abstract List<BucketSlot> getRequiredBuckets(R recipe);

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(C container, R recipe, IRecipeSlotsView recipeSlots,
            Player player, boolean maxTransfer, boolean doTransfer) {

        List<BucketSlot> required = getRequiredBuckets(recipe);
        if (required.isEmpty()) {
            return missing();
        }
        // Every slot must map to a real bucket, and the player must hold enough of each bucket item
        // (two of the same fluid needs two buckets — hence the per-item count).
        for (BucketSlot req : required) {
            if (req.bucket() == Items.AIR) {
                return missing();
            }
            int needed = 0;
            for (BucketSlot other : required) {
                if (other.bucket() == req.bucket()) {
                    needed++;
                }
            }
            if (JeiTransferUtil.countMatching(player.getInventory(), new ItemStack(req.bucket())) < needed) {
                return missing();
            }
        }

        if (doTransfer) {
            container.sendMessage(ContainerBC_Neptune.NET_JEI_TRANSFER_BUCKETS, buf -> {
                buf.writeVarInt(required.size());
                for (BucketSlot req : required) {
                    buf.writeVarInt(req.slot());
                    buf.writeUtf(BuiltInRegistries.ITEM.getKey(req.bucket()).toString());
                }
            });
        }
        return null;
    }

    protected IRecipeTransferError missing() {
        return helper.createUserErrorWithTooltip(
                Component.translatable("gui.jei.transfer.buildcraftunofficial.missing"));
    }

    /** The bucket item destined for a given container slot index. */
    public record BucketSlot(int slot, Item bucket) {
        /** Resolve the bucket item for a fluid ({@link Items#AIR} when the fluid is empty/bucketless). */
        public static BucketSlot of(int slot, @Nullable FluidStack fluid) {
            Item bucket = (fluid == null || fluid.isEmpty()) ? Items.AIR : fluid.getFluid().getBucket();
            return new BucketSlot(slot, bucket);
        }
    }
}
