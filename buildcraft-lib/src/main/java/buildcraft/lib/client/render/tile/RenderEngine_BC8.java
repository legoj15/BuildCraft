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
import net.minecraft.client.renderer.Sheets;
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
import net.minecraft.world.phys.Vec3;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.lib.engine.TileEngineBase_BC8;

import java.util.Map;

/**
 * Block Entity Renderer for all BuildCraft engines.
 * Renders the 4-part engine model: base plate, trunk, chamber, and piston head.
 */
public class RenderEngine_BC8 implements BlockEntityRenderer<TileEngineBase_BC8, EngineRenderState> {

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
        // Derive block position from PoseStack translation + camera position
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        // The PoseStack is pre-translated to (blockX - camX, blockY - camY, blockZ - camZ)
        // Extract position using the camera position from cameraState
        Vec3 cameraPos = cameraState.pos;
        org.joml.Vector3f translation = new org.joml.Vector3f();
        poseStack.last().pose().getTranslation(translation);
        BlockPos pos = BlockPos.containing(
                cameraPos.x + translation.x,
                cameraPos.y + translation.y,
                cameraPos.z + translation.z);

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileEngineBase_BC8 engine)) return;

        Direction facing = engine.getOrientation();
        float progress = engine.getProgressClient(1.0f);
        EnumPowerStage powerStage = engine.getPowerStage();

        poseStack.pushPose();

        // Apply directional rotation - engine model is authored facing UP
        applyDirectionalRotation(poseStack, facing);

        float pProgress = progress * 0.5f; // 0 to 0.5 blocks of piston travel

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

        // Use Sheets.cutoutBlockSheet() for proper block-style rendering (no entity diffuse)
        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();

        // --- Engine geometry (in block units, 0-1) ---

        // 1. Base plate: 0,0,0 to 1,0.25,1
        renderBox(buffer, pose, 0, 0, 0, 1, 0.25f, 1,
                backSprite, sideSprite, light, overlay);

        // 2. Trunk: 0.25,0.25,0.25 to 0.75,1.0,0.75 (static center pole)
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
            case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
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
     * Render a box with 6 faces using CCW winding (viewed from outside).
     * Uses Minecraft-standard face shade factors for block lighting.
     */
    private void renderBox(VertexConsumer b, PoseStack.Pose pose,
                           float x0, float y0, float z0, float x1, float y1, float z1,
                           TextureAtlasSprite topBot, TextureAtlasSprite side,
                           int light, int overlay) {
        // UV in pixels (0-16 scale) 
        float tw = (x1 - x0) * 16f;
        float th = (z1 - z0) * 16f;
        float sw = (x1 - x0) * 16f;
        float sh = (y1 - y0) * 16f;
        float szw = (z1 - z0) * 16f;

        // Top (Y+) - shade 1.0
        face(b, pose, topBot, light, overlay, 1.0f,
                x0, y1, z0,  0, 0,
                x0, y1, z1,  0, th,
                x1, y1, z1,  tw, th,
                x1, y1, z0,  tw, 0);
        // Bottom (Y-) - shade 0.5
        face(b, pose, topBot, light, overlay, 0.5f,
                x1, y0, z0,  0, 0,
                x1, y0, z1,  0, th,
                x0, y0, z1,  tw, th,
                x0, y0, z0,  tw, 0);
        // North (Z-) - shade 0.8
        face(b, pose, side, light, overlay, 0.8f,
                x0, y1, z0,  0, 0,
                x1, y1, z0,  sw, 0,
                x1, y0, z0,  sw, sh,
                x0, y0, z0,  0, sh);
        // South (Z+) - shade 0.8
        face(b, pose, side, light, overlay, 0.8f,
                x1, y1, z1,  0, 0,
                x0, y1, z1,  sw, 0,
                x0, y0, z1,  sw, sh,
                x1, y0, z1,  0, sh);
        // West (X-) - shade 0.6
        face(b, pose, side, light, overlay, 0.6f,
                x0, y1, z1,  0, 0,
                x0, y1, z0,  szw, 0,
                x0, y0, z0,  szw, sh,
                x0, y0, z1,  0, sh);
        // East (X+) - shade 0.6
        face(b, pose, side, light, overlay, 0.6f,
                x1, y1, z0,  0, 0,
                x1, y1, z1,  szw, 0,
                x1, y0, z1,  szw, sh,
                x1, y0, z0,  0, sh);
    }

    /** Emit a single quad with 4 vertices. UV coords are in pixels (0-16). */
    private void face(VertexConsumer b, PoseStack.Pose pose, TextureAtlasSprite sprite,
                      int light, int overlay, float shade,
                      float x0, float y0, float z0, float u0, float v0,
                      float x1, float y1, float z1, float u1, float v1,
                      float x2, float y2, float z2, float u2, float v2,
                      float x3, float y3, float z3, float u3, float v3) {
        // Compute face normal from first 3 vertices
        float e1x = x1-x0, e1y = y1-y0, e1z = z1-z0;
        float e2x = x2-x0, e2y = y2-y0, e2z = z2-z0;
        float nx = e1y*e2z - e1z*e2y;
        float ny = e1z*e2x - e1x*e2z;
        float nz = e1x*e2y - e1y*e2x;
        float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (len > 0) { nx /= len; ny /= len; nz /= len; }

        float r = shade, g = shade, bl = shade;
        vtx(b, pose, x0, y0, z0, sprite.getU(u0/16f), sprite.getV(v0/16f), nx, ny, nz, r, g, bl, light, overlay);
        vtx(b, pose, x1, y1, z1, sprite.getU(u1/16f), sprite.getV(v1/16f), nx, ny, nz, r, g, bl, light, overlay);
        vtx(b, pose, x2, y2, z2, sprite.getU(u2/16f), sprite.getV(v2/16f), nx, ny, nz, r, g, bl, light, overlay);
        vtx(b, pose, x3, y3, z3, sprite.getU(u3/16f), sprite.getV(v3/16f), nx, ny, nz, r, g, bl, light, overlay);
    }

    private void vtx(VertexConsumer b, PoseStack.Pose p,
                     float x, float y, float z, float u, float v,
                     float nx, float ny, float nz,
                     float r, float g, float bl,
                     int light, int overlay) {
        b.addVertex(p, x, y, z)
         .setColor(r, g, bl, 1.0f)
         .setUv(u, v)
         .setOverlay(overlay)
         .setLight(light)
         .setNormal(p, nx, ny, nz);
    }

    // --- Static helpers ---

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
