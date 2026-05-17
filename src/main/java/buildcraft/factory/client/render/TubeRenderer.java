/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client.render;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserRow;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;

import buildcraft.factory.tile.TileMiner;
import buildcraft.factory.tile.TileMiningWell;
import buildcraft.factory.tile.TilePump;

/**
 * Renders pump/mining well tubes as world-space lasers via RenderLevelStageEvent,
 * bypassing the BER pipeline to avoid chunk-section frustum culling — the tube
 * extends straight down from the source block and the destination chunk often
 * isn't in the BE renderer's set when the source chunk is.
 */
public class TubeRenderer {
    private static final Set<TileMiner> ACTIVE_MINERS = Collections.newSetFromMap(new WeakHashMap<>());

    private static final LaserType PUMP_TUBE;
    private static final LaserType MINING_WELL_TUBE;

    static {
        {
            SpriteHolder sprite = SpriteHolderRegistry.getHolder("buildcraftunofficial:block/pump/tube");
            LaserRow cap = new LaserRow(sprite, 0, 8, 8, 16);
            LaserRow middle = new LaserRow(sprite, 0, 0, 16, 8);
            PUMP_TUBE = new LaserType(cap, middle, new LaserRow[]{ middle }, null, cap);
        }
        {
            SpriteHolder sprite = SpriteHolderRegistry.getHolder("buildcraftunofficial:block/mining_well/tube");
            LaserRow cap = new LaserRow(sprite, 0, 8, 8, 16);
            LaserRow middle = new LaserRow(sprite, 0, 0, 16, 8);
            MINING_WELL_TUBE = new LaserType(cap, middle, new LaserRow[]{ middle }, null, cap);
        }
    }

    public static void addMiner(TileMiner miner) {
        ACTIVE_MINERS.add(miner);
    }

    public static void removeMiner(TileMiner miner) {
        ACTIVE_MINERS.remove(miner);
    }

    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (ACTIVE_MINERS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Prune stale entries — client-side level teardown (logout, dimension change)
        // doesn't reliably call setRemoved() on every BE, so TileMiners from a previous
        // world can linger in this WeakHashMap-backed set until GC. Without this, a
        // world reload can produce two render submissions per miner per frame at
        // slightly-different currentLength values (stale vs fresh smoothing state),
        // which the depth test then alternates between, producing visible per-frame
        // flicker at the segmentation boundaries.
        ACTIVE_MINERS.removeIf(miner -> miner.isRemoved() || miner.getLevel() != mc.level);

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        for (TileMiner miner : ACTIVE_MINERS) {
            double length = miner.getLength(partialTicks);
            if (length <= 0) continue;

            BlockPos pos = miner.getBlockPos();
            Vec3 start = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            Vec3 end = new Vec3(pos.getX() + 0.5, pos.getY() - length, pos.getZ() + 0.5);

            LaserType type = (miner instanceof TilePump) ? PUMP_TUBE : MINING_WELL_TUBE;
            // Tubes use world block-light per vertex (smooth gradient along the column
            // as it descends into darker rock) but NO per-face diffuse — the 4-arg
            // default's enableDiffuse=true gives top/bottom/sides each their own flat
            // brightness (1.0/0.5/0.8), which produces a visible discontinuity at face
            // edges that shifts with the camera angle and looks like "two systems
            // competing" against the per-vertex gradient. Explicit (false, false, 0)
            // = uniform face brightness, world-lit per vertex.
            LaserData_BC8 data = new LaserData_BC8(type, start, end, 1 / 16.0,
                false, false, 0);
            LaserRenderer_BC8.renderLaserStatic(poseStack, data, cameraPos);
        }
    }
}
