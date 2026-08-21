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

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** The break-board base: keep a tool the board knows how to use ({@link #isExpectedTool}) equipped —
 *  fetching one from the station when none is held, unloading a broken one when it wears out — and, once
 *  a block is found, hand it to {@code AIRobotBreak}. Ported from 7.1.x
 *  {@code BoardRobotGenericBreakBlock}.
 *
 *  <p>Red-baseline skeleton: the tool/lifecycle wiring lands in the AI step; until then {@link #update()}
 *  inherits the base terminate-on-cycle.</p> */
public abstract class BoardRobotGenericBreakBlock extends BoardRobotGenericSearchBlock {

    public BoardRobotGenericBreakBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    /** The tool predicate: an equipped stack passes when the board can break its target with it. */
    public abstract boolean isExpectedTool(ItemStack stack);

    @Override
    public final void update() {
        // Red-baseline skeleton — no tool: AIRobotFetchAndEquipItemStack(damage<max && isExpectedTool);
        // worn: AIRobotGotoStationAndUnload; blockFound: AIRobotBreak(blockFound); else super.update().
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        // Red-baseline skeleton — Fetch/Unload failure -> AIRobotGotoSleep; Break -> releaseBlockFound,
        // in the AI step.
        super.delegateAIEnded(ai);
    }
}
