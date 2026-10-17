/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.robotics.JobClaim.OnReload;
import buildcraft.robotics.ai.MockRobotAccess;

/** The one claim latch every job-holding board and the search-and-goto AI share: a job is either claimed in this
 *  session (by the search that found it, or by a re-claim) or merely remembered (read back from NBT, or reported
 *  unreserved), and a remembered job is re-claimed before it is worked - or given up, with the work stopped. Driven
 *  through a recording {@link JobClaim.Claimer}, so the latch is pinned apart from any one registry. */
public class JobClaimTest extends VanillaSetupBaseTester {

    private static final BlockPos BLOCK = new BlockPos(3, 64, 3);

    /** Records every reclaim/release and answers reclaims with {@link #free}. */
    private static final class RecordingClaimer implements JobClaim.Claimer<String> {
        final List<String> calls = new ArrayList<>();
        boolean free = true;

        @Override
        public boolean reclaim(IRobotAccess robot, IRobotRegistry registry, String job) {
            calls.add("reclaim " + job);
            return free;
        }

        @Override
        public void release(IRobotAccess robot, IRobotRegistry registry, String job) {
            calls.add("release " + job);
        }
    }

    /** An owner AI with a running delegate, to see whether a lost claim stops the work. */
    private static AIRobot ownerWorking(IRobotAccess robot) {
        AIRobot owner = new AIRobot(robot);
        owner.startDelegateAI(new AIRobot(robot) {
            @Override
            public void start() {
            }
        });
        return owner;
    }

    @Test
    public void aJobClaimedThisSessionIsNeverReclaimed() {
        MockRobotAccess robot = new MockRobotAccess();
        RecordingClaimer claimer = new RecordingClaimer();
        JobClaim<String> claim = new JobClaim<>(robot, OnReload.RECLAIM, claimer);
        AIRobot owner = ownerWorking(robot);

        claim.adopt("job", true);
        claim.preempt(owner, owner.getDelegateAI());

        Assertions.assertTrue(claim.isHolding(), "the search's own claim is trusted");
        Assertions.assertTrue(claimer.calls.isEmpty(), "...and not asked about again - got " + claimer.calls);
    }

    @Test
    public void aRememberedJobIsReclaimedOnceThenTrusted() {
        MockRobotAccess robot = new MockRobotAccess();
        RecordingClaimer claimer = new RecordingClaimer();
        JobClaim<String> claim = new JobClaim<>(robot, OnReload.RECLAIM, claimer);
        AIRobot owner = ownerWorking(robot);

        claim.restore("job");
        Assertions.assertFalse(claim.isHolding(), "a job read back from NBT is remembered, not held");
        claim.preempt(owner, owner.getDelegateAI());
        claim.preempt(owner, owner.getDelegateAI());

        Assertions.assertTrue(claim.isHolding() && "job".equals(claim.job()), "a free job is claimed again");
        Assertions.assertEquals(List.of("reclaim job"), claimer.calls, "...once");
        Assertions.assertNotNull(owner.getDelegateAI(), "...and the work goes on");
    }

    @Test
    public void aJobAnotherRobotTookIsGivenUpAndItsWorkStopped() {
        MockRobotAccess robot = new MockRobotAccess();
        RecordingClaimer claimer = new RecordingClaimer();
        claimer.free = false;
        JobClaim<String> claim = new JobClaim<>(robot, OnReload.RECLAIM, claimer);
        AIRobot owner = ownerWorking(robot);

        claim.adopt("job", false);
        boolean lost = claim.preempt(owner, owner.getDelegateAI());

        Assertions.assertTrue(lost, "the preempt reports the job lost");
        Assertions.assertNull(claim.job(), "the job is forgotten");
        Assertions.assertNull(owner.getDelegateAI(), "...and the work on it stopped");
        Assertions.assertTrue(claim.isEmpty(), "nothing is left to release");
    }

    @Test
    public void aRememberedJobWithNothingWorkingItIsForgottenUnderReclaimWhileWorking() {
        MockRobotAccess robot = new MockRobotAccess();
        RecordingClaimer claimer = new RecordingClaimer();
        JobClaim<String> claim = new JobClaim<>(robot, OnReload.RECLAIM_WHILE_WORKING, claimer);
        AIRobot idle = new AIRobot(robot);

        claim.restore("job");
        claim.preempt(idle, null);

        Assertions.assertNull(claim.job(), "with no resumed action there is nothing to re-claim the job for");
        Assertions.assertTrue(claimer.calls.isEmpty(), "...so it is not claimed - got " + claimer.calls);
    }

    @Test
    public void aRememberedJobWithNothingWorkingItIsReclaimedUnderReclaim() {
        MockRobotAccess robot = new MockRobotAccess();
        RecordingClaimer claimer = new RecordingClaimer();
        JobClaim<String> claim = new JobClaim<>(robot, OnReload.RECLAIM, claimer);

        claim.restore("job");
        claim.preempt(new AIRobot(robot), null);

        Assertions.assertTrue(claim.isHolding(), "a board whose update() resumes the job claims it with no delegate");
    }

    @Test
    public void forgetIsNeitherSavedNorRestored() {
        MockRobotAccess robot = new MockRobotAccess();
        JobClaim<BlockPos> claim = JobClaim.block(robot, OnReload.FORGET);
        claim.adopt(BLOCK, true);

        CompoundTag nbt = new CompoundTag();
        JobClaim.writeBlock(claim, nbt, "blockFound");
        Assertions.assertFalse(nbt.contains("blockFound"), "a job that cannot resume is not saved");

        JobClaim<BlockPos> loaded = JobClaim.block(robot, OnReload.FORGET);
        CompoundTag saved = new CompoundTag();
        saved.putIntArray("blockFound", new int[] { BLOCK.getX(), BLOCK.getY(), BLOCK.getZ() });
        JobClaim.readBlock(loaded, saved, "blockFound");
        Assertions.assertNull(loaded.job(), "...nor read back from an older save that has it");
    }

    @Test
    public void aSavedBlockRoundTripsAsRememberedNotHeld() {
        MockRobotAccess robot = new MockRobotAccess();
        JobClaim<BlockPos> claim = JobClaim.block(robot, OnReload.RECLAIM_WHILE_WORKING);
        claim.adopt(BLOCK, true);
        CompoundTag nbt = new CompoundTag();
        JobClaim.writeBlock(claim, nbt, "blockFound");

        JobClaim<BlockPos> loaded = JobClaim.block(robot, OnReload.RECLAIM_WHILE_WORKING);
        JobClaim.readBlock(loaded, nbt, "blockFound");

        Assertions.assertEquals(BLOCK, loaded.job(), "the block survives the save");
        Assertions.assertFalse(loaded.isHolding(), "...but the claim does not: the unload freed it");
    }

    @Test
    public void releaseGivesBackAndForgetsWhileReleaseClaimsKeepsTheJob() {
        MockRobotAccess robot = new MockRobotAccess();
        RecordingClaimer claimer = new RecordingClaimer();
        JobClaim<String> claim = new JobClaim<>(robot, OnReload.RECLAIM, claimer);

        claim.adopt("job", true);
        claim.releaseClaims();
        Assertions.assertEquals("job", claim.job(), "releaseClaims keeps the job to take back on resume");
        Assertions.assertFalse(claim.isHolding(), "...but no longer holds it");

        claim.release();
        Assertions.assertNull(claim.job(), "release forgets the job");
        Assertions.assertEquals(List.of("release job", "release job"), claimer.calls, "both give the claim back");

        claim.release();
        Assertions.assertEquals(2, claimer.calls.size(), "an empty claim has nothing to release");
    }

    @Test
    public void withoutARegistryTheRememberedJobIsKept() {
        MockRobotAccess robot = new MockRobotAccess() {
            @Override
            public IRobotRegistry getRegistry() {
                return null;
            }
        };
        RecordingClaimer claimer = new RecordingClaimer();
        JobClaim<String> claim = new JobClaim<>(robot, OnReload.RECLAIM, claimer);

        claim.restore("job");
        Assertions.assertDoesNotThrow(() -> claim.preempt(new AIRobot(robot), null));
        Assertions.assertEquals("job", claim.job(), "no registry means nothing to lose the job to");
        Assertions.assertDoesNotThrow(claim::release, "...nor to release it to");
    }
}
