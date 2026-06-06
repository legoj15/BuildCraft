/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.tile;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.robotics.BCRoboticsBlockEntities;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.zone.ZonePlan;

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * The Zone Planner tile entity — stores 16 zone layers (one per dye colour),
 * manages paintbrush/map location input/output inventories, and processes
 * zone reading/writing operations.
 * Ported from 1.12.2 TileZonePlanner.
 */
public class TileZonePlanner extends TileBC_Neptune implements MenuProvider {
    // 7 inventories matching 1.12.2 layout, using ItemHandlerSimple for SlotBase compatibility
    public final ItemHandlerSimple invPaintbrushes = new ItemHandlerSimple(16, null);
    public final ItemHandlerSimple invInputPaintbrush = new ItemHandlerSimple(1, null);
    public final ItemHandlerSimple invInputMapLocation = new ItemHandlerSimple(1, null);
    public final ItemHandlerSimple invInputResult = new ItemHandlerSimple(1, null);
    public final ItemHandlerSimple invOutputPaintbrush = new ItemHandlerSimple(1, null);
    public final ItemHandlerSimple invOutputMapLocation = new ItemHandlerSimple(1, null);
    public final ItemHandlerSimple invOutputResult = new ItemHandlerSimple(1, null);

    private int progressInput = -1;
    private int progressOutput = -1;

    public ZonePlan[] layers = new ZonePlan[16];

    public TileZonePlanner(BlockPos pos, BlockState state) {
        super(BCRoboticsBlockEntities.ZONE_PLANNER.get(), pos, state);
        for (int i = 0; i < layers.length; i++) {
            layers[i] = new ZonePlan();
        }
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Input processing: read a map location + paintbrush → store the zone in the tile
        // Output processing: write the zone for a paintbrush colour to a map location
        // Both use 200-tick progress timers matching 1.12.2 behavior.
        // The actual paintbrush/map location item validation and zone transfer logic
        // requires ItemMapLocation and ItemPaintbrush_BC8 from buildcraftcore.
        // For now this is a no-op tick — the slot processing will be wired once
        // the items are fully validated.
    }

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        for (int i = 0; i < layers.length; i++) {
            CompoundTag layerTag = new CompoundTag();
            layers[i].writeToNBT(layerTag);
            output.store("layer_" + i, CompoundTag.CODEC, layerTag);
        }
        output.store("invPaintbrushes", CompoundTag.CODEC, invPaintbrushes.serializeNBT());
        output.store("invInputPaintbrush", CompoundTag.CODEC, invInputPaintbrush.serializeNBT());
        output.store("invInputMapLocation", CompoundTag.CODEC, invInputMapLocation.serializeNBT());
        output.store("invInputResult", CompoundTag.CODEC, invInputResult.serializeNBT());
        output.store("invOutputPaintbrush", CompoundTag.CODEC, invOutputPaintbrush.serializeNBT());
        output.store("invOutputMapLocation", CompoundTag.CODEC, invOutputMapLocation.serializeNBT());
        output.store("invOutputResult", CompoundTag.CODEC, invOutputResult.serializeNBT());
        output.putInt("progressInput", progressInput);
        output.putInt("progressOutput", progressOutput);
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        for (int i = 0; i < layers.length; i++) {
            final int idx = i;
            input.read("layer_" + i, CompoundTag.CODEC).ifPresent(tag -> {
                layers[idx] = new ZonePlan();
                layers[idx].readFromNBT(tag);
            });
        }
        input.read("invPaintbrushes", CompoundTag.CODEC).ifPresent(invPaintbrushes::deserializeNBT);
        input.read("invInputPaintbrush", CompoundTag.CODEC).ifPresent(invInputPaintbrush::deserializeNBT);
        input.read("invInputMapLocation", CompoundTag.CODEC).ifPresent(invInputMapLocation::deserializeNBT);
        input.read("invInputResult", CompoundTag.CODEC).ifPresent(invInputResult::deserializeNBT);
        input.read("invOutputPaintbrush", CompoundTag.CODEC).ifPresent(invOutputPaintbrush::deserializeNBT);
        input.read("invOutputMapLocation", CompoundTag.CODEC).ifPresent(invOutputMapLocation::deserializeNBT);
        input.read("invOutputResult", CompoundTag.CODEC).ifPresent(invOutputResult::deserializeNBT);
        progressInput = input.getIntOr("progressInput", -1);
        progressOutput = input.getIntOr("progressOutput", -1);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.zone_planner");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerZonePlanner(containerId, playerInv, this);
    }

    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("progress_input = " + progressInput);
        left.add("progress_output = " + progressOutput);
    }
}
