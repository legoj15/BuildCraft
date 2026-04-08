/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.client;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;

import buildcraft.silicon.tile.TileLaser;

/**
 * Renders laser beams from TileLaser blocks to their target positions.
 * Uses the existing LaserRenderer_BC8 infrastructure with POWER_* laser types.
 */
public class RenderLaser {
    /** Set of all client-side TileLaser instances that should be rendered. */
    private static final Set<TileLaser> ACTIVE_LASERS = Collections.newSetFromMap(new WeakHashMap<>());

    /** Called by TileLaser when it loads on the client. */
    public static void addLaser(TileLaser laser) {
        ACTIVE_LASERS.add(laser);
    }

    /** Called by TileLaser when it is removed on the client. */
    public static void removeLaser(TileLaser laser) {
        ACTIVE_LASERS.remove(laser);
    }

    /** Returns the number of tracked laser instances (for debug). */
    public static int getActiveCount() {
        return ACTIVE_LASERS.size();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (ACTIVE_LASERS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;

        for (TileLaser laser : ACTIVE_LASERS) {
            Vec3 target = laser.laserPos;
            if (target == null) continue;
            if (laser.isRemoved()) continue;

            // Beam start: centre of the laser block face (direction-dependent would be
            // better, but centre works as a first pass)
            Vec3 start = Vec3.atCenterOf(laser.getBlockPos());

            // Choose laser type based on power level
            LaserType type = getLaserTypeForPower(laser);

            LaserData_BC8 data = new LaserData_BC8(type, start, target, 1.0 / 16.0,
                true, false, 15);

            LaserRenderer_BC8.renderLaserStatic(poseStack, data, cameraPos);
        }
    }

    private static LaserType getLaserTypeForPower(TileLaser laser) {
        long avg = laser.getAverageClient();
        long max = laser.getMaxPowerPerTick();
        if (max <= 0) return BuildCraftLaserManager.POWER_LOW;

        double ratio = (double) avg / max;
        if (ratio > 0.75) return BuildCraftLaserManager.POWER_FULL;
        if (ratio > 0.5) return BuildCraftLaserManager.POWER_HIGH;
        if (ratio > 0.25) return BuildCraftLaserManager.POWER_MED;
        return BuildCraftLaserManager.POWER_LOW;
    }
}
