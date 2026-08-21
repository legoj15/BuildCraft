/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.world.entity.Entity;

import buildcraft.api.core.IZone;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.IEntityFilter;

/** Scans the robot's surroundings for the nearest entity matching {@code filter} (within {@code maxRange},
 *  narrowed to {@code zone} when non-null) and reports it as {@link #target}. Ported from 7.1.x
 *  {@code AIRobotSearchEntity}; the modern scan is the level's entity query (the Ph4
 *  {@code AIRobotFetchItem} pattern) with the registry's known-unreachable cache consulted first.
 *
 *  <p>Red-baseline skeleton: the entity scan lands in the combat step; until then the inherited
 *  {@code update()} terminates on the first cycle and {@link #target} stays null.</p> */
public class AIRobotSearchEntity extends AIRobot {

    public Entity target;

    private float maxRange;
    private IZone zone;
    private IEntityFilter filter;

    public AIRobotSearchEntity(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotSearchEntity(IRobotAccess iRobot, IEntityFilter iFilter, float iMaxRange, IZone iZone) {
        super(iRobot);

        filter = iFilter;
        maxRange = iMaxRange;
        zone = iZone;
    }

    @Override
    public boolean success() {
        return target != null;
    }
}
