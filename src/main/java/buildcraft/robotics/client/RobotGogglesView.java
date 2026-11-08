/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.core.item.ItemGoggles;
import buildcraft.robotics.entity.EntityRobot;

/**
 * Whether the local player is looking through Robot Goggles, for the robot's see-through charge outline, and which robot
 * they are looking at, for its status label. Client side only (it names {@link Minecraft}): {@code EntityRobot} is
 * loaded on the dedicated server, so it reaches this class only behind {@code level().isClientSide()}, and the answer
 * crosses the boundary as a plain {@code boolean} - the same split as {@code StallHighlights.canSeeLocal}, which keeps
 * the client player class out of every common signature.
 */
public final class RobotGogglesView {
    private RobotGogglesView() {}

    private static int cachedPlayerId = Integer.MIN_VALUE;
    private static int cachedTick;
    private static boolean cachedWorn;

    /** How far along the look direction a robot counts as looked at: the same range Goggles keep robots drawn to
     *  (the robot's 64-block tracking range, also the distance vanilla draws a name tag to), so the ray never picks a
     *  robot the client does not draw, and every drawn robot can be picked. */
    static final double LOOK_RANGE = EntityRobot.GOGGLES_RENDER_BLOCKS;
    /** Aiming slack around a robot's box: its cube is half a block wide, which at any distance is a few pixels, so the
     *  box is padded by a fixed amount plus 0.75% of the distance (about half a degree of aim at any range). */
    private static final double LOOK_MARGIN = 0.15;
    private static final double LOOK_MARGIN_PER_BLOCK = 0.0075;

    private static int lookPlayerId = Integer.MIN_VALUE;
    private static int lookTick;
    private static int lookedAtRobotId = -1;

    /** Whether the local player has Robot Goggles on. False before a world is joined. Every robot asks it once or twice
     *  a frame, and with an accessory mod installed the answer scans that mod's slots, so it is worked out once per
     *  player tick (the slots cannot change in between; the player's own tick, not the level's, which stops under
     *  {@code /tick freeze}). Keyed by entity id, not the player object, so no left world is kept alive. */
    public static boolean localPlayerWearsGoggles() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        if (mc.player.getId() != cachedPlayerId || mc.player.tickCount != cachedTick) {
            cachedPlayerId = mc.player.getId();
            cachedTick = mc.player.tickCount;
            cachedWorn = ItemGoggles.isWornBy(mc.player);
        }
        return cachedWorn;
    }

    /** Whether the local player wears Robot Goggles and is looking at {@code robot}: the one robot whose status label is
     *  shown. Worked out once per player tick (the robots asking are every one in sight, every frame), keyed by entity
     *  id like {@link #localPlayerWearsGoggles}. */
    public static boolean isLookedAt(EntityRobot robot) {
        if (!localPlayerWearsGoggles()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (player.getId() != lookPlayerId || player.tickCount != lookTick) {
            lookPlayerId = player.getId();
            lookTick = player.tickCount;
            EntityRobot found = findLookedAtRobot(player);
            lookedAtRobotId = found == null ? -1 : found.getId();
        }
        return robot.getId() == lookedAtRobotId;
    }

    /** The nearest robot along the player's look ray, up to {@link #LOOK_RANGE} blocks. Walls do not stop it: the Goggles
     *  show robots through them, so a robot behind a wall is looked at all the same. Not vanilla's crosshair pick, which
     *  reaches a few blocks, and robots fly. */
    @Nullable
    private static EntityRobot findLookedAtRobot(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(LOOK_RANGE));
        AABB search = new AABB(eye, end).inflate(LOOK_MARGIN + LOOK_MARGIN_PER_BLOCK * LOOK_RANGE);
        EntityRobot nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (EntityRobot robot : player.level().getEntitiesOfClass(EntityRobot.class, search, EntityRobot::isAlive)) {
            double distance = rayDistance(eye, end, robot.getBoundingBox());
            if (distance < nearestDistance) {
                nearest = robot;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** How far from {@code from} along the segment to {@code to} the segment enters {@code box} once the box is padded
     *  for aiming (see {@link #LOOK_MARGIN}), or infinity when it misses; zero when {@code from} is inside the padded box.
     *  The padding grows with the box's distance from {@code from}, so a far robot is as easy to aim at as a near one. */
    static double rayDistance(Vec3 from, Vec3 to, AABB box) {
        double margin = LOOK_MARGIN + LOOK_MARGIN_PER_BLOCK * from.distanceTo(box.getCenter());
        AABB padded = box.inflate(margin);
        if (padded.contains(from)) {
            return 0;
        }
        return padded.clip(from, to).map(from::distanceTo).orElse(Double.POSITIVE_INFINITY);
    }
}
