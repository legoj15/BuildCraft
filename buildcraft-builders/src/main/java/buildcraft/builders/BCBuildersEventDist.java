/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders;

import java.lang.ref.WeakReference;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import buildcraft.lib.client.render.laser.LaserBoxRenderer;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.misc.data.Box;

import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.client.BuildCraftLaserManager;

/** Event distribution for BCBuilders. Handles quarry tracking and rendering. */
public enum BCBuildersEventDist {
    INSTANCE;

    private final Map<Level, Deque<WeakReference<TileQuarry>>> allQuarries = new WeakHashMap<>();

    public synchronized void validateQuarry(TileQuarry quarry) {
        Deque<WeakReference<TileQuarry>> quarries =
            allQuarries.computeIfAbsent(quarry.getLevel(), k -> new LinkedList<>());
        quarries.add(new WeakReference<>(quarry));
    }

    public synchronized void invalidateQuarry(TileQuarry quarry) {
        Deque<WeakReference<TileQuarry>> quarries = allQuarries.get(quarry.getLevel());
        if (quarries == null) {
            return;
        }
        Iterator<WeakReference<TileQuarry>> iter = quarries.iterator();
        while (iter.hasNext()) {
            WeakReference<TileQuarry> ref = iter.next();
            TileQuarry pos = ref.get();
            if (pos == null || pos == quarry) {
                iter.remove();
            }
        }
    }

    /** Called from RenderLevelStageEvent to render quarry frame outlines and drill beams. */
    public void renderAllQuarries(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Deque<WeakReference<TileQuarry>> quarries = allQuarries.get(mc.level);
        if (quarries == null || quarries.isEmpty()) return;

        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        float partialTicks = 0f; // TODO: find correct 1.21.11 partial tick API

        Iterator<WeakReference<TileQuarry>> iter = quarries.iterator();
        while (iter.hasNext()) {
            WeakReference<TileQuarry> ref = iter.next();
            TileQuarry quarry = ref.get();
            if (quarry == null || quarry.isRemoved()) {
                iter.remove();
                continue;
            }
            renderQuarry(quarry, poseStack, cameraPos, partialTicks);
        }
    }

    private void renderQuarry(TileQuarry tile, PoseStack poseStack, Vec3 cameraPos, float partialTicks) {
        if (!tile.frameBox.isInitialized()) {
            return;
        }

        final BlockPos min = tile.frameBox.min();
        final BlockPos max = tile.frameBox.max();

        double yOffset = 1 + 4 / 16D;

        // Render laser from quarry to target block when breaking without drill
        if (tile.currentTask instanceof TileQuarry.TaskBreakBlock taskBreakBlock) {
            BlockPos pos = taskBreakBlock.breakPos;

            if (tile.drillPos == null) {
                if (taskBreakBlock.clientPower != 0) {
                    Vec3 from = VecUtil.convertCenter(tile.getBlockPos());
                    Vec3 to = VecUtil.convertCenter(pos);
                    LaserData_BC8 laser = new LaserData_BC8(BuildCraftLaserManager.POWER_LOW, from, to, 1 / 16.0);
                    LaserRenderer_BC8.renderLaserStatic(poseStack, laser, cameraPos);
                }
            } else {
                long power = (long) (
                    taskBreakBlock.prevClientPower +
                        (taskBreakBlock.clientPower - taskBreakBlock.prevClientPower) * (double) partialTicks
                );
                double value = (double) power / taskBreakBlock.getTarget();
                if (value < 0.9) {
                    value = 1 - value / 0.9;
                } else {
                    value = (value - 0.9) / 0.1;
                }
                double scaleMin = 0.5;
                double scaleMax = 1 + 4 / 16D;
                yOffset = scaleMin + value * (scaleMax - scaleMin);
            }
        }

        // Render quarry frame beams and drill
        if (tile.clientDrillPos != null && tile.prevClientDrillPos != null) {
            Vec3 interpolatedPos = tile.prevClientDrillPos.add(
                tile.clientDrillPos.subtract(tile.prevClientDrillPos).scale(partialTicks)
            );

            // Z-axis beams
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(BuildCraftLaserManager.STRIPES_WRITE,
                    new Vec3(interpolatedPos.x + 0.5, max.getY() + 0.5, interpolatedPos.z),
                    new Vec3(interpolatedPos.x + 0.5, max.getY() + 0.5, max.getZ() + 12 / 16D),
                    1 / 16D),
                cameraPos);
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(BuildCraftLaserManager.STRIPES_WRITE,
                    new Vec3(interpolatedPos.x + 0.5, max.getY() + 0.5, interpolatedPos.z),
                    new Vec3(interpolatedPos.x + 0.5, max.getY() + 0.5, min.getZ() + 4 / 16D),
                    1 / 16D),
                cameraPos);
            // X-axis beams
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(BuildCraftLaserManager.STRIPES_WRITE,
                    new Vec3(interpolatedPos.x, max.getY() + 0.5, interpolatedPos.z + 0.5),
                    new Vec3(max.getX() + 12 / 16D, max.getY() + 0.5, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos);
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(BuildCraftLaserManager.STRIPES_WRITE,
                    new Vec3(interpolatedPos.x, max.getY() + 0.5, interpolatedPos.z + 0.5),
                    new Vec3(min.getX() + 4 / 16D, max.getY() + 0.5, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos);
            // Vertical beam
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(BuildCraftLaserManager.STRIPES_WRITE,
                    new Vec3(interpolatedPos.x + 0.5, interpolatedPos.y + 1 + 4 / 16D, interpolatedPos.z + 0.5),
                    new Vec3(interpolatedPos.x + 0.5, max.getY() + 0.5, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos);
            // Drill beam
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(BuildCraftLaserManager.POWER_LOW,
                    new Vec3(interpolatedPos.x + 0.5, interpolatedPos.y + 1 + yOffset, interpolatedPos.z + 0.5),
                    new Vec3(interpolatedPos.x + 0.5, interpolatedPos.y + yOffset, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos);
        } else {
            // No drill yet — show the whole frame box as laser outline
            LaserBoxRenderer.renderLaserBoxStatic(poseStack, tile.frameBox, BuildCraftLaserManager.STRIPES_WRITE, true, cameraPos);
        }
    }
}
