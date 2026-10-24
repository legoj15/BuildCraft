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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.crops.CropManager;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.robotics.RobotProtection;

/** Plants the held seed at {@code blockFound} through the
 *  {@link buildcraft.api.crops.CropManager} (sustainability check + crop placement via a fake player).
 *  Ported from 7.1.x {@code AIRobotPlant}.
 *
 *  <p>After a planting that took, 7.1.x dropped whatever was still in the hand (the seed stack's remainder) at the
 *  robot's feet and cleared the hand, so the next cycle re-fetches a seed; that is kept. After one that did not take
 *  7.1.x did the same, and the planter then fetched a seed to try the same spot again; here the robot keeps the seeds
 *  and the planting reports failure. */
public class AIRobotPlant extends AIRobot {

    private BlockPos blockFound;
    private int delay = 0;

    public AIRobotPlant(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotPlant(IRobotAccess iRobot, BlockPos iBlockFound) {
        super(iRobot);

        blockFound = iBlockFound;
    }

    @Override
    public void start() {
        if (blockFound != null) {
            robot.aimItemAt(blockFound);
            robot.setItemActive(true);
        }
    }

    @Override
    public void update() {
        if (blockFound == null) {
            setSuccess(false);
            terminate();
            return;
        }

        if (delay++ > 40) {
            if (robot.level() instanceof ServerLevel serverLevel) {
                // The fake player stands where the robot stands; the handler puts the seed in its main
                // hand itself before the useOn, and closing the lease takes it back out. It is the robot's
                // deployer (see RobotProtection), so a claim mod judges the deployer through the planting's own
                // right-click and placement events, and a planting advancement reaches them. Those events say
                // nothing for the home station's owner, who is asked first with the placement probe for the
                // crop's cell, the act the planting is.
                ItemStack held = robot.getHeldItem();
                boolean planted = false;
                if (RobotProtection.stationOwnerCanPlace(robot, serverLevel, blockFound.above())) {
                    try (FakePlayerUtil.Lease lease = RobotProtection.lease(robot, serverLevel)) {
                        FakePlayer player = lease.player();
                        player.setPos(robot.position());
                        planted = CropManager.plantCrop(serverLevel, player, held, blockFound);
                    }
                }
                if (planted) {
                    if (!held.isEmpty()) {
                        serverLevel.addFreshEntity(new ItemEntity(serverLevel,
                                robot.position().x, robot.position().y, robot.position().z, held));
                    }
                    robot.setItemInUse(ItemStack.EMPTY);
                } else {
                    // Nothing was planted (a claim mod refused the right-click or the placement, for the deployer or
                    // the station's owner, or the soil changed since the search), so the seed was not used: the robot
                    // keeps it, and the board remembers the spot instead of fetching a fresh seed to be refused again.
                    setSuccess(false);
                }
            } else {
                setSuccess(false);
            }
            terminate();
        }
    }

    @Override
    public void end() {
        robot.setItemActive(false);
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (blockFound != null) {
            int[] arr = {blockFound.getX(), blockFound.getY(), blockFound.getZ()};
            nbt.putIntArray("blockFound", arr);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        int[] arr = NbtApiUtil.getIntArray(nbt, "blockFound", null);
        if (arr != null && arr.length == 3) {
            blockFound = new BlockPos(arr[0], arr[1], arr[2]);
        }
    }
}
