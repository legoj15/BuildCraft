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

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.crops.CropManager;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** Plants the held seed at {@code blockFound} through the
 *  {@link buildcraft.api.crops.CropManager} (sustainability check + crop placement via a fake player).
 *  Ported from 7.1.x {@code AIRobotPlant}.
 *
 *  <p>7.1.x dropped whatever was still in the hand after the attempt (the seed stack's remainder) at the
 *  robot's feet and cleared the hand, so the next cycle re-fetches a seed; that is kept. */
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
                // hand itself before the useOn.
                FakePlayer player = BuildCraftAPI.fakePlayerProvider.getBuildCraftPlayer(serverLevel);
                player.setPos(robot.position());
                ItemStack held = robot.getHeldItem();
                if (!CropManager.plantCrop(serverLevel, player, held, blockFound)) {
                    setSuccess(false);
                }
                if (!held.isEmpty()) {
                    serverLevel.addFreshEntity(new ItemEntity(serverLevel,
                            robot.position().x, robot.position().y, robot.position().z, held));
                }
                robot.setItemInUse(ItemStack.EMPTY);
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
