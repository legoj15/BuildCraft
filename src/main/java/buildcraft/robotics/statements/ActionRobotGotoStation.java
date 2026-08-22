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
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;

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

    /** Red-baseline degenerate: the docked-robot redirect is Ph6-green work (it flies the robot via
     *  {@code AIRobotGotoStation} with the takeAsMain flag). */
    @Override
    public void actionActivate(IStatementContainer container, IStatementParameter[] parameters) {
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
