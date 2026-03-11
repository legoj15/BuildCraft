/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

import buildcraft.core.BCCoreConfig;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.tile.TileBC_Neptune;

/**
 * Abstract base class for mining machines (mining well, quarry).
 * Ported from 1.12.2 TileMiner.
 */
public abstract class TileMiner extends TileBC_Neptune {

    protected int progress = 0;
    @Nullable
    protected BlockPos currentPos = null;

    private int wantedLength = 0;
    private double currentLength = 0;
    private double lastLength = 0;
    private int offset;

    protected boolean isComplete = false;
    protected final MjBattery battery = new MjBattery(getBatteryCapacity());

    public TileMiner(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract void mine();

    protected abstract IMjReceiver createMjReceiver();

    // --- Ticking ---

    public void serverTick() {
        battery.tick(getLevel(), getBlockPos());

        if (getLevel().getGameTime() % 10 == offset) {
            // TODO: sendNetworkUpdate(NET_LED_STATUS) once networking is ported
        }

        mine();
    }

    public void clientTick() {
        lastLength = currentLength;
        if (Math.abs(wantedLength - currentLength) <= 0.01) {
            currentLength = wantedLength;
        } else {
            currentLength = currentLength + (wantedLength - currentLength) / 7D;
        }
    }

    public void onLoad() {
        if (level != null && level.random != null) {
            offset = level.random.nextInt(10);
        }
    }

    public void onRemove() {
        for (int y = worldPosition.getY() - 1; y > worldPosition.getY() - BCCoreConfig.miningMaxDepth; y--) {
            BlockPos blockPos = new BlockPos(worldPosition.getX(), y, worldPosition.getZ());
            if (level.getBlockState(blockPos).is(BCFactoryBlocks.TUBE.get())) {
                level.removeBlock(blockPos, false);
            } else {
                break;
            }
        }
    }

    protected void updateLength() {
        int newY = getTargetPos() != null ? getTargetPos().getY() : worldPosition.getY();
        int newLength = worldPosition.getY() - newY;
        if (newLength != wantedLength) {
            // Remove old tubes
            for (int y = worldPosition.getY() - 1; y > worldPosition.getY() - BCCoreConfig.miningMaxDepth; y--) {
                BlockPos blockPos = new BlockPos(worldPosition.getX(), y, worldPosition.getZ());
                if (level.getBlockState(blockPos).is(BCFactoryBlocks.TUBE.get())) {
                    level.removeBlock(blockPos, false);
                } else {
                    break;
                }
            }
            // Place new tubes
            for (int y = worldPosition.getY() - 1; y > newY; y--) {
                BlockPos blockPos = new BlockPos(worldPosition.getX(), y, worldPosition.getZ());
                level.setBlockAndUpdate(blockPos, BCFactoryBlocks.TUBE.get().defaultBlockState());
            }
            currentLength = wantedLength = newLength;
            // TODO: sendNetworkUpdate(NET_WANTED_Y) once networking is ported
        }
    }

    @Nullable
    protected BlockPos getTargetPos() {
        return currentPos;
    }

    public double getLength(float partialTicks) {
        if (partialTicks <= 0) {
            return lastLength;
        } else if (partialTicks >= 1) {
            return currentLength;
        } else {
            return lastLength * (1 - partialTicks) + currentLength * partialTicks;
        }
    }

    public boolean isComplete() {
        return level != null && level.isClientSide() ? isComplete : currentPos == null;
    }

    public float getPercentFilledForRender() {
        float val = battery.getStored() / (float) battery.getCapacity();
        return val < 0 ? 0 : val > 1 ? 1 : val;
    }

    protected long getBatteryCapacity() {
        return 500 * MjAPI.MJ;
    }

    /** @return The IMjReceiver for capability registration. */
    public IMjReceiver getMjReceiver() {
        return createMjReceiver();
    }

    // --- Save / Load ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (currentPos != null) {
            output.putInt("currentPosX", currentPos.getX());
            output.putInt("currentPosY", currentPos.getY());
            output.putInt("currentPosZ", currentPos.getZ());
            output.putBoolean("hasCurrentPos", true);
        } else {
            output.putBoolean("hasCurrentPos", false);
        }
        output.putInt("wantedLength", wantedLength);
        output.putInt("progress", progress);
        output.putLong("mjStored", battery.getStored());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (input.getBooleanOr("hasCurrentPos", false)) {
            int x = input.getIntOr("currentPosX", 0);
            int y = input.getIntOr("currentPosY", 0);
            int z = input.getIntOr("currentPosZ", 0);
            currentPos = new BlockPos(x, y, z);
        } else {
            currentPos = null;
        }
        wantedLength = input.getIntOr("wantedLength", 0);
        progress = input.getIntOr("progress", 0);
        battery.addPowerChecking(input.getLongOr("mjStored", 0L), false);
    }
}
