/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;

//? if >=1.21.10 {
import net.minecraft.world.entity.animal.wolf.Wolf;
//?} else {
/*import net.minecraft.world.entity.animal.Wolf;*/
//?}

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.ai.AIRobotAttack;
import buildcraft.robotics.ai.AIRobotFetchAndEquipItemStack;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnload;
import buildcraft.robotics.ai.AIRobotSearchEntity;

/** The knight: keeps a sword equipped (fetching one from the station when none is held, unloading a
 *  broken one), then searches for hostile entities (monsters, or an angry wolf) and attacks them with
 *  {@code AIRobotAttack}. Ported from 7.1.x {@code BoardRobotKnight} (7.1.x's tool check was
 *  {@code ItemSword}; the modern equivalent is {@link RobotToolPredicates#isSword}, and the 7.1.x
 *  {@code IMob} target check is the {@link Monster} hierarchy).
 *
 *  <p>Two documented divergences from 7.1.x, matching the break-board base: the fetch filter refuses a
 *  WORN sword (7.1.x would fetch a broken sword and spin forever), and the worn-hand check is guarded
 *  by {@code isDamageableItem()} (7.1.x compared {@code damage >= maxDamage} unguarded, so an
 *  undamageable item in the hand — 0 &gt;= 0 — would unload every cycle). */
public class BoardRobotKnight extends RedstoneBoardRobot {

    public BoardRobotKnight(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotKnightNBT.INSTANCE;
    }

    /** The sword predicate: vanilla swords by identity plus anything the {@code c:tools/sword} tag admits. */
    public boolean isExpectedSword(ItemStack stack) {
        return RobotToolPredicates.isSword(stack);
    }

    /** The target predicate: any monster, or a wolf that is angry. */
    public boolean isExpectedTarget(Entity entity) {
        return entity instanceof Monster
                || (entity instanceof Wolf wolf && wolf.isAngry());
    }

    @Override
    public final void update() {
        ItemStack held = robot.getHeldItem();
        if (held.isEmpty() && !isExpectedSword(ItemStack.EMPTY)) {
            startDelegateAI(new AIRobotFetchAndEquipItemStack(robot,
                    stack -> !stack.isEmpty() && stack.getDamageValue() < stack.getMaxDamage()
                            && isExpectedSword(stack)));
        } else if (!held.isEmpty() && held.isDamageableItem()
                && held.getDamageValue() >= held.getMaxDamage()) {
            startDelegateAI(new AIRobotGotoStationAndUnload(robot));
        } else {
            startDelegateAI(new AIRobotSearchEntity(robot, this::isExpectedTarget, 250f, robot.getZoneToWork()));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotFetchAndEquipItemStack) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotSearchEntity search) {
            if (search.success()) {
                startDelegateAI(new AIRobotAttack(robot, search.target));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }
}
