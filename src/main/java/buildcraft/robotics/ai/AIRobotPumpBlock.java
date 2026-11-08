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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;


import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.fluids.BCFluidStack;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.lib.transfer.FluidHandlerAdvs;
import buildcraft.robotics.RobotProtection;

/** Pumps a bucket of fluid out of the source block at {@code blockToPump} (through the in-tree
 *  {@code BlockUtil.drainBlock}) into the robot's tank. Ported from 7.1.x {@code AIRobotPumpBlock}
 *  (7.1.x pumped through the robot's fluid transactor; the modern equivalent is
 *  {@link IRobotAccess#getFluidHandler()}).
 *
 *  <p>7.1.x's pump simulated the fill and then drained the block WITHOUT actually filling the robot —
 *  and never set its {@code pumped} counter, so its {@code success()} was permanently false. The pump
 *  board ignored the success (it just released the block and re-searched), so the bug was inert; this
 *  port tanks the bucket for real and reports it, per the design doc. One bucket per pass: a partially
 *  full tank (room &lt; 1000) fails the pass, and the board's tank check routes the robot to unload first.
 *
 *  <p>Before the world changes, the pass asks protection mods ({@link RobotProtection#canBreak}, for the robot's
 *  deployer and its home station's owner alike) about the one block it was sent to. A refusal — from a mod, or
 *  from a block that reports a source yet refuses its pickup — is reported through {@link #refused()} so the board can stop sending the robot there.
 *
 *  <p>The pass never loads a chunk: taking the bucket updates the cells around the block, out to
 *  {@link BlockUtil#DRAIN_UPDATE_REACH}, and a read in a chunk that is not in memory would load (or generate) it on the
 *  server thread, so a block whose own chunk, or one within that reach, is absent is left alone
 *  ({@link BlockUtil#drainStaysInLoadedChunks}, as the pump block does). The pass then pumps nothing and is not
 *  {@link #refused()}: the chunk can load later, and the board's search does not offer such a block in the first place. */
public class AIRobotPumpBlock extends AIRobot {

    private static final int BUCKET = 1000;

    private BlockPos blockToPump;
    private long waited = 0;
    /** mB moved on the completing cycle — package-private so the Ph9 action-AI game test can pin the
     *  exactly-one-bucket contract. */
    int pumped = 0;
    /** Whether a protection mod (or the block's own pickup) refused to give the block up; see {@link #refused()}. */
    private boolean refused = false;
    private boolean protectionRefused = false;

    public AIRobotPumpBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotPumpBlock(IRobotAccess iRobot, BlockPos iBlockToPump) {
        super(iRobot);

        blockToPump = iBlockToPump;
    }

    @Override
    public void start() {
        if (blockToPump != null) {
            robot.aimItemAt(blockToPump);
        }
    }

    @Override
    public void update() {
        if (waited < 40) {
            waited++;
            return;
        }

        if (blockToPump != null && robot.level() instanceof ServerLevel serverLevel
                && BlockUtil.drainStaysInLoadedChunks(serverLevel, blockToPump, null)) {
            BCFluidStack preview = BlockUtil.drainBlock(serverLevel, blockToPump, false);
            if (preview != null) {
                BCFluidStorage tank = FluidHandlerAdvs.storage(robot.getFluidHandler());
                int room = tank.getCapacity(0, preview) - tank.getAmount(0);
                if (room >= BUCKET && isPermitted(serverLevel)) {
                    // The robot's tank journals its changes, and a journalling storage REQUIRES a live
                    // transaction (a bare insert threw NPE on every pump cycle, a production bug the Ph9 pump
                    // game test caught). Commit only when the bucket actually moved AND the world gave it up
                    // (a pickup can refuse; committing first would mint fluid), so the tank and the world
                    // change together.
                    try (BCTransaction tx = BCTransaction.openRoot()) {
                        int inserted = tank.insert(0, preview, BUCKET, tx);
                        if (inserted >= BUCKET) {
                            if (BlockUtil.drainBlock(serverLevel, blockToPump, true) != null) {
                                tx.commit();
                                pumped = BUCKET;
                            } else {
                                refused = true;
                            }
                        }
                    }
                }
            }
        }
        terminate();
    }

    @Override
    public boolean success() {
        return pumped > 0;
    }

    /** The block this AI was sent to pump (null for an AI that was never given one). */
    public BlockPos blockToPump() {
        return blockToPump;
    }

    /**
     * True when this pass failed because the block would not be given up, not because the robot was full or the
     * block was already gone: a claim/protection mod refused the pump (asked for the deployer and the home station's
     * owner, once the robot has room for the bucket), or the block reports a source yet refused its bucket pickup. The pump board
     * remembers such a block so it does not send the robot straight back to it.
     */
    public boolean refused() {
        return refused;
    }

    /** Whether the {@link #refused()} came from a protection mod, rather than the block's own pickup. */
    public boolean refusedByProtection() {
        return protectionRefused;
    }

    /** Asks protection mods whether this robot may take fluid from the block, as {@link RobotProtection} does for
     *  every robot that changes the world; only the block this pass actually targets is asked about. A refusal is
     *  recorded for {@link #refused()}. */
    private boolean isPermitted(ServerLevel serverLevel) {
        if (RobotProtection.canBreak(robot, serverLevel, blockToPump)) {
            return true;
        }
        refused = true;
        protectionRefused = true;
        return false;
    }

    @Override
    public long getPowerCost() {
        // Ph5 cost table: 5 RF per tick, at the 1 RF = 100_000 micro-MJ bridge; 7.1.x left this AI on
        // the base default cost (no getPowerCost override in the 7.1.x source).
        return 500_000;
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (blockToPump != null) {
            int[] arr = {blockToPump.getX(), blockToPump.getY(), blockToPump.getZ()};
            nbt.putIntArray("blockToPump", arr);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        int[] arr = NbtApiUtil.getIntArray(nbt, "blockToPump", null);
        if (arr != null && arr.length == 3) {
            blockToPump = new BlockPos(arr[0], arr[1], arr[2]);
        }
    }
}
