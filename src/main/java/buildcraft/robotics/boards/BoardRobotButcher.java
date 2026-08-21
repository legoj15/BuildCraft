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
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.IRobotAccess;

/** The butcher: keeps a sword equipped (fetching one from the station when none is held, unloading a
 *  broken one), then searches for animals and slaughters them with {@code AIRobotAttack} (the sword
 *  drops are collected by the usual item-fetch paths). Ported from 7.1.x {@code BoardRobotButcher}
 *  (7.1.x's tool check was {@code ItemSword}; the modern equivalent is {@code instanceof SwordItem}).
 *
 *  <p>Red-baseline skeleton: the sword/target predicates and the search/attack wiring land in the AI
 *  step; until then {@link #update()} inherits the base terminate-on-cycle.</p> */
public class BoardRobotButcher extends RedstoneBoardRobot {

    public BoardRobotButcher(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotButcherNBT.INSTANCE;
    }

    /** The sword predicate — {@code instanceof SwordItem} in the AI step. */
    public boolean isExpectedSword(ItemStack stack) {
        // Red-baseline skeleton.
        return false;
    }

    /** The target predicate — an animal, in the AI step. */
    public boolean isExpectedTarget(Entity entity) {
        // Red-baseline skeleton.
        return false;
    }
}
