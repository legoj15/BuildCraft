/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.statements;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.core.IZone;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementSlot;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemMapLocation;
import buildcraft.robotics.ai.MockRobotAccess;
import buildcraft.robotics.statements.ActionRobotWorkInArea.AreaType;
import buildcraft.robotics.zone.ZonePlan;

/** {@link ActionRobotWorkInArea.getArea}: a WORK/LOAD_UNLOAD action slot resolves its zone from the
 *  map-location parameter — a ZONE map round-trips through {@link ItemMapLocation#setZone}. */
public class ActionRobotWorkInAreaTest extends VanillaSetupBaseTester {

    private static StatementSlot areaSlot(AreaType type, StatementParameterMapLocation param) {
        StatementSlot slot = new StatementSlot();
        slot.statement = new ActionRobotWorkInArea(type);
        slot.parameters = new IStatementParameter[] { param };
        return slot;
    }

    @Test
    public void workAreaResolvesZoneFromMapParam() {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ZonePlan zone = new ZonePlan();
        ItemMapLocation.setZone(map, zone);

        IZone resolved = ActionRobotWorkInArea.getArea(new MockRobotAccess(),
                areaSlot(AreaType.WORK, new StatementParameterMapLocation(map)));

        Assertions.assertNotNull(resolved, "a ZONE map parameter must resolve to the work area");
    }

    @Test
    public void loadUnloadAreaResolvesZoneFromMapParam() {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        ZonePlan zone = new ZonePlan();
        ItemMapLocation.setZone(map, zone);

        IZone resolved = ActionRobotWorkInArea.getArea(new MockRobotAccess(),
                areaSlot(AreaType.LOAD_UNLOAD, new StatementParameterMapLocation(map)));

        Assertions.assertNotNull(resolved, "a ZONE map parameter must resolve to the load/unload area");
    }

    @Test
    public void emptyMapParamResolvesNothing() {
        Assertions.assertNull(ActionRobotWorkInArea.getArea(new MockRobotAccess(),
                areaSlot(AreaType.WORK, new StatementParameterMapLocation())),
                "an empty map parameter must resolve to no area");
    }
}
