/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.core.properties.WorldPropertyIsHarvestable;

/** The harvester: finds fully-grown crops ({@code "harvestable"} world property) and harvests them
 *  with {@code AIRobotHarvest} (no tool needed — the hand is the tool). Ported from 7.1.x
 *  {@code BoardRobotHarvester}.
 *
 *  <p>Red-baseline skeleton: the {@code AIRobotHarvest} wiring lands in the AI step; until then
 *  {@link #update()} inherits the base terminate-on-cycle and the property answers false.</p> */
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
        // The "harvestable" world property (registered by BCCore; the skeleton property answers false
        // until the foundations commit).
        return ((WorldPropertyIsHarvestable) BuildCraftAPI.getWorldProperty("harvestable")).matches(state);
    }
}
