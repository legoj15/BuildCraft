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

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import buildcraft.lib.client.render.laser.LaserBoxRenderer;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserRow;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.VecUtil;

import buildcraft.builders.tile.TileFiller;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.client.BuildCraftLaserManager;

/** Event distribution for BCBuilders. Handles quarry tracking and rendering. */
public enum BCBuildersEventDist {
    INSTANCE;

    // Quarry-specific laser types matching 1.12.2 RenderQuarry
    public static final LaserType FRAME;
    public static final LaserType FRAME_BOTTOM;
    public static final LaserType DRILL;

    static {
        {
            SpriteHolder sprite = SpriteHolderRegistry.getHolder("buildcraftunofficial:block/frame/default");
            LaserRow capStart = new LaserRow(sprite, 0, 0, 0, 0);
            LaserRow start = null;
            LaserRow[] middle = { new LaserRow(sprite, 0, 4, 16, 12) };
            LaserRow end = new LaserRow(sprite, 0, 4, 16, 12);
            LaserRow capEnd = new LaserRow(sprite, 0, 0, 0, 0);
            FRAME = new LaserType(capStart, start, middle, end, capEnd);
        }
        {
            SpriteHolder sprite = SpriteHolderRegistry.getHolder("buildcraftunofficial:block/frame/default");
            LaserRow capStart = new LaserRow(sprite, 0, 0, 0, 0);
            LaserRow start = null;
            LaserRow[] middle = { new LaserRow(sprite, 0, 4, 16, 12) };
            LaserRow end = new LaserRow(sprite, 0, 4, 16, 12);
            LaserRow capEnd = new LaserRow(sprite, 4, 4, 12, 12);
            FRAME_BOTTOM = new LaserType(capStart, start, middle, end, capEnd);
        }
        {
            SpriteHolder sprite = SpriteHolderRegistry.getHolder("buildcraftunofficial:block/quarry/drill");
            LaserRow capStart = new LaserRow(sprite, 6, 0, 10, 4);
            LaserRow start = null;
            LaserRow[] middle = { new LaserRow(sprite, 0, 0, 16, 4) };
            LaserRow end = null;
            LaserRow capEnd = new LaserRow(sprite, 6, 0, 10, 4);
            DRILL = new LaserType(capStart, start, middle, end, capEnd);
        }
    }

    private final Map<Level, Deque<WeakReference<TileQuarry>>> allQuarries = new WeakHashMap<>();
    private final Map<Level, Deque<WeakReference<TileFiller>>> allFillers = new WeakHashMap<>();

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

    public synchronized void validateFiller(TileFiller filler) {
        Deque<WeakReference<TileFiller>> fillers =
            allFillers.computeIfAbsent(filler.getLevel(), k -> new LinkedList<>());
        fillers.add(new WeakReference<>(filler));
    }

    public synchronized void invalidateFiller(TileFiller filler) {
        Deque<WeakReference<TileFiller>> fillers = allFillers.get(filler.getLevel());
        if (fillers == null) return;
        Iterator<WeakReference<TileFiller>> iter = fillers.iterator();
        while (iter.hasNext()) {
            WeakReference<TileFiller> ref = iter.next();
            TileFiller f = ref.get();
            if (f == null || f == filler) {
                iter.remove();
            }
        }
    }

    /** Called from RenderLevelStageEvent to render quarry frame outlines and drill beams. */
    public void renderAllQuarries(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Deque<WeakReference<TileQuarry>> quarries = allQuarries.get(mc.level);
        if (quarries == null || quarries.isEmpty()) {
            return;
        }

        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

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
                // Compute AABB-based yOffset matching 1.12.2 scaleMin logic
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

            double frameY = max.getY() + 0.5;

            // Z-axis frame beams
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(FRAME,
                    new Vec3(interpolatedPos.x + 0.5, frameY, interpolatedPos.z),
                    new Vec3(interpolatedPos.x + 0.5, frameY, max.getZ() + 12 / 16D),
                    1 / 16D),
                cameraPos);
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(FRAME,
                    new Vec3(interpolatedPos.x + 0.5, frameY, interpolatedPos.z),
                    new Vec3(interpolatedPos.x + 0.5, frameY, min.getZ() + 4 / 16D),
                    1 / 16D),
                cameraPos);
            // X-axis frame beams
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(FRAME,
                    new Vec3(interpolatedPos.x, frameY, interpolatedPos.z + 0.5),
                    new Vec3(max.getX() + 12 / 16D, frameY, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos);
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(FRAME,
                    new Vec3(interpolatedPos.x, frameY, interpolatedPos.z + 0.5),
                    new Vec3(min.getX() + 4 / 16D, frameY, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos);
            // Vertical column beam (drill column to top)
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(FRAME_BOTTOM,
                    new Vec3(interpolatedPos.x + 0.5, interpolatedPos.y + 1 + 4 / 16D, interpolatedPos.z + 0.5),
                    new Vec3(interpolatedPos.x + 0.5, max.getY() + 0.5, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos);
            // Drill head beam
            LaserRenderer_BC8.renderLaserStatic(poseStack,
                new LaserData_BC8(DRILL,
                    new Vec3(interpolatedPos.x + 0.5, interpolatedPos.y + 1 + yOffset, interpolatedPos.z + 0.5),
                    new Vec3(interpolatedPos.x + 0.5, interpolatedPos.y + yOffset, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos);
        } else {
            // No drill yet — show the whole frame box as laser outline (caution stripes)
            LaserBoxRenderer.renderLaserBoxStatic(poseStack, tile.frameBox, BuildCraftLaserManager.STRIPES_WRITE, true, false, cameraPos);
        }
    }

    /** Called from RenderLevelStageEvent to render filler laser box outlines. */
    public void renderAllFillers(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Deque<WeakReference<TileFiller>> fillers = allFillers.get(mc.level);
        if (fillers == null || fillers.isEmpty()) return;

        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();

        Iterator<WeakReference<TileFiller>> iter = fillers.iterator();
        while (iter.hasNext()) {
            WeakReference<TileFiller> ref = iter.next();
            TileFiller filler = ref.get();
            if (filler == null || filler.isRemoved()) {
                iter.remove();
                continue;
            }
            if (filler.markerBox && filler.box.isInitialized()) {
                LaserBoxRenderer.renderLaserBoxStatic(
                    poseStack, filler.box,
                    BuildCraftLaserManager.STRIPES_WRITE,
                    true, false, cameraPos
                );
            }

            // Render the robot doodad and its break lasers
            if (filler.builder != null) {
                float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                Vec3 robotPos = filler.builder.visualRobotPos;
                if (robotPos != null) {
                    // Interpolate robot position for smooth movement
                    if (filler.builder.visualPrevRobotPos != null) {
                        robotPos = filler.builder.visualPrevRobotPos.add(
                            robotPos.subtract(filler.builder.visualPrevRobotPos).scale(partialTicks)
                        );
                    }

                    // Render robot cube
                    net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
                    com.mojang.blaze3d.vertex.VertexConsumer buffer = bufferSource.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));

                    poseStack.pushPose();
                    poseStack.translate(robotPos.x - cameraPos.x, robotPos.y - cameraPos.y, robotPos.z - cameraPos.z);
                    int worldLight = buildcraft.lib.client.render.laser.LaserRenderer_BC8.computeLightmap(robotPos.x, robotPos.y, robotPos.z, 0);

                    int i = 0;
                    for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
                        buildcraft.lib.client.model.ModelUtil.createFace(
                            face,
                            new org.joml.Vector3f(0f, 0f, 0f),
                            new org.joml.Vector3f(4 / 16F, 4 / 16F, 4 / 16F),
                            new buildcraft.lib.client.model.ModelUtil.UvFaceData(
                                buildcraft.builders.BCBuildersSprites.ROBOT.getInterpU((i * 8) / 64D),
                                buildcraft.builders.BCBuildersSprites.ROBOT.getInterpV(0 / 64D),
                                buildcraft.builders.BCBuildersSprites.ROBOT.getInterpU(((i + 1) * 8) / 64D),
                                buildcraft.builders.BCBuildersSprites.ROBOT.getInterpV(8 / 64D)
                            )
                        )
                        .lighti(worldLight)
                        .render(poseStack.last(), buffer);
                        i++;
                    }
                    poseStack.popPose();
                    bufferSource.endBatch();

                    // Render break lasers from robot to each break task position
                    for (buildcraft.builders.snapshot.SnapshotBuilder.BreakTask breakTask : filler.builder.clientBreakTasks) {
                        double progress = Math.max(0, Math.min(1,
                            breakTask.power * 1D / breakTask.getTarget()
                        ));
                        int powerIdx = (int) Math.round(progress * (BuildCraftLaserManager.POWERS.length - 1));
                        LaserRenderer_BC8.renderLaserStatic(poseStack,
                            new LaserData_BC8(
                                BuildCraftLaserManager.POWERS[powerIdx],
                                robotPos.subtract(new Vec3(0, 0.27, 0)),
                                Vec3.atCenterOf(breakTask.pos),
                                1 / 16D
                            ),
                            cameraPos
                        );
                    }
                }
            }
        }
    }
}
