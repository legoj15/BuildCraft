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

/** The miner: breaks ore blocks with a pickaxe, targeting the ore tier ({@code "ore@hardness=0..3"}
 *  world properties) its currently-held tool can harvest. Ported from 7.1.x {@code BoardRobotMiner}
 *  (7.1.x detected the tier from the tool's {@code harvestLevel} NBT tag; the modern equivalent is
 *  {@code isCorrectToolForDrops} against a probe block per tier).
 *
 *  <p>Red-baseline skeleton: tier detection answers 0 and the tool predicate answers false until the
 *  AI step lands the real logic.</p> */
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
     *  successful {@code AIRobotFetchAndEquipItemStack} in the AI step. */
    public int getHarvestLevel() {
        return harvestLevel;
    }

    /** The world-property key of the ore tier this robot can mine, clamped to
     *  {@link #MAX_HARVEST_LEVEL}. */
    public String orePropertyKey() {
        return "ore@hardness=" + Math.min(MAX_HARVEST_LEVEL, harvestLevel);
    }

    /** Red-baseline skeleton — tier detection from the held tool in the AI step. */
    public void detectHarvestLevel() {
        harvestLevel = 0;
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        // Red-baseline skeleton — re-run detectHarvestLevel() after a successful
        // AIRobotFetchAndEquipItemStack in the AI step.
        super.delegateAIEnded(ai);
    }

    @Override
    public boolean isExpectedTool(ItemStack stack) {
        // Red-baseline skeleton — a pickaxe in the AI step.
        return false;
    }

    @Override
    public boolean isExpectedBlock(BlockState state) {
        // The ore property for the current tier (registered by BCCore; the skeleton property answers
        // false until the foundations commit).
        return ((WorldPropertyIsOre) BuildCraftAPI.getWorldProperty(orePropertyKey())).matches(state);
    }
}
