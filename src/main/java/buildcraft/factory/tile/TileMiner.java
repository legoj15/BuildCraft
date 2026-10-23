/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.tile;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
//?}

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.tiles.IHasWork;

import buildcraft.core.BCCoreConfig;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.lib.mj.MjBatteryComponent;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.tile.TileBC_Neptune;

/**
 * Abstract base class for mining machines (mining well, quarry).
 * Ported from 1.12.2 TileMiner.
 */
@SuppressWarnings("this-escape")
public abstract class TileMiner extends TileBC_Neptune implements IHasWork {

    protected int progress = 0;
    @Nullable
    protected BlockPos currentPos = null;

    protected int wantedLength = 0;
    protected double currentLength = 0;
    protected double lastLength = 0;
    private int offset;

    protected boolean isComplete = false;
    protected final MjBatteryComponent mjPower = new MjBatteryComponent(getBatteryCapacity(), this::makeReceiver);
    /** Alias for {@link #mjPower}'s battery, so hot-path mining code reads {@code battery.extractPower(...)}. */
    protected final MjBattery battery = mjPower.getBattery();

    public TileMiner(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract void mine();

    /** Builds this miner's MJ receiver over its battery: the mining well takes a plain battery receiver,
     *  the pump a redstone one. Called once, when {@link #mjPower} is constructed. */
    protected abstract IMjReceiver makeReceiver(MjBattery battery);

    // --- Ticking ---

    public void serverTick() {
        battery.tick(getLevel(), getBlockPos());

        if (getLevel().getGameTime() % 10 == offset) {
            setChanged();
            MessageUtil.sendUpdateToTrackingPlayers(this);
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
        if (level != null && level.getRandom() != null) {
            offset = level.getRandom().nextInt(10);
        }
        if (level != null && level.isClientSide()) {
            buildcraft.factory.client.render.TubeRenderer.addMiner(this);
        }
    }

    public void onRemove() {
        removeTubes();
    }

    /** Removes the whole tube column under the miner. {@code removeBlock} swaps each tube for its fluid state, so
     *  a waterlogged tube leaves its water source behind and a dry one leaves air. The column is the contiguous run
     *  of tubes below the machine, plus any gap inside the length the machine last laid: {@link #updateLength} leaves
     *  a cell it may not take (a claimed block the well skipped) untouched, and the tubes below it still go. */
    private void removeTubes() {
        for (int y = worldPosition.getY() - 1; y > worldPosition.getY() - BCCoreConfig.miningMaxDepth.get(); y--) {
            BlockPos blockPos = new BlockPos(worldPosition.getX(), y, worldPosition.getZ());
            if (BlockUtil.isMinerTube(level.getBlockState(blockPos))) {
                level.removeBlock(blockPos, false);
            } else if (worldPosition.getY() - y >= wantedLength) {
                break;
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) {
            buildcraft.factory.client.render.TubeRenderer.removeMiner(this);
        }
    }

    /**
     * Brings the tube column to the length the current target wants. Tubes sit in the cells below the machine down to
     * (but not including) the target. Growing the column fills every cell above the new end that lacks its tube, so
     * a tube lost mid-column (an explosion, a creative player) comes back with the next mined block, as it did when
     * this rewrote the whole column; shrinking it removes just the cells it gave up. Only missing cells are written:
     * rewriting every cell per mined block cost O(depth) block updates and, in water, an un-waterlog and re-waterlog
     * of every cell, where a cell that already holds its tube now costs one state read.
     */
    protected void updateLength() {
        int newY = getTargetPos() != null ? getTargetPos().getY() : worldPosition.getY();
        int newLength = worldPosition.getY() - newY;
        if (newLength != wantedLength) {
            if (newLength < wantedLength) {
                retractTubes(newLength, wantedLength);
            } else {
                extendTubes(newLength);
            }
            currentLength = wantedLength = newLength;
            setChanged();
            MessageUtil.sendUpdateToTrackingPlayers(this);
        }
    }

    /** Lays a tube in every cell from just below the machine down to just above depth {@code newLength} that does
     *  not hold one yet. A cell holding a water source gets a waterlogged tube so the water survives. The tube takes
     *  only air and bare fluid; anything else is left alone, so the tube has a gap there: a block the machine was
     *  refused (a claim the well skipped past) or one that is not the tube's to replace (a waterlogged slab in a
     *  pump's column). A fluid cell is replaced only once the claim system allows it: the well drills through water
     *  and the pump past it without ever breaking it, so this is the only ask that cell gets (repeated each time the
     *  column grows while the cell stays a refused fluid: one break probe per mined block, against a gap that is
     *  rare and that the old full rewrite simply overwrote). */
    private void extendTubes(int newLength) {
        for (int depth = 1; depth < newLength; depth++) {
            BlockPos blockPos = worldPosition.below(depth);
            BlockState existing = level.getBlockState(blockPos);
            if (existing.isAir() || (isBareFluid(existing) && mayReplace(blockPos))) {
                level.setBlockAndUpdate(blockPos, BCFactoryBlocks.TUBE.get().stateFor(level, blockPos));
            }
        }
    }

    /** A cell that is only fluid: a fluid block, or a replaceable block holding fluid that is no block of its own
     *  (a modded fluid block outside {@link LiquidBlock}), never a waterlogged block, kelp or seagrass. */
    private static boolean isBareFluid(BlockState state) {
        return state.getBlock() instanceof LiquidBlock
                || (state.canBeReplaced() && !state.getFluidState().isEmpty() && !BlockUtil.isFluidloggedBlock(state));
    }

    private boolean mayReplace(BlockPos pos) {
        return !(level instanceof ServerLevel serverLevel) || BlockUtil.canMachineBreak(serverLevel, pos, getOwner());
    }

    /** Removes the tubes in the cells at depth {@code newLength} down to just above depth {@code oldLength}
     *  (waterlogged ones leave their water source behind). */
    private void retractTubes(int newLength, int oldLength) {
        for (int depth = Math.max(1, newLength); depth < oldLength; depth++) {
            BlockPos blockPos = worldPosition.below(depth);
            if (BlockUtil.isMinerTube(level.getBlockState(blockPos))) {
                level.removeBlock(blockPos, false);
            }
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

    @Override
    public boolean hasWork() {
        return !isComplete();
    }

    /** Debug accessor for wanted tube length. */
    public int getWantedLength() {
        return wantedLength;
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
        return mjPower.getMjReceiver();
    }

    /** @return The internal MJ battery, for Forge-Energy capability registration. */
    public MjBattery getBattery() {
        return battery;
    }

    //? if >=1.21.10 {
    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
    }
    //?} else {
    /*@Override
    public void handleUpdateTag(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookupProvider) {
        super.handleUpdateTag(tag, lookupProvider);
    }*/
    //?}

    // --- Save / Load ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
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
    protected void readData(BCValueInput input) {
        super.readData(input);
        if (input.getBooleanOr("hasCurrentPos", false)) {
            int x = input.getIntOr("currentPosX", 0);
            int y = input.getIntOr("currentPosY", 0);
            int z = input.getIntOr("currentPosZ", 0);
            currentPos = new BlockPos(x, y, z);
        } else {
            currentPos = null;
        }
        // Derive isComplete on client — must be here (not handleUpdateTag)
        // because onDataPacket calls loadAdditional directly, not handleUpdateTag
        if (level != null && level.isClientSide()) {
            isComplete = (currentPos == null);
        }
        int newWantedLength = input.getIntOr("wantedLength", 0);
        // Only update wantedLength — clientTick() smoothly interpolates currentLength toward it
        wantedLength = newWantedLength;
        progress = input.getIntOr("progress", 0);
        // Reset battery then set to synced value (avoid accumulation on client)
        battery.extractPower(0, Long.MAX_VALUE);
        battery.addPowerChecking(input.getLongOr("mjStored", 0L), false);
    }
}
