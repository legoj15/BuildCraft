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
import buildcraft.robotics.ai.AIRobotBreak;
import buildcraft.robotics.ai.AIRobotFetchAndEquipItemStack;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnload;

/** The break-board base: keep a tool the board knows how to use ({@link #isExpectedTool}) equipped —
 *  fetching one from the station when none is held, unloading a broken one when it wears out — and, once
 *  a block is found, hand it to {@code AIRobotBreak}. Ported from 7.1.x
 *  {@code BoardRobotGenericBreakBlock}.
 *
 *  <p>One documented divergence from 7.1.x: the worn-tool check is guarded by {@code isDamageableItem()}.
 *  7.1.x compared {@code itemDamage >= maxDamage} on whatever was held, and for an undamageable item both
 *  sides are 0, so a robot holding one (say after a fetch filter let it through) would unload it every
 *  cycle. The fetch filter keeps 7.1.x's shape — {@code damage < maxDamage && isExpectedTool} — so
 *  undamageable items are not fetched (0 &lt; 0 is false), exactly as upstream.
 *
 *  <p>A second: a break that fails on a block that stays (one the tool cannot break, a negative-hardness block a
 *  modded tool was pointed at) is remembered, and a run of them sends the board to sleep
 *  ({@link #finishAttempt}). 7.1.x released the block and searched again, and the nearest block won every time. */
public abstract class BoardRobotGenericBreakBlock extends BoardRobotGenericSearchBlock {

    public BoardRobotGenericBreakBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    /** The tool predicate: an equipped stack passes when the board can break its target with it. */
    public abstract boolean isExpectedTool(ItemStack stack);

    @Override
    public final void update() {
        ItemStack held = robot.getHeldItem();
        if (held.isEmpty() && !isExpectedTool(ItemStack.EMPTY)) {
            startDelegateAI(new AIRobotFetchAndEquipItemStack(robot,
                    stack -> !stack.isEmpty() && stack.getDamageValue() < stack.getMaxDamage()
                            && isExpectedTool(stack)));
        } else if (!held.isEmpty() && held.isDamageableItem()
                && held.getDamageValue() >= held.getMaxDamage()) {
            startDelegateAI(AIRobotGotoStationAndUnload.forHeldItem(robot));
        } else if (blockFound() != null) {
            startDelegateAI(new AIRobotBreak(robot, blockFound()));
        } else {
            super.update();
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotFetchAndEquipItemStack || ai instanceof AIRobotGotoStationAndUnload) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotBreak) {
            // A break that failed on a block that is still there is remembered (the search skips it from now on), and
            // a run of them sends the board to sleep - see finishAttempt.
            if (finishAttempt(ai)) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
        super.delegateAIEnded(ai);
    }
}
