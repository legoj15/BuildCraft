/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.tile;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.mj.ILaserTarget;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.recipes.IngredientStack;

import buildcraft.lib.misc.data.AverageLong;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerSimple;

public abstract class TileLaserTableBase extends TileBC_Neptune implements ILaserTarget {
    private static final long MJ_FLOW_ROUND = MjAPI.MJ / 10;
    private final AverageLong avgPower = new AverageLong(120);
    public long avgPowerClient;
    public long power;

    protected TileLaserTableBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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

        if (getTarget() <= 0) {
            power = 0;
            avgPower.clear();
        }
    }

    // --- Save / Load ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("power", power);
        output.store("items", CompoundTag.CODEC, itemManager.serializeNBT());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        power = input.getLongOr("power", 0L);
        input.read("items", CompoundTag.CODEC).ifPresent(tag -> itemManager.deserializeNBT(tag));
    }

    // --- Network Sync ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Debug ---

    public void getDebugInfo(List<Component> left, List<Component> right, Direction side) {
        left.add(Component.literal("power - " + power));
        left.add(Component.literal("target - " + getTarget()));
    }

    // --- Ingredient extraction helper ---

    protected boolean extract(ItemHandlerSimple inv, Collection<IngredientStack> items, boolean simulate,
        boolean precise) {
        AtomicLong remainingStacks = new AtomicLong(inv.stacks.stream().filter(stack -> !stack.isEmpty()).count());
        boolean allItemsConsumed = items.stream().allMatch((definition) -> {
            int remaining = definition.count;
            for (int i = 0; i < inv.getSlots() && remaining > 0; i++) {
                ItemStack slotStack = inv.getStackInSlot(i);
                if (slotStack.isEmpty()) continue;
                if (definition.ingredient.test(slotStack)) {
                    int spend = Math.min(remaining, slotStack.getCount());
                    remaining -= spend;
                    if (!simulate) {
                        slotStack = slotStack.copy();
                        slotStack.setCount(slotStack.getCount() - spend);
                        inv.setStackInSlot(i, slotStack);
                    }
                }
            }
            if (remaining == 0) {
                remainingStacks.decrementAndGet();
                return true;
            }
            return false;
        });
        return allItemsConsumed && (!precise || remainingStacks.get() == 0);
    }
}
