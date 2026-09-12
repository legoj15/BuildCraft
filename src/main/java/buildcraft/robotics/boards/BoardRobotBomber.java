/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.ai.AIRobotGotoBlock;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoad;
import buildcraft.robotics.ai.AIRobotLoad;
import buildcraft.robotics.ai.AIRobotSearchRandomGroundBlock;

/** The bomber: loads TNT at a station, flies to a random spot {@code flyingHeight} above the ground the
 *  random search found, and drops primed TNT from there. Ported from 7.1.x {@code BoardRobotBomber}
 *  (7.1.x's manual inventory scan is {@link IRobotAccess#containsItems()}; the TNT extraction is the
 *  transactor's filter extract where 7.1.x had {@code Transactor.remove}).
 *
 *  <p>One documented divergence: 7.1.x credited the explosion to the robot (its TNT constructor took the
 *  {@code LivingEntity} robot for kill credit); the port's robot is a bare {@code Entity}, so the TNT is
 *  primed ownerless — exactly as redstone-ignited TNT — and no one collects the kill credit. */
public class BoardRobotBomber extends RedstoneBoardRobot {

    private static final IStackFilter TNT_FILTER = stack -> stack.is(Items.TNT);

    private int flyingHeight = 20;

    public BoardRobotBomber(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotBomberNBT.INSTANCE;
    }

    @Override
    public final void update() {
        if (!robot.containsItems()) {
            startDelegateAI(new AIRobotGotoStationAndLoad(robot, TNT_FILTER, AIRobotLoad.ANY_QUANTITY));
        } else {
            // 7.1.x's filter compared against world.getHeight() (the fixed 256 cap); the modern
            // equivalent is the level's build ceiling.
            //? if >=1.21.10 {
            int ceiling = robot.level().getMaxY();
            //?} else {
            /*int ceiling = robot.level().getMaxBuildHeight();*/
            //?}
            startDelegateAI(new AIRobotSearchRandomGroundBlock(robot, 100,
                    pos -> pos.getY() < ceiling - flyingHeight
                            && !robot.level().isEmptyBlock(pos),
                    robot.getZoneToWork()));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStationAndLoad) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotSearchRandomGroundBlock aiFind) {
            if (ai.success()) {
                startDelegateAI(new AIRobotGotoBlock(robot,
                        aiFind.blockFound.getX(),
                        aiFind.blockFound.getY() + flyingHeight,
                        aiFind.blockFound.getZ()));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotGotoBlock) {
            if (ai.success()) {
                ItemStack stack = robot.getTransactor().extract(TNT_FILTER, 1, 64, false);

                if (!stack.isEmpty()) {
                    PrimedTnt tnt = new PrimedTnt(
                            robot.level(),
                            robot.position().x + 0.25,
                            robot.position().y - 1,
                            robot.position().z + 0.25,
                            null);
                    tnt.setFuse(37);
                    robot.level().addFreshEntity(tnt);
                    robot.level().playSound(null,
                            tnt.getX(), tnt.getY(), tnt.getZ(),
                            SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }
}
