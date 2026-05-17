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
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;

import buildcraft.silicon.block.BlockLaser;
import buildcraft.silicon.tile.TileLaser;

/**
 * Renders laser beams from TileLaser blocks to their target positions via
 * RenderLevelStageEvent rather than the BER pipeline — cross-block beams need
 * world-space rendering so they aren't tied to a single block's chunk section
 * (see the analogous note in RenderQuarry).
 * Faithful port of 1.12.2's FastTESR&lt;TileLaser&gt; visual behaviour:
 * direction-aware emitter offset, avg &gt; 200_000 mj/t threshold,
 * linear POWERS[index] colour quantization.
 */
public class RenderLaser {
    private static final int MAX_POWER = BuildCraftLaserManager.POWERS.length - 1;

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

        // Prune stale entries — client-side level teardown (logout, dimension change)
        // doesn't reliably call setRemoved() on every BE, so TileLasers from a previous
        // world can linger in this WeakHashMap-backed set until GC. Without this,
        // a world reload leaves a ghost laser stuck at its pre-save laserPos.
        ACTIVE_LASERS.removeIf(laser -> laser.isRemoved() || laser.getLevel() != mc.level);

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;

        for (TileLaser laser : ACTIVE_LASERS) {
            Vec3 target = laser.laserPos;
            if (target == null) continue;

            long avg = laser.getAverageClient();
            if (avg <= 200_000) continue;
            avg += 200_000;

            // Direction-aware start offset: emit from the red face of the laser block.
            Direction side = laser.getBlockState().getValue(BlockLaser.FACING);
            Vec3 offset = new Vec3(0.5, 0.5, 0.5).add(
                Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(4 / 16D));
            Vec3 start = Vec3.atLowerCornerOf(laser.getBlockPos()).add(offset);

            int index = (int) (avg * MAX_POWER / laser.getMaxPowerPerTick());
            if (index > MAX_POWER) index = MAX_POWER;

            // Lasers are self-illuminating — fullbright + no face shading, like the
            // pre-port look (and unlike the 1.12.2 FastTESR default which used the
            // shared 4-arg form). Explicit args bypass the diffuse/world-light defaults
            // that the tube/quarry/etc. inherit from the 4-arg form.
            LaserData_BC8 data = new LaserData_BC8(
                BuildCraftLaserManager.POWERS[index], start, target, 1.0 / 16.0,
                false, false, 15);

            LaserRenderer_BC8.renderLaserStatic(poseStack, data, cameraPos);
        }
    }
}
