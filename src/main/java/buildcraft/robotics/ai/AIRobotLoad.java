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

/** Pulls items from the docked station's item input into the robot's four slots. The 7.1.x slot-by-slot
 *  {@code InventoryIterator}/{@code ITransactor} scan becomes a D2 {@code IItemTransactor} exchange — the
 *  station input {@code Container} is wrapped, matching stacks are {@code extract}ed up to the quantity, and
 *  the robot's transactor {@code insert}s them; {@code doLoad} maps to {@code simulate}. The station's
 *  permissive {@code canRobotExtractItem} policy (D1) gates each stack, exactly where
 *  {@code ActionStationProvideItems} did. */
public class AIRobotLoad extends AIRobot {

    public static final int ANY_QUANTITY = -1;
    private IStackFilter filter;
    private int quantity;
    private int waitedCycles = 0;

    public AIRobotLoad(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotLoad(IRobotAccess iRobot, IStackFilter iFilter, int iQuantity) {
        super(iRobot);

        filter = iFilter;
        quantity = iQuantity;
    }

    @Override
    public void update() {
        if (filter == null) {
            terminate();
            return;
        }

        waitedCycles++;

        if (waitedCycles > 40) {
            setSuccess(load(robot, robot.getDockingStation(), filter, quantity, true));
            terminate();
        }
    }

    /** Loads up to {@code quantity} matching items from {@code station}'s item input onto {@code robot}.
     *  {@code ANY_QUANTITY} loads a single matching stack. With {@code doLoad} false the whole exchange is
     *  simulated (7.1.x's dry-run flag → {@code IItemTransactor} simulate). */
    public static boolean load(IRobotAccess robot, DockingStation station, IStackFilter filter,
                               int quantity, boolean doLoad) {
        if (station == null) {
            return false;
        }

        Container input = station.getItemInput();
        if (input == null) {
            return false;
        }

        IItemTransactor inputTransactor = new InventoryWrapper(input);
        IItemTransactor robotTransactor = robot.getTransactor();

        if (quantity == ANY_QUANTITY) {
            ItemStack extracted = inputTransactor.extract(filter, 1, Integer.MAX_VALUE, !doLoad);
            if (extracted.isEmpty() || !station.canRobotExtractItem(extracted)) {
                if (!extracted.isEmpty() && doLoad) {
                    // Policy forbids this stack — put it back (permissive in Ph4, real gates are Ph6).
                    inputTransactor.insert(extracted, false, false);
                }
                return false;
            }
            ItemStack overflow = robotTransactor.insert(extracted, false, doLoad);
            if (!overflow.isEmpty()) {
                if (doLoad) {
                    inputTransactor.insert(overflow, false, false);
                }
                return false;
            }
            return true;
        }

        int loaded = 0;
        while (loaded < quantity) {
            int take = quantity - loaded;
            ItemStack extracted = inputTransactor.extract(filter, 1, take, !doLoad);
            if (extracted.isEmpty()) {
                break;
            }
            if (!station.canRobotExtractItem(extracted)) {
                if (doLoad) {
                    inputTransactor.insert(extracted, false, false);
                }
                continue;
            }
            ItemStack overflow = robotTransactor.insert(extracted, false, doLoad);
            if (!overflow.isEmpty()) {
                if (doLoad) {
                    inputTransactor.insert(overflow, false, false);
                }
                loaded += extracted.getCount() - overflow.getCount();
                break;
            }
            loaded += extracted.getCount();
        }
        return loaded > 0;
    }

    @Override
    public long getPowerCost() {
        return 8;
    }
}
