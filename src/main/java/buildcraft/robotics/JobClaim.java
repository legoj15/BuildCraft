/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.IRobotRegistry;

/** The claim latch of one job a robot works - the block a board or a search-and-goto targets, the order a delivery
 *  board fills, the station a goto flies to - shared by every board and AI that holds one, so they all follow one
 *  rule:
 *
 *  <ul>
 *  <li>A job is <b>held</b> when it was claimed in this session: by the search that found it ({@link #adopt} with
 *      {@code held}) or by a re-claim. It is merely <b>remembered</b> when it was read back from NBT ({@link #restore}:
 *      the unload that saved it freed every claim), reported by a search that did not claim it, or kept across an
 *      abort that gave its claims back ({@link #releaseClaims}).</li>
 *  <li>Before a remembered job is worked, the owner's {@code preempt} calls {@link #preempt}, which re-claims it
 *      (owner-checked: free, or still this robot's) or - when another robot holds it - forgets it and aborts the
 *      owner's delegate, so two robots never work one job. Without a registry (a client-side robot) nothing can be
 *      lost to anyone, and the job is kept.</li>
 *  <li>The job ends through {@link #release}: the claim goes back, owner-checked, and the job is forgotten.</li>
 *  </ul>
 *
 *  <p>What a reload does with the job is each owner's explicit choice ({@link OnReload}). Not 7.1.x, whose boards
 *  re-used a remembered job unreserved after a reload. */
public final class JobClaim<J> {

    /** What a reload does with a remembered job. */
    public enum OnReload {
        /** Saved; a remembered job (reloaded, or given back with {@link #releaseClaims}) is re-claimed on the owner's
         *  very next {@code preempt}, delegate or not - for an owner whose own {@code update()} goes back to work on
         *  it (the break boards resume the break, the delivery board the load or the delivery, the search-and-goto
         *  reports its block). So a release lasts at most until the owner's next cycle. */
        RECLAIM,
        /** Saved; a reloaded job is re-claimed only while a resumed action AI is working it. With none, nothing
         *  would work it - the owner's {@code update()} starts a fresh search - so it is forgotten instead of held
         *  for nobody (the farmer, planter and pump). */
        RECLAIM_WHILE_WORKING,
        /** Never saved: the work on the job cannot resume (the stripes handler), so a reload simply forgets it. */
        FORGET
    }

    /** How a job is claimed and given back. */
    public interface Claimer<J> {
        /** Owner-checked re-claim: true when the robot holds the job on return. */
        boolean reclaim(IRobotAccess robot, IRobotRegistry registry, J job);

        /** Owner-checked release: never drops a claim another robot holds. */
        void release(IRobotAccess robot, IRobotRegistry registry, J job);
    }

    /** A block job: the job ledger's {@code ResourceIdBlock} claim, through {@link BlockClaims} (so a re-claim is
     *  refused while another robot's search holds the cell). */
    private static final Claimer<BlockPos> BLOCK = new Claimer<>() {
        @Override
        public boolean reclaim(IRobotAccess robot, IRobotRegistry registry, BlockPos pos) {
            return BlockClaims.claim(registry, pos, robot.getRobotId());
        }

        @Override
        public void release(IRobotAccess robot, IRobotRegistry registry, BlockPos pos) {
            BlockClaims.release(registry, pos, robot.getRobotId());
        }
    };

    /** A plain (visit) claim on a docking station - what a goto takes for its flight and a delivery order takes on its
     *  requester. The take is owner-checked: true when the station was free, or is this robot's already (its home,
     *  its dock or a visit), which is left as it was. The release gives back only a plain claim this robot holds
     *  that may go: never its home, and not while it is docked there ({@code canRelease}), since that claim is the
     *  robot's dock now and undocking is what frees it.
     *
     *  <p>{@code take()} needs the concrete entity: a live board or AI only ever drives a real robot, so the cast is
     *  safe; abstracting it onto {@link IRobotAccess} would leak entity lifecycle into the AI surface. */
    public static final Claimer<DockingStation> STATION_VISIT = new Claimer<>() {
        @Override
        public boolean reclaim(IRobotAccess robot, IRobotRegistry registry, DockingStation station) {
            return station.take((EntityRobotBase) robot);
        }

        @Override
        public void release(IRobotAccess robot, IRobotRegistry registry, DockingStation station) {
            if (station.robotIdTaking() == robot.getRobotId() && station.canRelease()) {
                station.release((EntityRobotBase) robot);
            }
        }
    };

    /** A station job by value, resolved against the registry on every claim ({@link #station}). A station the robot
     *  already held when it was claimed - its home, or a plain claim a parent AI took first - outlives the claim:
     *  only one this latch took is given back. One per latch, since it remembers which station that was. */
    private static final class StationByIndex implements Claimer<StationIndex> {
        /** The station this latch's claim took, or null when the robot held it already (or nothing is taken). */
        @Nullable
        private DockingStation taken;

        @Override
        public boolean reclaim(IRobotAccess robot, IRobotRegistry registry, StationIndex index) {
            DockingStation station = registry.getStation(index.index(), index.side());
            if (station == null) {
                return false;
            }
            boolean heldBefore = station.robotIdTaking() == robot.getRobotId();
            if (!STATION_VISIT.reclaim(robot, registry, station)) {
                return false;
            }
            taken = heldBefore ? null : station;
            return true;
        }

        @Override
        public void release(IRobotAccess robot, IRobotRegistry registry, StationIndex index) {
            DockingStation station = taken;
            taken = null;
            if (station != null) {
                STATION_VISIT.release(robot, registry, station);
            }
        }
    }

    private final IRobotAccess robot;
    private final OnReload onReload;
    private final Claimer<J> claimer;

    @Nullable
    private J job;
    /** Whether {@link #job} is claimed in this session. Never saved, by design. */
    private boolean held;

    public JobClaim(IRobotAccess robot, OnReload onReload, Claimer<J> claimer) {
        this.robot = robot;
        this.onReload = onReload;
        this.claimer = claimer;
    }

    /** A claim on a block, the registry's {@code ResourceIdBlock}. */
    public static JobClaim<BlockPos> block(IRobotAccess robot, OnReload onReload) {
        return new JobClaim<>(robot, onReload, BLOCK);
    }

    /** A plain (visit) claim on the docking station at an index, the claim a goto holds for its flight: taken with
     *  {@link #STATION_VISIT}'s owner-checked take, and given back only when this latch took it - a station the
     *  robot already held (its home, a parent's claim) is left as it was. {@link #promoteToHome} is its take-as-main
     *  variant. */
    public static JobClaim<StationIndex> station(IRobotAccess robot, OnReload onReload) {
        return new JobClaim<>(robot, onReload, new StationByIndex());
    }

    /**
     * The take-as-main variant of a station claim, for a goto that is to make the station the robot's home once it
     * gets there: the station it just reached becomes its home and the old home is let go ({@code takeAsMain}). The
     * flight held the station as a plain claim, which {@code takeAsMain} would read as "already this robot's" and
     * leave plain, so that claim is given back first; a station that already is this robot's home has nothing to
     * promote. Either way the latch forgets the visit: a home is not a visit's to give back. False, with the latch
     * left as it was, when the robot no longer holds the station at all.
     */
    public static boolean promoteToHome(JobClaim<StationIndex> claim, DockingStation station) {
        long robotId = claim.robot.getRobotId();
        if (station.robotIdTaking() != robotId) {
            return false;
        }
        claim.adopt(null, false);
        if (station.isMainStation()) {
            return true;
        }
        EntityRobotBase self = (EntityRobotBase) claim.robot;
        station.release(self);
        return station.takeAsMain(self);
    }

    @Nullable
    public J job() {
        return job;
    }

    public boolean isEmpty() {
        return job == null;
    }

    /** Whether the robot holds the job's claim in this session - what an owner taking the job over may rely on. */
    public boolean isHolding() {
        return job != null && held;
    }

    /** Takes over a job someone else found: {@code held} says whether its claim was taken in this session (a
     *  search-and-goto reports that through {@code holdsReservation()}); an unheld one is re-claimed by
     *  {@link #preempt} before it is worked. */
    public void adopt(@Nullable J job, boolean held) {
        this.job = job;
        this.held = job != null && held;
    }

    /** Claims {@code job} now (owner-checked) and holds it: for an owner that picks its own job rather than taking
     *  over a search's. False when the claim is refused. Without a registry nothing can be lost to anyone, and the
     *  job is taken as held.
     *
     *  <p>A job this latch already holds is never overwritten with its claim still out: claiming it again changes
     *  nothing (a second take would, for a station visit, read the first as the robot's own and stop giving it
     *  back), and claiming another job gives the held one back <em>first</em> - before the new take, since a claimer
     *  that remembers what it took ({@link #station}) would lose the old one when the new take overwrites its
     *  record. So a refusal has already cost the held job its claim: it is taken straight back, and the latch is
     *  left as it was. Only if that fails too (another robot got in between) is the job left remembered, unheld,
     *  for {@link #preempt} to resolve like any other. */
    public boolean claim(J job) {
        IRobotRegistry registry = robot.getRegistry();
        if (registry == null) {
            adopt(job, true);
            return true;
        }
        J previous = this.job;
        boolean releasedPrevious = false;
        if (previous != null && held) {
            if (previous.equals(job)) {
                return true;
            }
            claimer.release(robot, registry, previous);
            held = false;
            releasedPrevious = true;
        }
        if (!claimer.reclaim(robot, registry, job)) {
            if (releasedPrevious) {
                held = claimer.reclaim(robot, registry, previous);
            }
            return false;
        }
        adopt(job, true);
        return true;
    }

    /** Remembers a job read back from NBT, unheld: the unload that saved it freed its claim. Ignored for
     *  {@link OnReload#FORGET}. */
    public void restore(@Nullable J job) {
        if (onReload != OnReload.FORGET) {
            adopt(job, false);
        }
    }

    /** Whether the job is to be saved (there is one, and the owner keeps jobs across a reload). */
    public boolean shouldSave() {
        return job != null && onReload != OnReload.FORGET;
    }

    /** The owner's {@code preempt}: a remembered job is re-claimed before {@code delegate} (the owner's running
     *  delegate, or null) touches it. Returns true when the job was lost - another robot holds it, or (for
     *  {@link OnReload#RECLAIM_WHILE_WORKING}) nothing is working it - in which case it is forgotten and the owner's
     *  delegate is aborted. */
    public boolean preempt(AIRobot owner, @Nullable AIRobot delegate) {
        if (job == null || held) {
            return false;
        }
        IRobotRegistry registry = robot.getRegistry();
        if (registry == null) {
            return false;
        }
        boolean worked = delegate != null || onReload == OnReload.RECLAIM;
        if (worked && claimer.reclaim(robot, registry, job)) {
            held = true;
            return false;
        }
        job = null;
        owner.abortDelegateAI();
        return true;
    }

    /** Gives the claim back (owner-checked) and forgets the job. */
    public void release() {
        releaseClaims();
        job = null;
    }

    /** Gives the claim back (owner-checked) but keeps the job, unheld, for {@link #preempt} to take back - for an
     *  owner aborted mid-job (a recharge) whose same instance resumes afterwards, or one whose next AI releases every
     *  reservation anyway (the delivery board's wait for an unloaded requester). With {@link OnReload#RECLAIM} the
     *  owner's next cycle takes it back. */
    public void releaseClaims() {
        if (job != null) {
            IRobotRegistry registry = robot.getRegistry();
            if (registry != null) {
                claimer.release(robot, registry, job);
            }
        }
        held = false;
    }

    /** Saves a block job under {@code key} (nothing for {@link OnReload#FORGET}, or with no job). */
    public static void writeBlock(JobClaim<BlockPos> claim, CompoundTag nbt, String key) {
        if (claim.shouldSave()) {
            BlockPos pos = claim.job;
            nbt.putIntArray(key, new int[] { pos.getX(), pos.getY(), pos.getZ() });
        }
    }

    /** Restores a block job saved under {@code key}, unheld. */
    public static void readBlock(JobClaim<BlockPos> claim, CompoundTag nbt, String key) {
        int[] arr = NbtApiUtil.getIntArray(nbt, key, null);
        if (arr != null && arr.length == 3) {
            claim.restore(new BlockPos(arr[0], arr[1], arr[2]));
        }
    }
}
