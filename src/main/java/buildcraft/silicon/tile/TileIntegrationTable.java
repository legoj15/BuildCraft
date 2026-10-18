/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.tile;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
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
    // Access follows 7.1.x: canInsertItem/canExtractItem (pipes) put the robot and the board IN and take only the
    // finished robot OUT, and isItemValidForSlot (pipes and hands alike) lets in only what can be crafted from --
    // a robot in the centre, and a board in the ring only once there is a robot to program and only while the ring
    // would still hold exactly what the recipe consumes (7.1.x capped the robot recipe at one expansion). A pipe
    // cannot pull an input back out, so letting in a second board or a stray stack used to jam the table.
    public final ItemHandlerSimple invTarget = itemManager.addInvHandler(
        "target",
        1,
        (slot, stack) -> isCentreInput(stack),
        ItemHandlerManager.EnumAccess.INSERT,
        EnumPipePart.VALUES
    );
    public final ItemHandlerSimple invToIntegrate = itemManager.addInvHandler(
        "toIntegrate",
        3 * 3 - 1,
        (slot, stack) -> canRingTake(invTarget.getStackInSlot(0), slot, stack),
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

    /** True if some registered recipe takes {@code stack} as its centre item. */
    private static boolean isCentreInput(ItemStack stack) {
        for (IntegrationRecipe candidate : IntegrationRecipeRegistry.INSTANCE.getAllRecipes()) {
            if (candidate.getCenterStack().ingredient.test(stack)) return true;
        }
        return false;
    }

    /** True if ring slot {@code slot} may hold {@code stack} with {@code centre} in the centre: the ring, with that
     *  stack in that slot, must make a recipe whose requirements it holds exactly (the craft's own precise check),
     *  so nothing that goes in can stop the table crafting. Public so the JEI transfer can ask the question for the
     *  robot it is about to put in the centre. */
    public boolean canRingTake(ItemStack centre, int slot, ItemStack stack) {
        if (centre.isEmpty()) return false;
        NonNullList<ItemStack> ring = NonNullList.withSize(invToIntegrate.getSlots(), ItemStack.EMPTY);
        for (int i = 0; i < ring.size(); i++) {
            ring.set(i, i == slot ? stack : invToIntegrate.getStackInSlot(i));
        }
        IntegrationRecipe candidate = IntegrationRecipeRegistry.INSTANCE.getRecipeFor(centre, ring);
        if (candidate == null) return false;
        ItemStack output = candidate.getOutput(centre, ring);
        return !output.isEmpty() && planExtraction(ring, candidate.getRequirements(output), true) != null;
    }

    private boolean isSpaceEnough(ItemStack stack) {
        ItemStack output = invResult.getStackInSlot(0);
        return output.isEmpty() || (StackUtil.canMerge(stack, output) && stack.getCount() + output.getCount() <= stack.getMaxStackSize());
    }

    private void updateRecipe() {
        recipeResolves++;
        if (recipe != null) {
            ItemStack output = recipeOutput();
            if (!output.isEmpty() && extract(recipe.getCenterStack(), recipe.getRequirements(output), true))
                return;
        }
        recipe = IntegrationRecipeRegistry.INSTANCE.getRecipeFor(invTarget.getStackInSlot(0), invToIntegrate.stacks);
    }

    /** The matched recipe's output for the current inventories, from the cached {@link #recipe} as it stands. */
    private ItemStack recipeOutput() {
        return recipe != null ? recipe.getOutput(invTarget.getStackInSlot(0), invToIntegrate.stacks) : ItemStack.EMPTY;
    }

    /** {@link #serverTick} brings {@link #recipe} up to date on the server every tick (a tick with the inputs as they
     *  were costs the comparison, not a resolve), so server readers see it fresh. A tile with no level behind it has
     *  no tick either, and a client never ticks this tile at all: nothing would ever resolve the recipe there, so the
     *  readers do it themselves, from the inventories (which the open menu keeps in step with the server's) -- see
     *  {@link #resolveRecipe}. */
    private boolean ticksHere() {
        return level != null && !level.isClientSide();
    }

    // --- Recipe cache (the server tick and the client preview) ---
    // The preview slot asks getOutput() every frame (twice: getItem and hasItem) and the progress bar and power
    // ledger ask getTarget(); the server ticks the table every tick. Resolving is a registry-wide recipe match plus
    // an extract simulation, and with nothing to craft it walks the whole registry to find that out, so the result
    // is kept until an input changes. The key is the contents of the centre stack and the ring, compared by value against a private
    // copy: that catches a menu sync, a stack edited in place and a write straight into the stack list alike,
    // with no dependence on every route into the inventories reporting its change. The result slot is not an
    // input (the cost reads it fresh), and recipes register once at startup, so neither is in the key.

    /** The centre stack, then the ring in slot order, as they were when the preview was last resolved (copies, so
     *  a stack changed in place differs from its snapshot). Null until the first resolve and after a load. */
    private ItemStack[] previewInputs;
    /** What the preview resolved to from {@link #previewInputs}; handed out as copies. */
    private ItemStack previewOutput = ItemStack.EMPTY;
    private int recipeResolves;

    /** How many times the recipe has been resolved from scratch (a registry match plus an extract simulation) rather
     *  than read from the cache, by a server tick and an unticked preview read alike. */
    int recipeResolves() {
        return recipeResolves;
    }

    private boolean previewIsCurrent() {
        if (previewInputs == null || !ItemStack.matches(previewInputs[0], invTarget.getStackInSlot(0))) {
            return false;
        }
        for (int i = 0; i < invToIntegrate.getSlots(); i++) {
            if (!ItemStack.matches(previewInputs[i + 1], invToIntegrate.getStackInSlot(i))) {
                return false;
            }
        }
        return true;
    }

    /** Brings {@link #recipe} and {@link #previewOutput} up to date with the inventories, resolving only if an
     *  input has changed since the last time. */
    private void resolveRecipe() {
        if (previewIsCurrent()) {
            return;
        }
        updateRecipe();
        previewOutput = recipeOutput();
        ItemStack[] inputs = new ItemStack[1 + invToIntegrate.getSlots()];
        inputs[0] = invTarget.getStackInSlot(0).copy();
        for (int i = 0; i < invToIntegrate.getSlots(); i++) {
            inputs[i + 1] = invToIntegrate.getStackInSlot(i).copy();
        }
        previewInputs = inputs;
    }

    /** What the table would produce right now: the GUI's preview slot, on the client as well as the server. */
    public ItemStack getOutput() {
        if (ticksHere()) {
            return recipeOutput();
        }
        resolveRecipe();
        return previewOutput.copy();
    }

    @Override
    public long getTarget() {
        ItemStack output;
        if (ticksHere()) {
            output = recipeOutput();
        } else {
            resolveRecipe();
            output = previewOutput;
        }
        return recipe != null && isSpaceEnough(output) ? recipe.getRequiredMicroJoules(output) : 0;
    }

    @Override
    public void serverTick() {
        super.serverTick();

        // Gated on the same input snapshot the client preview uses: the registry match and the extract simulation are
        // a pure function of the centre stack and the ring, and this runs every tick of every loaded table.
        resolveRecipe();

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
        recipe = null; // Re-resolved on the next server tick, or on the next read when this is a client
        previewInputs = null; // and the client's cached preview goes with it: it describes a recipe that is gone
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerIntegrationTable(containerId, player, this);
    }
}
