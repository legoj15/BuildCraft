/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.core.properties.WorldPropertyIsWood;

/** The lumberjack: breaks log blocks (the {@code "wood"} world property) with an axe. Ported from 7.1.x
 *  {@code BoardRobotLumberjack} (7.1.x's tool check was the {@code "axe"} tool class; the modern
 *  equivalent is {@link RobotToolPredicates#isAxe}). */
public class BoardRobotLumberjack extends BoardRobotGenericBreakBlock {

    public BoardRobotLumberjack(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotLumberjackNBT.INSTANCE;
    }

    @Override
    public boolean isExpectedTool(ItemStack stack) {
        return RobotToolPredicates.isAxe(stack);
    }

    @Override
    public boolean isExpectedBlock(BlockState state) {
        return ((WorldPropertyIsWood) BuildCraftAPI.getWorldProperty("wood")).matches(state);
    }
}
