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
     *  parameter names, taking it as MAIN (7.1.x verbatim — no break: every docked robot is redirected). Unlike
     *  7.1.x the new home is linked only once the robot docks there ({@code AIRobotGotoStation}'s takeAsMain), so a
     *  redirect whose flight fails leaves the robot homed where it was.
     *
     *  <p>With NO map location set, 7.1.x defaulted the destination to the gate's OWN station
     *  ({@code DockingStation newStation = station;} before the parameter check), so the action still
     *  redirected the robot — at the station it is already sitting on. That case is reproduced here.
     *  7.1.x expressed "unset" as a literal {@code null} in the gate's parameter array; the port's gates
     *  always hold a parameter object and express "unset" as an EMPTY stack, so both spellings count.
     *  A parameter that IS set but names no live station still does nothing, exactly as 7.1.x — a player
     *  pointing at a station that has since been destroyed must not be silently re-homed.
     *
     *  <p>A robot already docked at the destination is left alone (an empty parameter on a docked robot's own
     *  station is the common case), and so is one sent to a station another robot holds. 7.1.x redirected both
     *  anyway, the redirect ended on the spot, and the gate re-fired it every tick: the robot's board was
     *  restarted each tick and the robot never got on with its work. The docked case's redirect also never
     *  promoted the station to MAIN ({@code DockingStation.takeAsMain} returns early for a station the robot
     *  already holds), so skipping it loses nothing. See {@link #shouldRedirect}. */
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
                if (shouldRedirect(robot, newStation)) {
                    robot.overrideAI(new AIRobotGotoStation(robot, newStation, true));
                }
            }
        }
    }

    /** Whether sending {@code robot} to {@code target} can lead anywhere. A gate that holds fires this every tick, and
     *  every redirect handed out aborts the robot's board AI, so a redirect that is bound to end on the spot must not
     *  be handed out at all, or the robot is frozen for as long as the gate stays on:
     *  <ul>
     *  <li>the robot is already docked at the target: the goto ends in its own {@code start()};</li>
     *  <li>another robot holds the target (its home, its dock or a visit claim): the goto's claim ({@code take}) is
     *  refused and it fails in {@code start()}. Compared by id, as the claim itself is. 7.1.x handed this one out
     *  too, every tick.</li>
     *  </ul>
     *  A redirect that fails later (no path to the station) is the controller's to throttle: {@code AIRobotMain}
     *  refuses new overrides for a while after one fails. */
    static boolean shouldRedirect(EntityRobot robot, DockingStation target) {
        if (target == null || target == robot.getDockingStation()) {
            return false;
        }
        return !target.isTaken() || target.robotIdTaking() == robot.getRobotId();
    }

    /** The station a {@code StatementParameterItemStack} map-location param points at, or null. */
    static DockingStation getStation(StatementParameterItemStack stackParam, IRobotRegistry registry) {
        ItemStack item = stackParam.getItemStack();

        if (!item.isEmpty() && item.getItem() instanceof IMapLocation) {
            IMapLocation map = (IMapLocation) item.getItem();
            net.minecraft.core.BlockPos point = map.getPoint(item);

            if (point != null) {
                // No side (a map location whose side byte is not a face) names no station; the registry key needs one.
                net.minecraft.core.Direction side = map.getPointSide(item);
                DockingStation paramStation = side == null ? null : registry.getStation(point, side);

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
