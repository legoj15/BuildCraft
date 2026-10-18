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
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.core.item.ItemMapLocation;
import buildcraft.core.item.ItemPaintbrush_BC8;

import buildcraft.robotics.BCRoboticsBlockEntities;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.zone.ZonePlan;

import buildcraft.lib.gui.IBCMenuProvider;
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
public class TileZonePlanner extends TileBC_Neptune implements IBCMenuProvider {
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
        // Slot filters: paintbrush slots take only paintbrushes; the map slots only map locations.
        // (Result slots stay open here — the container's SlotOutput blocks player placement, and the
        // tick writes results via setStackInSlot, which bypasses the checker.)
        invPaintbrushes.setChecker((slot, stack) -> stack.getItem() instanceof ItemPaintbrush_BC8);
        invInputPaintbrush.setChecker((slot, stack) -> stack.getItem() instanceof ItemPaintbrush_BC8);
        invOutputPaintbrush.setChecker((slot, stack) -> stack.getItem() instanceof ItemPaintbrush_BC8);
        invInputMapLocation.setChecker((slot, stack) -> stack.getItem() instanceof ItemMapLocation);
        invOutputMapLocation.setChecker((slot, stack) -> stack.getItem() instanceof ItemMapLocation);

        // setChanged() is what flags the chunk unsaved, and vanilla only writes an unsaved chunk (it
        // serialises block entities live at that point, so the data itself is never stale). An inventory edit
        // with no callback never flags it, and the rack's contents were then never written — brushes stashed
        // in it vanished on relog. Same wiring as the distiller / heat exchanger tiles.
        for (ItemHandlerSimple inv : new ItemHandlerSimple[] {
                invPaintbrushes, invInputPaintbrush, invInputMapLocation, invInputResult,
                invOutputPaintbrush, invOutputMapLocation, invOutputResult }) {
            inv.setCallback((handler, slot, before, after) -> setChanged());
        }
    }

    /** Ticks to complete one input/output transfer — 10 s, matching 1.12.2. */
    private static final int PROGRESS = 200;

    /** Raw input/output progress (−1 idle, else 0..{@link #PROGRESS}); read by the container to drive the
     *  GUI progress bars. */
    public int getProgressInput() {
        return progressInput;
    }

    public int getProgressOutput() {
        return progressOutput;
    }

    public static int getProgressMax() {
        return PROGRESS;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        // INPUT (map location + paintbrush -> store the zone in the paintbrush-colour's layer).
        // It has priority: while it is mid-progress the OUTPUT side is held off, matching 1.12.2.
        if (isInputValid()) {
            if (progressInput < 0) {
                progressInput = 0;
            }
            if (progressInput < PROGRESS) {
                progressInput++;
                setChanged();
                return;
            }
            completeInput();
            progressInput = 0;
            setChanged();
            return;
        } else if (progressInput != -1) {
            progressInput = -1;
            setChanged();
        }

        // OUTPUT (paintbrush colour's layer -> write the zone onto a map location).
        if (isOutputValid()) {
            if (progressOutput < 0) {
                progressOutput = 0;
            }
            if (progressOutput < PROGRESS) {
                progressOutput++;
                setChanged();
                return;
            }
            completeOutput();
            progressOutput = 0;
            setChanged();
        } else if (progressOutput != -1) {
            progressOutput = -1;
            setChanged();
        }
    }

    /** Runs every server tick, so the cheap checks come first: a full result slot (the usual idle state) or a
     *  missing brush never gets as far as the map's zone. */
    private boolean isInputValid() {
        return invInputResult.getStackInSlot(0).isEmpty()
                && paintbrushColour(invInputPaintbrush.getStackInSlot(0)) != null
                && inputZone(invInputMapLocation.getStackInSlot(0)) != null;
    }

    private void completeInput() {
        ItemStack map = invInputMapLocation.getStackInSlot(0);
        DyeColor colour = paintbrushColour(invInputPaintbrush.getStackInSlot(0));
        ZonePlan worldZone = inputZone(map);
        if (colour == null || worldZone == null) {
            return;
        }
        // Layers are stored tile-relative; the map carries absolute world coords.
        layers[colour.ordinal()] = worldZone.getWithOffset(-getBlockPos().getX(), -getBlockPos().getZ());

        // Hand back a blank map location of the same item and consume one input.
        invInputResult.setStackInSlot(0, new ItemStack(map.getItem()));
        map.shrink(1);
        invInputMapLocation.setStackInSlot(0, map.isEmpty() ? ItemStack.EMPTY : map);
    }

    private boolean isOutputValid() {
        DyeColor colour = paintbrushColour(invOutputPaintbrush.getStackInSlot(0));
        // An empty layer (brush colour never painted here) refuses the transfer outright — otherwise a
        // colour-mismatched brush would happily burn 10 s and produce a ZONE map containing zero chunks.
        return colour != null
                && !layers[colour.ordinal()].isEmpty()
                && invOutputMapLocation.getStackInSlot(0).getItem() instanceof ItemMapLocation
                && invOutputResult.getStackInSlot(0).isEmpty();
    }

    private void completeOutput() {
        ItemStack map = invOutputMapLocation.getStackInSlot(0);
        DyeColor colour = paintbrushColour(invOutputPaintbrush.getStackInSlot(0));
        if (colour == null || !(map.getItem() instanceof ItemMapLocation)) {
            return;
        }
        ItemStack written = map.copy();
        written.setCount(1);
        // Tile-relative layer back to absolute world coords for the item.
        ItemMapLocation.setZone(written,
                layers[colour.ordinal()].getWithOffset(getBlockPos().getX(), getBlockPos().getZ()));
        invOutputResult.setStackInSlot(0, written);
        map.shrink(1);
        invOutputMapLocation.setStackInSlot(0, map.isEmpty() ? ItemStack.EMPTY : map);
    }

    /** The brush's dye colour, or null if the stack is not a coloured paintbrush. */
    @Nullable
    private static DyeColor paintbrushColour(ItemStack stack) {
        if (stack.getItem() instanceof ItemPaintbrush_BC8 brush) {
            return brush.getBrushFromStack(stack).colour;
        }
        return null;
    }

    /** The zone stored on a map-location stack (absolute coords), or null if it is not a ZONE map. Parses the
     *  stack's NBT, so it is overridable only for a test to count the parses. */
    @Nullable
    protected ZonePlan decodeZone(ItemStack stack) {
        if (stack.getItem() instanceof ItemMapLocation map && map.getZone(stack) instanceof ZonePlan zone) {
            return zone;
        }
        return null;
    }

    /** The data component {@link #inputZone} last parsed, and what it parsed to (null for a map holding no zone). */
    @Nullable
    private CustomData inputZoneKey;
    @Nullable
    private ZonePlan inputZoneCache;

    /**
     * {@link #decodeZone} for the stack in the input slot, parsed once per distinct map instead of every tick. A
     * map-location stack carries its type and zone only in its custom-data component, which is replaced (never edited
     * in place) when the stack is rewritten, so that component's identity is the key: a swapped, rewritten or removed
     * map misses, and an unchanged one hits. A map with no custom data is a blank one and holds no zone.
     */
    @Nullable
    private ZonePlan inputZone(ItemStack stack) {
        CustomData data = stack.getItem() instanceof ItemMapLocation ? stack.get(DataComponents.CUSTOM_DATA) : null;
        if (data == null) {
            inputZoneKey = null;
            inputZoneCache = null;
            return null;
        }
        if (data != inputZoneKey) {
            inputZoneCache = decodeZone(stack);
            inputZoneKey = data;
        }
        return inputZoneCache;
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
