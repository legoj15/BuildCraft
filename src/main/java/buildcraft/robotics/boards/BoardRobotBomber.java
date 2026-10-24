/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.robotics.RobotProtection;
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
 *  <p>Two documented divergences. 7.1.x credited the explosion to the robot (its TNT constructor took the
 *  {@code LivingEntity} robot for kill credit); the port's robot is a bare {@code Entity}, so the TNT is owned by the
 *  robot's deployer instead (the fake player it acts as, {@link RobotProtection#lease}), which is who claim mods,
 *  kill credit and death messages then see. And 7.1.x asked nobody before bombing: here the block the TNT will land
 *  on must be one both the deployer and the home station's owner may break ({@link RobotProtection#canBreak}), or
 *  nothing is dropped. A refused landing block is remembered ({@link WorkRefusals}), and so is the ground the
 *  search picked for that flight when the TNT would have landed elsewhere (the robot hovering off its column's centre,
 *  or something built in the way since), so the random search picks neither again for a while; three refusals in a
 *  row send the robot to sleep.
 *
 *  <p>Only the landing block is asked about, not the blast around it: the TNT goes off about where it lands, and the
 *  blocks it destroys are judged by protection mods' explosion handling, which sees the deployer's player as the
 *  igniter. The home station's owner is not asked about those blocks, so a claim of the station owner's that adjoins
 *  a permitted landing block can still be damaged by the blast. */
public class BoardRobotBomber extends RedstoneBoardRobot {

    private static final IStackFilter TNT_FILTER = stack -> stack.is(Items.TNT);

    /** How far below the robot a landing block is looked for: the flight hovers {@code flyingHeight} over the
     *  ground the search found, so this leaves room for the robot stopping a little high or the ground being lower. */
    private static final int LANDING_SCAN_DEPTH = 64;

    private int flyingHeight = 20;
    private final WorkRefusals refusals;
    /** The ground the last successful search picked, which the flight hovers over; a refused drop remembers it too. */
    @Nullable
    private BlockPos aimedAt;

    public BoardRobotBomber(IRobotAccess iRobot) {
        super(iRobot);
        refusals = new WorkRefusals(iRobot);
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
            startDelegateAI(new AIRobotSearchRandomGroundBlock(robot, 100, this::isDropTarget, robot.getZoneToWork()));
        }
    }

    /** Whether the random search may pick {@code pos} as the ground to hover over: low enough to hover
     *  {@code flyingHeight} above, not air, and not refused for this robot lately. */
    public boolean isDropTarget(BlockPos pos) {
        // 7.1.x's filter compared against world.getHeight() (the fixed 256 cap); the modern
        // equivalent is the level's build ceiling.
        //? if >=1.21.10 {
        int ceiling = robot.level().getMaxY();
        //?} else {
        /*int ceiling = robot.level().getMaxY();*/
        //?}
        return pos.getY() < ceiling - flyingHeight
                && !robot.level().isEmptyBlock(pos)
                && !refusals.isRefused(robot.level(), pos);
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStationAndLoad) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotSearchRandomGroundBlock aiFind) {
            if (ai.success()) {
                aimedAt = aiFind.blockFound;
                startDelegateAI(new AIRobotGotoBlock(robot,
                        aiFind.blockFound.getX(),
                        aiFind.blockFound.getY() + flyingHeight,
                        aiFind.blockFound.getZ()));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotGotoBlock) {
            if (ai.success()) {
                dropBomb();
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }

    /** Drops one primed TNT from where the robot hovers, owned by the robot's deployer, if both its players may break
     *  the block it will land on; a refusal drops nothing (the TNT stays in the robot) and is remembered, for the
     *  landing block and for the ground the search picked. */
    private void dropBomb() {
        if (!(robot.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 drop = new Vec3(robot.position().x + 0.25, robot.position().y - 1, robot.position().z + 0.25);
        BlockPos landing = landingBlock(level, drop);
        BlockPos aimed = aimedAt;
        aimedAt = null;
        if (!RobotProtection.canBreak(robot, level, landing)) {
            // The search tests the ground it picks, which is not the landing block when the two columns differ:
            // remembering only the landing block let the search pick the same ground, and be refused, again.
            if (aimed != null && !aimed.equals(landing)) {
                refusals.remember(level, aimed);
            }
            if (refusals.failed(level, landing)) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
            return;
        }
        // Exactly ONE: 7.1.x's Transactor.remove took a single item per run. Asking for up to a
        // stack here primed one TNT and silently destroyed the rest of what was extracted.
        ItemStack stack = robot.getTransactor().extract(TNT_FILTER, 1, 1, false);
        if (stack.isEmpty()) {
            return;
        }
        refusals.succeeded();
        PrimedTnt tnt;
        try (FakePlayerUtil.Lease lease = RobotProtection.lease(robot, level)) {
            // The TNT keeps the player as its owner after the lease closes: it only names who sent it.
            tnt = new PrimedTnt(level, drop.x, drop.y, drop.z, lease.player());
        }
        tnt.setFuse(37);
        level.addFreshEntity(tnt);
        level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** The block TNT dropped at {@code drop} falls onto: the first non-empty block straight below it, within
     *  {@link #LANDING_SCAN_DEPTH} and the loaded chunks; the cell under the drop point when there is none (it falls
     *  out of reach, and the question is asked about where it starts). */
    private static BlockPos landingBlock(ServerLevel level, Vec3 drop) {
        BlockPos start = BlockPos.containing(drop);
        BlockPos.MutableBlockPos cell = start.mutable();
        for (int depth = 0; depth < LANDING_SCAN_DEPTH && level.isLoaded(cell); depth++, cell.move(Direction.DOWN)) {
            if (!level.isEmptyBlock(cell)) {
                return cell.immutable();
            }
        }
        return start;
    }
}
