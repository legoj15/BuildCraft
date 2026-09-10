/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.statements;

import net.minecraft.world.item.ItemStack;

import buildcraft.api.items.IMapLocation;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;
import buildcraft.robotics.RobotUtils;
import buildcraft.robotics.ai.AIRobotGotoStation;
import buildcraft.robotics.entity.EntityRobot;

public class ActionRobotGotoStation extends BCStatement implements IActionInternal {

    public ActionRobotGotoStation() {
        super("buildcraft:robot.goto_station");
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.action.robot.goto_station");
    }

    @Override
    public SpriteHolder getSprite() {
        return BCRoboticsSprites.ACTION_ROBOT_GOTO_STATION;
    }

    /** Redirects every docked, non-preempted robot on the gate's pipe to the station the map-location
     *  parameter names, taking it as MAIN (7.1.x verbatim — no break: every docked robot is redirected).
     *
     *  <p>With NO map location set, 7.1.x defaulted the destination to the gate's OWN station
     *  ({@code DockingStation newStation = station;} before the parameter check), so the action still
     *  redirected the robot — at the station it is already sitting on. That case is reproduced here.
     *  7.1.x expressed "unset" as a literal {@code null} in the gate's parameter array; the port's gates
     *  always hold a parameter object and express "unset" as an EMPTY stack, so both spellings count.
     *  A parameter that IS set but names no live station still does nothing, exactly as 7.1.x — a player
     *  pointing at a station that has since been destroyed must not be silently re-homed.
     *
     *  <p>Note that the destination being the robot's current station makes this mostly a re-dock:
     *  {@code DockingStation.takeAsMain} returns early for a station the robot has already taken, so it
     *  does not promote that station to MAIN. That is 7.1.x's behaviour too, not an omission here. */
    @Override
    public void actionActivate(IStatementContainer container, IStatementParameter[] parameters) {
        IStatementParameter param = parameters != null && parameters.length > 0 ? parameters[0] : null;
        boolean hasLocation = param instanceof StatementParameterItemStack stackParam
                && !stackParam.getItemStack().isEmpty();

        for (DockingStation station : RobotUtils.getStations(container.getTile())) {
            if (station.robotTaking() instanceof EntityRobot robot && robot.getOverridingAI() == null) {
                DockingStation newStation = station;
                if (hasLocation) {
                    IRobotRegistry registry = RobotManager.registryProvider.getRegistry(robot.level());
                    newStation = getStation((StatementParameterItemStack) param, registry);
                }
                if (newStation != null) {
                    robot.overrideAI(new AIRobotGotoStation(robot, newStation, true));
                }
            }
        }
    }

    /** The station a {@code StatementParameterItemStack} map-location param points at, or null. */
    private DockingStation getStation(StatementParameterItemStack stackParam, IRobotRegistry registry) {
        ItemStack item = stackParam.getItemStack();

        if (!item.isEmpty() && item.getItem() instanceof IMapLocation) {
            IMapLocation map = (IMapLocation) item.getItem();
            net.minecraft.core.BlockPos point = map.getPoint(item);

            if (point != null) {
                net.minecraft.core.Direction side = map.getPointSide(item);
                DockingStation paramStation = registry.getStation(point, side);

                if (paramStation != null) {
                    return paramStation;
                }
            }
        }
        return null;
    }

    @Override
    public int maxParameters() {
        return 1;
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return new StatementParameterItemStack();
    }

}
