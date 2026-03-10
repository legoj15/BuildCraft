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
 * The engine model is authored facing UP; rotation is applied for other orientations.
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
        // The PoseStack is pre-translated to (blockX - camX, blockY - camY, blockZ - camZ)
        // Use Math.round() instead of floor() to avoid floating-point precision errors
        Vec3 camPos = cameraState.pos;
        if (camPos == null) return;
        org.joml.Vector3f t = new org.joml.Vector3f();
        poseStack.last().pose().getTranslation(t);
        BlockPos pos = new BlockPos(
                Math.round((float)(camPos.x + t.x)),
                Math.round((float)(camPos.y + t.y)),
                Math.round((float)(camPos.z + t.z)));

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileEngineBase_BC8 engine)) return;

        Direction facing = engine.getOrientation();
        float progress = engine.getProgressClient(1.0f);
        EnumPowerStage powerStage = engine.getPowerStage();

        poseStack.pushPose();

        // Apply directional rotation - engine model is authored facing UP
        applyDirectionalRotation(poseStack, facing);



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

        // --- 1.12.2 parity: triangle wave for piston animation ---
        // Expression: progress_size = (progress > 0.5 ? ((1-progress) * 15.99) : (progress * 15.99))
        // This maps progress 0->0.5->1 to visual size 0->~8->0 pixels (triangle wave)
        float progressSize;
        if (progress > 0.5f) {
            progressSize = (1.0f - progress) * (8 * 2 - 0.01f);
        } else {
            progressSize = progress * (8 * 2 - 0.01f);
        }
        float progressBlocks = progressSize / 16.0f; // convert pixels to block units

        // --- Engine geometry (in block units, 0-1) ---

        // 1. Base plate: [0,0,0] to [16,4,16]
        renderBox(buffer, pose, 0, 0, 0, 1, 0.25f, 1,
                backSprite, backSprite, sideSprite, light, overlay);

        // 2. Trunk: [4,4,4] to [12,16,12] (static center pole)
        //    UV: caps = 0-8,0-8 / sides = 8-16,0-12
        renderTrunk(buffer, pose, 0.25f, 0.25f, 0.25f, 0.75f, 1f, 0.75f,
                trunkSprite, light, overlay);

        // 3. Chamber: [3,4,3] to [13,4+progressSize,13] (animated, sides only)
        if (progressBlocks > 0.001f) {
            float cy0 = 0.25f;
            float cy1 = 0.25f + progressBlocks;
            renderBox(buffer, pose, 3/16f, cy0, 3/16f, 13/16f, cy1, 13/16f,
                    chamberSprite, chamberSprite, chamberSprite, light, overlay);
        }

        // 4. Piston head: [0,4+progressSize,0] to [16,8+progressSize,16]
        float py0 = 0.25f + progressBlocks;
        float py1 = 0.5f + progressBlocks;
        renderBox(buffer, pose, 0, py0, 0, 1, py1, 1,
                backSprite, backSprite, sideSprite, light, overlay);

        bufferSource.endBatch();
        poseStack.popPose();
    }

    /**
     * Rotate the model from the default UP orientation to the given facing.
     * The base (output connector) is at Y=0, piston head is at Y=0.5..1.
     * After rotation, the base faces the given direction.
     */
    private void applyDirectionalRotation(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5f, 0.5f, 0.5f);
        switch (facing) {
            case DOWN  -> poseStack.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            case WEST  -> poseStack.mulPose(Axis.ZP.rotationDegrees(90));
            case EAST  -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90));
            default -> {} // UP = no rotation
        }
        poseStack.translate(-0.5f, -0.5f, -0.5f);
    }

    private TextureAtlasSprite getSprite(Identifier texture) {
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        return atlas.getSprite(texture);
    }

    /**
     * Render a box with top, bottom, and side textures.
     * Applies Minecraft standard face shade factors via vertex color.
     */
    private void renderBox(VertexConsumer b, PoseStack.Pose pose,
                           float x0, float y0, float z0, float x1, float y1, float z1,
                           TextureAtlasSprite top, TextureAtlasSprite bottom, TextureAtlasSprite side,
                           int light, int overlay) {
        float xw = (x1 - x0) * 16f;
        float zw = (z1 - z0) * 16f;
        float yh = (y1 - y0) * 16f;

        // Top (Y+) - shade 1.0
        face(b, pose, top, light, overlay, 1.0f, 0, 1, 0,
                x0, y1, z0,  0, 0,
                x0, y1, z1,  0, zw,
                x1, y1, z1,  xw, zw,
                x1, y1, z0,  xw, 0);
        // Bottom (Y-) - shade 0.5
        face(b, pose, bottom, light, overlay, 0.5f, 0, -1, 0,
                x1, y0, z0,  0, 0,
                x1, y0, z1,  0, zw,
                x0, y0, z1,  xw, zw,
                x0, y0, z0,  xw, 0);
        // North (Z-) - shade 0.8
        face(b, pose, side, light, overlay, 0.8f, 0, 0, -1,
                x0, y1, z0,  0, 0,
                x1, y1, z0,  xw, 0,
                x1, y0, z0,  xw, yh,
                x0, y0, z0,  0, yh);
        // South (Z+) - shade 0.8
        face(b, pose, side, light, overlay, 0.8f, 0, 0, 1,
                x1, y1, z1,  0, 0,
                x0, y1, z1,  xw, 0,
                x0, y0, z1,  xw, yh,
                x1, y0, z1,  0, yh);
        // West (X-) - shade 0.6
        face(b, pose, side, light, overlay, 0.6f, -1, 0, 0,
                x0, y1, z1,  0, 0,
                x0, y1, z0,  zw, 0,
                x0, y0, z0,  zw, yh,
                x0, y0, z1,  0, yh);
        // East (X+) - shade 0.6
        face(b, pose, side, light, overlay, 0.6f, 1, 0, 0,
                x1, y1, z0,  0, 0,
                x1, y1, z1,  zw, 0,
                x1, y0, z1,  zw, yh,
                x1, y0, z0,  0, yh);
    }

    /**
     * Render the trunk with correct 1.12.2 UV regions.
     * Uses the exact same vertex winding as renderBox (which works correctly).
     * Caps (top/bottom): UV 0-8, 0-8 (top-left quadrant of texture)
     * Sides: UV 8-16, 0-12 (right half of texture)
     */
    private void renderTrunk(VertexConsumer b, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             TextureAtlasSprite sprite, int light, int overlay) {
        // Top (Y+) - cap UV 0-8, 0-8 (same winding as renderBox top)
        face(b, pose, sprite, light, overlay, 1.0f, 0, 1, 0,
                x0, y1, z0,  0, 0,
                x0, y1, z1,  0, 8,
                x1, y1, z1,  8, 8,
                x1, y1, z0,  8, 0);
        // Bottom (Y-) - cap UV 0-8, 0-8 (same winding as renderBox bottom)
        face(b, pose, sprite, light, overlay, 0.5f, 0, -1, 0,
                x1, y0, z0,  0, 0,
                x1, y0, z1,  0, 8,
                x0, y0, z1,  8, 8,
                x0, y0, z0,  8, 0);
        // North (Z-) - side UV: U reversed to match Minecraft JSON convention
        face(b, pose, sprite, light, overlay, 0.8f, 0, 0, -1,
                x0, y1, z0,  16, 0,
                x1, y1, z0,  8, 0,
                x1, y0, z0,  8, 12,
                x0, y0, z0,  16, 12);
        // South (Z+) - side UV: U reversed
        face(b, pose, sprite, light, overlay, 0.8f, 0, 0, 1,
                x1, y1, z1,  16, 0,
                x0, y1, z1,  8, 0,
                x0, y0, z1,  8, 12,
                x1, y0, z1,  16, 12);
        // West (X-) - side UV: U reversed
        face(b, pose, sprite, light, overlay, 0.6f, -1, 0, 0,
                x0, y1, z1,  16, 0,
                x0, y1, z0,  8, 0,
                x0, y0, z0,  8, 12,
                x0, y0, z1,  16, 12);
        // East (X+) - side UV: U reversed
        face(b, pose, sprite, light, overlay, 0.6f, 1, 0, 0,
                x1, y1, z0,  16, 0,
                x1, y1, z1,  8, 0,
                x1, y0, z1,  8, 12,
                x1, y0, z0,  16, 12);
    }

    /** Emit a single quad with 4 vertices. UV coords are in pixels (0-16). */
    private void face(VertexConsumer b, PoseStack.Pose pose, TextureAtlasSprite sprite,
                      int light, int overlay, float shade,
                      float nx, float ny, float nz,
                      float x0, float y0, float z0, float u0, float v0,
                      float x1, float y1, float z1, float u1, float v1,
                      float x2, float y2, float z2, float u2, float v2,
                      float x3, float y3, float z3, float u3, float v3) {
        vtx(b, pose, x0, y0, z0, sprite.getU(u0/16f), sprite.getV(v0/16f), nx, ny, nz, shade, light, overlay);
        vtx(b, pose, x1, y1, z1, sprite.getU(u1/16f), sprite.getV(v1/16f), nx, ny, nz, shade, light, overlay);
        vtx(b, pose, x2, y2, z2, sprite.getU(u2/16f), sprite.getV(v2/16f), nx, ny, nz, shade, light, overlay);
        vtx(b, pose, x3, y3, z3, sprite.getU(u3/16f), sprite.getV(v3/16f), nx, ny, nz, shade, light, overlay);
    }

    private void vtx(VertexConsumer b, PoseStack.Pose p,
                     float x, float y, float z, float u, float v,
                     float nx, float ny, float nz, float shade,
                     int light, int overlay) {
        b.addVertex(p, x, y, z)
         .setColor(shade, shade, shade, 1.0f)
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
