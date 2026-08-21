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
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.core.properties.WorldPropertyIsOre;
import buildcraft.robotics.ai.AIRobotFetchAndEquipItemStack;

/** The miner: breaks ore blocks with a pickaxe, targeting the ore tier ({@code "ore@hardness=0..3"}
 *  world properties) its currently-held tool can harvest. Ported from 7.1.x {@code BoardRobotMiner}
 *  (7.1.x detected the tier from the tool's {@code harvestLevel} NBT tag; the modern equivalent is the
 *  tier of the held item in {@link RobotToolPredicates}). */
public class BoardRobotMiner extends BoardRobotGenericBreakBlock {

    /** The highest ore property that exists ({@code ore@hardness=0..3}); a netherite tool (level 4) is
     *  clamped onto the hardness-3 property. */
    private static final int MAX_HARVEST_LEVEL = 3;

    private int harvestLevel = 0;

    public BoardRobotMiner(IRobotAccess iRobot) {
        super(iRobot);
        detectHarvestLevel();
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotMinerNBT.INSTANCE;
    }

    /** Harvest tier of the currently-held tool (0 = bare hand … 4 = netherite); re-detected after a
     *  successful {@code AIRobotFetchAndEquipItemStack}. */
    public int getHarvestLevel() {
        return harvestLevel;
    }

    /** The world-property key of the ore tier this robot can mine, clamped to
     *  {@link #MAX_HARVEST_LEVEL}. */
    public String orePropertyKey() {
        return "ore@hardness=" + Math.min(MAX_HARVEST_LEVEL, harvestLevel);
    }

    /** The tier of whatever is in the hand right now (0 when the hand is empty or the item is a
     *  tag-detected pickaxe of unknown tier). */
    public void detectHarvestLevel() {
        harvestLevel = RobotToolPredicates.pickaxeTier(robot.getHeldItem());
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        super.delegateAIEnded(ai);

        if (ai instanceof AIRobotFetchAndEquipItemStack && ai.success()) {
            detectHarvestLevel();
        }
    }

    @Override
    public boolean isExpectedTool(ItemStack stack) {
        return RobotToolPredicates.isPickaxe(stack);
    }

    @Override
    public boolean isExpectedBlock(BlockState state) {
        return ((WorldPropertyIsOre) BuildCraftAPI.getWorldProperty(orePropertyKey())).matches(state);
    }
}
