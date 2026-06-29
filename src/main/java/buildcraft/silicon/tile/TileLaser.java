/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.tile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.mj.ILaserTarget;
import buildcraft.api.mj.ILaserTargetBlock;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

import buildcraft.lib.block.ILocalBlockUpdateSubscriber;
import buildcraft.lib.block.LocalBlockUpdateNotifier;
import buildcraft.lib.debug.IAdvDebugTarget;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.VolumeUtil;
import buildcraft.lib.misc.data.AverageLong;
import buildcraft.lib.tile.AbstractBCBlockEntity;
import buildcraft.lib.mj.MjBatteryReceiver;

import buildcraft.api.tiles.IDebuggable;
import buildcraft.silicon.BCSiliconBlockEntities;
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.BCSiliconConfig;
import buildcraft.silicon.block.BlockLaser;

public class TileLaser extends AbstractBCBlockEntity implements ILocalBlockUpdateSubscriber, IDebuggable, IAdvDebugTarget {
    /** Forward range of the modern line-of-sight cone (LOS_CONE mode). Package-private so the tester
     *  can pass the production value rather than a literal. */
    static final int TARGETING_RANGE = 6;
    /** Radius of the legacy axis-aligned box (BOX mode); deliberately 5, not {@link #TARGETING_RANGE}. */
    static final int BOX_RADIUS = 5;

    private final SafeTimeTracker clientLaserMoveInterval = new SafeTimeTracker(5, 10);
    private final SafeTimeTracker serverTargetMoveInterval = new SafeTimeTracker(10, 20);
    private final SafeTimeTracker rescanInterval = new SafeTimeTracker(40, 20);

    private final List<BlockPos> targetPositions = new ArrayList<>();
    private BlockPos targetPos;
    public Vec3 laserPos;
    private boolean worldHasUpdated = true;

    private final AverageLong avgPower = new AverageLong(100);
    private long averageClient;
    private final MjBattery battery;
    private final MjBatteryReceiver mjReceiver;

    public TileLaser(BlockPos pos, BlockState state) {
        super(BCSiliconBlockEntities.LASER.get(), pos, state);
        battery = new MjBattery(1024 * MjAPI.MJ);
        mjReceiver = new MjBatteryReceiver(battery);
    }

    public MjBatteryReceiver getMjReceiver() {
        return mjReceiver;
    }

    /** @return The internal MJ battery, for Forge-Energy capability registration. */
    public MjBattery getBattery() {
        return battery;
    }

    // --- ILocalBlockUpdateSubscriber ---

    @Override
    public int getUpdateRange() {
        return TARGETING_RANGE;
    }

    @Override
    public BlockPos getSubscriberPos() {
        return getBlockPos();
    }

    @Override
    public void setLevelUpdated(Level world, BlockPos eventPos, BlockState oldState, BlockState newState, int flags) {
        this.worldHasUpdated = true;
    }

    // --- Target scanning ---

    private void findPossibleTargets() {
        targetPositions.clear();
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() != BCSiliconBlocks.LASER.get()) {
            return;
        }
        Direction face = state.getValue(BlockLaser.FACING);

        // Config picks the scan shape; default LOS_CONE preserves modern behaviour. The field is null
        // only in headless contexts where the config spec isn't booted (e.g. unit tests).
        BCSiliconConfig.LaserTargetingMode mode = BCSiliconConfig.laserTargetingBehavior != null
                ? BCSiliconConfig.laserTargetingBehavior.get()
                : BCSiliconConfig.LaserTargetingMode.LOS_CONE;
        if (mode == BCSiliconConfig.LaserTargetingMode.BOX) {
            targetPositions.addAll(scanBox(level, worldPosition, face, BOX_RADIUS));
        } else {
            targetPositions.addAll(scanCone(level, worldPosition, face, TARGETING_RANGE));
        }
    }

    /**
     * Modern (8.0.x) targeting: a square cone of {@code range} blocks projecting from the laser
     * {@code face}, gated by line-of-sight — any solid block on the path to a candidate hides it.
     * Package-private + static so {@code LaserTargetingTester} can exercise it without a live tile.
     */
    static List<BlockPos> scanCone(Level level, BlockPos origin, Direction face, int range) {
        List<BlockPos> found = new ArrayList<>();
        VolumeUtil.iterateCone(level, origin, face, range, (w, s, p, visible) -> {
            if (!visible) {
                return;
            }
            BlockState stateAt = level.getBlockState(p);
            if (stateAt.getBlock() instanceof ILaserTargetBlock) {
                BlockEntity tileAt = level.getBlockEntity(p);
                if (tileAt instanceof ILaserTarget) {
                    found.add(p);
                }
            }
        });
        return found;
    }

    /**
     * Legacy (BuildCraft 7.x) targeting: an axis-aligned box of {@code radius} blocks, clipped to the
     * half-space the laser {@code face}s into (including the laser's own slice), with NO line-of-sight
     * — a table off to the side at the laser's own level, or one tucked behind a wall, is still reached.
     */
    static List<BlockPos> scanBox(Level level, BlockPos origin, Direction face, int radius) {
        List<BlockPos> found = new ArrayList<>();
        int x = origin.getX(), y = origin.getY(), z = origin.getZ();
        int minX = x - radius, minY = y - radius, minZ = z - radius;
        int maxX = x + radius, maxY = y + radius, maxZ = z + radius;
        // Collapse the bound behind the laser to its own coordinate so only the faced hemisphere is
        // scanned. 1:1 with BuildCraft 7.x's ForgeDirection switch — Direction axis dirs are identical.
        switch (face) {
            case WEST -> maxX = x;  // faces -X
            case EAST -> minX = x;  // faces +X
            case DOWN -> maxY = y;  // faces -Y
            case UP -> minY = y;    // faces +Y
            case NORTH -> maxZ = z; // faces -Z
            case SOUTH -> minZ = z; // faces +Z
        }
        for (BlockPos p : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState stateAt = level.getBlockState(p);
            if (stateAt.getBlock() instanceof ILaserTargetBlock) {
                BlockEntity tileAt = level.getBlockEntity(p);
                if (tileAt instanceof ILaserTarget) {
                    found.add(p.immutable()); // betweenClosed reuses one MutableBlockPos cursor
                }
            }
        }
        return found;
    }

    private void randomlyChooseTargetPos() {
        List<BlockPos> targetsNeedingPower = new ArrayList<>();
        for (BlockPos position : targetPositions) {
            if (isPowerNeededAt(position)) {
                targetsNeedingPower.add(position);
            }
        }
        if (targetsNeedingPower.isEmpty()) {
            targetPos = null;
            return;
        }
        targetPos = targetsNeedingPower.get(level.getRandom().nextInt(targetsNeedingPower.size()));
    }

    private boolean isPowerNeededAt(BlockPos position) {
        if (position != null) {
            BlockEntity tile = level.getBlockEntity(position);
            if (tile instanceof ILaserTarget target) {
                return target.getRequiredLaserPower() > 0;
            }
        }
        return false;
    }

    private ILaserTarget getTarget() {
        if (targetPos != null) {
            BlockEntity tile = level.getBlockEntity(targetPos);
            if (tile instanceof ILaserTarget) {
                return (ILaserTarget) tile;
            }
        }
        return null;
    }

    private void updateLaser() {
        if (targetPos != null) {
            laserPos = Vec3.atLowerCornerOf(targetPos).add(
                (5 + level.getRandom().nextInt(6) + 0.5) / 16D,
                9 / 16D,
                (5 + level.getRandom().nextInt(6) + 0.5) / 16D
            );
        } else {
            laserPos = null;
        }
    }

    public long getAverageClient() {
        return averageClient;
    }

    public long getMaxPowerPerTick() {
        return 4 * MjAPI.MJ;
    }

    // --- Ticking ---

    public void clientTick() {
        if (clientLaserMoveInterval.markTimeIfDelay(level) || targetPos == null) {
            updateLaser();
        }
    }

    public void serverTick() {
        ensureRegistered();
        avgPower.tick();

        BlockPos previousTargetPos = targetPos;
        if (worldHasUpdated || rescanInterval.markTimeIfDelay(level)) {
            findPossibleTargets();
            worldHasUpdated = false;
        }

        // Only target when we have power to send
        if (battery.getStored() <= 0) {
            targetPos = null;
        } else {
            if (!isPowerNeededAt(targetPos)) {
                targetPos = null;
            }

            if (serverTargetMoveInterval.markTimeIfDelay(level) || !isPowerNeededAt(targetPos)) {
                randomlyChooseTargetPos();
            }
        }

        ILaserTarget target = getTarget();
        if (target != null) {
            long max = getMaxPowerPerTick();
            max *= battery.getStored() + max;
            max /= battery.getCapacity() / 2;
            max = Math.min(Math.min(max, getMaxPowerPerTick()), target.getRequiredLaserPower());
            long power = battery.extractPower(0, max);
            long excess = target.receiveLaserPower(power);
            if (excess > 0) {
                battery.addPowerChecking(excess, false);
            }
            avgPower.push(power - excess);
        } else {
            avgPower.clear();
        }

        averageClient = (long) avgPower.getAverage();

        if (!Objects.equals(previousTargetPos, targetPos) || true) {
            // Always sync for now — matches 1.12.2 behaviour
            setChanged();
            MessageUtil.sendUpdateToTrackingPlayers(this);
        }
    }

    // --- Save / Load (ValueOutput / ValueInput API) ---

    // The saveAdditional/loadAdditional signature directive lives once in AbstractBCBlockEntity;
    // here we only override the version-neutral writeData/readData hooks it dispatches to.

    protected void writeData(BCValueOutput output) {
        output.store("battery", CompoundTag.CODEC, battery.serializeNBT());
        if (laserPos != null) {
            output.putDouble("laser_x", laserPos.x);
            output.putDouble("laser_y", laserPos.y);
            output.putDouble("laser_z", laserPos.z);
        }
        if (targetPos != null) {
            output.putInt("target_x", targetPos.getX());
            output.putInt("target_y", targetPos.getY());
            output.putInt("target_z", targetPos.getZ());
            output.putBoolean("has_target", true);
        }
        CompoundTag avgTag = new CompoundTag();
        avgPower.writeToNbt(avgTag, "average_power");
        output.store("avg_power", CompoundTag.CODEC, avgTag);
    }

    protected void readData(BCValueInput input) {
        input.read("battery", CompoundTag.CODEC).ifPresent(batteryTag -> battery.deserializeNBT(batteryTag));
        if (input.getBooleanOr("has_target", false)) {
            targetPos = new BlockPos(
                input.getIntOr("target_x", 0),
                input.getIntOr("target_y", 0),
                input.getIntOr("target_z", 0)
            );
        } else {
            targetPos = null;
        }
        double lx = input.getDoubleOr("laser_x", Double.NaN);
        if (!Double.isNaN(lx)) {
            laserPos = new Vec3(
                lx,
                input.getDoubleOr("laser_y", 0),
                input.getDoubleOr("laser_z", 0)
            );
        }
        input.read("avg_power", CompoundTag.CODEC)
            .ifPresent(tag -> avgPower.readFromNbt(tag, "average_power"));
        averageClient = (long) avgPower.getAverage();
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

    // --- Lifecycle ---

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            buildcraft.silicon.client.RenderLaser.addLaser(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            LocalBlockUpdateNotifier.instance(level).removeSubscriberFromUpdateNotifications(this);
        }
        if (level != null && level.isClientSide()) {
            buildcraft.silicon.client.RenderLaser.removeLaser(this);
        }
    }

    private boolean registered = false;
    private void ensureRegistered() {
        if (!registered && level != null && !level.isClientSide()) {
            LocalBlockUpdateNotifier.instance(level).registerSubscriberForUpdateNotifications(this);
            registered = true;
        }
    }

    // --- Debug ---

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("battery = " + battery.getStored() + " / " + battery.getCapacity());
        left.add("target = " + targetPos);
        left.add("laser = " + laserPos);
        left.add("average = " + averageClient);
        if (level != null && level.isClientSide()) {
            left.add("active_lasers = " + buildcraft.silicon.client.RenderLaser.getActiveCount());
        }
    }

    @Override
    public Component getAdvDebugMessage() {
        return Component.translatable("chat.debugger.laser");
    }
}
