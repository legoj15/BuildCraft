/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.compat.jei;

import java.util.List;
import java.util.function.ToIntFunction;

import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
//? if >=1.21.10 {
import mezz.jei.api.recipe.types.IRecipeType;
//?} else {
/*import mezz.jei.api.recipe.RecipeType;*/
//?}
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.compat.jei.AbstractTransferHandler;
import buildcraft.lib.compat.jei.JeiTransferUtil;
import buildcraft.lib.compat.jei.TransferResult;
import buildcraft.lib.gui.ContainerBC_Neptune;

import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.container.AssemblyTableTransfer;
import buildcraft.silicon.container.ContainerAssemblyTable;

/**
 * JEI "+" recipe transfer for the Assembly Table. Unlike the crafting-table handler
 * ({@code BlueprintTransferHandler}, which fills phantom slots), the Assembly Table has a real
 * 12-slot input inventory, so this moves the recipe's actual ingredients out of the player's
 * inventory and into the table (server-side, via {@link JeiTransferUtil}).
 *
 * <p>Per input slot it picks an alternative the player can satisfy, aggregates identical picks, and
 * only enables the button when the player has the full demand ({@link AssemblyTableTransfer#chooseNeed}
 * and {@link AssemblyTableTransfer#check}). Shift-click ({@code maxTransfer}) fills with everything
 * available; otherwise it moves one recipe's worth.
 */
public class AssemblyTableTransferHandler
        extends AbstractTransferHandler<ContainerAssemblyTable, AssemblyRecipeJei> {

    public AssemblyTableTransferHandler(IRecipeTransferHandlerHelper helper) {
        super(helper, ContainerAssemblyTable.class, BCSiliconMenuTypes.ASSEMBLY_TABLE::get);
    }

    @Override
    //? if >=1.21.10 {
    public IRecipeType<AssemblyRecipeJei> getRecipeType() {
    //?} else {
    /*public RecipeType<AssemblyRecipeJei> getRecipeType() {*/
    //?}
        return AssemblyRecipeJeiTypes.ASSEMBLY;
    }

    @Override
    protected TransferResult check(ContainerAssemblyTable container, AssemblyRecipeJei recipe, Player player,
            boolean maxTransfer) {
        Inventory inv = player.getInventory();
        return AssemblyTableTransfer.check(need(inv, recipe), held(inv));
    }

    @Override
    protected void send(ContainerAssemblyTable container, AssemblyRecipeJei recipe, Player player,
            boolean maxTransfer) {
        List<ItemStack> need = need(player.getInventory(), recipe);
        var registries = player.level().registryAccess();
        container.sendMessage(ContainerBC_Neptune.NET_JEI_TRANSFER_ITEMS,
                buf -> AssemblyTableTransfer.write(buf, registries, maxTransfer, need));
    }

    private static List<ItemStack> need(Inventory inv, AssemblyRecipeJei recipe) {
        return AssemblyTableTransfer.chooseNeed(recipe.inputSlots(), held(inv));
    }

    /** How many of a stack the player holds, matched on item and components like the server's move does. */
    private static ToIntFunction<ItemStack> held(Inventory inv) {
        return stack -> JeiTransferUtil.countMatching(inv, stack);
    }
}
