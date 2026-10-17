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
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.lib.misc.NBTUtilBC;

/** Flies the robot to a docking station and mounts it: {@link AIRobotGotoBlock} to the face-adjacent block, then
 *  {@link AIRobotStraightMoveTo} to the exact dock point, then {@code dock()}. The station is reserved first
 *  through {@code station.take} so two robots never race for the same mount. The station's position+side
 *  serialise as an int[3] pos and a side byte (Ph3 Decision 5 shape) rather than 7.1.x's {@code BlockIndex}. */
public class AIRobotGotoStation extends AIRobot {

    private BlockPos stationIndex;
    private Direction stationSide;
    private boolean takeAsMain;
    /** The station this AI took a plain (visit) claim on in {@link #start}, or null when it took none. */
    private DockingStation visitClaim;
    /** Whether this AI holds its station in this session. Not saved: the unload that saved a mid-flight goto freed a
     *  plain claim, so a reloaded goto has to take the station again ({@link #preempt}) before it may dock. */
    private boolean taken;

    public AIRobotGotoStation(IRobotAccess iRobot) {
        super(iRobot);
        // A goto read back from NBT starts out failed, like a constructed one: it only succeeds by docking.
        setSuccess(false);
    }

    public AIRobotGotoStation(IRobotAccess iRobot, DockingStation station) {
        this(iRobot, station, false);
    }

    /** takeAsMain reserves the station as the robot's main station, used when a gate's goto-station
     *  action (Ph6) redirects a docked robot to another station. */
    public AIRobotGotoStation(IRobotAccess iRobot, DockingStation station, boolean takeAsMain) {
        this(iRobot);

        this.takeAsMain = takeAsMain;
        if (station != null) {
            stationIndex = station.getPos();
            stationSide = station.side();
        }
        setSuccess(false);
    }

    /** The cell the robot pathfinds to before its final straight move onto the dock: one cell out along
     *  the station's face for a plain visit, TWO for a take-as-main link.
     *
     *  <p>7.1.x split these across two classes — {@code AIRobotGotoStation} flew to {@code station + side},
     *  {@code AIRobotGoAndLinkToDock} (the take-as-main equivalent) to {@code station + side*2}. Merging
     *  them behind the {@code takeAsMain} flag lost the second cell of run-up; it is restored here. The
     *  trade-off is 7.1.x's own: a link needs two cells of clear air in front of the station, not one. */
    static BlockPos approachCell(BlockPos stationPos, Direction side, boolean takeAsMain) {
        return stationPos.relative(side, takeAsMain ? 2 : 1);
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
            if (claim(station)) {
                BlockPos approach = approachCell(stationIndex, stationSide, takeAsMain);
                startDelegateAI(new AIRobotGotoBlock(robot,
                        approach.getX(), approach.getY(), approach.getZ()));
            } else {
                terminate();
            }
        }
    }

    /** Takes {@code station} for this flight (owner-checked: free, or this robot's already). */
    private boolean claim(DockingStation station) {
        // take() needs the concrete entity; a live GotoStation only ever drives a real robot, so the cast
        // is safe. Abstracting take() onto IRobotAccess would leak entity lifecycle into the AI surface.
        boolean heldBefore = station.robotIdTaking() == robot.getRobotId();
        if (!(takeAsMain ? station.takeAsMain((EntityRobotBase) robot) : station.take((EntityRobotBase) robot))) {
            return false;
        }
        // Only a plain claim this AI made is its to give back: the home link (a take-as-main goto's, or the robot's
        // own station it flies home to) and a claim a parent took first outlive it.
        visitClaim = !takeAsMain && !heldBefore ? station : null;
        taken = true;
        return true;
    }

    /** A flight reloaded from NBT resumes its approach leg, but the unload freed the plain claim it held. Take the
     *  station again before the leg moves; if another robot took it meanwhile (or it is gone), fail the flight rather
     *  than dock beside that robot. The leg is aborted, so this AI's {@code update()} ends it, unsuccessful. Not 7.1.x,
     *  which flew on unclaimed. */
    @Override
    public void preempt(AIRobot ai) {
        if (taken || ai == null || stationIndex == null) {
            return;
        }
        IRobotRegistry registry = robot.getRegistry();
        if (registry == null) {
            return;
        }
        DockingStation station = registry.getStation(stationIndex, stationSide);
        if (station == null || !claim(station)) {
            setSuccess(false);
            abortDelegateAI();
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        // A goto reloaded from a save that carried no station can still have its approach leg reloaded under it;
        // there is nothing to fly onto, and the registry's station key rejects a null position.
        if (stationIndex == null) {
            setSuccess(false);
            terminate();
            return;
        }
        DockingStation station = robot.getRegistry().getStation(stationIndex, stationSide);

        if (station == null) {
            terminate();
        } else if (ai instanceof AIRobotGotoBlock) {
            if (ai.success()) {
                // Double arithmetic end to end: 7.1.x's 0.5F literals made this float maths, which drops the
                // half-block face offset for any station past ~8.4M blocks out.
                startDelegateAI(new AIRobotStraightMoveTo(robot,
                        stationIndex.getX() + 0.5 + stationSide.getStepX() * 0.5,
                        stationIndex.getY() + 0.5 + stationSide.getStepY() * 0.5,
                        stationIndex.getZ() + 0.5 + stationSide.getStepZ() * 0.5));
            } else {
                // Explicit, not left to the constructor's starting value: a failed approach is a failed goto.
                setSuccess(false);
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

    /** Gives back the visit claim {@link #start} took when the robot did not end up docked at the station: an abort
     *  (the robot lives on, so no registry release runs) or a flight that failed. A robot that arrived keeps it, it
     *  is the dock now and undocking frees it. {@code release} is owner-checked and refuses a main station, so the
     *  robot's home link can never go through here. Not saved: a reloaded goto lost its claim to the unload, and
     *  {@link #preempt} records the one it takes again. */
    @Override
    public void end() {
        DockingStation station = visitClaim;
        visitClaim = null;
        if (station != null && station.robotIdTaking() == robot.getRobotId() && station.canRelease()) {
            station.release((EntityRobotBase) robot);
        }
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        // Stationless (a summoned robot, or one loaded from a save with no station): write nothing, which
        // loadSelfFromNBT reads back as stationless and start() fails cleanly.
        if (stationIndex == null) {
            return;
        }
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
