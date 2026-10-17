/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.tile;

import java.util.Collection;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.mj.ILaserTarget;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.gui.IBCMenuProvider;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.data.AverageLong;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerSimple;

public abstract class TileLaserTableBase extends TileBC_Neptune implements ILaserTarget, IDebuggable, IBCMenuProvider {
    private static final long MJ_FLOW_ROUND = MjAPI.MJ / 10;
    private final AverageLong avgPower = new AverageLong(120);
    public long avgPowerClient;
    public long power;
    private long lastSyncedPower = -1;

    protected TileLaserTableBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- IBCMenuProvider (GUI) ---
    // The tile is the sole MenuProvider (writeClientSideData supplies the pos on every open path). The
    // display name matches the old block-side SimpleMenuProvider (the block's own name); each concrete
    // table (Assembly / Advanced Crafting / Integration) builds its own container in createMenu.

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public abstract AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player);

    // All real contents live in the itemManager (playerWillDestroy itself drops via addDrops), so the
    // central addDrops path spills them on a non-player removal (explosion / piston / command).
    @Override
    protected boolean spillsContentsOnRemoval() {
        return true;
    }

    public abstract long getTarget();

    @Override
    public long getRequiredLaserPower() {
        return getTarget() - power;
    }

    @Override
    public long receiveLaserPower(long microJoules) {
        long received = Math.min(microJoules, getRequiredLaserPower());
        power += received;
        avgPower.push(received);
        return microJoules - received;
    }

    @Override
    public boolean isInvalidTarget() {
        return isRemoved();
    }

    /** Called each server tick by the block's ticker. */
    public void serverTick() {
        avgPower.tick();
        avgPowerClient = (long) avgPower.getAverage();

        if (getTarget() <= 0) {
            // Don't zero power — retain it so pausing a recipe doesn't waste MJ.
            // Power will be applied to the next active recipe.
            avgPower.clear();
        }

        // Sync power to clients when it changes
        if (power != lastSyncedPower) {
            lastSyncedPower = power;
            setChanged();
            if (getLevel() != null) {
                MessageUtil.sendUpdateToTrackingPlayers(this);
            }
        }
    }

    // --- Save / Load ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        output.putLong("power", power);
        output.putLong("avg_power", avgPowerClient);
        output.store("items", CompoundTag.CODEC, itemManager.serializeNBT());
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        power = input.getLongOr("power", 0L);
        avgPowerClient = input.getLongOr("avg_power", 0L);
        input.read("items", CompoundTag.CODEC).ifPresent(tag -> itemManager.deserializeNBT(tag));
    }

    // --- Debug ---

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("power = " + power);
        left.add("target = " + getTarget());
    }

    // --- Ingredient extraction helper ---

    /** Takes {@code items} out of {@code inv}, all or nothing: every requirement is worked out against a scratch copy
     *  of the slot counts first (so two requirements can't both claim the same items), and the inventory is touched
     *  only once the whole set is known to fit. A failed call, simulated or not, leaves {@code inv} exactly as it was.
     *
     *  @param precise also demand that the inventory holds nothing but the requirements: the number of occupied slots
     *                 must equal the number of requirements, so a stray stack anywhere makes the call fail. */
    protected boolean extract(ItemHandlerSimple inv, Collection<IngredientStack> items, boolean simulate,
        boolean precise) {
        int[] counts = planExtraction(inv.stacks, items, precise);
        if (counts == null) return false;
        if (!simulate) {
            for (int i = 0; i < counts.length; i++) {
                ItemStack slotStack = inv.getStackInSlot(i);
                if (slotStack.isEmpty() || counts[i] == slotStack.getCount()) continue;
                slotStack = slotStack.copy();
                slotStack.setCount(counts[i]);
                inv.setStackInSlot(i, slotStack);
            }
        }
        return true;
    }

    /** The planning half of {@link #extract}, on plain stacks so a hypothetical inventory (an insertion check's
     *  "what if this stack went here") can be tested too.
     *
     *  @return each slot's count after taking {@code items}, or null if they cannot all be taken (or, with
     *          {@code precise}, if anything would be left over in a slot no requirement used). */
    protected static int[] planExtraction(List<ItemStack> stacks, Collection<IngredientStack> items, boolean precise) {
        int slots = stacks.size();
        int[] counts = new int[slots];
        long remainingStacks = 0;
        for (int i = 0; i < slots; i++) {
            ItemStack slotStack = stacks.get(i);
            counts[i] = slotStack.isEmpty() ? 0 : slotStack.getCount();
            if (counts[i] > 0) remainingStacks++;
        }
        for (IngredientStack definition : items) {
            int remaining = definition.count;
            for (int i = 0; i < slots && remaining > 0; i++) {
                if (counts[i] <= 0) continue;
                if (definition.ingredient.test(stacks.get(i))) {
                    int spend = Math.min(remaining, counts[i]);
                    remaining -= spend;
                    counts[i] -= spend;
                }
            }
            if (remaining > 0) return null;
            remainingStacks--;
        }
        if (precise && remainingStacks != 0) return null;
        return counts;
    }
}
