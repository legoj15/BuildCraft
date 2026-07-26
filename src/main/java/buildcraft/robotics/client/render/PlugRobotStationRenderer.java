/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client.render;

import java.util.EnumMap;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

import buildcraft.api.transport.pluggable.IPlugDynamicRenderer;
import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.SpriteUtil;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.RobotStationPluggable.RobotStationState;

/**
 * Draws the docking station's available/reserved/linked indicator as a small decal on the baked
 * post's outward face, sampled per-frame from {@link RobotStationPluggable#getRenderState()}. See
 * {@code KeyPlugRobotStation} for why this state lives here instead of in the baked model.
 */
public enum PlugRobotStationRenderer implements IPlugDynamicRenderer<RobotStationPluggable> {
    INSTANCE;

    private static final Map<RobotStationState, MutableQuad> CAPS = new EnumMap<>(RobotStationState.class);

    private static void initCache() {
        if (!CAPS.isEmpty()) {
            return;
        }
        CAPS.put(RobotStationState.AVAILABLE, buildCap("buildcraftunofficial:pipes/robot_station_available"));
        CAPS.put(RobotStationState.RESERVED, buildCap("buildcraftunofficial:pipes/robot_station_reserved"));
        CAPS.put(RobotStationState.LINKED, buildCap("buildcraftunofficial:pipes/robot_station_linked"));
    }

    /** A single quad on the post's outward-facing end (x = 4/16, just proud of the baked box so it
     *  doesn't z-fight), built WEST-canonical to match {@code PlugBakerSimple}'s rotation convention. */
    private static MutableQuad buildCap(String spritePath) {
        TextureAtlasSprite sprite = SpriteUtil.getSprite(spritePath);
        Vector3f center = new Vector3f(4.01f / 16f, 8f / 16f, 8f / 16f);
        Vector3f radius = new Vector3f(0f, 3f / 16f, 3f / 16f);
        ModelUtil.UvFaceData uv = ModelUtil.UvFaceData.from16(5f, 5f, 11f, 11f);
        MutableQuad q = ModelUtil.createFace(Direction.WEST, center, radius, uv);
        q.texFromSprite(sprite);
        q.setTint(-1);
        return q;
    }

    @Override
    public void render(RobotStationPluggable plug, double x, double y, double z, float partialTicks,
                        VertexConsumer bb, PoseStack ps) {
        initCache();
        MutableQuad cap = CAPS.get(plug.getRenderState());
        if (cap == null) {
            // NONE — no station resolved on this pluggable yet (onTick() hasn't run server-side).
            return;
        }

        ps.pushPose();
        ps.translate(x, y, z);
        ps.translate(0.5, 0.5, 0.5);
        switch (plug.side) {
            case EAST -> ps.mulPose(Axis.YP.rotationDegrees(180));
            case NORTH -> ps.mulPose(Axis.YP.rotationDegrees(-90));
            case SOUTH -> ps.mulPose(Axis.YP.rotationDegrees(90));
            case DOWN -> ps.mulPose(Axis.ZP.rotationDegrees(90));
            case UP -> ps.mulPose(Axis.ZP.rotationDegrees(-90));
            case WEST -> {
            }
        }
        ps.translate(-0.5, -0.5, -0.5);

        MutableQuad mq = new MutableQuad(cap);
        mq.lighti(15, 15);
        mq.render(ps.last(), bb);

        ps.popPose();
    }
}
