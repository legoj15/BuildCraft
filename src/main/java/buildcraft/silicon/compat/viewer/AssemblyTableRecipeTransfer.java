/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.compat.viewer;

import java.util.List;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.compat.viewer.RecipeTransfer;
import buildcraft.lib.compat.viewer.TransferResult;
import buildcraft.lib.gui.ContainerBC_Neptune;

import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.container.AssemblyTableTransfer;
import buildcraft.silicon.container.ContainerAssemblyTable;

/**
 * "+" recipe transfer for the Assembly Table, shared by JEI and REI. Unlike the crafting tables (which fill phantom
 * slots), the Assembly Table has a real 12-slot input inventory, so this moves the recipe's actual ingredients out of
 * the player's inventory and into the table (server-side, via {@link AssemblyTableTransfer}).
 *
 * <p>It sends the recipe's registry name and the output the entry shows, never stacks: the server resolves the
 * recipe's own ingredients and takes the stacks they accept, the same test the table runs on a stack placed by hand
 * (a renamed ingot counts). The button is enabled only when the player's inventory covers every ingredient's count
 * under that same test ({@link AssemblyTableTransfer#check}). Shift-click ({@code maxTransfer}) fills with everything
 * the ingredients accept; otherwise it moves one recipe's worth.
 */
public final class AssemblyTableRecipeTransfer extends RecipeTransfer<ContainerAssemblyTable, AssemblyRecipeEntry> {

    public AssemblyTableRecipeTransfer() {
        super(ContainerAssemblyTable.class, BCSiliconMenuTypes.ASSEMBLY_TABLE::get);
    }

    @Override
    protected TransferResult check(ContainerAssemblyTable container, AssemblyRecipeEntry recipe, Player player,
            boolean maxTransfer) {
        Inventory inv = player.getInventory();
        return AssemblyTableTransfer.check(AssemblyTableTransfer.inputsFor(recipe.recipe(), outputFor(recipe, inv)),
                inv);
    }

    @Override
    protected void send(ContainerAssemblyTable container, AssemblyRecipeEntry recipe, Player player,
            boolean maxTransfer) {
        ItemStack output = outputFor(recipe, player.getInventory());
        var registries = player.level().registryAccess();
        container.sendMessage(ContainerBC_Neptune.NET_JEI_TRANSFER_ITEMS,
                buf -> AssemblyTableTransfer.write(buf, registries, maxTransfer, recipe.recipe(), output));
    }

    /**
     * The output to ask the recipe for. A regular entry shows one. A cycling entry (the facades: one output per
     * wrappable block) pairs its outputs by index with the alternatives of its focus-linked input slot, so the output
     * asked for is the one paired with the first alternative whose item the player holds, else the first output (the
     * shortfall then shows in the check). The pairing only chooses the output; what the player needs for it is the
     * recipe's own {@code getInputsFor}.
     */
    private static ItemStack outputFor(AssemblyRecipeEntry recipe, Inventory inv) {
        List<ItemStack> outputs = recipe.outputs();
        if (outputs.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int link = recipe.focusLinkInputIndex();
        if (outputs.size() > 1 && link >= 0 && link < recipe.inputSlots().size()) {
            List<ItemStack> alternatives = recipe.inputSlots().get(link);
            for (int i = 0; i < Math.min(alternatives.size(), outputs.size()); i++) {
                if (holdsItem(inv, alternatives.get(i))) {
                    return outputs.get(i);
                }
            }
        }
        return outputs.get(0);
    }

    private static boolean holdsItem(Inventory inv, ItemStack template) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack held = inv.getItem(i);
            if (!held.isEmpty() && ItemStack.isSameItem(held, template)) {
                return true;
            }
        }
        return false;
    }
}
