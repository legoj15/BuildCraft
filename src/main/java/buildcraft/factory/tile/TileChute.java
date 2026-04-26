/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.block.BlockChute;
import buildcraft.factory.container.ContainerChute;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Chute tile entity. Picks up items from the ground on its intake face and
 * inserts them into adjacent inventories. Powered by MJ.
 * Ported from 1.12.2 TileChute.
 */
public class TileChute extends TileBC_Neptune implements MenuProvider {

    private static final int PICKUP_MAX = 3;
    private static final long PROGRESS_TARGET = 100_000;
    private static final Identifier ADVANCEMENT = Identifier.parse("buildcraftunofficial:retired_hopper");

    public final ItemHandlerSimple inv;

    private final MjBattery battery = new MjBattery(1 * MjAPI.MJ);
    private final IMjReceiver mjReceiver = new MjBatteryReceiver(battery);
    private int progress = 0;

    public TileChute(BlockPos pos, BlockState state) {
        super(BCFactoryBlockEntities.CHUTE.get(), pos, state);
        this.inv = itemManager.addInvHandler("inv", 4, EnumAccess.BOTH, EnumPipePart.VALUES);
    }

    public IMjReceiver getMjReceiver() {
        return mjReceiver;
    }

    public ItemHandlerSimple getInv() {
        return inv;
    }

    // --- Ticking ---

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (!(level.getBlockState(worldPosition).getBlock() instanceof BlockChute)) {
            return;
        }

        battery.tick(getLevel(), getBlockPos());

        Direction currentSide = level.getBlockState(worldPosition).getValue(BlockChute.FACING);

        long target = PROGRESS_TARGET;
        if (currentSide == Direction.UP) {
            progress += 1000; // free because of gravity
        }
        progress += (int) battery.extractPower(0, target - progress);

        if (progress >= target) {
            progress = 0;
            pickupItems(currentSide);
        }

        putInNearInventories(currentSide);
    }

    // --- Item Pickup ---

    private void pickupItems(Direction currentSide) {
        AABB aabb = createPickupBox(worldPosition, currentSide);
        int count = PICKUP_MAX;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, aabb, Entity::isAlive)) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;

            // Try to insert into our inventory
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < inv.getSlots() && !remaining.isEmpty() && count > 0; slot++) {
                int before = remaining.getCount();
                remaining = inv.insertItem(slot, remaining, false);
                int moved = before - remaining.getCount();
                if (moved > 0) {
                    count -= moved;
                }
            }

            if (remaining.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(remaining);
            }

            if (count <= 0) {
                return;
            }
        }
    }

    private static AABB createPickupBox(BlockPos pos, Direction side) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        // Extend 0.25 blocks out from the face
        return switch (side) {
            case DOWN -> new AABB(x, y - 0.25, z, x + 1, y, z + 1);
            case UP -> new AABB(x, y + 1, z, x + 1, y + 1.25, z + 1);
            case NORTH -> new AABB(x, y, z - 0.25, x + 1, y + 1, z);
            case SOUTH -> new AABB(x, y, z + 1, x + 1, y + 1, z + 1.25);
            case WEST -> new AABB(x - 0.25, y, z, x, y + 1, z + 1);
            case EAST -> new AABB(x + 1, y, z, x + 1.25, y + 1, z + 1);
        };
    }

    // --- Item Insertion ---

    private void putInNearInventories(Direction currentSide) {
        for (int ourSlot = 0; ourSlot < inv.getSlots(); ourSlot++) {
            ItemStack inSlot = inv.getStackInSlot(ourSlot);
            if (inSlot.isEmpty()) continue;

            // 1) Try BC pipes via IInjectable (skips intake side).
            ItemStack oneItem = inSlot.copyWithCount(1);
            ItemStack leftover = InventoryUtil.addToRandomInjectable(
                    level, worldPosition, currentSide, oneItem);
            if (leftover.isEmpty()) {
                inv.extractItem(ourSlot, 1, false);
                grantAdvancement();
                return;
            }

            // 2) Try vanilla inventories on the 5 non-intake sides.
            List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
            Collections.shuffle(sides);
            sides.remove(currentSide);
            ItemResource resource = ItemResource.of(inSlot);
            for (Direction side : sides) {
                ResourceHandler<ItemResource> handler = level.getCapability(
                        Capabilities.Item.BLOCK,
                        worldPosition.relative(side),
                        side.getOpposite());
                if (handler == null) continue;
                int inserted = ResourceHandlerUtil.insertStacking(handler, resource, 1, null);
                if (inserted > 0) {
                    inv.extractItem(ourSlot, inserted, false);
                    grantAdvancement();
                    return;
                }
            }
        }
    }

    private void grantAdvancement() {
        if (getOwner() != null) {
            AdvancementUtil.unlockAdvancement(getOwner().id(), level, ADVANCEMENT);
        }
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.chute");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerChute(containerId, playerInv, this);
    }

    // --- Save / Load ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("progress", progress);
        output.putLong("mjStored", battery.getStored());
        output.store("items", CompoundTag.CODEC, itemManager.serializeNBT());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = input.getIntOr("progress", 0);
        battery.addPowerChecking(input.getLongOr("mjStored", 0L), false);
        input.read("items", CompoundTag.CODEC).ifPresent(itemManager::deserializeNBT);
    }
}
