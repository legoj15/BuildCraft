/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.ai.AIRobotFetchAndEquipItemStack;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.robotics.ai.AIRobotUseToolOnBlock;
import buildcraft.robotics.path.IBlockFilter;

/** The farmer: keeps a hoe equipped (fetching one from the station when none is held), searches for
 *  {@code "dirt"} with air above it, then tills it with {@code AIRobotUseToolOnBlock}. Ported from
 *  7.1.x {@code BoardRobotFarmer} (7.1.x's tool check was {@code ItemHoe}; the modern equivalent is
 *  {@link RobotToolPredicates#isHoe}).
 *
 *  <p>One divergence from 7.1.x: the fetch filter additionally refuses a WORN hoe
 *  ({@code damage >= maxDamage}) — 7.1.x would fetch a broken hoe and then spin forever, since a broken
 *  tool's failed till keeps the (damageable) item in the hand. Same guard the break-board base carries. */
public class BoardRobotFarmer extends RedstoneBoardRobot {

    private BlockPos blockFound;

    public BoardRobotFarmer(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotFarmerNBT.INSTANCE;
    }

    /** The hoe predicate: vanilla hoes by identity plus anything the {@code c:tools/hoe} tag admits. */
    public boolean isExpectedHoe(ItemStack stack) {
        return RobotToolPredicates.isHoe(stack);
    }

    @Override
    public void update() {
        if (robot.getHeldItem().isEmpty()) {
            startDelegateAI(new AIRobotFetchAndEquipItemStack(robot,
                    stack -> !stack.isEmpty() && stack.getDamageValue() < stack.getMaxDamage()
                            && isExpectedHoe(stack)));
        } else {
            Level level = robot.level();
            IBlockFilter blockFilter = pos -> level != null
                    && BuildCraftAPI.getWorldProperty("dirt").get(level, pos)
                    && level.getBlockState(pos.above()).isAir()
                    && !robot.getRegistry().isTaken(new ResourceIdBlock(pos));
            startDelegateAI(new AIRobotSearchAndGotoBlock(robot, false, blockFilter));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoBlock searchAndGoto) {
            if (searchAndGoto.success()) {
                blockFound = searchAndGoto.getBlockFound();
                startDelegateAI(new AIRobotUseToolOnBlock(robot, blockFound));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotFetchAndEquipItemStack) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotUseToolOnBlock) {
            releaseBlockFound();
        }
    }

    @Override
    public void end() {
        releaseBlockFound();
    }

    protected BlockPos blockFound() {
        return blockFound;
    }

    private void releaseBlockFound() {
        if (blockFound != null) {
            robot.getRegistry().release(new ResourceIdBlock(blockFound));
            blockFound = null;
        }
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
