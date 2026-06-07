/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;

import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemFragileFluidContainer;

/**
 * Shared "alternate-form" lookup for fluid recipe slots. A fluid recipe in JEI
 * normally only matches when the player presses U/R on the bare fluid; this
 * utility lets each category register every <em>item</em> form of the fluid
 * (vanilla bucket, fragile shard, modded clay buckets, etc.) as an invisible
 * lookup ingredient, so the player can still find the recipe with what they
 * actually have in hand.
 *
 * <p>Two providers are registered out of the box:
 * <ul>
 *   <li>The vanilla bucket via {@code Fluid#getBucket()} — covers vanilla
 *   water/lava and any modded fluid that registers a bucket via the NeoForge
 *   {@code FluidType#getBucket} convention.</li>
 *   <li>The BuildCraft fragile fluid shard with the fluid baked into its
 *   {@code FLUID_CONTENT} data component.</li>
 * </ul>
 *
 * <p>Other mods can plug in their own container forms by calling
 * {@link #registerProvider(Provider)} from their own JEI plugin lifecycle.
 */
public final class FluidContainerAliases {

    private static final List<Provider> providers = new ArrayList<>();

    static {
        // Vanilla / NeoForge bucket form. Mirrors what the recipe categories
        // used to do inline before this utility existed.
        registerProvider((fluidStack, sink) -> {
            Item bucket = fluidStack.getFluid().getBucket();
            if (bucket != null && bucket != Items.AIR) {
                sink.accept(new ItemStack(bucket));
            }
        });

        // BuildCraft fragile fluid shard. The shard stores the fluid in the
        // FLUID_CONTENT data component, so we have to construct an ItemStack
        // with the component set rather than passing a bare Item. Amount is
        // pinned to MAX_FLUID_HELD because the JEI subtype interpreter for the
        // shard ignores amount — any-mB shard the player holds will still
        // match this alias.
        registerProvider((fluidStack, sink) -> {
            if (BCCoreItems.FRAGILE_FLUID_CONTAINER == null) {
                return;
            }
            Item shardItem = BCCoreItems.FRAGILE_FLUID_CONTAINER.get();
            if (shardItem == null) {
                return;
            }
            ItemStack shard = new ItemStack(shardItem);
            FluidStack copy = fluidStack.copy();
            copy.setAmount(ItemFragileFluidContainer.MAX_FLUID_HELD);
            ItemFragileFluidContainer.setFluid(shard, copy);
            sink.accept(shard);
        });
    }

    private FluidContainerAliases() {
    }

    /**
     * Register an additional provider. Called once per provider, typically
     * from a {@code @JeiPlugin}'s lifecycle. Registration order does not
     * matter because providers are consumed at recipe-display time.
     */
    public static void registerProvider(Provider provider) {
        providers.add(provider);
    }

    /**
     * Run every registered provider against {@code stack} and add their
     * emitted item forms to {@code builder} as invisible ingredients with
     * the given role. No-op for empty fluids.
     */
    public static void addAliases(IRecipeLayoutBuilder builder, FluidStack stack, RecipeIngredientRole role) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        IIngredientAcceptor<?> slot = builder.addInvisibleIngredients(role);
        for (Provider provider : providers) {
            provider.addAliases(stack, alias -> {
                if (alias != null && !alias.isEmpty()) {
                    //? if >=1.21.10 {
                    slot.add(alias);
                    //?} else {
                    /*slot.addItemStack(alias);*/
                    //?}
                }
            });
        }
    }

    @FunctionalInterface
    public interface Provider {
        void addAliases(FluidStack fluidStack, Consumer<ItemStack> sink);
    }
}
