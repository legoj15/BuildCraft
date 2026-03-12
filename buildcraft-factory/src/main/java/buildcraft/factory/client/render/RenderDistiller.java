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
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.lib.fluid.FluidSmoother;

/**
 * Block entity renderer for the distiller. Renders the fluid inside the three
 * tank compartments (input, gas output, liquid output).
 * Ported from 1.12.2 RenderDistiller, simplified to use direct vertex rendering.
 */
public class RenderDistiller implements BlockEntityRenderer<TileDistiller_BC8, DistillerRenderState> {

    /** Tank render sizes for each facing direction, in pixel coordinates (1/16 scale). */
    private static final Map<Direction, TankSizes> TANK_SIZES = new EnumMap<>(Direction.class);

    static {
        // Base sizes for WEST facing (the default)
        // Input tank: (0,0,4) to (8,16,12)
        TankSizes sizes = new TankSizes(
            new TankBounds(0, 0, 4, 8, 16, 12),   // input
            new TankBounds(8, 8, 0, 16, 16, 16),  // gas out (top)
            new TankBounds(8, 0, 0, 16, 8, 16)    // liquid out (bottom)
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

        renderSmoothedFluid(tile.getSmoothIn(), sizes.tankIn, poseStack, bufferSource, light, partialTicks);
        renderSmoothedFluid(tile.getSmoothGasOut(), sizes.tankGasOut, poseStack, bufferSource, light, partialTicks);
        renderSmoothedFluid(tile.getSmoothLiquidOut(), sizes.tankLiquidOut, poseStack, bufferSource, light, partialTicks);

        bufferSource.endBatch();
        poseStack.popPose();
    }

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

    // --- Inner classes for tank geometry ---

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

    static class TankSizes {
        final TankBounds tankIn, tankGasOut, tankLiquidOut;

        TankSizes(TankBounds tankIn, TankBounds tankGasOut, TankBounds tankLiquidOut) {
            this.tankIn = tankIn;
            this.tankGasOut = tankGasOut;
            this.tankLiquidOut = tankLiquidOut;
        }

        TankSizes rotateY() {
            return new TankSizes(tankIn.rotateY(), tankGasOut.rotateY(), tankLiquidOut.rotateY());
        }
    }
}
