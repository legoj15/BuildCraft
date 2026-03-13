/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client.render;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.lib.fluid.FluidSmoother;

/**
 * Block entity renderer for the distiller. Renders the fluid inside the three
 * tank compartments (input, gas output, liquid output) and two animated
 * power indicator cubes that bob up/down when the distiller is active.
 * Ported from 1.12.2 RenderDistiller.
 */
public class RenderDistiller implements BlockEntityRenderer<TileDistiller_BC8, DistillerRenderState> {

    /** Tank render sizes for each facing direction, in pixel coordinates (1/16 scale). */
    private static final Map<Direction, TankSizes> TANK_SIZES = new EnumMap<>(Direction.class);

    /**
     * Power texture definitions, matching the 1.12.2 tile model JSON.
     * Each entry is { texture_id, u0, v0, u1, v1 } in pixel coords (0-16 range).
     * Index 0 = off, 1-6 = power levels.
     */
    private static final Identifier[] POWER_TEXTURES = {
        Identifier.parse("buildcraftfactory:block/distiller/power_sprite_a"),
        Identifier.parse("buildcraftfactory:block/distiller/power_sprite_a"),
        Identifier.parse("buildcraftfactory:block/distiller/power_sprite_b"),
        Identifier.parse("buildcraftfactory:block/distiller/power_sprite_b"),
        Identifier.parse("buildcraftfactory:block/distiller/power_sprite_c"),
        Identifier.parse("buildcraftfactory:block/distiller/power_sprite_c"),
        Identifier.parse("buildcraftfactory:block/distiller/power_sprite_d"),
    };
    /** Whether to use the top half (true) or bottom half (false) of the texture. */
    private static final boolean[] POWER_TOP_HALF = {
        true,   // off:      sprite_a top half
        false,  // power_1:  sprite_a bottom half
        true,   // power_2:  sprite_b top half
        false,  // power_3:  sprite_b bottom half
        true,   // power_4:  sprite_c top half
        false,  // power_5:  sprite_c bottom half
        true,   // power_6:  sprite_d top half
    };

    static {
        // Base sizes for WEST facing (the default)
        // Input tank: (0,0,4) to (8,16,12)
        TankSizes sizes = new TankSizes(
            new TankBounds(0, 0, 4, 8, 16, 12),   // input
            new TankBounds(8, 8, 0, 16, 16, 16),  // gas out (top)
            new TankBounds(8, 0, 0, 16, 8, 16),   // liquid out (bottom)
            // Power cubes (from 1.12.2 tile model): 8x4 in XY, 4-wide in Z
            // Right cube: (0, y1*12, 12) to (8, y1*12+4, 16)
            // Left cube:  (0, y2*12, 0)  to (8, y2*12+4, 4)
            new PowerCubeBounds(0, 12, 8, 4, 4),  // right: x0-8, z12-16
            new PowerCubeBounds(0, 0, 8, 4, 4)    // left:  x0-8, z0-4
        );
        Direction face = Direction.WEST;
        for (int i = 0; i < 4; i++) {
            TANK_SIZES.put(face, sizes);
            face = face.getClockWise();
            sizes = sizes.rotateY();
        }
    }

    public RenderDistiller(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public DistillerRenderState createRenderState() {
        return new DistillerRenderState();
    }

    @Override
    public void submit(DistillerRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
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
        if (!(be instanceof TileDistiller_BC8 tile)) return;

        BlockState state = level.getBlockState(pos);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        TankSizes sizes = TANK_SIZES.get(facing);
        if (sizes == null) return;

        int light = LevelRenderer.getLightColor(level, pos);

        poseStack.pushPose();

        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();

        // FluidSmoother handles tick-based interpolation; use 0 for partial ticks
        float partialTicks = 0f;

        // Render fluid in tanks
        renderSmoothedFluid(tile.getSmoothIn(), sizes.tankIn, poseStack, bufferSource, light, partialTicks);
        renderSmoothedFluid(tile.getSmoothGasOut(), sizes.tankGasOut, poseStack, bufferSource, light, partialTicks);
        renderSmoothedFluid(tile.getSmoothLiquidOut(), sizes.tankLiquidOut, poseStack, bufferSource, light, partialTicks);

        // Render animated power indicator cubes
        renderPowerCubes(tile, sizes, poseStack, bufferSource, light);

        bufferSource.endBatch();
        poseStack.popPose();
    }

    // --- Fluid Rendering ---

    private static void renderSmoothedFluid(FluidSmoother smoother, TankBounds bounds, PoseStack poseStack,
                                             MultiBufferSource.BufferSource bufferSource, int light, float partialTicks) {
        FluidSmoother.FluidStackInterp interp = smoother.getFluidForRender(partialTicks);
        if (interp == null || interp.amount() <= 0) return;

        FluidStack fluid = interp.fluid();
        int capacity = smoother.getCapacity();
        if (capacity <= 0) return;

        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(fluid.getFluid());
        Identifier stillTexture = fluidExt.getStillTexture(fluid);
        if (stillTexture == null) return;

        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(stillTexture);

        int color = fluidExt.getTintColor(fluid);
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        if (a <= 0) a = 1.0f;

        float fillRatio = (float) (interp.amount() / capacity);

        // Shrink bounds slightly for z-fighting avoidance
        float shrink = 1.0f / 64.0f;
        float minX = bounds.minX / 16.0f + shrink;
        float minY = bounds.minY / 16.0f + shrink;
        float minZ = bounds.minZ / 16.0f + shrink;
        float maxX = bounds.maxX / 16.0f - shrink;
        float maxY = bounds.maxY / 16.0f - shrink;
        float maxZ = bounds.maxZ / 16.0f - shrink;

        // Scale fluid height by fill ratio
        float fluidTop = minY + (maxY - minY) * fillRatio;

        VertexConsumer buffer = bufferSource.getBuffer(
                a < 1.0f ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();
        int overlay = OverlayTexture.NO_OVERLAY;

        // North face (-Z)
        quad(pose, buffer, sprite, minX, fluidTop, minZ, maxX, fluidTop, minZ,
                maxX, minY, minZ, minX, minY, minZ,
                0, 0, -1, r, g, b, a, light, overlay);
        // South face (+Z)
        quad(pose, buffer, sprite, minX, minY, maxZ, maxX, minY, maxZ,
                maxX, fluidTop, maxZ, minX, fluidTop, maxZ,
                0, 0, 1, r, g, b, a, light, overlay);
        // West face (-X)
        quad(pose, buffer, sprite, minX, minY, minZ, minX, minY, maxZ,
                minX, fluidTop, maxZ, minX, fluidTop, minZ,
                -1, 0, 0, r, g, b, a, light, overlay);
        // East face (+X)
        quad(pose, buffer, sprite, maxX, fluidTop, minZ, maxX, fluidTop, maxZ,
                maxX, minY, maxZ, maxX, minY, minZ,
                1, 0, 0, r, g, b, a, light, overlay);
        // Top face
        quadHorizontal(pose, buffer, sprite, minX, maxX, maxZ, minZ, fluidTop,
                0, 1, 0, r, g, b, a, light, overlay);
        // Bottom face
        quadHorizontal(pose, buffer, sprite, minX, maxX, maxZ, minZ, minY,
                0, -1, 0, r, g, b, a, light, overlay);
    }

    // --- Power Cube Rendering ---

    private static void renderPowerCubes(TileDistiller_BC8 tile, TankSizes sizes,
                                          PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, int light) {
        double animState = tile.getAnimState();
        long powerAvg = tile.getPowerAvgClient();

        // Compute Y positions from animation state (matches 1.12.2 expressions)
        // y1 = 1 - abs((state % 1) - 0.5) * 2  → bounces 0→1→0
        double stMod1 = animState - Math.floor(animState);
        float y1 = (float) (1.0 - Math.abs(stMod1 - 0.5) * 2.0);

        // y2 = 1 - abs(((state - 0.5) % 1) - 0.5) * 2  → same but offset by half cycle
        double st2 = animState <= 0.5 ? 0 : animState - 0.5;
        double st2Mod1 = st2 - Math.floor(st2);
        float y2 = (float) (1.0 - Math.abs(st2Mod1 - 0.5) * 2.0);

        // Select power texture (0=off, 1-6 based on powerAvg)
        int texIndex;
        if (powerAvg <= 0) {
            texIndex = 0; // off
        } else {
            texIndex = (int) Math.min(powerAvg, 6);
        }

        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(POWER_TEXTURES[texIndex]);
        boolean topHalf = POWER_TOP_HALF[texIndex];

        // Color: full white (texture provides color)
        float r = 1.0f, g = 1.0f, b = 1.0f, a = 1.0f;

        VertexConsumer buffer = bufferSource.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();
        int overlay = OverlayTexture.NO_OVERLAY;

        // Right power cube
        renderPowerCube(pose, buffer, sprite, topHalf, sizes.powerRight, y1, r, g, b, a, light, overlay);
        // Left power cube
        renderPowerCube(pose, buffer, sprite, topHalf, sizes.powerLeft, y2, r, g, b, a, light, overlay);
    }

    private static void renderPowerCube(PoseStack.Pose pose, VertexConsumer buffer,
                                         TextureAtlasSprite sprite, boolean topHalf,
                                         PowerCubeBounds pcb, float yFraction,
                                         float r, float g, float b, float a, int light, int overlay) {
        // Cube Y position: yFraction * 12 pixels, cube is 4 pixels tall
        float cubeMinY = (yFraction * 12.0f) / 16.0f;
        float cubeMaxY = cubeMinY + 4.0f / 16.0f;

        float minX = pcb.minX / 16.0f;
        float maxX = (pcb.minX + pcb.sizeX) / 16.0f;
        float minZ = pcb.minZ / 16.0f;
        float maxZ = (pcb.minZ + pcb.sizeZ) / 16.0f;

        // UV regions from the sprite (each power_sprite is 16x16, split into top/bottom halves)
        // Face UVs from the 1.12.2 model:
        //   north/south: (4,8)→(12,16) pixel coords on the 16x16 texture
        //   east/west:   (0,8)→(4,16) pixel coords
        //   up/down:     (4,0)→(12,8) pixel coords
        // The "top half" or "bottom half" selection shifts the V coordinates.
        // In atlas space, we interpolate between sprite U0/V0 and U1/V1.
        float sprU0 = sprite.getU0();
        float sprU1 = sprite.getU1();
        float sprV0 = sprite.getV0();
        float sprV1 = sprite.getV1();
        float sprURange = sprU1 - sprU0;
        float sprVRange = sprV1 - sprV0;

        // If topHalf: use V 0→0.5 of sprite; if bottomHalf: use V 0.5→1.0
        float vBase = topHalf ? 0 : 0.5f;

        // North/South face UVs: pixel (4,8)→(12,16) → normalized (0.25, 0.5)→(0.75, 1.0), shift by half
        float nsU0 = sprU0 + sprURange * (4.0f / 16.0f);
        float nsU1 = sprU0 + sprURange * (12.0f / 16.0f);
        float nsV0 = sprV0 + sprVRange * (vBase + 8.0f / 32.0f);
        float nsV1 = sprV0 + sprVRange * (vBase + 16.0f / 32.0f);

        // East/West face UVs: pixel (0,8)→(4,16) → normalized (0, 0.5)→(0.25, 1.0)
        float ewU0 = sprU0 + sprURange * (0.0f / 16.0f);
        float ewU1 = sprU0 + sprURange * (4.0f / 16.0f);
        float ewV0 = sprV0 + sprVRange * (vBase + 8.0f / 32.0f);
        float ewV1 = sprV0 + sprVRange * (vBase + 16.0f / 32.0f);

        // Up/Down face UVs: pixel (4,0)→(12,8) → normalized (0.25, 0)→(0.75, 0.5)
        float udU0 = sprU0 + sprURange * (4.0f / 16.0f);
        float udU1 = sprU0 + sprURange * (12.0f / 16.0f);
        float udV0 = sprV0 + sprVRange * (vBase + 0.0f / 32.0f);
        float udV1 = sprV0 + sprVRange * (vBase + 8.0f / 32.0f);

        // North face (-Z)
        quadUV(pose, buffer, minX, cubeMaxY, minZ, maxX, cubeMaxY, minZ,
                maxX, cubeMinY, minZ, minX, cubeMinY, minZ,
                0, 0, -1, r, g, b, a, light, overlay,
                nsU0, nsV0, nsU1, nsV0, nsU1, nsV1, nsU0, nsV1);
        // South face (+Z)
        quadUV(pose, buffer, minX, cubeMinY, maxZ, maxX, cubeMinY, maxZ,
                maxX, cubeMaxY, maxZ, minX, cubeMaxY, maxZ,
                0, 0, 1, r, g, b, a, light, overlay,
                nsU0, nsV1, nsU1, nsV1, nsU1, nsV0, nsU0, nsV0);
        // West face (-X)
        quadUV(pose, buffer, minX, cubeMinY, minZ, minX, cubeMinY, maxZ,
                minX, cubeMaxY, maxZ, minX, cubeMaxY, minZ,
                -1, 0, 0, r, g, b, a, light, overlay,
                ewU0, ewV1, ewU1, ewV1, ewU1, ewV0, ewU0, ewV0);
        // East face (+X)
        quadUV(pose, buffer, maxX, cubeMaxY, minZ, maxX, cubeMaxY, maxZ,
                maxX, cubeMinY, maxZ, maxX, cubeMinY, minZ,
                1, 0, 0, r, g, b, a, light, overlay,
                ewU0, ewV0, ewU1, ewV0, ewU1, ewV1, ewU0, ewV1);
        // Top face (+Y)
        quadUV(pose, buffer, minX, cubeMaxY, minZ, maxX, cubeMaxY, minZ,
                maxX, cubeMaxY, maxZ, minX, cubeMaxY, maxZ,
                0, 1, 0, r, g, b, a, light, overlay,
                udU0, udV0, udU1, udV0, udU1, udV1, udU0, udV1);
        // Bottom face (-Y)
        quadUV(pose, buffer, minX, cubeMinY, maxZ, maxX, cubeMinY, maxZ,
                maxX, cubeMinY, minZ, minX, cubeMinY, minZ,
                0, -1, 0, r, g, b, a, light, overlay,
                udU0, udV1, udU1, udV1, udU1, udV0, udU0, udV0);
    }

    // --- Quad helpers ---

    private static void quad(PoseStack.Pose pose, VertexConsumer builder, TextureAtlasSprite sprite,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float nx, float ny, float nz,
            float r, float g, float b, float a, int light, int overlay) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        builder.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x4, y4, z4).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    private static void quadHorizontal(PoseStack.Pose pose, VertexConsumer builder, TextureAtlasSprite sprite,
            float x1, float x2, float z1, float z2, float y,
            float nx, float ny, float nz,
            float r, float g, float b, float a, int light, int overlay) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        builder.addVertex(pose, x1, y, z1).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x2, y, z1).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x2, y, z2).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x1, y, z2).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** Quad with fully custom UV coordinates per vertex. */
    private static void quadUV(PoseStack.Pose pose, VertexConsumer builder,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float nx, float ny, float nz,
            float r, float g, float b, float a, int light, int overlay,
            float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4) {
        builder.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(u3, v3).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x4, y4, z4).setColor(r, g, b, a).setUv(u4, v4).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    // --- Inner classes for geometry ---

    static class TankBounds {
        final float minX, minY, minZ, maxX, maxY, maxZ;

        TankBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        TankBounds rotateY() {
            // Rotate 90 degrees clockwise around Y axis
            float newMinX = 16 - maxZ;
            float newMinZ = minX;
            float newMaxX = 16 - minZ;
            float newMaxZ = maxX;
            return new TankBounds(
                Math.min(newMinX, newMaxX), minY, Math.min(newMinZ, newMaxZ),
                Math.max(newMinX, newMaxX), maxY, Math.max(newMinZ, newMaxZ)
            );
        }
    }

    /** Power cube position in pixel coordinates. The Y position is dynamic (driven by animation). */
    static class PowerCubeBounds {
        final float minX, minZ, sizeX, sizeY, sizeZ;

        PowerCubeBounds(float minX, float minZ, float sizeX, float sizeY, float sizeZ) {
            this.minX = minX;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        PowerCubeBounds rotateY() {
            // Rotate 90 degrees clockwise: new position based on 16x16 block
            float newMinX = 16 - minZ - sizeZ;
            float newMinZ = minX;
            return new PowerCubeBounds(newMinX, newMinZ, sizeZ, sizeY, sizeX);
        }
    }

    static class TankSizes {
        final TankBounds tankIn, tankGasOut, tankLiquidOut;
        final PowerCubeBounds powerRight, powerLeft;

        TankSizes(TankBounds tankIn, TankBounds tankGasOut, TankBounds tankLiquidOut,
                  PowerCubeBounds powerRight, PowerCubeBounds powerLeft) {
            this.tankIn = tankIn;
            this.tankGasOut = tankGasOut;
            this.tankLiquidOut = tankLiquidOut;
            this.powerRight = powerRight;
            this.powerLeft = powerLeft;
        }

        TankSizes rotateY() {
            return new TankSizes(tankIn.rotateY(), tankGasOut.rotateY(), tankLiquidOut.rotateY(),
                    powerRight.rotateY(), powerLeft.rotateY());
        }
    }
}
