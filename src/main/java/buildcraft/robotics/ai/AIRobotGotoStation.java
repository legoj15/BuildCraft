/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.NBTUtilBC;

/** Flies the robot to a docking station and mounts it: {@link AIRobotGotoBlock} to the face-adjacent block, then
 *  {@link AIRobotStraightMoveTo} to the exact dock point, then {@code dock()}. The station is reserved first
 *  through {@code station.take} so two robots never race for the same mount. The station's position+side
 *  serialise as an int[3] pos and a side byte (Ph3 Decision 5 shape) rather than 7.1.x's {@code BlockIndex}. */
public class AIRobotGotoStation extends AIRobot {

    private BlockPos stationIndex;
    private Direction stationSide;

    public AIRobotGotoStation(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotGotoStation(IRobotAccess iRobot, DockingStation station) {
        this(iRobot);

        if (station != null) {
            stationIndex = station.getPos();
            stationSide = station.side();
        }
        setSuccess(false);
    }

    @Override
    public void start() {
        // A stationless robot (summoned, or a board whose linked station vanished) must fail the move
        // rather than NPE on a null station — {@code AIRobotGotoSleep} hands getLinkedStation() straight
        // here. 7.1.x assumed every robot has a main station; that assumption no longer holds.
        if (stationIndex == null) {
            setSuccess(false);
            terminate();
            return;
        }
        DockingStation station = robot.getRegistry().getStation(stationIndex, stationSide);

        if (station == null) {
            terminate();
        } else if (station == robot.getDockingStation()) {
            setSuccess(true);
            terminate();
        } else {
            // take() needs the concrete entity; a live GotoStation only ever drives a real robot, so the cast
            // is safe. Abstracting take() onto IRobotAccess would leak entity lifecycle into the AI surface.
            if (station.take((EntityRobotBase) robot)) {
                startDelegateAI(new AIRobotGotoBlock(robot,
                        stationIndex.getX() + stationSide.getStepX(),
                        stationIndex.getY() + stationSide.getStepY(),
                        stationIndex.getZ() + stationSide.getStepZ()));
            } else {
                terminate();
            }
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        DockingStation station = robot.getRegistry().getStation(stationIndex, stationSide);

        if (station == null) {
            terminate();
        } else if (ai instanceof AIRobotGotoBlock) {
            if (ai.success()) {
                startDelegateAI(new AIRobotStraightMoveTo(robot,
                        stationIndex.getX() + 0.5F + stationSide.getStepX() * 0.5F,
                        stationIndex.getY() + 0.5F + stationSide.getStepY() * 0.5F,
                        stationIndex.getZ() + 0.5F + stationSide.getStepZ() * 0.5F));
            } else {
                terminate();
            }
        } else {
            setSuccess(true);
            if (stationSide.getStepY() == 0) {
                robot.aimItemAt(new BlockPos(stationIndex.getX() + 2 * stationSide.getStepX(),
                        stationIndex.getY(), stationIndex.getZ() + 2 * stationSide.getStepZ()));
            } else {
                robot.aimItemAt(Mth.floor(robot.getAimYaw() / 90f) * 90f + 180f, robot.getAimPitch());
            }
            robot.dock(station);
            terminate();
        }
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        nbt.putIntArray("stationIndex",
                new int[] { stationIndex.getX(), stationIndex.getY(), stationIndex.getZ() });
        nbt.putByte("stationSide", (byte) stationSide.ordinal());
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        int[] pos = NBTUtilBC.getIntArray(nbt, "stationIndex", new int[0]);
        if (pos.length == 3) {
            stationIndex = new BlockPos(pos[0], pos[1], pos[2]);
        }
        // Bounds-check the side byte: a corrupt or pre-1.13 save could hold an out-of-range ordinal, and the
        // AI must not crash the whole robot on load.
        int sideOrd = NbtApiUtil.getByte(nbt, "stationSide", (byte) 0) & 0xFF;
        if (sideOrd >= 0 && sideOrd < Direction.values().length) {
            stationSide = Direction.values()[sideOrd];
        } else {
            stationSide = Direction.NORTH;
        }
    }
}
