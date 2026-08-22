/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.statements;

import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementSlot;
import buildcraft.core.statements.BCStatement;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.robotics.BCRoboticsSprites;

public class ActionRobotWorkInArea extends BCStatement implements IActionInternal {

    public static enum AreaType {
        WORK("work_in_area", BCRoboticsSprites.ACTION_ROBOT_WORK_IN_AREA),
        LOAD_UNLOAD("load_unload_area", BCRoboticsSprites.ACTION_ROBOT_LOAD_UNLOAD_AREA);

        private final String tag;
        private final String unlocalizedName;
        private final SpriteHolder icon;

        AreaType(String tag, SpriteHolder icon) {
            this.tag = "buildcraft:robot." + tag;
            this.unlocalizedName = "gate.action.robot." + tag;
            this.icon = icon;
        }

        public String getTag() {
            return tag;
        }

        public String getUnlocalizedName() {
            return unlocalizedName;
        }

        public SpriteHolder getIcon() {
            return icon;
        }
    }

    private final AreaType areaType;

    public ActionRobotWorkInArea(AreaType areaType) {
        super(areaType.getTag());
        this.areaType = areaType;
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize(areaType.getUnlocalizedName());
    }

    @Override
    public SpriteHolder getSprite() {
        return areaType.getIcon();
    }

    /** Red-baseline degenerate: no zone is handed out yet. Ph6-green resolves the area from a
     *  WORK/LOAD_UNLOAD action slot's map-location parameter (SPOT box or ZONE plan). */
    public static buildcraft.api.core.IZone getArea(buildcraft.api.robots.IRobotAccess robot, StatementSlot slot) {
        return null;
    }

    @Override
    public void actionActivate(IStatementContainer source, IStatementParameter[] parameters) {
    }

    @Override
    public int minParameters() {
        return 1;
    }

    @Override
    public int maxParameters() {
        return 1;
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return new StatementParameterMapLocation();
    }
}
