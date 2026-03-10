/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.lib.engine.TileEngineBase_BC8;

import java.util.Map;

/**
 * Block Entity Renderer for all BuildCraft engines.
 * Renders the 4-part engine model: base plate, trunk, chamber, and piston head.
 */
public class RenderEngine_BC8 implements BlockEntityRenderer<TileEngineBase_BC8, EngineRenderState> {

    private static final float P = 1.0f / 16.0f; // pixel to block unit

    private final Identifier backTexture;
    private final Identifier sideTexture;
    private final Identifier chamberTexture;
    private final Map<EnumPowerStage, Identifier> trunkTextures;

    public RenderEngine_BC8(Identifier backTex, Identifier sideTex,
                            Identifier chamberTex, Map<EnumPowerStage, Identifier> trunkTexMap) {
        this.backTexture = backTex;
        this.sideTexture = sideTex;
        this.chamberTexture = chamberTex;
        this.trunkTextures = trunkTexMap;
    }

    @Override
    public EngineRenderState createRenderState() {
        return new EngineRenderState();
    }

    @Override
    public void submit(EngineRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        // Read engine data from the world directly since extractRenderState is not called
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos pos = state.blockPos;
        if (pos == null) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileEngineBase_BC8 engine)) return;

        float partialTick = 1.0f; // full tick — client-side animation handles interpolation
        Direction facing = engine.getOrientation();
        float progress = engine.getProgressClient(partialTick);
        EnumPowerStage powerStage = engine.getPowerStage();

        poseStack.pushPose();

        // Apply directional rotation - engine model is authored facing UP
        applyDirectionalRotation(poseStack, facing);

        float pProgress = progress * 8.0f * P; // 0 to 0.5 blocks of piston travel

        // Determine trunk texture from power stage
        Identifier trunkTex = trunkTextures.getOrDefault(powerStage, trunkTextures.get(EnumPowerStage.BLUE));

        // Get sprites from the block atlas
        TextureAtlasSprite backSprite = getSprite(backTexture);
        TextureAtlasSprite sideSprite = getSprite(sideTexture);
        TextureAtlasSprite trunkSprite = getSprite(trunkTex);
        TextureAtlasSprite chamberSprite = getSprite(chamberTexture);

        // Get combined light at the engine's position
        int light = LevelRenderer.getLightColor(level, pos);
        int overlay = OverlayTexture.NO_OVERLAY;

        // Get vertex consumer
        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(
                net.minecraft.client.renderer.rendertype.RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();

        // 1. Base plate: 0,0,0 to 1,0.25,1
        renderBox(buffer, pose, 0, 0, 0, 1, 0.25f, 1,
                backSprite, sideSprite, light, overlay);

        // 2. Trunk: 0.25,0.25,0.25 to 0.75,1,0.75 (static center pole)
        renderBox(buffer, pose, 0.25f, 0.25f, 0.25f, 0.75f, 1f, 0.75f,
                trunkSprite, trunkSprite, light, overlay);

        // 3. Chamber: 0.1875,0.25,0.1875 to 0.8125,0.25+pProgress,0.8125 (animated)
        float chamberTop = 0.25f + pProgress;
        if (pProgress > 0.001f) {
            renderBox(buffer, pose, 0.1875f, 0.25f, 0.1875f, 0.8125f, chamberTop, 0.8125f,
                    chamberSprite, chamberSprite, light, overlay);
        }

        // 4. Piston head: 0,0.25+pProgress,0 to 1,0.5+pProgress,1 (animated)
        renderBox(buffer, pose, 0, 0.25f + pProgress, 0, 1, 0.5f + pProgress, 1,
                backSprite, sideSprite, light, overlay);

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private void applyDirectionalRotation(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5f, 0.5f, 0.5f);
        switch (facing) {
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> { poseStack.mulPose(Axis.XP.rotationDegrees(90)); poseStack.mulPose(Axis.YP.rotationDegrees(180)); }
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90));
            case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(90));
            default -> {} // UP is default, no rotation needed
        }
        poseStack.translate(-0.5f, -0.5f, -0.5f);
    }

    private TextureAtlasSprite getSprite(Identifier texture) {
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        return atlas.getSprite(texture);
    }

    /**
     * Render a box with 6 faces. topBottomSprite is used for Y+ and Y-,
     * sideSprite is used for all 4 side faces.
     * Coordinates are in block units (0-1).
     */
    private void renderBox(VertexConsumer b, PoseStack.Pose pose,
                           float x0, float y0, float z0, float x1, float y1, float z1,
                           TextureAtlasSprite topBot, TextureAtlasSprite side,
                           int light, int overlay) {
        // UV coordinates in 0-1 range mapped to face dimensions (pixels)
        float uSideW = (x1 - x0) * 16f;
        float uSideH = (y1 - y0) * 16f;
        float zSideW = (z1 - z0) * 16f;
        float topW = (x1 - x0) * 16f;
        float topH = (z1 - z0) * 16f;

        // Top face (Y+) - normal (0,1,0)
        quadYP(b, pose, x0, y1, z0, x1, z1, topBot, 0, 0, topW, topH, light, overlay);
        // Bottom face (Y-) - normal (0,-1,0)
        quadYN(b, pose, x0, y0, z0, x1, z1, topBot, 0, 0, topW, topH, light, overlay);
        // North face (Z-) - normal (0,0,-1)
        quadZN(b, pose, x0, y0, z0, x1, y1, side, 0, 0, uSideW, uSideH, light, overlay);
        // South face (Z+) - normal (0,0,1)
        quadZP(b, pose, x0, y0, z1, x1, y1, side, 0, 0, uSideW, uSideH, light, overlay);
        // West face (X-) - normal (-1,0,0)
        quadXN(b, pose, x0, y0, z0, y1, z1, side, 0, 0, zSideW, uSideH, light, overlay);
        // East face (X+) - normal (1,0,0)
        quadXP(b, pose, x1, y0, z0, y1, z1, side, 0, 0, zSideW, uSideH, light, overlay);
    }

    // Y+ face: vertices at y, spanning x0..x1, z0..z1
    private void quadYP(VertexConsumer b, PoseStack.Pose p, float x0, float y, float z0, float x1, float z1,
                        TextureAtlasSprite s, float u0, float v0, float u1, float v1, int light, int ov) {
        float su0 = s.getU(u0/16f), su1 = s.getU(u1/16f), sv0 = s.getV(v0/16f), sv1 = s.getV(v1/16f);
        vertex(b, p, x0, y, z0, su0, sv0, 0, 1, 0, light, ov);
        vertex(b, p, x0, y, z1, su0, sv1, 0, 1, 0, light, ov);
        vertex(b, p, x1, y, z1, su1, sv1, 0, 1, 0, light, ov);
        vertex(b, p, x1, y, z0, su1, sv0, 0, 1, 0, light, ov);
    }

    // Y- face: vertices at y, spanning x0..x1, z0..z1
    private void quadYN(VertexConsumer b, PoseStack.Pose p, float x0, float y, float z0, float x1, float z1,
                        TextureAtlasSprite s, float u0, float v0, float u1, float v1, int light, int ov) {
        float su0 = s.getU(u0/16f), su1 = s.getU(u1/16f), sv0 = s.getV(v0/16f), sv1 = s.getV(v1/16f);
        vertex(b, p, x0, y, z1, su0, sv1, 0, -1, 0, light, ov);
        vertex(b, p, x0, y, z0, su0, sv0, 0, -1, 0, light, ov);
        vertex(b, p, x1, y, z0, su1, sv0, 0, -1, 0, light, ov);
        vertex(b, p, x1, y, z1, su1, sv1, 0, -1, 0, light, ov);
    }

    // Z- (north) face: vertices at z, spanning x0..x1, y0..y1
    private void quadZN(VertexConsumer b, PoseStack.Pose p, float x0, float y0, float z, float x1, float y1,
                        TextureAtlasSprite s, float u0, float v0, float u1, float v1, int light, int ov) {
        float su0 = s.getU(u0/16f), su1 = s.getU(u1/16f), sv0 = s.getV(v0/16f), sv1 = s.getV(v1/16f);
        vertex(b, p, x1, y1, z, su0, sv0, 0, 0, -1, light, ov);
        vertex(b, p, x1, y0, z, su0, sv1, 0, 0, -1, light, ov);
        vertex(b, p, x0, y0, z, su1, sv1, 0, 0, -1, light, ov);
        vertex(b, p, x0, y1, z, su1, sv0, 0, 0, -1, light, ov);
    }

    // Z+ (south) face: vertices at z, spanning x0..x1, y0..y1
    private void quadZP(VertexConsumer b, PoseStack.Pose p, float x0, float y0, float z, float x1, float y1,
                        TextureAtlasSprite s, float u0, float v0, float u1, float v1, int light, int ov) {
        float su0 = s.getU(u0/16f), su1 = s.getU(u1/16f), sv0 = s.getV(v0/16f), sv1 = s.getV(v1/16f);
        vertex(b, p, x0, y1, z, su0, sv0, 0, 0, 1, light, ov);
        vertex(b, p, x0, y0, z, su0, sv1, 0, 0, 1, light, ov);
        vertex(b, p, x1, y0, z, su1, sv1, 0, 0, 1, light, ov);
        vertex(b, p, x1, y1, z, su1, sv0, 0, 0, 1, light, ov);
    }

    // X- (west) face: vertices at x, spanning z0..z1, y0..y1
    private void quadXN(VertexConsumer b, PoseStack.Pose p, float x, float y0, float z0, float y1, float z1,
                        TextureAtlasSprite s, float u0, float v0, float u1, float v1, int light, int ov) {
        float su0 = s.getU(u0/16f), su1 = s.getU(u1/16f), sv0 = s.getV(v0/16f), sv1 = s.getV(v1/16f);
        vertex(b, p, x, y1, z0, su0, sv0, -1, 0, 0, light, ov);
        vertex(b, p, x, y0, z0, su0, sv1, -1, 0, 0, light, ov);
        vertex(b, p, x, y0, z1, su1, sv1, -1, 0, 0, light, ov);
        vertex(b, p, x, y1, z1, su1, sv0, -1, 0, 0, light, ov);
    }

    // X+ (east) face: vertices at x, spanning z0..z1, y0..y1
    private void quadXP(VertexConsumer b, PoseStack.Pose p, float x, float y0, float z0, float y1, float z1,
                        TextureAtlasSprite s, float u0, float v0, float u1, float v1, int light, int ov) {
        float su0 = s.getU(u0/16f), su1 = s.getU(u1/16f), sv0 = s.getV(v0/16f), sv1 = s.getV(v1/16f);
        vertex(b, p, x, y1, z1, su0, sv0, 1, 0, 0, light, ov);
        vertex(b, p, x, y0, z1, su0, sv1, 1, 0, 0, light, ov);
        vertex(b, p, x, y0, z0, su1, sv1, 1, 0, 0, light, ov);
        vertex(b, p, x, y1, z0, su1, sv0, 1, 0, 0, light, ov);
    }

    private void vertex(VertexConsumer b, PoseStack.Pose p,
                        float x, float y, float z,
                        float u, float v,
                        float nx, float ny, float nz,
                        int light, int overlay) {
        b.addVertex(p, x, y, z)
         .setColor(1.0f, 1.0f, 1.0f, 1.0f)
         .setUv(u, v)
         .setOverlay(overlay)
         .setLight(light)
         .setNormal(p, nx, ny, nz);
    }

    // --- Static helpers for creating trunk texture maps ---

    public static Map<EnumPowerStage, Identifier> defaultTrunkTextures() {
        return Map.of(
                EnumPowerStage.BLUE, Identifier.parse("buildcraftcore:block/engine/trunk_blue"),
                EnumPowerStage.GREEN, Identifier.parse("buildcraftcore:block/engine/trunk_green"),
                EnumPowerStage.YELLOW, Identifier.parse("buildcraftcore:block/engine/trunk_yellow"),
                EnumPowerStage.RED, Identifier.parse("buildcraftcore:block/engine/trunk_red"),
                EnumPowerStage.OVERHEAT, Identifier.parse("buildcraftcore:block/engine/trunk_overheat")
        );
    }
}
