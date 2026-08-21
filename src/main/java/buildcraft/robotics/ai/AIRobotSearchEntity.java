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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.IZone;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.IEntityFilter;

/** Scans the robot's surroundings for the nearest entity matching {@code filter} (within {@code maxRange},
 *  narrowed to {@code zone} when non-null) and reports it as {@link #target}. Ported from 7.1.x
 *  {@code AIRobotSearchEntity}; the modern scan is the level's entity query (the Ph4
 *  {@code AIRobotFetchItem} pattern) with the registry's known-unreachable cache consulted first. */
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
    public void start() {
        Level level = robot.level();
        if (level == null) {
            terminate();
            return;
        }

        Vec3 center = robot.position();
        double previousDistance = Double.MAX_VALUE;
        AABB box = AABB.ofSize(center, maxRange * 2, maxRange * 2, maxRange * 2);

        for (Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
            if (entity.isRemoved()
                    || robot.isKnownUnreachable(entity)
                    || (zone != null && !zone.contains(entity.position()))
                    || !filter.matches(entity)) {
                continue;
            }

            double dx = entity.getX() - center.x;
            double dy = entity.getY() - center.y;
            double dz = entity.getZ() - center.z;
            double sqrDistance = dx * dx + dy * dy + dz * dz;
            double maxDistance = maxRange * maxRange;

            if (sqrDistance >= maxDistance) {
                continue;
            }

            if (target == null || sqrDistance < previousDistance) {
                previousDistance = sqrDistance;
                target = entity;
            }
        }

        terminate();
    }

    @Override
    public boolean success() {
        return target != null;
    }

    @Override
    public long getPowerCost() {
        // Ph5 cost table: 2 RF per tick, at the 1 RF = 100_000 micro-MJ bridge; 7.1.x left this AI on
        // the base default cost (no getPowerCost override in the 7.1.x source).
        return 200_000;
    }
}
