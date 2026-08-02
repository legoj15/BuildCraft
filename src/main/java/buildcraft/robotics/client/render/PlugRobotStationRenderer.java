/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client.render;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import buildcraft.api.transport.pluggable.IPlugDynamicRenderer;

import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.render.LightUtil;
import buildcraft.lib.misc.SpriteUtil;

import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.RobotStationPluggable.RobotStationState;
import buildcraft.robotics.client.model.RobotStationModel;

/**
 * Re-emits the whole docking-station pedestal in the reserved or linked sprite, a hair proud of the
 * chunk-baked one, so the station's status reads from any angle exactly as it did in 1.7.10.
 *
 * <p>Nothing is drawn for NONE or AVAILABLE: 1.7.10 used one sprite for both, and the baked pedestal
 * already wears it. So the common case — every idle station in the world — costs zero quads here, and
 * the per-frame work only appears once a robot has actually claimed the station.
 *
 * <p>The state stays out of the baked model on purpose; see {@code KeyPlugRobotStation} for why a
 * state-varying baked key is a real FPS bug.
 */
public enum PlugRobotStationRenderer implements IPlugDynamicRenderer<RobotStationPluggable> {
    INSTANCE;

    /** Separation from the baked pedestal. 1/64 of a texel, so the UV projection ignores it. */
    private static final float PROUD = 1f / 1024f;

    private static final Map<RobotStationState, String> SPRITES = new EnumMap<>(Map.of(
        RobotStationState.RESERVED, "buildcraftunofficial:pipes/robot_station_reserved",
        RobotStationState.LINKED, "buildcraftunofficial:pipes/robot_station_linked"
    ));

    /** state → side → the 11 pedestal quads, pre-rotated and pre-shaded. */
    private static Map<RobotStationState, Map<Direction, MutableQuad[]>> cache;

    private static void initCache() {
        if (cache != null) {
            return;
        }
        Map<RobotStationState, Map<Direction, MutableQuad[]>> built = new EnumMap<>(RobotStationState.class);
        for (Map.Entry<RobotStationState, String> entry : SPRITES.entrySet()) {
            TextureAtlasSprite sprite = SpriteUtil.getSprite(entry.getValue());
            Map<Direction, MutableQuad[]> bySide = new EnumMap<>(Direction.class);
            for (Direction side : Direction.values()) {
                bySide.put(side, buildSide(sprite, side));
            }
            built.put(entry.getKey(), bySide);
        }
        cache = built;
    }

    /** Rotates the WEST-canonical pedestal onto {@code side} with the IDENTICAL call
     *  {@code PlugBakerSimple} makes, so baked and dynamic land on the same world positions by
     *  construction rather than by an equivalence someone has to re-derive.
     *
     *  <p>The two calls after the rotation are ordered and both load-bearing. {@code multShade} bakes
     *  the vanilla BLOCK diffuse curve into the vertex colours from the already-rotated normal — it
     *  reads the normal, so it has to run first, and without it the overlay would sit up to 20%
     *  darker than the baked pedestal underneath. Flattening the normals to straight up then
     *  neutralises the ENTITY diffuse term: plug renderers are handed
     *  {@code BCLibRenderTypes.cutoutBlockSheet()}, which is {@code entityCutoutCull} on every node,
     *  and that pipeline caps a face at 0.40–1.00 by orientation on top of whatever the lightmap says
     *  (docs/robotics-ph3-design.md amendment 7). A straight-up normal saturates the term to 1.0 on
     *  all six faces. Culling is by winding, not normal, so nothing downstream misses them.
     *
     *  <p>Deliberately NOT {@code setCalculatedNormal()} — it comes out inverted for
     *  {@code createFace}'s winding (see {@code PlugGateRenderer}). */
    private static MutableQuad[] buildSide(TextureAtlasSprite sprite, Direction side) {
        MutableQuad[] quads = RobotStationModel.buildPedestal(sprite, PROUD);
        for (MutableQuad q : quads) {
            q.rotate(Direction.WEST, side, 0.5f, 0.5f, 0.5f);
            q.multShade();
            q.normalf(0f, 1f, 0f);
        }
        return quads;
    }

    /** Drops the cached atlas references on a resource reload. */
    public static void onModelBake() {
        cache = null;
    }

    @Override
    public void render(RobotStationPluggable plug, double x, double y, double z, float partialTicks,
                        VertexConsumer bb, PoseStack ps) {
        MutableQuad[] quads = quadsFor(plug.getRenderState(), plug.side);
        if (quads == null) {
            // NONE or AVAILABLE — the baked pedestal already wears the right sprite.
            return;
        }

        // The baked pedestal is not uniformly lit: the chunk builder samples the neighbouring block
        // for the post's tip (the only face on the block boundary) and the pipe's own position for
        // the other ten interior faces. Sampling both keeps the overlay matched face for face.
        // Full-bright by default because the snapshot preview drives this renderer with a null world.
        int lightBody = LightUtil.FULL_BRIGHT;
        int lightTip = LightUtil.FULL_BRIGHT;
        if (plug.holder != null && plug.holder.getPipeWorld() != null) {
            Level world = plug.holder.getPipeWorld();
            BlockPos pos = plug.holder.getPipePos();
            lightBody = LightUtil.getLightCoords(world, pos);
            lightTip = LightUtil.getLightCoords(world, pos.relative(plug.side));
        }

        ps.pushPose();
        // No rotation here: the cache is already rotated, which keeps pose.normal() identity so it
        // cannot tilt the flattened normals back into the entity diffuse term.
        ps.translate(x, y, z);
        PoseStack.Pose pose = ps.last();
        for (int i = 0; i < quads.length; i++) {
            MutableQuad mq = new MutableQuad(quads[i]);
            mq.lighti(i == RobotStationModel.TIP_QUAD_INDEX ? lightTip : lightBody);
            mq.render(pose, bb);
        }
        ps.popPose();
    }

    private static MutableQuad[] quadsFor(RobotStationState state, Direction side) {
        if (!SPRITES.containsKey(state)) {
            return null;
        }
        initCache();
        return cache.get(state).get(side);
    }
}
