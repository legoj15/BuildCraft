/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.tile;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.api.recipes.IntegrationRecipe;

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.recipe.IntegrationRecipeRegistry;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.silicon.BCSiliconBlockEntities;
import buildcraft.silicon.container.ContainerIntegrationTable;

public class TileIntegrationTable extends TileLaserTableBase {
    // Pipe access follows the machine, as in 7.1.x (canInsertItem: the centre and the ring; canExtractItem: the
    // output slot only) and the Programming Table: the robot and the boards go IN, only the finished robot comes
    // OUT. Pipes used to be able to drain the inputs and stuff the output.
    public final ItemHandlerSimple invTarget = itemManager.addInvHandler(
        "target",
        1,
        ItemHandlerManager.EnumAccess.INSERT,
        EnumPipePart.VALUES
    );
    public final ItemHandlerSimple invToIntegrate = itemManager.addInvHandler(
        "toIntegrate",
        3 * 3 - 1,
        ItemHandlerManager.EnumAccess.INSERT,
        EnumPipePart.VALUES
    );
    public final ItemHandlerSimple invResult = itemManager.addInvHandler(
        "result",
        1,
        ItemHandlerManager.EnumAccess.EXTRACT,
        EnumPipePart.VALUES
    );
    public IntegrationRecipe recipe;

    public TileIntegrationTable(BlockPos pos, BlockState state) {
        super(BCSiliconBlockEntities.INTEGRATION_TABLE.get(), pos, state);
    }

    /** Consumes the recipe's inputs: the centre stack and, precisely, the ring requirements. Both are checked
     *  against the current contents before either inventory is touched, and the inventories change only when both
     *  pass, so a failed call (a stray stack in the ring, say) never costs the player an input. */
    private boolean extract(IngredientStack item, ImmutableList<IngredientStack> items, boolean simulate) {
        ItemStack targetStack = invTarget.getStackInSlot(0);
        if (targetStack.isEmpty()) return false;
        // Count check only: the 1.12-era idiom compared against a fresh component-less stack of the same
        // item, which StackUtil.contains now (correctly, for the components era) rejects against a stack
        // carrying data — so any data-carrying target (a robot's board+charge) could never craft. The
        // ingredient test below already decides identity.
        if (targetStack.getCount() < item.count) return false;
        if (!item.ingredient.test(targetStack)) return false;
        if (!extract(invToIntegrate, items, true, true)) return false;
        if (!simulate) {
            extract(invToIntegrate, items, false, true);
            targetStack = targetStack.copy();
            targetStack.setCount(targetStack.getCount() - item.count);
            invTarget.setStackInSlot(0, targetStack);
        }
        return true;
    }

    private boolean isSpaceEnough(ItemStack stack) {
        ItemStack output = invResult.getStackInSlot(0);
        return output.isEmpty() || (StackUtil.canMerge(stack, output) && stack.getCount() + output.getCount() <= stack.getMaxStackSize());
    }

    private void updateRecipe() {
        if (recipe != null) {
            ItemStack output = getOutput();
            if (!output.isEmpty() && extract(recipe.getCenterStack(), recipe.getRequirements(output), true))
                return;
        }
        recipe = IntegrationRecipeRegistry.INSTANCE.getRecipeFor(invTarget.getStackInSlot(0), invToIntegrate.stacks);
    }

    public ItemStack getOutput() {
        return recipe != null ? recipe.getOutput(invTarget.getStackInSlot(0), invToIntegrate.stacks) : ItemStack.EMPTY;
    }

    @Override
    public long getTarget() {
        ItemStack output = getOutput();
        return recipe != null && isSpaceEnough(output) ? recipe.getRequiredMicroJoules(output) : 0;
    }

    @Override
    public void serverTick() {
        super.serverTick();

        updateRecipe();

        // The cost is read BEFORE the inputs are consumed: once they are gone the recipe has no output and
        // getTarget() reads 0, which used to make the deduction below a no-op, so one charge programmed every
        // robot after it.
        long cost = getTarget();
        if (cost > 0 && power >= cost) {
            ItemStack output = getOutput();
            // Gate the whole craft on the consumption actually succeeding — the return value used to be
            // ignored, which could emit the output while leaving every input where it was (a dupe,
            // reachable through any extract guard failing mid-tick).
            if (extract(recipe.getCenterStack(), recipe.getRequirements(output), false)) {
                ItemStack result = invResult.getStackInSlot(0);
                if (!result.isEmpty()) {
                    result = result.copy();
                    result.setCount(result.getCount() + output.getCount());
                } else {
                    result = output.copy();
                }
                invResult.setStackInSlot(0, result);
                power -= cost;
            }
        }
    }

    // --- Save / Load ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        // Recipe is transient — re-discovered from inventory contents on load
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        recipe = null; // Will be updated on next tick
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerIntegrationTable(containerId, player, this);
    }
}
