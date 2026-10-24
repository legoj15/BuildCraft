/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy.tile;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.mj.MjAPI;
import buildcraft.core.BCCoreItems;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.BlockDropsUtil;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * The part the FE engine ({@link TileEngineFE}) and the MJ Dynamo ({@link TileDynamoMJ}) share: four gear upgrade
 * slots (one iron or gold gear each) that raise the engine's MJ-per-tick conversion rate on top of a base of 4 MJ.
 * Holds the gear table, the slot handler, the rate they sum to, the persistence of the slots, and what the slots
 * spill when the engine is removed (the removal hook itself is {@code AbstractBCSyncedBlockEntity}'s).
 */
@SuppressWarnings("this-escape")
public abstract class TileEngineGearBase extends TileEngineBase_BC8 {

    /** Gear item to the extra MJ per tick it adds. Filled lazily by {@link #initUpgrades()}: items are not
     *  registered yet when this class loads. */
    public static final Map<Item, Long> UPGRADE_VALUES = new LinkedHashMap<>();

    public static void initUpgrades() {
        if (UPGRADE_VALUES.isEmpty()) {
            UPGRADE_VALUES.put(BCCoreItems.GEAR_IRON.get(), MjAPI.MJ * 2);
            UPGRADE_VALUES.put(BCCoreItems.GEAR_GOLD.get(), MjAPI.MJ * 3);
        }
    }

    public final ItemHandlerSimple upgrades =
        new ItemHandlerSimple(4, (handler, slot, bef, aft) -> setChanged());

    {
        upgrades.setChecker((slot, stack) -> {
            initUpgrades();
            return UPGRADE_VALUES.containsKey(stack.getItem());
        });
        upgrades.setLimitedInsertor(1);
    }

    protected TileEngineGearBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The base 4 MJ per tick plus what each gear in the upgrade slots adds. */
    public long getMjPerTick() {
        initUpgrades();
        long value = MjAPI.MJ * 4;
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            ItemStack stack = upgrades.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            Long add = UPGRADE_VALUES.get(stack.getItem());
            if (add != null) {
                value += add;
            }
        }
        return value;
    }

    // --- Removal drops (the hook is AbstractBCSyncedBlockEntity's; this is just what these engines hold) ---
    // The upgrade slots are a raw ItemHandlerSimple outside any ItemHandlerManager. They empty as they spill,
    // which keeps a second call from dropping the gears again. A command REPLACE wipes the same slots
    // (clearContent below).

    @Override
    public void dropContentsOnRemoval(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        BlockDropsUtil.dropItems(level, pos, upgrades);
        clearContent();
    }

    /** Wipes the upgrade slots {@link #dropContentsOnRemoval} would spill; see
     *  {@code AbstractBCBlockEntity#clearContent}. */
    @Override
    public void clearContent() {
        upgrades.clearStacks();
    }

    // --- Persistence ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        output.store("upgrades", CompoundTag.CODEC, upgrades.serializeNBT());
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        upgrades.deserializeNBT(input.read("upgrades", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }
}
