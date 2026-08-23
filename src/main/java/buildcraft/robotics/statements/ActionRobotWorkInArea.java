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

import buildcraft.api.core.IZone;
import buildcraft.api.items.IMapLocation;
import buildcraft.api.robots.IRobotAccess;
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

    /** The zone a WORK/LOAD_UNLOAD action slot's map-location parameter names (SPOT box or ZONE plan),
     *  or null when the parameter is empty or not a map. */
    public static IZone getArea(IRobotAccess robot, StatementSlot slot) {
        if (slot == null || slot.parameters.length < 1 || slot.parameters[0] == null) {
            return null;
        }
        ItemStack stack = slot.parameters[0].getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof IMapLocation map)) {
            return null;
        }
        return map.getZone(stack);
    }

    /** Which area kind this statement names — {@link EntityRobot} reads it to pick work vs load/unload. */
    public AreaType getAreaType() {
        return areaType;
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
