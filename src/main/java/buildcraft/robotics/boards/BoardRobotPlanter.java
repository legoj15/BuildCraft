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
import buildcraft.api.core.IStackFilter;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.crops.CropManager;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.ai.AIRobotFetchAndEquipItemStack;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotPlant;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.lib.inventory.filter.AggregateFilter;
import buildcraft.robotics.path.IBlockFilter;
import buildcraft.robotics.statements.ActionRobotFilter;

/** The planter: keeps a seed equipped (fetching one from the station when none is held), searches for
 *  a spot that is not {@code "replaceable"} and can sustain the planted seed, then plants it with
 *  {@code AIRobotPlant}. Ported from 7.1.x {@code BoardRobotPlanter} (the 7.1.x gate filter is Ph6,
 *  so the effective block filter is {@code !replaceable && canSustainPlant && !isTaken} — D1).
 *
 *  <p>7.1.x searched with {@code maxDistanceToEnd = 1} (the path may end on any soft block within one of
 *  the target) — kept: the planter does not need to stand on the exact cell before the seed spot. */
public class BoardRobotPlanter extends RedstoneBoardRobot {

    private BlockPos blockFound;

    public BoardRobotPlanter(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotPlanterNBT.INSTANCE;
    }

    /** The seed predicate: anything {@link CropManager#isSeed} recognises (class-based, so it works in
     *  the unit-test JVM where data tags do not resolve). */
    public boolean matchesSeed(ItemStack stack) {
        return CropManager.isSeed(stack);
    }

    /** What the planter is willing to fetch: its own seed predicate AND the linked station's gate
     *  "Filter" action (7.1.x: {@code new AggregateFilter(seedFilter,
     *  ActionRobotFilter.getGateFilter(robot.getLinkedStation()))}), so a station filtered on pumpkin
     *  seeds gets a pumpkin planter. An unset Filter — or no linked station — is pass-through. */
    IStackFilter seedFetchFilter() {
        return new AggregateFilter(this::matchesSeed,
                ActionRobotFilter.getGateFilter(robot.getLinkedStation()));
    }

    @Override
    public void update() {
        if (robot.getHeldItem().isEmpty()) {
            startDelegateAI(new AIRobotFetchAndEquipItemStack(robot, seedFetchFilter()));
        } else {
            Level level = robot.level();
            ItemStack seed = robot.getHeldItem();
            IBlockFilter blockFilter = pos -> level != null
                    && !BuildCraftAPI.getWorldProperty("replaceable").get(level, pos)
                    && CropManager.canSustainPlant(level, seed, pos)
                    && !robot.getRegistry().isTaken(new ResourceIdBlock(pos));
            startDelegateAI(new AIRobotSearchAndGotoBlock(robot, true, blockFilter, 1));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchAndGotoBlock searchAndGoto) {
            if (searchAndGoto.success()) {
                blockFound = searchAndGoto.getBlockFound();
                startDelegateAI(new AIRobotPlant(robot, blockFound));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotPlant) {
            releaseBlockFound();
        } else if (ai instanceof AIRobotFetchAndEquipItemStack) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
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
