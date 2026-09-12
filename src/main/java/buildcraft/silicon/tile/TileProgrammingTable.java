/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.silicon.tile;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.recipes.IProgrammingRecipe;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.recipe.ProgrammingRecipeRegistry;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.robotics.container.ContainerProgrammingTable;
import buildcraft.silicon.BCSiliconBlockEntities;

/** The robot Programming Table: laser energy in, one redstone board out — programmed to any board selected in the
 *  option grid. Ported from 7.1.x {@code buildcraft.silicon.TileProgrammingTable} (whose 8.0.x namesake is an empty
 *  stub). Two slots only: a board goes in (pipe-insertable, like 7.1.x's {@code canInsertItem} slot 0) and a
 *  programmed board comes out (pipe-extractable, slot 1); the "ingredients" are pure MJ.
 *
 *  <p>Unlike 7.1.x, which zeroed the buffer on craft, this keeps the port-wide laser-table convention of
 *  {@code power -= target} — in practice the lasers' own {@code receiveLaserPower} clamp keeps power at exactly the
 *  target, so the difference only shows for a stale over-filled buffer from an edited save. Option selection is
 *  server-authoritative and clamped (7.1.x's RPC could NPE on a stale select after the recipe list changed). */
public class TileProgrammingTable extends TileLaserTableBase {

    /** The option grid's dimensions — 7.1.x's GUI panel size, and the slice of {@code getOptions} it shows. */
    public static final int OPTION_COLS = 6;
    public static final int OPTION_ROWS = 4;

    public final ItemHandlerSimple invBoard = itemManager.addInvHandler(
        "board",
        1,
        ItemHandlerManager.EnumAccess.INSERT,
        EnumPipePart.VALUES
    );
    public final ItemHandlerSimple invResult = itemManager.addInvHandler(
        "result",
        1,
        ItemHandlerManager.EnumAccess.EXTRACT,
        EnumPipePart.VALUES
    );

    /** The matched recipe's id — the only recipe state that persists; everything else is re-derived. */
    @Nullable
    public String currentRecipeId;

    /** The option grid: one stack per craftable board, cheapest first. Rebuilt on both sides from the registry. */
    @Nullable
    public List<ItemStack> options;

    /** The selected grid index, or -1 for none. Persisted so a relog keeps the selection. */
    public int optionId = -1;

    @Nullable
    private IProgrammingRecipe currentRecipe;

    public TileProgrammingTable(BlockPos pos, BlockState state) {
        super(BCSiliconBlockEntities.PROGRAMMING_TABLE.get(), pos, state);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ContainerProgrammingTable(containerId, player, this);
    }

    /** Server-authoritative selection. Clamped to the live option list — a stale index from a changed registry or a
     *  malicious packet degrades to "no selection" rather than indexing off the end (7.1.x's known NPE). */
    public void selectOption(int id) {
        int max = options == null ? -1 : options.size() - 1;
        optionId = Math.max(-1, Math.min(id, max));
        setChanged();
        if (getLevel() != null) {
            markForGuiUpdate();
        }
    }

    public boolean hasWork() {
        return currentRecipe != null && options != null
            && optionId >= 0 && optionId < options.size()
            && invResult.getStackInSlot(0).isEmpty();
    }

    @Override
    public long getTarget() {
        if (!hasWork()) {
            return 0;
        }
        ItemStack input = invBoard.getStackInSlot(0);
        return currentRecipe.canCraft(input) ? currentRecipe.getEnergyCost(options.get(optionId)) : 0;
    }

    @Override
    public void serverTick() {
        super.serverTick();

        findRecipe();

        long target = getTarget();
        if (target > 0 && power >= target) {
            ItemStack input = invBoard.getStackInSlot(0);
            ItemStack out = currentRecipe.craft(input, options.get(optionId));
            input = input.copy();
            input.setCount(input.getCount() - out.getCount());
            invBoard.setStackInSlot(0, input);
            // hasWork() gates on an empty result slot, so the insert branch is the only live one; the pop is
            // belt-and-braces for a race with a same-tick change, mirroring 7.1.x's outputStack fallback.
            if (invResult.getStackInSlot(0).isEmpty()) {
                invResult.setStackInSlot(0, out);
            } else {
                Block.popResource(getLevel(), getBlockPos().above(), out);
            }
            power -= target;
            setChanged();
        }
    }

    /** Re-derives {@link #currentRecipeId} from the input stack, resetting the selection whenever it changes — the
     *  input board becoming something else (or nothing) invalidates the chosen option, exactly as 7.1.x did. */
    private void findRecipe() {
        String oldId = currentRecipeId;
        currentRecipeId = null;
        ItemStack input = invBoard.getStackInSlot(0);
        if (!input.isEmpty()) {
            for (IProgrammingRecipe recipe : ProgrammingRecipeRegistry.INSTANCE.getRecipes()) {
                if (recipe.canCraft(input)) {
                    currentRecipeId = recipe.getId();
                    break;
                }
            }
        }
        boolean changed = !Objects.equals(oldId, currentRecipeId);
        if (changed) {
            optionId = -1;
        }
        // Also re-resolve after a load or a registry change, which leave currentRecipe null with an unchanged id.
        if (changed || currentRecipe == null) {
            updateRecipe();
            if (changed) {
                setChanged();
                if (getLevel() != null) {
                    markForGuiUpdate();
                }
            }
        }
    }

    private void updateRecipe() {
        currentRecipe = currentRecipeId == null ? null : ProgrammingRecipeRegistry.INSTANCE.getRecipe(currentRecipeId);
        options = currentRecipe == null ? null : currentRecipe.getOptions(OPTION_COLS, OPTION_ROWS);
        if (options != null && optionId >= options.size()) {
            optionId = -1;
        }
    }

    // --- Save / Load ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        if (currentRecipeId != null) {
            output.putString("recipeId", currentRecipeId);
        }
        output.putInt("optionId", optionId);
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        currentRecipeId = input.getStringOr("recipeId", null);
        optionId = input.getIntOr("optionId", -1);
        updateRecipe();
    }
}
