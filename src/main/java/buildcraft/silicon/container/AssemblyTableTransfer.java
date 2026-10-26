/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.facades.FacadeType;
import buildcraft.api.recipes.AssemblyRecipe;
import buildcraft.api.recipes.IngredientStack;

import buildcraft.lib.compat.viewer.TransferResult;
import buildcraft.lib.compat.viewer.TransferUtil;
import buildcraft.lib.gui.BCContainerSupport;
import buildcraft.lib.recipe.AssemblyRecipeRegistry;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.recipe.FacadeAssemblyRecipes;

/**
 * The wire format and server-side move of the recipe-viewer "+" transfer into the Assembly Table, kept free of JEI
 * and REI classes so the very same {@link #write} the viewers send with is what a headless test replays into
 * {@link ContainerAssemblyTable#readMessage}.
 *
 * <p>The request names a recipe, not stacks: the recipe's registry name plus the output it was shown for (an
 * assembly recipe can make several, and {@link AssemblyRecipe#getInputsFor} takes one). The server resolves that to
 * the recipe's own {@link IngredientStack}s and moves from the player's inventory the stacks the recipe's
 * ingredients accept ({@code ingredient.test}, the very test the table runs on a stack placed by hand), up to each
 * ingredient's count, so a renamed or otherwise tagged ingredient the table would take is taken here too.
 *
 * <p>The server never trusts the request for more than that: an unknown recipe, or an output the recipe does not
 * make, moves nothing, and only stacks the player actually holds move (debited by exactly what the table took), so a
 * tampered request can at most rearrange the sender's own items.
 */
public final class AssemblyTableTransfer {
    private AssemblyTableTransfer() {}

    /** Longest recipe name {@link #read} will decode; the longest real one is a few dozen characters. */
    public static final int MAX_RECIPE_NAME = 128;

    /** A decoded transfer request: the recipe's registry name and the output it was shown for. */
    public record Request(boolean maxTransfer, String recipe, ItemStack output) {}

    /** Writes the request: a flag byte, the recipe's registry name, then the output through
     *  {@link ItemStack#OPTIONAL_STREAM_CODEC} (a gate output carries its variant, a facade its block state).
     *  The codec needs a registry-aware buffer to resolve registry-backed components, hence {@code registries}: the
     *  sender's level on the client, the player's level on the server. */
    public static void write(FriendlyByteBuf buf, RegistryAccess registries, boolean maxTransfer, String recipe,
            ItemStack output) {
        RegistryFriendlyByteBuf rbuf = BCContainerSupport.registryBuf(buf, registries);
        rbuf.writeByte(maxTransfer ? 1 : 0);
        rbuf.writeUtf(recipe, MAX_RECIPE_NAME);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(rbuf, output);
    }

    /** Reads a request written by {@link #write}. Throws on a malformed one (a name longer than
     *  {@link #MAX_RECIPE_NAME}, a truncated stack); the container-message dispatcher logs that and drops it. */
    public static Request read(FriendlyByteBuf buf, RegistryAccess registries) {
        RegistryFriendlyByteBuf rbuf = BCContainerSupport.registryBuf(buf, registries);
        boolean maxTransfer = rbuf.readByte() != 0;
        String recipe = rbuf.readUtf(MAX_RECIPE_NAME);
        return new Request(maxTransfer, recipe, ItemStack.OPTIONAL_STREAM_CODEC.decode(rbuf));
    }

    /** The ingredients {@code recipe} asks for when it makes {@code output}, or {@code null} when there is no such
     *  recipe or it does not make that output (count and ordering of the output's stack aside). The one lookup the
     *  server's {@link #apply} and the client's advisory {@link #check} share. */
    @Nullable
    public static List<IngredientStack> inputsFor(String recipeName, ItemStack output) {
        AssemblyRecipe recipe = AssemblyRecipeRegistry.REGISTRY.get(recipeName);
        if (recipe == null) {
            return null;
        }
        try {
            return makes(recipe, output) ? new ArrayList<>(recipe.getInputsFor(output)) : null;
        } catch (RuntimeException e) {
            // A recipe that cannot read the output it was handed (a mangled facade stack) makes nothing of it.
            return null;
        }
    }

    /** Whether {@code recipe} lists {@code output} among the things it makes. */
    private static boolean makes(AssemblyRecipe recipe, ItemStack output) {
        if (output.isEmpty()) {
            return false;
        }
        if (recipe instanceof FacadeAssemblyRecipes) {
            // The facade recipe lists no previews: it makes a single-state (FacadeType.Basic, solid or hollow) facade
            // per visible wrappable block state.
            if (!output.is(BCSiliconItems.PLUG_FACADE.get())) {
                return false;
            }
            FacadeInstance facade = ItemPluggableFacade.getStates(output);
            FacadeBlockStateInfo info = facade.phasedStates[0].stateInfo;
            return facade.type == FacadeType.Basic && info != null && info.isVisible;
        }
        for (ItemStack made : recipe.getOutputPreviews()) {
            if (ItemStack.isSameItemSameComponents(made, output)) {
                return true;
            }
        }
        return false;
    }

    /** The advisory answer for {@code inputs} against what the player holds: {@code INVALID} when there is nothing to
     *  ask for, {@code MISSING} when the inventory cannot cover an ingredient's count, else {@code OK}. Ingredients
     *  draw on the inventory in the order {@link #apply} uses, so stock two ingredients both accept is not counted
     *  twice. The server runs no such check: {@link #apply} moves whatever the player holds, so a stale client at
     *  most moves less than it showed.
     *
     *  @param inputs {@link #inputsFor}'s answer, {@code null} for an unknown recipe or output */
    public static TransferResult check(@Nullable List<IngredientStack> inputs, Container inventory) {
        if (inputs == null || inputs.isEmpty()) {
            return TransferResult.INVALID;
        }
        int[] left = new int[inventory.getContainerSize()];
        for (int i = 0; i < left.length; i++) {
            left[i] = inventory.getItem(i).getCount();
        }
        for (IngredientStack input : inputs) {
            int want = input.count;
            for (int i = 0; i < left.length && want > 0; i++) {
                ItemStack held = inventory.getItem(i);
                if (left[i] > 0 && !held.isEmpty() && input.ingredient.test(held)) {
                    int take = Math.min(left[i], want);
                    left[i] -= take;
                    want -= take;
                }
            }
            if (want > 0) {
                return TransferResult.MISSING;
            }
        }
        return TransferResult.OK;
    }

    /** Moves the request's recipe ingredients from {@code playerInv} into {@code dest}; returns the total item count
     *  moved ({@code 0} for an unknown recipe or an output it does not make). One recipe's worth first (each
     *  ingredient's count, in order, so an ingredient cannot starve the next one that accepts the same stock), then,
     *  for {@code maxTransfer}, everything more that the ingredients accept and the table has room for. */
    public static int apply(Container playerInv, ItemHandlerSimple dest, Request request) {
        List<IngredientStack> inputs = inputsFor(request.recipe(), request.output());
        if (inputs == null) {
            return 0;
        }
        int moved = 0;
        for (IngredientStack input : inputs) {
            moved += TransferUtil.moveMatchingToHandler(playerInv, input.ingredient::test, input.count, dest);
        }
        if (request.maxTransfer()) {
            for (IngredientStack input : inputs) {
                moved += TransferUtil.moveMatchingToHandler(playerInv, input.ingredient::test, Integer.MAX_VALUE,
                        dest);
            }
        }
        return moved;
    }
}
