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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.core.properties.WorldPropertyIsWood;

/** The lumberjack: breaks log blocks (the {@code "wood"} world property) with an axe. Ported from 7.1.x
 *  {@code BoardRobotLumberjack} (7.1.x's tool check was the {@code "axe"} tool class; the modern
 *  equivalent is {@link RobotToolPredicates#isAxe}).
 *
 *  <p>Not 7.1.x: it hunts a trunk from its base. Tree-felling mods (FallingTree and its kin) bring a whole tree down
 *  when its bottom log is cut and ignore a cut from the middle, and the search takes the NEAREST log, which is often
 *  mid-trunk; so a log with wood directly below it is not a candidate ({@link #isExpectedBlock(BlockGetter,
 *  BlockPos)}). Without such a mod nothing is lost: each column is chopped bottom-up, since the log above a cut one
 *  has air below it and is the base of what is left. A floating log (air, or anything but wood, below) stays a
 *  candidate.
 *
 *  <p>The log below only counts while this robot's search may pick it ({@link #searchMayPick}): a base the gate's
 *  Filter leaves out, protection refused, the work zone excludes or a search could not reach would otherwise hold the
 *  whole column above it back for good, since nothing ever brings that base down. */
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

    /** Wood with no wood below it that this robot's search could take: the base of a trunk, as far as it is
     *  concerned. The state-only {@link #isExpectedBlock(BlockState)} stays "is it wood"; this neighbour-aware form is
     *  the one the search and the failed-attempt memory ({@code finishAttempt}) ask. A break that failed is judged by
     *  the same rule, which is the right one: a log that waited for a base it can now have is simply not remembered as
     *  a failure, and the base is what the next search takes. */
    @Override
    public boolean isExpectedBlock(BlockGetter access, BlockPos pos) {
        if (!isExpectedBlock(access.getBlockState(pos))) {
            return false;
        }
        BlockPos below = pos.below();
        return !(isExpectedBlock(access.getBlockState(below)) && searchMayPick(access, below));
    }
}
