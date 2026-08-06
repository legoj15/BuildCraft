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
import buildcraft.lib.misc.StackUtil;

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
    /** One stack's worth: the vanilla stack-size ceiling, which is also the most a single slot of any
     *  inventory can hold. Capping the extract here keeps ANY_QUANTITY at "a single matching stack" (the
     *  documented contract) instead of silently becoming "the entire supply or nothing" — the transactor's
     *  cross-slot accumulation would otherwise pull a whole chest of one item type and fail whenever the
     *  robot's four slots cannot hold it all. */
    private static final int ONE_STACK = 64;

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
            ItemStack extracted = inputTransactor.extract(filter, 1, ONE_STACK, !doLoad);
            if (extracted.isEmpty() || !station.canRobotExtractItem(extracted)) {
                if (!extracted.isEmpty() && doLoad) {
                    // Policy forbids this stack — put it back (permissive in Ph4, real gates are Ph6).
                    inputTransactor.insert(extracted, false, false);
                }
                return false;
            }
            // The transactor never mutates the offered stack, so taken = offered minus leftover is exact.
            // The simulate flag must be !doLoad (NOT doLoad): with doLoad=false this whole exchange is the
            // station-search dry-run, and a real insert would steal the chest's items into the robot while
            // the (simulated) extract leaves them in place — duplication with the robot never flying.
            int taken = extracted.getCount();
            ItemStack overflow = robotTransactor.insert(extracted, false, !doLoad);
            if (doLoad && !overflow.isEmpty()) {
                inputTransactor.insert(overflow, false, false);
            }
            return taken - overflow.getCount() > 0;
        }

        int loaded = 0;
        while (loaded < quantity) {
            int take = quantity - loaded;
            ItemStack extracted = inputTransactor.extract(filter, 1, take, !doLoad);
            if (extracted.isEmpty()) {
                break;
            }
            if (!station.canRobotExtractItem(extracted)) {
                // Copy BEFORE the put-back: the station transactor (InventoryWrapper) drains the offered
                // stack itself when it lands back in an emptied slot, so reading `extracted` after the
                // insert can capture an EMPTY stack — and excluding "empty" excludes nothing, looping on
                // the same refused stack forever.
                ItemStack refused = extracted.copy();
                if (doLoad) {
                    inputTransactor.insert(extracted, false, false);
                }
                // The station refused this stack: exclude it from the remaining attempts. 7.1.x advanced
                // an InventoryIterator to the next slot here; the transactor API has no per-slot skip, and
                // re-extracting with the same filter returns this same stack every time — a plain
                // `continue` would spin forever the moment a real (Ph6) policy refuses anything.
                IStackFilter previous = filter;
                filter = stack -> !StackUtil.canMerge(stack, refused) && previous.matches(stack);
                continue;
            }
            int before = extracted.getCount();
            ItemStack overflow = robotTransactor.insert(extracted, false, !doLoad);
            if (!overflow.isEmpty()) {
                if (doLoad) {
                    inputTransactor.insert(overflow, false, false);
                }
                loaded += before - overflow.getCount();
                break;
            }
            loaded += before;
        }
        return loaded > 0;
    }

    @Override
    public long getPowerCost() {
        // 7.1.x charged 8 RF per tick; at the canonical 1 MJ = 10 RF bridge that is 8 * 100_000 micro-MJ.
        return 800_000;
    }
}
