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
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.robotics.JobClaim;
import buildcraft.robotics.StationIndex;

/** Flies the robot to a docking station and mounts it: {@link AIRobotGotoBlock} to the face-adjacent block, then
 *  {@link AIRobotStraightMoveTo} to the exact dock point, then {@code dock()}. The station is reserved first
 *  through {@code station.take} so two robots never race for the same mount; a take-as-main goto promotes that
 *  claim to the robot's home only on docking. The station's position+side
 *  serialise as an int[3] pos and a side byte (Ph3 Decision 5 shape) rather than 7.1.x's {@code BlockIndex}. */
public class AIRobotGotoStation extends AIRobot {

    private BlockPos stationIndex;
    private Direction stationSide;
    private boolean takeAsMain;
    /** The plain (visit) claim on the station ({@link JobClaim#station}): held once {@link #start} took it, or once
     *  {@link #preempt} took it again for a goto reloaded mid-flight - the claim itself is not saved, the unload that
     *  saved the goto freed it. Only a claim this AI took is given back ({@link #end}): the robot's home and a claim a
     *  parent took first outlive it. */
    private final JobClaim<StationIndex> visit = JobClaim.station(robot, JobClaim.OnReload.RECLAIM);

    public AIRobotGotoStation(IRobotAccess iRobot) {
        super(iRobot);
        // A goto read back from NBT starts out failed, like a constructed one: it only succeeds by docking.
        setSuccess(false);
    }

    public AIRobotGotoStation(IRobotAccess iRobot, DockingStation station) {
        this(iRobot, station, false);
    }

    /** takeAsMain makes the station the robot's main station once the robot docks there, used when a gate's
     *  goto-station action (Ph6) redirects a docked robot to another station. Until then it is held as a plain claim
     *  and the old home stays the robot's, so a redirect that never arrives leaves the robot where it was homed. */
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

    /** The exact point the robot docks at: the station block's centre pushed half a block out along its side.
     *  Double arithmetic end to end: 7.1.x's 0.5F literals made this float maths, which drops the half-block face
     *  offset for any station past ~8.4M blocks out. */
    static Vec3 dockPoint(BlockPos stationPos, Direction side) {
        return new Vec3(stationPos.getX() + 0.5 + side.getStepX() * 0.5,
                stationPos.getY() + 0.5 + side.getStepY() * 0.5,
                stationPos.getZ() + 0.5 + side.getStepZ() * 0.5);
    }

    /** The block a robot is in once docked at the station at {@code stationPos} on {@code side}: where a flight
     *  that leaves the station starts from. */
    public static BlockPos dockBlock(BlockPos stationPos, Direction side) {
        return BlockPos.containing(dockPoint(stationPos, side));
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
            if (claim()) {
                BlockPos approach = approachCell(stationIndex, stationSide, takeAsMain);
                startDelegateAI(new AIRobotGotoBlock(robot,
                        approach.getX(), approach.getY(), approach.getZ()));
            } else {
                terminate();
            }
        }
    }

    /** Takes the station for this flight (owner-checked: free, or this robot's already). Always a plain claim, a
     *  take-as-main goto's included: the robot's home only moves once it has docked
     *  ({@link JobClaim#promoteToHome}).
     *
     *  <p>Not 7.1.x, whose {@code AIRobotGoAndLinkToDock} linked the new home at its start, releasing the old one: a
     *  redirect whose flight then failed (a target past {@link AIRobotGotoBlock#FLIGHT_REACH}, or walled in) left the
     *  robot homed on a station it never reached and its old home free for any other robot. */
    private boolean claim() {
        return visit.claim(new StationIndex(stationSide, stationIndex));
    }

    /** A flight reloaded from NBT resumes its approach leg, but the unload freed the plain claim it held. Take the
     *  station again before the leg moves; if another robot took it meanwhile (or it is gone), fail the flight rather
     *  than dock beside that robot. The leg is aborted, so this AI's {@code update()} ends it, unsuccessful. Not 7.1.x,
     *  which flew on unclaimed. */
    @Override
    public void preempt(AIRobot ai) {
        if (ai == null || stationIndex == null) {
            return;
        }
        if (visit.preempt(this, ai)) {
            setSuccess(false);
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
                Vec3 dock = dockPoint(stationIndex, stationSide);
                startDelegateAI(new AIRobotStraightMoveTo(robot, dock.x, dock.y, dock.z));
            } else {
                // Explicit, not left to the constructor's starting value: a failed approach is a failed goto.
                setSuccess(false);
                terminate();
            }
        } else {
            if (takeAsMain && !JobClaim.promoteToHome(visit, station)) {
                // The claim was lost under the flight (another robot cannot take a held station, so only a forced
                // release gets here): docking beside whoever holds it now is not this goto's to do.
                setSuccess(false);
                terminate();
                return;
            }
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
        visit.release();
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
        // Written only when set, so a plain visit saves exactly as it always did. Without it a take-as-main goto
        // reloaded as a plain visit (one-cell approach, a visit claim, the home left where it was): a gate redirect
        // saved while it waited behind a recharge then docked as a visit, and the robot flew back to its old home.
        if (takeAsMain) {
            nbt.putBoolean("takeAsMain", true);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        int[] pos = NBTUtilBC.getIntArray(nbt, "stationIndex", new int[0]);
        // Bounds-check the side byte: a corrupt or 7.1.x-era save could hold an out-of-range ordinal
        // (ForgeDirection.UNKNOWN was 6). Such a goto names no real station face, so it loads stationless and
        // fails at start, as DockingStation.readFromNbt and StackRequest.loadFromNBT refuse the same byte;
        // substituting a face would fly the robot to, and claim, whatever station sits there.
        Direction side = NBTUtilBC.getDirectionOrNull(nbt, "stationSide", (byte) 0);
        if (pos.length == 3 && side != null) {
            stationIndex = new BlockPos(pos[0], pos[1], pos[2]);
            stationSide = side;
            // Remembered, not held: the unload that saved this goto freed its claim, so preempt takes it again.
            visit.restore(new StationIndex(stationSide, stationIndex));
        }
        // Absent in every save written before the flag was kept: those read back as the plain visit they reloaded as.
        takeAsMain = NbtApiUtil.getBoolean(nbt, "takeAsMain", false);
    }
}
