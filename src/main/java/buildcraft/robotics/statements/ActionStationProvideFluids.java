/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.statements;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;

public class ActionStationProvideFluids extends BCStatement implements IActionInternal {

    public ActionStationProvideFluids() {
        // Upstream ships this key with the povide_fluids typo — kept at red, renamed at Ph6-green.
        super("buildcraft:station.provide_fluids");
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.action.station.povide_fluids");
    }

    @Override
    public SpriteHolder getSprite() {
        return BCRoboticsSprites.ACTION_STATION_PROVIDE_FLUIDS;
    }

    @Override
    public int maxParameters() {
        return 3;
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return new StatementParameterItemStack();
    }

    /** Red-baseline degenerate: no fluid is accepted yet. Ph6-green checks the active provide-fluids
     *  actions' parameters against the fluid carried by {@code stack}. */
    public static boolean canInteractWithFluid(DockingStation station, IFluidFilter filter, Class<?> actionClass) {
        return false;
    }

    @Override
    public void actionActivate(IStatementContainer source, IStatementParameter[] parameters) {
    }
}
