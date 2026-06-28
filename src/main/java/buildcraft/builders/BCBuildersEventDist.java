/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders;

import java.lang.ref.WeakReference;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.VecUtil;

import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.builders.tile.TileFiller;
import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.client.BuildCraftLaserManager;

/** Event distribution for BCBuilders. Handles quarry tracking and rendering. */
@SuppressWarnings({"rawtypes", "unchecked"})  // SnapshotBuilder<T>'s BreakTask/PlaceTask inner classes
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
    private final Map<Level, Deque<WeakReference<TileArchitectTable>>> allArchitectTables = new WeakHashMap<>();
    private final Map<Level, Deque<WeakReference<TileBuilder>>> allBuilders = new WeakHashMap<>();

    public synchronized void validateArchitectTable(TileArchitectTable table) {
        Deque<WeakReference<TileArchitectTable>> tables =
            allArchitectTables.computeIfAbsent(table.getLevel(), k -> new LinkedList<>());
        tables.add(new WeakReference<>(table));
    }

    public synchronized void invalidateArchitectTable(TileArchitectTable table) {
        Deque<WeakReference<TileArchitectTable>> tables = allArchitectTables.get(table.getLevel());
        if (tables == null) return;
        Iterator<WeakReference<TileArchitectTable>> iter = tables.iterator();
        while (iter.hasNext()) {
            WeakReference<TileArchitectTable> ref = iter.next();
            TileArchitectTable t = ref.get();
            if (t == null || t == table) {
                iter.remove();
            }
        }
    }

    public synchronized void validateBuilder(TileBuilder builder) {
        Deque<WeakReference<TileBuilder>> builders =
            allBuilders.computeIfAbsent(builder.getLevel(), k -> new LinkedList<>());
        builders.add(new WeakReference<>(builder));
    }

    public synchronized void invalidateBuilder(TileBuilder builder) {
        Deque<WeakReference<TileBuilder>> builders = allBuilders.get(builder.getLevel());
        if (builders == null) return;
        Iterator<WeakReference<TileBuilder>> iter = builders.iterator();
        while (iter.hasNext()) {
            WeakReference<TileBuilder> ref = iter.next();
            TileBuilder b = ref.get();
            if (b == null || b == builder) {
                iter.remove();
            }
        }
    }

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

    // --- "Destroying the world" advancement scan ---------------------------------------
    // Granted when one owner has ≥2 quarries simultaneously running on 64×64+ frames at
    // full power. The signal is sampled from TileQuarry.lastFullSpeedTick (stamped each
    // tick the quarry is in unrestricted-power mode while actively mining), with a tick
    // window to absorb engine sputter, drill-move ticks, and the scan throttle interval.
    public static final Identifier DESTROYING_THE_WORLD =
        Identifier.parse("buildcraftunofficial:destroying_the_world");

    /** Window inside which a quarry counts as "currently running at full speed." Two
     * scan-intervals wide so two consecutive scans cover any tick at which a quarry
     * was eligible — without this, the throttle could miss a transient pair. Public
     * so game tests can stamp {@link TileQuarry#lastFullSpeedTick} relative to it. */
    public static final long FULL_SPEED_WINDOW_TICKS = 40;
    /** Throttle: scan all worlds once per second. Must be < FULL_SPEED_WINDOW_TICKS / 2
     * so back-to-back scans see overlapping eligibility windows. */
    static final int SCAN_INTERVAL_TICKS = 20;

    /** How far (in blocks) each path-segment laser is shortened at both ends, so consecutive
     * segments leave a small visual gap at each waypoint instead of overlapping at the centers.
     * Matches 1.12.2 RenderBuilder.OFFSET. */
    private static final double PATH_LASER_INSET = 0.1;

    private long serverTickCounter = 0L;

    /** Pure predicate exposed for unit/game-test coverage: snapshot the registered
     * quarries on {@code level}, group them by owner UUID, and return the set of
     * owners with ≥2 quarries that all satisfy the 64×64+ frame + full-speed-within-
     * window condition at {@code currentTick}. Skips client-side levels and quarries
     * with no owner. Does not award anything. */
    public synchronized Set<UUID> findOwnersToAward(Level level, long currentTick) {
        Set<UUID> winners = new HashSet<>();
        if (level == null || level.isClientSide()) return winners;
        Deque<WeakReference<TileQuarry>> quarries = allQuarries.get(level);
        if (quarries == null || quarries.size() < 2) return winners;

        Map<UUID, Integer> countByOwner = new HashMap<>();
        Iterator<WeakReference<TileQuarry>> iter = quarries.iterator();
        while (iter.hasNext()) {
            TileQuarry q = iter.next().get();
            if (q == null || q.isRemoved()) {
                iter.remove();
                continue;
            }
            if (!q.frameBox.isInitialized()) continue;
            int sizeX = q.frameBox.max().getX() - q.frameBox.min().getX() + 1;
            int sizeZ = q.frameBox.max().getZ() - q.frameBox.min().getZ() + 1;
            if (sizeX < 64 || sizeZ < 64) continue;
            // Skip never-stamped quarries explicitly — otherwise `currentTick - Long.MIN_VALUE`
            // overflows into a large negative, which fails the `> WINDOW` test and lets an
            // unpowered freshly-placed quarry look "currently at full speed."
            long lastFullSpeed = q.getLastFullSpeedTick();
            if (lastFullSpeed == Long.MIN_VALUE) continue;
            if (currentTick - lastFullSpeed > FULL_SPEED_WINDOW_TICKS) continue;
            GameProfile owner = q.getOwner();
            if (owner == null || GameProfileUtil.getId(owner) == null) continue;
            int next = countByOwner.getOrDefault(GameProfileUtil.getId(owner), 0) + 1;
            countByOwner.put(GameProfileUtil.getId(owner), next);
            if (next >= 2) winners.add(GameProfileUtil.getId(owner));
        }
        return winners;
    }

    /** Called once per server tick. Throttled internally to SCAN_INTERVAL_TICKS to keep
     * server load near zero; bails immediately if no level has ≥2 quarries (the common
     * case). Owners that have already earned the advancement are no-op'd inside
     * {@link AdvancementUtil#unlockAdvancement} (PlayerAdvancements.award returns false). */
    public synchronized void onServerTick() {
        serverTickCounter++;
        if (serverTickCounter % SCAN_INTERVAL_TICKS != 0) return;
        for (Map.Entry<Level, Deque<WeakReference<TileQuarry>>> entry : allQuarries.entrySet()) {
            Level level = entry.getKey();
            Deque<WeakReference<TileQuarry>> quarries = entry.getValue();
            if (quarries == null || quarries.size() < 2) continue;
            long now = level.getGameTime();
            for (UUID winner : findOwnersToAward(level, now)) {
                AdvancementUtil.unlockAdvancement(winner, level, DESTROYING_THE_WORLD);
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

    /** Called from the render event to render quarry frame outlines and drill beams. On 26.1+ the
     *  source event is SubmitCustomGeometryEvent (immediate-mode rendering was removed); on earlier
     *  nodes it's RenderLevelStageEvent.AfterTranslucentBlocks. */
    //? if >=26.1 {
    public void renderAllQuarries(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event) {
    //?} elif >=1.21.10 {
    /*public void renderAllQuarries(RenderLevelStageEvent.AfterTranslucentBlocks event) {*/
    //?} else {
    /*public void renderAllQuarries(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;*/
    //?}
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Deque<WeakReference<TileQuarry>> quarries = allQuarries.get(mc.level);
        if (quarries == null || quarries.isEmpty()) {
            return;
        }

        //? if >=1.21.10 {
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        //?} else {
        /*Vec3 cameraPos = event.getCamera().getPosition();*/
        //?}
        PoseStack poseStack = event.getPoseStack();
        //? if >=26.1 {
        net.minecraft.client.renderer.SubmitNodeCollector collector = event.getSubmitNodeCollector();
        //?} else {
        /*Object collector = null;*/
        //?}
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        Iterator<WeakReference<TileQuarry>> iter = quarries.iterator();
        while (iter.hasNext()) {
            WeakReference<TileQuarry> ref = iter.next();
            TileQuarry quarry = ref.get();
            if (quarry == null || quarry.isRemoved()) {
                iter.remove();
                continue;
            }
            renderQuarry(quarry, poseStack, cameraPos, partialTicks, collector);
        }
    }

    //? if >=26.1 {
    private void renderQuarry(TileQuarry tile, PoseStack poseStack, Vec3 cameraPos, float partialTicks,
            net.minecraft.client.renderer.SubmitNodeCollector collector) {
    //?} else {
    /*private void renderQuarry(TileQuarry tile, PoseStack poseStack, Vec3 cameraPos, float partialTicks,
            Object collector) {*/
    //?}
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
                    LaserData_BC8 laserData = new LaserData_BC8(BuildCraftLaserManager.POWER_LOW, from, to, 1 / 16.0);
                    laser(poseStack, laserData, cameraPos, collector);
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
            laser(poseStack,
                new LaserData_BC8(FRAME,
                    new Vec3(interpolatedPos.x + 0.5, frameY, interpolatedPos.z),
                    new Vec3(interpolatedPos.x + 0.5, frameY, max.getZ() + 12 / 16D),
                    1 / 16D),
                cameraPos, collector);
            laser(poseStack,
                new LaserData_BC8(FRAME,
                    new Vec3(interpolatedPos.x + 0.5, frameY, interpolatedPos.z),
                    new Vec3(interpolatedPos.x + 0.5, frameY, min.getZ() + 4 / 16D),
                    1 / 16D),
                cameraPos, collector);
            // X-axis frame beams
            laser(poseStack,
                new LaserData_BC8(FRAME,
                    new Vec3(interpolatedPos.x, frameY, interpolatedPos.z + 0.5),
                    new Vec3(max.getX() + 12 / 16D, frameY, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos, collector);
            laser(poseStack,
                new LaserData_BC8(FRAME,
                    new Vec3(interpolatedPos.x, frameY, interpolatedPos.z + 0.5),
                    new Vec3(min.getX() + 4 / 16D, frameY, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos, collector);
            // Vertical column beam (drill column to top)
            laser(poseStack,
                new LaserData_BC8(FRAME_BOTTOM,
                    new Vec3(interpolatedPos.x + 0.5, interpolatedPos.y + 1 + 4 / 16D, interpolatedPos.z + 0.5),
                    new Vec3(interpolatedPos.x + 0.5, max.getY() + 0.5, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos, collector);
            // Drill head beam
            laser(poseStack,
                new LaserData_BC8(DRILL,
                    new Vec3(interpolatedPos.x + 0.5, interpolatedPos.y + 1 + yOffset, interpolatedPos.z + 0.5),
                    new Vec3(interpolatedPos.x + 0.5, interpolatedPos.y + yOffset, interpolatedPos.z + 0.5),
                    1 / 16D),
                cameraPos, collector);
        } else {
            // No drill yet — show the whole frame box as laser outline (caution stripes)
            laserBox(poseStack, tile.frameBox, BuildCraftLaserManager.STRIPES_WRITE, true, false, cameraPos, collector);
        }
    }

    // ── Node-uniform laser draw helpers ──────────────────────────────────────────────────────
    // Hide the >=26.1 (SubmitNodeCollector) vs <26.1 (immediate-mode) overload split behind one
    // call site each. The trailing {@code collector} arg is always present at call sites so the
    // code reads uniformly; on >=26.1 it carries the real SubmitNodeCollector, on <26.1 it's a
    // bound-to-null Object that the wrapper ignores (the <26.1 renderLaserStatic overload has no
    // collector). Keeps the many quarry/frame draw sites readable across nodes.
    //? if >=26.1 {
    private static void laser(PoseStack poseStack, LaserData_BC8 data, Vec3 cameraPos,
            net.minecraft.client.renderer.SubmitNodeCollector collector) {
        LaserRenderer_BC8.renderLaserStatic(poseStack, data, cameraPos, collector);
    }

    private static void laserBox(PoseStack poseStack, buildcraft.lib.misc.data.Box box, LaserType type,
            boolean center, boolean enableDiffuse, Vec3 cameraPos,
            net.minecraft.client.renderer.SubmitNodeCollector collector) {
        LaserBoxRenderer.renderLaserBoxStatic(poseStack, box, type, center, enableDiffuse, cameraPos, collector);
    }
    //?} else {
    /*private static void laser(PoseStack poseStack, LaserData_BC8 data, Vec3 cameraPos, Object collector) {
        LaserRenderer_BC8.renderLaserStatic(poseStack, data, cameraPos);
    }

    private static void laserBox(PoseStack poseStack, buildcraft.lib.misc.data.Box box, LaserType type,
            boolean center, boolean enableDiffuse, Vec3 cameraPos, Object collector) {
        LaserBoxRenderer.renderLaserBoxStatic(poseStack, box, type, center, enableDiffuse, cameraPos);
    }*/
    //?}

    /** Draws the floating 6-faced robot doodad (the breaking-block "robot") textured with the ROBOT
     *  sprite. Shared by the Builder and Filler break-task renderers. On 26.1+ the cube geometry is
     *  queued through the SubmitNodeCollector (immediate-mode rendering was removed); on earlier
     *  nodes it goes straight to a MultiBufferSource. */
    //? if >=26.1 {
    private static void renderRobotCube(PoseStack poseStack, Vec3 robotPos, Vec3 cameraPos, Minecraft mc,
            net.minecraft.client.renderer.SubmitNodeCollector collector) {
        net.minecraft.client.renderer.rendertype.RenderType renderType =
            net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(
                buildcraft.builders.BCBuildersSprites.ROBOT.getAtlasLocation());
        collector.submitCustomGeometry(poseStack, renderType,
            (pose, buffer) -> emitRobotCube(poseStack, robotPos, cameraPos, buffer));
    }
    //?} else {
    /*private static void renderRobotCube(PoseStack poseStack, Vec3 robotPos, Vec3 cameraPos, Minecraft mc,
            Object collector) {
        net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource =
            mc.renderBuffers().bufferSource();
        com.mojang.blaze3d.vertex.VertexConsumer buffer = bufferSource.getBuffer(
            net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(
                buildcraft.builders.BCBuildersSprites.ROBOT.getAtlasLocation()));
        emitRobotCube(poseStack, robotPos, cameraPos, buffer);
        bufferSource.endBatch();
    }*/
    //?}

    /** Emits the robot cube's 6 sprite-mapped faces into {@code buffer}, translating the captured
     *  poseStack to the camera-relative robot position. Pure geometry — node-uniform. */
    private static void emitRobotCube(PoseStack poseStack, Vec3 robotPos, Vec3 cameraPos,
            com.mojang.blaze3d.vertex.VertexConsumer buffer) {
        poseStack.pushPose();
        poseStack.translate(robotPos.x - cameraPos.x, robotPos.y - cameraPos.y, robotPos.z - cameraPos.z);
        int worldLight = buildcraft.lib.client.render.laser.LaserRenderer_BC8
            .computeLightmap(robotPos.x, robotPos.y, robotPos.z, 0);

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
    }

    /** Called from RenderLevelStageEvent to render architect table laser box outlines and the
     * fading green "digitizing" cubes for blocks currently being scanned. */
    //? if >=26.1 {
    public void renderAllArchitectTables(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event) {
    //?} elif >=1.21.10 {
    /*public void renderAllArchitectTables(RenderLevelStageEvent.AfterTranslucentBlocks event) {*/
    //?} else {
    /*public void renderAllArchitectTables(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;*/
    //?}
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        //? if >=1.21.10 {
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        //?} else {
        /*Vec3 cameraPos = event.getCamera().getPosition();*/
        //?}
        PoseStack poseStack = event.getPoseStack();
        //? if >=26.1 {
        net.minecraft.client.renderer.SubmitNodeCollector collector = event.getSubmitNodeCollector();
        //?} else {
        /*Object collector = null;*/
        //?}

        Deque<WeakReference<TileArchitectTable>> tables = allArchitectTables.get(mc.level);
        if (tables != null && !tables.isEmpty()) {
            Iterator<WeakReference<TileArchitectTable>> iter = tables.iterator();
            while (iter.hasNext()) {
                WeakReference<TileArchitectTable> ref = iter.next();
                TileArchitectTable table = ref.get();
                if (table == null || table.isRemoved()) {
                    iter.remove();
                    continue;
                }
                if (table.getIsValid() && table.markerBox && table.box.isInitialized()) {
                    laserBox(
                        poseStack, table.box,
                        BuildCraftLaserManager.STRIPES_READ,
                        true, false, cameraPos, collector
                    );
                }
            }
        }

        //? if >=26.1 {
        renderDigitizingCubes(cameraPos, poseStack, mc, collector);
        //?} else {
        /*renderDigitizingCubes(cameraPos, poseStack, mc);*/
        //?}
    }

    /** Standalone scan texture — bound via RenderType directly so we don't depend on the block
     * atlas stitching picking it up (atlas/blocks.json sources are hit-or-miss for textures that
     * aren't referenced by a block model). */
    private static final net.minecraft.resources.Identifier SCAN_TEXTURE =
            net.minecraft.resources.Identifier.parse("buildcraftunofficial:textures/block/scan.png");

    /** Draws a translucent green cube at every position reported by the server as being scanned
     * this second. Alpha fades with the entry's remaining lifetime so blocks pulse out over
     * ~2.5s, matching the 1.12.2 effect. */
    //? if >=26.1 {
    private void renderDigitizingCubes(Vec3 cameraPos, PoseStack poseStack, Minecraft mc,
            net.minecraft.client.renderer.SubmitNodeCollector collector) {
    //?} else {
    /*private void renderDigitizingCubes(Vec3 cameraPos, PoseStack poseStack, Minecraft mc) {*/
    //?}
        Map<BlockPos, Integer> scanned = buildcraft.builders.snapshot.ClientArchitectScans.INSTANCE.getScanned();
        if (scanned.isEmpty()) return;

        // Far-to-near sort so translucent faces blend correctly when the camera is inside or
        // near the scan volume.
        java.util.List<Map.Entry<BlockPos, Integer>> sorted = new java.util.ArrayList<>(scanned.entrySet());
        sorted.sort((a, b) -> Double.compare(
                Vec3.atCenterOf(b.getKey()).distanceToSqr(cameraPos),
                Vec3.atCenterOf(a.getKey()).distanceToSqr(cameraPos)
        ));

        net.minecraft.client.renderer.rendertype.RenderType renderType =
                net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(SCAN_TEXTURE);

        // Direct-texture binding → full 0..1 UV spans the entire scan.png file.
        buildcraft.lib.client.model.ModelUtil.UvFaceData uvs =
                new buildcraft.lib.client.model.ModelUtil.UvFaceData(0f, 0f, 1f, 1f);
        org.joml.Vector3f center = new org.joml.Vector3f(0.5f, 0.5f, 0.5f);
        org.joml.Vector3f radius = new org.joml.Vector3f(0.5f, 0.5f, 0.5f);

        //? if >=26.1 {
        // MC 26.1+ removed immediate-mode rendering — queue all cubes through the collector. The
        // captured poseStack's per-cube push/translate/pop runs synchronously inside the lambda.
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            for (Map.Entry<BlockPos, Integer> entry : sorted) {
                BlockPos pos = entry.getKey();
                int remaining = entry.getValue();
                int alpha = Math.max(0, Math.min(50, remaining));

                poseStack.pushPose();
                poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
                for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
                    buildcraft.lib.client.model.ModelUtil.createFace(face, center, radius, uvs)
                            .lighti(15, 15)
                            .colouri(255, 255, 255, alpha)
                            .render(poseStack.last(), buffer);
                }
                poseStack.popPose();
            }
        });
        //?} else {
        /*net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        com.mojang.blaze3d.vertex.VertexConsumer buffer = bufferSource.getBuffer(renderType);

        for (Map.Entry<BlockPos, Integer> entry : sorted) {
            BlockPos pos = entry.getKey();
            int remaining = entry.getValue();
            // 1.12.2 parity: alpha ramps 0..50 (not 0..255), so the cube stays translucent at
            // peak instead of briefly going opaque when it first spawns.
            int alpha = Math.max(0, Math.min(50, remaining));

            poseStack.pushPose();
            poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
            for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
                buildcraft.lib.client.model.ModelUtil.createFace(face, center, radius, uvs)
                        .lighti(15, 15)
                        .colouri(255, 255, 255, alpha)
                        .render(poseStack.last(), buffer);
            }
            poseStack.popPose();
        }
        bufferSource.endBatch(renderType);*/
        //?}
    }

    /** Called from RenderLevelStageEvent to render the Builder's laser box outline (the volume
     *  the blueprint is going to be built in), the path between path-provider waypoints, the
     *  floating robot cube while it's breaking blocks, and the break lasers from the robot to
     *  each target. Place-task block-throwing animation lives in
     *  {@link #renderAllBuildersCustomGeometry}. */
    //? if >=26.1 {
    public void renderAllBuilders(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event) {
    //?} elif >=1.21.10 {
    /*public void renderAllBuilders(RenderLevelStageEvent.AfterTranslucentBlocks event) {*/
    //?} else {
    /*public void renderAllBuilders(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;*/
    //?}
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Deque<WeakReference<TileBuilder>> builders = allBuilders.get(mc.level);
        if (builders == null || builders.isEmpty()) return;

        //? if >=1.21.10 {
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        //?} else {
        /*Vec3 cameraPos = event.getCamera().getPosition();*/
        //?}
        PoseStack poseStack = event.getPoseStack();
        //? if >=26.1 {
        net.minecraft.client.renderer.SubmitNodeCollector collector = event.getSubmitNodeCollector();
        //?} else {
        /*Object collector = null;*/
        //?}
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        Iterator<WeakReference<TileBuilder>> iter = builders.iterator();
        while (iter.hasNext()) {
            WeakReference<TileBuilder> ref = iter.next();
            TileBuilder builder = ref.get();
            if (builder == null || builder.isRemoved()) {
                iter.remove();
                continue;
            }
            // Only draw when there's an initialized box — before a snapshot is loaded the Builder
            // has no footprint to advertise.
            if (builder.getBox() != null && builder.getBox().isInitialized()) {
                laserBox(
                    poseStack, builder.getBox(),
                    BuildCraftLaserManager.STRIPES_WRITE,
                    true, false, cameraPos, collector
                );
            }
            // Path-based builders (fed by a path-marker chain consumed at placement) show
            // directional-striped laser lines connecting the waypoints so the player can see the
            // full route the Builder will traverse and which direction it's heading. Endpoints
            // are inset 0.1 blocks so consecutive segments leave a small visual gap at each
            // waypoint instead of overlapping at the centers. Matches 1.12.2 RenderBuilder.
            List<BlockPos> path = builder.path;
            if (path != null && path.size() >= 2) {
                for (int i = 1; i < path.size(); i++) {
                    Vec3 from = Vec3.atCenterOf(path.get(i - 1));
                    Vec3 to = Vec3.atCenterOf(path.get(i));
                    Vec3 dir = to.subtract(from).normalize().scale(PATH_LASER_INSET);
                    laser(poseStack,
                        new LaserData_BC8(
                            BuildCraftLaserManager.STRIPES_WRITE_DIRECTION,
                            from.add(dir),
                            to.subtract(dir),
                            1 / 16.1
                        ),
                        cameraPos, collector
                    );
                }
            }

            // Robot + break lasers. The active SnapshotBuilder (template or blueprint) exposes
            // clientBreakTasks / visualRobotPos that were synced from the server via
            // the block-entity update packet + getUpdateTag every 5 ticks and interpolated by
            // SnapshotBuilder.clientTick().
            buildcraft.builders.snapshot.SnapshotBuilder<?> active = builder.getBuilder();
            if (active == null) continue;

            Vec3 robotPos = active.visualRobotPos;
            if (robotPos == null) continue;
            if (active.visualPrevRobotPos != null) {
                robotPos = active.visualPrevRobotPos.add(
                    robotPos.subtract(active.visualPrevRobotPos).scale(partialTicks)
                );
            }

            // Robot cube renders only during breaking (matches Filler behaviour and 1.12.2).
            if (!active.clientBreakTasks.isEmpty()) {
                renderRobotCube(poseStack, robotPos, cameraPos, mc, collector);
            }

            // Break lasers fan out from the robot to every current break task. Laser colour
            // tracks how far along that block's break is (white → red as power fills).
            for (buildcraft.builders.snapshot.SnapshotBuilder.BreakTask breakTask : active.clientBreakTasks) {
                double progress = Math.max(0, Math.min(1,
                    breakTask.power * 1D / breakTask.getTarget()
                ));
                int powerIdx = (int) Math.round(progress * (BuildCraftLaserManager.POWERS.length - 1));
                laser(poseStack,
                    new LaserData_BC8(
                        BuildCraftLaserManager.POWERS[powerIdx],
                        robotPos.subtract(new Vec3(0, 0.27, 0)),
                        Vec3.atCenterOf(breakTask.pos),
                        1 / 16D
                    ),
                    cameraPos, collector
                );
            }
            // 1.21.11 has no SubmitCustomGeometryEvent, so the place-task throwing animation runs
            // here (on RenderLevelStageEvent) instead of in renderAllBuildersCustomGeometry.
            //? if <26.1 {
            /*renderPlaceTaskCubes(active, cameraPos, poseStack, partialTicks);*/
            //?}
        }
    }

    /** Called from SubmitCustomGeometryEvent (26.1) to render the block-throwing animation for each
     *  active place task — items travel from the Builder toward their destination block. On 1.21.11
     *  this event is absent; the animation runs from {@link #renderAllBuilders} via
     *  {@code renderPlaceTaskCubes} (sprite cubes) instead. */
    //? if >=26.1 {
    public void renderAllBuildersCustomGeometry(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Deque<WeakReference<TileBuilder>> builders = allBuilders.get(mc.level);
        if (builders == null || builders.isEmpty()) return;

        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        com.mojang.blaze3d.vertex.PoseStack poseStack = event.getPoseStack();
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        net.minecraft.client.renderer.SubmitNodeCollector collector = event.getSubmitNodeCollector();

        Iterator<WeakReference<TileBuilder>> iter = builders.iterator();
        while (iter.hasNext()) {
            WeakReference<TileBuilder> ref = iter.next();
            TileBuilder builder = ref.get();
            if (builder == null || builder.isRemoved()) {
                continue;
            }

            buildcraft.builders.snapshot.SnapshotBuilder<?> active = builder.getBuilder();
            if (active == null || active.clientPlaceTasks.isEmpty()) continue;

            renderPlaceTasks(active, cameraPos, poseStack, collector, partialTicks);
        }
    }
    //?}

    /** Extracted so the wildcard on {@code active} gets captured into {@code T}, which lets the
     *  {@code prevClientPlaceTasks} stream reference the same instance's {@code PlaceTask} type
     *  as {@link buildcraft.builders.snapshot.SnapshotBuilder#getPlaceTaskItemPos}. Without this
     *  the compiler treats each dereference of {@code ?} as a fresh capture and fails the
     *  method-reference type check. */
    //? if >=1.21.10 {
    private static <T extends buildcraft.builders.snapshot.ITileForSnapshotBuilder> void renderPlaceTasks(
            buildcraft.builders.snapshot.SnapshotBuilder<T> active,
            Vec3 cameraPos,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.SubmitNodeCollector collector,
            float partialTicks) {
        for (buildcraft.builders.snapshot.SnapshotBuilder<T>.PlaceTask placeTask : active.clientPlaceTasks) {
            Vec3 prevPos = active.prevClientPlaceTasks.stream()
                .filter(task -> task.pos.equals(placeTask.pos))
                .map(active::getPlaceTaskItemPos)
                .findFirst()
                .orElse(active.getPlaceTaskItemPos(placeTask));

            Vec3 pos = prevPos.add(
                active.getPlaceTaskItemPos(placeTask).subtract(prevPos).scale(partialTicks)
            );
            int light = buildcraft.lib.client.render.laser.LaserRenderer_BC8
                .computeLightmap(pos.x, pos.y, pos.z, 0);

            buildcraft.lib.client.render.ItemRenderUtil.beginItemBatch(poseStack, collector, light);
            for (Object itemObj : placeTask.items) {
                net.minecraft.world.item.ItemStack item = (net.minecraft.world.item.ItemStack) itemObj;
                buildcraft.lib.client.render.ItemRenderUtil.renderItemStack(
                    pos.x - cameraPos.x,
                    pos.y - cameraPos.y,
                    pos.z - cameraPos.z,
                    item,
                    1,
                    light,
                    net.minecraft.core.Direction.SOUTH,
                    null
                );
            }
        }
    }
    //?}

    /** Called from the render event to render filler laser box outlines. On 26.1+ the source event
     *  is SubmitCustomGeometryEvent (immediate-mode rendering was removed); on earlier nodes it's
     *  RenderLevelStageEvent.AfterTranslucentBlocks. */
    //? if >=26.1 {
    public void renderAllFillers(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event) {
    //?} elif >=1.21.10 {
    /*public void renderAllFillers(RenderLevelStageEvent.AfterTranslucentBlocks event) {*/
    //?} else {
    /*public void renderAllFillers(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;*/
    //?}
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Deque<WeakReference<TileFiller>> fillers = allFillers.get(mc.level);
        if (fillers == null || fillers.isEmpty()) return;

        //? if >=1.21.10 {
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        //?} else {
        /*Vec3 cameraPos = event.getCamera().getPosition();*/
        //?}
        PoseStack poseStack = event.getPoseStack();
        //? if >=26.1 {
        net.minecraft.client.renderer.SubmitNodeCollector collector = event.getSubmitNodeCollector();
        //?} else {
        /*Object collector = null;*/
        //?}

        Iterator<WeakReference<TileFiller>> iter = fillers.iterator();
        while (iter.hasNext()) {
            WeakReference<TileFiller> ref = iter.next();
            TileFiller filler = ref.get();
            if (filler == null || filler.isRemoved()) {
                iter.remove();
                continue;
            }
            if (filler.markerBox && filler.box.isInitialized()) {
                laserBox(
                    poseStack, filler.box,
                    BuildCraftLaserManager.STRIPES_WRITE,
                    true, false, cameraPos, collector
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

                    // Only render robot cube if we are breaking (1.12.2 parity: Filler shouldn't use robot for placing)
                    if (!filler.builder.clientBreakTasks.isEmpty()) {
                        renderRobotCube(poseStack, robotPos, cameraPos, mc, collector);
                    }

                    // Render break lasers from robot to each break task position
                    for (buildcraft.builders.snapshot.SnapshotBuilder.BreakTask breakTask : filler.builder.clientBreakTasks) {
                        double progress = Math.max(0, Math.min(1,
                            breakTask.power * 1D / breakTask.getTarget()
                        ));
                        int powerIdx = (int) Math.round(progress * (BuildCraftLaserManager.POWERS.length - 1));
                        laser(poseStack,
                            new LaserData_BC8(
                                BuildCraftLaserManager.POWERS[powerIdx],
                                robotPos.subtract(new Vec3(0, 0.27, 0)),
                                Vec3.atCenterOf(breakTask.pos),
                                1 / 16D
                            ),
                            cameraPos, collector
                        );
                    }
                }
                // 1.21.11 place-task throwing animation (no SubmitCustomGeometryEvent here). Outside
                // the robotPos/break-task guards above because placing is independent of breaking.
                //? if <26.1 {
                /*renderPlaceTaskCubes(filler.builder, cameraPos, poseStack, partialTicks);*/
                //?}
            }
        }
    }

    /** Called from SubmitCustomGeometryEvent (26.1) to render place-task items in world space via
     *  SubmitNodeCollector. On 1.21.11 the animation runs from {@link #renderAllFillers} via
     *  {@code renderPlaceTaskCubes} (sprite cubes) instead. */
    //? if >=26.1 {
    public void renderAllFillersCustomGeometry(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Deque<WeakReference<TileFiller>> fillers = allFillers.get(mc.level);
        if (fillers == null || fillers.isEmpty()) return;

        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        com.mojang.blaze3d.vertex.PoseStack poseStack = event.getPoseStack();
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        net.minecraft.client.renderer.SubmitNodeCollector collector = event.getSubmitNodeCollector();

        Iterator<WeakReference<TileFiller>> iter = fillers.iterator();
        while (iter.hasNext()) {
            WeakReference<TileFiller> ref = iter.next();
            TileFiller filler = ref.get();
            if (filler == null || filler.isRemoved() || filler.builder == null) {
                continue;
            }

            if (!filler.builder.clientPlaceTasks.isEmpty()) {
                for (buildcraft.builders.snapshot.SnapshotBuilder.PlaceTask placeTask : filler.builder.clientPlaceTasks) {
                    Vec3 prevPos = filler.builder.prevClientPlaceTasks.stream()
                        .filter(task -> task.pos.equals(placeTask.pos))
                        .map(filler.builder::getPlaceTaskItemPos)
                        .findFirst()
                        .orElse(filler.builder.getPlaceTaskItemPos(placeTask));
                    
                    Vec3 pos = prevPos.add(filler.builder.getPlaceTaskItemPos(placeTask).subtract(prevPos).scale(partialTicks));
                    int light = buildcraft.lib.client.render.laser.LaserRenderer_BC8.computeLightmap(pos.x, pos.y, pos.z, 0);

                    buildcraft.lib.client.render.ItemRenderUtil.beginItemBatch(poseStack, collector, light);
                    for (Object itemObj : placeTask.items) {
                        net.minecraft.world.item.ItemStack item = (net.minecraft.world.item.ItemStack) itemObj;
                        buildcraft.lib.client.render.ItemRenderUtil.renderItemStack(
                            pos.x - cameraPos.x,
                            pos.y - cameraPos.y,
                            pos.z - cameraPos.z,
                            item,
                            1, // stackCount
                            light,
                            net.minecraft.core.Direction.SOUTH,
                            null
                        );
                    }
                }
                // We use endItemBatch manually if ItemRenderUtil had it, wait, ItemRenderUtil just clears state or nothing.
                // In NeoForge 1.21.11, ItemRenderUtil.beginItemBatch state is cleared or overwritten per call.
            }
        }
    }
    //?}

    // ── place-task throwing animation (<26.1) ────────────────────────────────────────────────
    // SubmitCustomGeometryEvent (the 26.1 per-frame custom-geometry submit that hands you a
    // SubmitNodeCollector) doesn't exist below 26.1, so the animation runs from renderAllBuilders /
    // renderAllFillers (both already on RenderLevelStageEvent), which fetch a MultiBufferSource via
    // mc.renderBuffers().bufferSource() themselves. PlaceTaskCubeRenderer draws BLOCK items — the
    // overwhelmingly common builder/filler case — as their real block model
    // (BlockRenderDispatcher.renderSingleBlock); only non-block items fall back to a particle-sprite
    // cube. That fallback is NOT for lack of a MultiBufferSource: it's because a fully-resolved
    // ItemStackRenderState can only be drawn via submit(..., SubmitNodeCollector, ...) (no collector at
    // RenderLevelStageEvent) and the high-level ItemRenderer.renderStatic was removed on 1.21.10/1.21.11,
    // leaving no clean public path to a full item model here. (On 1.21.1, renderStatic still exists —
    // see the ItemRenderUtil/PlaceTaskCubeRenderer <1.21.10 branches.)
    //? if <26.1 {
    /*private static <T extends buildcraft.builders.snapshot.ITileForSnapshotBuilder> void renderPlaceTaskCubes(
            buildcraft.builders.snapshot.SnapshotBuilder<T> active, Vec3 cameraPos,
            PoseStack poseStack, float partialTicks) {
        for (buildcraft.builders.snapshot.SnapshotBuilder<T>.PlaceTask placeTask : active.clientPlaceTasks) {
            Vec3 prevPos = active.prevClientPlaceTasks.stream()
                .filter(task -> task.pos.equals(placeTask.pos))
                .map(active::getPlaceTaskItemPos)
                .findFirst()
                .orElse(active.getPlaceTaskItemPos(placeTask));
            Vec3 pos = prevPos.add(
                active.getPlaceTaskItemPos(placeTask).subtract(prevPos).scale(partialTicks));
            for (Object itemObj : placeTask.items) {
                // Render via a separate client-only class — keeps the ClientLevel-loading item
                // rendering OUT of BCBuildersEventDist, which is loaded on the dedicated server (its
                // per-server-tick advancement scan) and would otherwise crash there on first tick.
                buildcraft.builders.client.render.PlaceTaskCubeRenderer.renderItemCube(
                    (net.minecraft.world.item.ItemStack) itemObj, pos, cameraPos, poseStack);
            }
        }
    }*/
    //?}
}
