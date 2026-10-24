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
import buildcraft.lib.mj.BCFeStorage;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * The shared base of the two FE converters, the FE engine ({@link TileEngineFE}, FE in and MJ out) and the MJ Dynamo
 * ({@link TileDynamoMJ}, MJ in and FE out). It holds what is the same in both directions: four gear upgrade slots
 * (one iron or gold gear each) that raise the MJ-per-tick conversion rate on top of a base of 4 MJ, the gear table,
 * the rate they sum to, and what the slots spill when the engine is removed (the removal hook itself is
 * {@code AbstractBCSyncedBlockEntity}'s). It also holds the 10,000 FE buffer with its {@code "currentFe"} save key,
 * and the heat that burning adds and idling bleeds off. The buffer's direction is the only difference between the
 * two buffers: each subclass passes its insert and extract limits to the constructor, so the engine's buffer takes
 * FE in only and the dynamo's gives FE out only. What genuinely differs stays in the subclasses: how FE moves to or
 * from neighbours, the per-tick update, the chain length and the power limits.
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

    /** Capacity of the FE buffer. */
    public static final int MAX_FE = 10_000;
    /** Heat gained per tick while the converter is working, capped at 200. */
    private static final float HEAT_RATE = 0.06f;
    /** Heat lost per tick back toward {@code MIN_HEAT}. */
    private static final float COOLDOWN_RATE = 0.01f;

    /** The FE buffer, exposed to neighbours as the energy capability (the facing side decides which tile hands it
     *  out, see {@code BCEnergy}). One class for both converters: a buffer that takes FE in only (the engine) and one
     *  that gives FE out only (the dynamo) differ just in the two limits the subclass passes to the constructor. */
    public final BCFeStorage energyStorage;

    public final ItemHandlerSimple upgrades =
        new ItemHandlerSimple(4, (handler, slot, bef, aft) -> setChanged());

    {
        upgrades.setChecker((slot, stack) -> {
            initUpgrades();
            return UPGRADE_VALUES.containsKey(stack.getItem());
        });
        upgrades.setLimitedInsertor(1);
    }

    /** @param feMaxInsert how much FE the buffer accepts per transfer ({@link #MAX_FE} for the engine, 0 for the dynamo)
     *  @param feMaxExtract how much FE the buffer gives out per transfer (0 for the engine, {@link #MAX_FE} for the
     *         dynamo) */
    protected TileEngineGearBase(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                 int feMaxInsert, int feMaxExtract) {
        super(type, pos, state);
        energyStorage = new BCFeStorage(MAX_FE, feMaxInsert, feMaxExtract) {
            @Override
            protected void onFeChanged() {
                setChanged();
            }
        };
    }

    public int getCurrentFe() {
        return (int) energyStorage.getAmountAsLong();
    }

    public void setCurrentFe(int fe) {
        energyStorage.set(Math.max(0, Math.min(MAX_FE, fe)));
    }

    /** Heat here is fed by burning, not derived from the stored power as in the base engine. */
    @Override
    public void updateHeatLevel() {
        if (heat > MIN_HEAT) {
            heat -= COOLDOWN_RATE;
        }
        if (heat <= MIN_HEAT) {
            heat = MIN_HEAT;
        }
        getPowerStage();
    }

    /** One tick of burn heat: {@link #HEAT_RATE} more, capped at 200. */
    protected void addBurnHeat() {
        heat += HEAT_RATE;
        if (heat >= 200) {
            heat = 200;
        }
    }

    @Override
    public float explosionRange() {
        return 4;
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
        output.putInt("currentFe", getCurrentFe());
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        upgrades.deserializeNBT(input.read("upgrades", CompoundTag.CODEC).orElseGet(CompoundTag::new));
        setCurrentFe(input.getIntOr("currentFe", 0));
    }
}
