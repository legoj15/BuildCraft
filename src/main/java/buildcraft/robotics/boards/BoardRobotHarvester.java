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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.core.properties.WorldPropertyIsHarvestable;
import buildcraft.robotics.ai.AIRobotHarvest;

/** The harvester: finds fully-grown crops (the {@code "harvestable"} world property) and harvests them
 *  with {@code AIRobotHarvest} (no tool needed — the hand is the tool). Ported from 7.1.x
 *  {@code BoardRobotHarvester}.
 *
 *  <p>The reservation is released UNCONDITIONALLY after the harvest AI, success or not — 7.1.x's
 *  {@code releaseBlockFound(boolean)} took the flag but ignored it (its TODO to blacklist failed blocks
 *  was never implemented), and the 7.1.x harvest AI's odd success semantics (it never terminates on
 *  success, so it reports false even when it harvested) made the flag meaningless anyway. */
public class BoardRobotHarvester extends BoardRobotGenericSearchBlock {

    public BoardRobotHarvester(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotHarvesterNBT.INSTANCE;
    }

    @Override
    public boolean isExpectedBlock(BlockState state) {
        return ((WorldPropertyIsHarvestable) BuildCraftAPI.getWorldProperty("harvestable")).matches(state);
    }

    /** Overridden because "harvestable" is NOT a pure-state property: a stacking crop (cactus, sugar cane)
     *  is ripe only relative to the block below it, so the search has to read the neighbour. The
     *  state-only seam above answers those with an empty getter — i.e. "never" — which is why the search
     *  must come through here. */
    @Override
    public boolean isExpectedBlock(BlockGetter access, BlockPos pos) {
        return ((WorldPropertyIsHarvestable) BuildCraftAPI.getWorldProperty("harvestable")).matches(access, pos);
    }

    @Override
    public void update() {
        if (blockFound() != null) {
            startDelegateAI(new AIRobotHarvest(robot, blockFound()));
        } else {
            super.update();
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotHarvest) {
            releaseBlockFound();
        }
        super.delegateAIEnded(ai);
    }
}
