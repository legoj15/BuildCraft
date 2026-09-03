/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.IStackFilter;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.inventory.InventoryWrapper;

/** Docks at the robot's station and equips, in the robot's hands, one stack matching {@code filter} taken
 *  from the station's item input. Ported from 7.1.x {@code AIRobotFetchAndEquipItemStack} (7.1.x reached
 *  the same through {@code AIRobotLoad.takeSingle} on the station input; the modern equivalent extracts one
 *  matching stack through the station's input transactor and then
 *  {@code robot.setItemInUse}). The board hands the merged work/tool gate filter in as {@code filter}; the
 *  station's {@code canRobotExtractItem} policy (D1, gate-semantics at Ph6) gates each candidate stack.
 *
 *  <p>The move to the station reuses the Ph4 {@link AIRobotGotoStationToLoad} with quantity 1 — its
 *  station search dry-runs {@link AIRobotLoad#load} with the same filter, so only a station that can
 *  actually supply a matching stack is a candidate. 7.1.x's null-filter hard abort (a load can wipe the
 *  filter out from under a running robot) is folded into the plain failure path: the board reacts to
 *  {@code !success()} with a sleep, which is the same observable behaviour. */
public class AIRobotFetchAndEquipItemStack extends AIRobot {

    private IStackFilter filter;
    private int delay = 0;

    public AIRobotFetchAndEquipItemStack(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotFetchAndEquipItemStack(IRobotAccess iRobot, IStackFilter iFilter) {
        super(iRobot);

        filter = iFilter;
    }

    @Override
    public void start() {
        if (filter != null) {
            startDelegateAI(new AIRobotGotoStationToLoad(robot, filter, 1));
        } else {
            setSuccess(false);
            terminate();
        }
    }

    @Override
    public void update() {
        if (filter == null || robot.getDockingStation() == null) {
            setSuccess(false);
            terminate();
            return;
        }

        if (delay++ > 40) {
            if (equipItemStack()) {
                terminate();
            } else {
                // The station had no matching stack yet — go back and look again (7.1.x looped the same).
                delay = 0;
                startDelegateAI(new AIRobotGotoStationToLoad(robot, filter, 1));
            }
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStationToLoad) {
            if (!ai.success()) {
                setSuccess(false);
                terminate();
            }
        }
    }

    private boolean equipItemStack() {
        DockingStation station = robot.getDockingStation();
        Container input = station.getItemInput();
        if (input == null) {
            return false;
        }

        // One real extract of EXACTLY ONE item (7.1.x takeSingle: decreaseStackInSlot(1) with
        // doTake=true); a refused stack goes straight back — the canRobotExtractItem policy (D1,
        // gate-semantics at Ph6) decides. The count matters downstream: AIRobotPlant plants a single
        // seed and drops the remainder of the hand on the ground, so equipping a full stack made a
        // planter fed from a seed chest spill 63 seeds every cycle.
        IItemTransactor inputTransactor = new InventoryWrapper(input);
        ItemStack possible = inputTransactor.extract(filter, 1, 1, false);
        if (possible.isEmpty()) {
            return false;
        }
        if (!station.canRobotExtractItem(possible)) {
            inputTransactor.insert(possible, false, false);
            return false;
        }
        robot.setItemInUse(possible);
        return true;
    }
}
