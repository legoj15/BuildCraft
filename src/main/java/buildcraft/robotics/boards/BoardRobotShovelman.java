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
import buildcraft.core.properties.WorldPropertyIsShoveled;

/** The shovelman: shovels up every block a shovel digs (the {@code "shoveled"} world property — dirt,
 *  sand, clay, gravel, farmland, grass, the snow layer). Ported from 7.1.x
 *  {@code BoardRobotShovelman} (7.1.x's tool check was the {@code "shovel"} tool class; the modern
 *  equivalent is {@link RobotToolPredicates#isShovel}). */
public class BoardRobotShovelman extends BoardRobotGenericBreakBlock {

    public BoardRobotShovelman(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotShovelmanNBT.INSTANCE;
    }

    @Override
    public boolean isExpectedTool(ItemStack stack) {
        return RobotToolPredicates.isShovel(stack);
    }

    @Override
    public boolean isExpectedBlock(BlockState state) {
        return ((WorldPropertyIsShoveled) BuildCraftAPI.getWorldProperty("shoveled")).matches(state);
    }
}
