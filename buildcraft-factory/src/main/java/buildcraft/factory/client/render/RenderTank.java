/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client.render;

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
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.factory.tile.TileTank;

/**
 * Block entity renderer for the tank. Renders the fluid inside the tank
 * volume (2/16 to 14/16 on X/Z), with height proportional to the fill level.
 * Ported from 1.12.2 RenderTank.
 */
public class RenderTank implements BlockEntityRenderer<TileTank, TankRenderState> {

    private static final float MIN_XZ = 2.0f / 16.0f + 0.01f;
    private static final float MAX_XZ = 14.0f / 16.0f - 0.01f;
    private static final float MIN_Y = 0.01f;
    private static final float MAX_Y = 1.0f - 0.01f;
    private static final float MIN_Y_CONNECTED = 0.0f;
    private static final float MAX_Y_CONNECTED = 1.0f - 1e-5f;

    public RenderTank(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public TankRenderState createRenderState() {
        return new TankRenderState();
    }

    @Override
    public void submit(TankRenderState renderState, PoseStack poseStack,
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
        if (!(be instanceof TileTank tile)) return;

        FluidStack fluid = tile.tank.getFluid();
        if (fluid.isEmpty()) return;

        int amount = tile.tank.getFluidAmount();
        int capacity = tile.tank.getCapacity();
        if (amount <= 0 || capacity <= 0) return;

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

        boolean connectedDown = isConnectedFluid(tile, Direction.DOWN);
        boolean connectedUp = isConnectedFluid(tile, Direction.UP);

        float minY = connectedDown ? MIN_Y_CONNECTED : MIN_Y;
        float maxYFull = connectedUp ? MAX_Y_CONNECTED : MAX_Y;
        float fillRatio = (float) amount / capacity;
        float fluidTop = minY + (maxYFull - minY) * fillRatio;

        int light = LevelRenderer.getLightColor(level, pos);
        int overlay = OverlayTexture.NO_OVERLAY;

        poseStack.pushPose();

        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(Sheets.translucentBlockItemSheet());
        PoseStack.Pose pose = poseStack.last();

        boolean renderBottom = !connectedDown;
        boolean renderTop = !connectedUp || fillRatio < 1.0f;

        // North face (facing -Z: CCW from outside)
        quad(pose, buffer, sprite, MIN_XZ, fluidTop, MIN_XZ, MAX_XZ, fluidTop, MIN_XZ,
                MAX_XZ, minY, MIN_XZ, MIN_XZ, minY, MIN_XZ,
                0, 0, -1, r, g, b, a, light, overlay);
        // South face (facing +Z: CCW from outside)
        quad(pose, buffer, sprite, MIN_XZ, minY, MAX_XZ, MAX_XZ, minY, MAX_XZ,
                MAX_XZ, fluidTop, MAX_XZ, MIN_XZ, fluidTop, MAX_XZ,
                0, 0, 1, r, g, b, a, light, overlay);
        // West face (facing -X: CCW from outside)
        quad(pose, buffer, sprite, MIN_XZ, minY, MIN_XZ, MIN_XZ, minY, MAX_XZ,
                MIN_XZ, fluidTop, MAX_XZ, MIN_XZ, fluidTop, MIN_XZ,
                -1, 0, 0, r, g, b, a, light, overlay);
        // East face (facing +X: CCW from outside)
        quad(pose, buffer, sprite, MAX_XZ, fluidTop, MIN_XZ, MAX_XZ, fluidTop, MAX_XZ,
                MAX_XZ, minY, MAX_XZ, MAX_XZ, minY, MIN_XZ,
                1, 0, 0, r, g, b, a, light, overlay);

        if (renderTop) {
            quadHorizontal(pose, buffer, sprite, MIN_XZ, MAX_XZ, MAX_XZ, MIN_XZ, fluidTop,
                    0, 1, 0, r, g, b, a, light, overlay);
        }
        if (renderBottom) {
            quadHorizontal(pose, buffer, sprite, MIN_XZ, MAX_XZ, MAX_XZ, MIN_XZ, minY,
                    0, -1, 0, r, g, b, a, light, overlay);
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    /** Checks if the shared face between this tank and its neighbor should be hidden.
     *  Ported from 1.12.2 isFullyConnected: the face is only hidden when the neighbor
     *  is full OR the direction is UP (a tank above with any matching fluid connects seamlessly downward). */
    private static boolean isConnectedFluid(TileTank tile, Direction direction) {
        if (tile.getLevel() == null) return false;
        BlockPos neighborPos = tile.getBlockPos().relative(direction);
        BlockEntity neighbor = tile.getLevel().getBlockEntity(neighborPos);
        if (neighbor instanceof TileTank otherTank) {
            if (!TileTank.canTanksConnect(tile, otherTank, direction)) return false;
            FluidStack otherFluid = otherTank.tank.getFluid();
            FluidStack thisFluid = tile.tank.getFluid();
            if (otherFluid.isEmpty() || thisFluid.isEmpty()) return false;
            if (!FluidStack.isSameFluidSameComponents(thisFluid, otherFluid)) return false;
            // Only hide the shared face if the neighbor is full, or we are looking upward
            // (a tank above with any amount of matching fluid should seamlessly connect)
            return otherTank.tank.getFluidAmount() >= otherTank.tank.getCapacity()
                    || direction == Direction.UP;
        }
        return false;
    }

    /** Emit a vertical quad with 4 explicit vertices (CCW winding from outside).
     *  UV maps the sprite at natural scale: U spans u0→u1 across the width,
     *  V uses v1 (bottom of sprite) at the bottom of the quad and v0 (top of sprite)
     *  at the top, so the texture is clipped rather than stretched. */
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
}
