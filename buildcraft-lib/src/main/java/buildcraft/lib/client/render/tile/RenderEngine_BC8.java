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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.lib.engine.TileEngineBase_BC8;

import java.util.Map;

/**
 * Block Entity Renderer for all BuildCraft engines.
 * Renders the 4-part engine model: base plate, trunk, chamber, and piston head.
 * Configured per engine type via texture identifiers passed at construction.
 */
public class RenderEngine_BC8 implements BlockEntityRenderer<TileEngineBase_BC8, EngineRenderState> {

    private static final float PIXEL = 1.0f / 16.0f;

    // Per-engine-type textures
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
        poseStack.pushPose();

        // Apply directional rotation - engine model is authored facing UP
        applyDirectionalRotation(poseStack, state.facing);

        float progressPixels = state.progress * 8.0f; // 0 to 8 pixels of piston travel

        // Determine trunk texture from power stage (use renderer's own fields, not state)
        EnumPowerStage stage = state.powerStage != null ? state.powerStage : EnumPowerStage.BLUE;
        Identifier trunkTex = trunkTextures.getOrDefault(stage, trunkTextures.get(EnumPowerStage.BLUE));

        // Get sprites from the block atlas (using renderer's fields directly)
        TextureAtlasSprite backSprite = getSprite(backTexture);
        TextureAtlasSprite sideSprite = getSprite(sideTexture);
        TextureAtlasSprite trunkSprite = getSprite(trunkTex);
        TextureAtlasSprite chamberSprite = getSprite(chamberTexture);

        // Get light from the block entity position
        int light = 15728880; // full brightness fallback
        int overlay = OverlayTexture.NO_OVERLAY;

        // Get a vertex consumer from the render buffer source (same pattern as LaserRenderer_BC8)
        net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(
                net.minecraft.client.renderer.rendertype.RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();

        // 1. Base plate: 0,0,0 to 16,4,16
        renderBox(buffer, pose, 0, 0, 0, 16, 4, 16,
                backSprite, sideSprite, light, overlay);

        // 2. Trunk: 4,4,4 to 12,16,12 (static)
        renderTrunk(buffer, pose, 4, 4, 4, 12, 16, 12,
                trunkSprite, light, overlay);

        // 3. Chamber: 3,4,3 to 13,4+progressPixels,13 (animated)
        float chamberTop = 4 + progressPixels;
        if (chamberTop > 4.01f) {
            renderChamber(buffer, pose, 3, 4, 3, 13, chamberTop, 13,
                    chamberSprite, light, overlay);
        }

        // 4. Piston head: 0,4+progressPixels,0 to 16,8+progressPixels,16 (animated)
        renderBox(buffer, pose, 0, 4 + progressPixels, 0, 16, 8 + progressPixels, 16,
                backSprite, sideSprite, light, overlay);

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private void applyDirectionalRotation(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5f, 0.5f, 0.5f);
        switch (facing) {
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> { poseStack.mulPose(Axis.XP.rotationDegrees(90)); poseStack.mulPose(Axis.YP.rotationDegrees(180)); }
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            case WEST -> { poseStack.mulPose(Axis.XP.rotationDegrees(90)); poseStack.mulPose(Axis.YP.rotationDegrees(90)); }
            case EAST -> { poseStack.mulPose(Axis.XP.rotationDegrees(90)); poseStack.mulPose(Axis.YP.rotationDegrees(270)); }
            default -> {} // UP is default, no rotation needed
        }
        poseStack.translate(-0.5f, -0.5f, -0.5f);
    }

    private TextureAtlasSprite getSprite(Identifier texture) {
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        return atlas.getSprite(texture);
    }

    // --- Quad rendering helpers ---
    // All coordinates in pixels (0-16), converted to block units (0-1)

    private void renderBox(VertexConsumer buffer, PoseStack.Pose pose,
                           float x0, float y0, float z0, float x1, float y1, float z1,
                           TextureAtlasSprite topBottomSprite, TextureAtlasSprite sideSprite,
                           int light, int overlay) {
        float px0 = x0 * PIXEL, py0 = y0 * PIXEL, pz0 = z0 * PIXEL;
        float px1 = x1 * PIXEL, py1 = y1 * PIXEL, pz1 = z1 * PIXEL;

        // Top face (Y+)
        quad(buffer, pose, px0, py1, pz0, px1, py1, pz1, 0, 1, 0, topBottomSprite,
                x0, z0, x1, z1, light, overlay);
        // Bottom face (Y-)
        quad(buffer, pose, px0, py0, pz1, px1, py0, pz0, 0, -1, 0, topBottomSprite,
                x0, z0, x1, z1, light, overlay);
        // North face (Z-)
        quad(buffer, pose, px1, py1, pz0, px0, py0, pz0, 0, 0, -1, sideSprite,
                x0, 16 - y1, x1, 16 - y0, light, overlay);
        // South face (Z+)
        quad(buffer, pose, px0, py1, pz1, px1, py0, pz1, 0, 0, 1, sideSprite,
                x0, 16 - y1, x1, 16 - y0, light, overlay);
        // West face (X-)
        quad(buffer, pose, px0, py1, pz0, px0, py0, pz1, -1, 0, 0, sideSprite,
                z0, 16 - y1, z1, 16 - y0, light, overlay);
        // East face (X+)
        quad(buffer, pose, px1, py1, pz1, px1, py0, pz0, 1, 0, 0, sideSprite,
                z0, 16 - y1, z1, 16 - y0, light, overlay);
    }

    private void renderTrunk(VertexConsumer buffer, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             TextureAtlasSprite sprite, int light, int overlay) {
        float px0 = x0 * PIXEL, py0 = y0 * PIXEL, pz0 = z0 * PIXEL;
        float px1 = x1 * PIXEL, py1 = y1 * PIXEL, pz1 = z1 * PIXEL;

        // Top + Bottom
        quad(buffer, pose, px0, py1, pz0, px1, py1, pz1, 0, 1, 0, sprite,
                0, 0, 8, 8, light, overlay);
        quad(buffer, pose, px0, py0, pz1, px1, py0, pz0, 0, -1, 0, sprite,
                0, 0, 8, 8, light, overlay);
        // 4 sides - UV from column 8..16 of the trunk texture
        quad(buffer, pose, px1, py1, pz0, px0, py0, pz0, 0, 0, -1, sprite,
                8, 0, 16, 12, light, overlay);
        quad(buffer, pose, px0, py1, pz1, px1, py0, pz1, 0, 0, 1, sprite,
                8, 0, 16, 12, light, overlay);
        quad(buffer, pose, px0, py1, pz0, px0, py0, pz1, -1, 0, 0, sprite,
                8, 0, 16, 12, light, overlay);
        quad(buffer, pose, px1, py1, pz1, px1, py0, pz0, 1, 0, 0, sprite,
                8, 0, 16, 12, light, overlay);
    }

    private void renderChamber(VertexConsumer buffer, PoseStack.Pose pose,
                               float x0, float y0, float z0, float x1, float y1, float z1,
                               TextureAtlasSprite sprite, int light, int overlay) {
        float px0 = x0 * PIXEL, py0 = y0 * PIXEL, pz0 = z0 * PIXEL;
        float px1 = x1 * PIXEL, py1 = y1 * PIXEL, pz1 = z1 * PIXEL;
        float uvHeight = y1 - y0;

        // 4 sides only (no top/bottom for chamber)
        quad(buffer, pose, px1, py1, pz0, px0, py0, pz0, 0, 0, -1, sprite,
                x0, 0, x1, uvHeight, light, overlay);
        quad(buffer, pose, px0, py1, pz1, px1, py0, pz1, 0, 0, 1, sprite,
                x0, 0, x1, uvHeight, light, overlay);
        quad(buffer, pose, px0, py1, pz0, px0, py0, pz1, -1, 0, 0, sprite,
                z0, 0, z1, uvHeight, light, overlay);
        quad(buffer, pose, px1, py1, pz1, px1, py0, pz0, 1, 0, 0, sprite,
                z0, 0, z1, uvHeight, light, overlay);
    }

    private void quad(VertexConsumer buffer, PoseStack.Pose pose,
                      float x0, float y0, float z0,
                      float x1, float y1, float z1,
                      float nx, float ny, float nz,
                      TextureAtlasSprite sprite,
                      float u0Px, float v0Px, float u1Px, float v1Px,
                      int light, int overlay) {
        float u0 = sprite.getU(u0Px / 16f);
        float v0 = sprite.getV(v0Px / 16f);
        float u1 = sprite.getU(u1Px / 16f);
        float v1 = sprite.getV(v1Px / 16f);

        if (ny != 0) {
            float y = (ny > 0) ? Math.max(y0, y1) : Math.min(y0, y1);
            if (ny > 0) {
                vertex(buffer, pose, x0, y, z0, u0, v0, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x0, y, z1, u0, v1, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x1, y, z1, u1, v1, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x1, y, z0, u1, v0, nx, ny, nz, light, overlay);
            } else {
                vertex(buffer, pose, x0, y, z1, u0, v1, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x0, y, z0, u0, v0, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x1, y, z0, u1, v0, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x1, y, z1, u1, v1, nx, ny, nz, light, overlay);
            }
        } else if (nz != 0) {
            float z = (nz > 0) ? Math.max(z0, z1) : Math.min(z0, z1);
            if (nz > 0) {
                vertex(buffer, pose, x0, y0, z, u0, v1, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x0, y1, z, u0, v0, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x1, y1, z, u1, v0, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x1, y0, z, u1, v1, nx, ny, nz, light, overlay);
            } else {
                vertex(buffer, pose, x1, y0, z, u1, v1, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x1, y1, z, u1, v0, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x0, y1, z, u0, v0, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x0, y0, z, u0, v1, nx, ny, nz, light, overlay);
            }
        } else {
            float x = (nx > 0) ? Math.max(x0, x1) : Math.min(x0, x1);
            if (nx > 0) {
                vertex(buffer, pose, x, y0, z0, u0, v1, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x, y1, z0, u0, v0, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x, y1, z1, u1, v0, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x, y0, z1, u1, v1, nx, ny, nz, light, overlay);
            } else {
                vertex(buffer, pose, x, y0, z1, u1, v1, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x, y1, z1, u1, v0, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x, y1, z0, u0, v0, nx, ny, nz, light, overlay);
                vertex(buffer, pose, x, y0, z0, u0, v1, nx, ny, nz, light, overlay);
            }
        }
    }

    private void vertex(VertexConsumer buffer, PoseStack.Pose pose,
                        float x, float y, float z,
                        float u, float v,
                        float nx, float ny, float nz,
                        int light, int overlay) {
        buffer.addVertex(pose, x, y, z)
                .setColor(1.0f, 1.0f, 1.0f, 1.0f)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
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
