/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
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
import buildcraft.core.properties.WorldPropertyIsLeaf;

/** The leave cutter: breaks leaf blocks (the {@code "leaves"} world property) with shears. Ported from
 *  7.1.x {@code BoardRobotLeaveCutter} (7.1.x's tool check was the {@code ItemShears} class; the modern
 *  equivalent is {@link RobotToolPredicates#isShears}). */
public class BoardRobotLeaveCutter extends BoardRobotGenericBreakBlock {

    public BoardRobotLeaveCutter(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotLeaveCutterNBT.INSTANCE;
    }

    @Override
    public boolean isExpectedTool(ItemStack stack) {
        return RobotToolPredicates.isShears(stack);
    }

    @Override
    public boolean isExpectedBlock(BlockState state) {
        return ((WorldPropertyIsLeaf) BuildCraftAPI.getWorldProperty("leaves")).matches(state);
    }
}
