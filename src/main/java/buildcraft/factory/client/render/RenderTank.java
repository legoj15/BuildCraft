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
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else {
/*import net.minecraft.client.renderer.state.CameraRenderState;*/
//?}
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import net.minecraft.client.renderer.rendertype.RenderType;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.factory.tile.TileTank;
import buildcraft.lib.misc.FluidUtilBC;

/**
 * Block entity renderer for the tank. Renders the fluid inside the tank
 * volume (2/16 to 14/16 on X/Z), with height proportional to the fill level.
 * Ported from 1.12.2 RenderTank.
 */
@SuppressWarnings("deprecation")
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
        ProfilerFiller _profiler = Profiler.get();
        _profiler.push("buildcraft:tank_submit");
        try {
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

        // Use the FluidSmoother for interpolated rendering — prevents level snapping
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        buildcraft.lib.fluid.FluidSmoother.FluidStackInterp interp = tile.smoothedTank.getFluidForRender(partialTicks);
        if (interp == null) return;

        FluidStack fluid = interp.fluid();
        double amount = interp.amount();
        int capacity = tile.smoothedTank.getCapacity();
        if (amount <= 0 || capacity <= 0) return;

        Identifier stillTexture = FluidUtilBC.getFluidTexture(fluid);
        if (stillTexture == null) return;

        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(stillTexture);

        int color = FluidUtilBC.getFluidColor(fluid);
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        if (a <= 0) a = 1.0f;

        boolean connectedDown = isConnectedFluid(tile, Direction.DOWN);
        boolean connectedUp = isConnectedFluid(tile, Direction.UP);

        float minY = connectedDown ? MIN_Y_CONNECTED : MIN_Y;
        float maxYFull = connectedUp ? MAX_Y_CONNECTED : MAX_Y;
        float fillRatio = (float) (amount / capacity);

        boolean gaseous = FluidUtilBC.isGaseous(fluid);
        float fluidTop, fluidBottom;
        if (gaseous) {
            // Gaseous: fluid renders at the top, filling downward
            fluidTop = maxYFull;
            fluidBottom = maxYFull - (maxYFull - minY) * fillRatio;
        } else {
            // Liquid: fluid renders at the bottom, filling upward
            fluidBottom = minY;
            fluidTop = minY + (maxYFull - minY) * fillRatio;
        }

        int light = buildcraft.lib.client.render.LightUtil.getLightCoords(level, pos);
        int overlay = OverlayTexture.NO_OVERLAY;

        poseStack.pushPose();

        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        // Translucent for vanilla water, cutout for BC fluids (reuse water texture opaquely)
        VertexConsumer buffer = bufferSource.getBuffer(
                FluidUtilBC.shouldRenderTranslucent(fluid)
                    ? net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS) : net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();

        boolean renderBottom = !connectedDown;
        boolean renderTop = !connectedUp || fillRatio < 1.0f;

        // North face (facing -Z: CCW from outside)
        quad(pose, buffer, sprite, MIN_XZ, fluidTop, MIN_XZ, MAX_XZ, fluidTop, MIN_XZ,
                MAX_XZ, fluidBottom, MIN_XZ, MIN_XZ, fluidBottom, MIN_XZ,
                0, 0, -1, r, g, b, a, light, overlay);
        // South face (facing +Z: CCW from outside)
        quad(pose, buffer, sprite, MIN_XZ, fluidBottom, MAX_XZ, MAX_XZ, fluidBottom, MAX_XZ,
                MAX_XZ, fluidTop, MAX_XZ, MIN_XZ, fluidTop, MAX_XZ,
                0, 0, 1, r, g, b, a, light, overlay);
        // West face (facing -X: CCW from outside)
        quad(pose, buffer, sprite, MIN_XZ, fluidBottom, MIN_XZ, MIN_XZ, fluidBottom, MAX_XZ,
                MIN_XZ, fluidTop, MAX_XZ, MIN_XZ, fluidTop, MIN_XZ,
                -1, 0, 0, r, g, b, a, light, overlay);
        // East face (facing +X: CCW from outside)
        quad(pose, buffer, sprite, MAX_XZ, fluidTop, MIN_XZ, MAX_XZ, fluidTop, MAX_XZ,
                MAX_XZ, fluidBottom, MAX_XZ, MAX_XZ, fluidBottom, MIN_XZ,
                1, 0, 0, r, g, b, a, light, overlay);

        if (renderTop) {
            quadHorizontal(pose, buffer, sprite, MIN_XZ, MAX_XZ, MAX_XZ, MIN_XZ, fluidTop,
                    0, 1, 0, r, g, b, a, light, overlay);
        }
        if (renderBottom) {
            quadHorizontal(pose, buffer, sprite, MIN_XZ, MAX_XZ, MAX_XZ, MIN_XZ, fluidBottom,
                    0, -1, 0, r, g, b, a, light, overlay);
        }
        // Render the "open" face for non-full gaseous fluid (bottom face visible)
        // or non-full liquid (top face visible when not connected up)
        if (gaseous && fillRatio < 1.0f && !connectedDown) {
            quadHorizontal(pose, buffer, sprite, MIN_XZ, MAX_XZ, MAX_XZ, MIN_XZ, fluidBottom,
                    0, -1, 0, r, g, b, a, light, overlay);
        }

        poseStack.popPose();
        } finally {
            _profiler.pop();
        }
    }

    /** Checks if the shared face between this tank and its neighbor should be hidden.
     *  Ported from 1.12.2 isFullyConnected: the face is only hidden when the neighbor
     *  is full OR the direction is UP (for liquids) / DOWN (for gases).
     *  For gaseous fluids, the direction check is inverted (matching 1.12.2 behavior). */
    private static boolean isConnectedFluid(TileTank tile, Direction direction) {
        if (tile.getLevel() == null) return false;
        BlockPos neighborPos = tile.getBlockPos().relative(direction);
        BlockEntity neighbor = tile.getLevel().getBlockEntity(neighborPos);
        if (neighbor instanceof TileTank otherTank) {
            if (!TileTank.canTanksConnect(tile, otherTank, direction)) return false;
            net.neoforged.neoforge.transfer.fluid.FluidResource otherFluid = otherTank.tank.getResource(0);
            net.neoforged.neoforge.transfer.fluid.FluidResource thisFluid = tile.tank.getResource(0);
            if (otherFluid.isEmpty() || thisFluid.isEmpty()) return false;
            if (!thisFluid.equals(otherFluid)) return false;

            // For gaseous fluids, invert the direction check:
            // a tank below with matching gas connects seamlessly (gas floats up)
            Direction checkDir = FluidUtilBC.isGaseous(thisFluid.toStack(1)) ? direction.getOpposite() : direction;
            return otherTank.tank.getAmountAsLong(0) >= otherTank.tank.getCapacityAsLong(0, net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY)
                    || checkDir == Direction.UP;
        }
        return false;
    }

    /** Emit a vertical quad with position-based UV mapping.
     *  UV is derived from the vertex's block-space position, so the texture
     *  renders at natural 1:1 scale and clips at face edges — matching the
     *  1.12.2 FluidRenderer.TexMap behavior. For N/S faces U comes from X
     *  and V from Y; for E/W faces U comes from Z and V from Y. */
    private static void quad(PoseStack.Pose pose, VertexConsumer builder, TextureAtlasSprite sprite,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float nx, float ny, float nz,
            float r, float g, float b, float a, int light, int overlay) {
        builder.addVertex(pose, x1, y1, z1).setColor(r, g, b, a)
                .setUv(posU(sprite, nx, x1, z1), posV(sprite, y1))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x2, y2, z2).setColor(r, g, b, a)
                .setUv(posU(sprite, nx, x2, z2), posV(sprite, y2))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x3, y3, z3).setColor(r, g, b, a)
                .setUv(posU(sprite, nx, x3, z3), posV(sprite, y3))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x4, y4, z4).setColor(r, g, b, a)
                .setUv(posU(sprite, nx, x4, z4), posV(sprite, y4))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** Emit a horizontal quad with position-based UV mapping.
     *  U derives from X position, V from Z position — matching TexMap.XZ. */
    private static void quadHorizontal(PoseStack.Pose pose, VertexConsumer builder, TextureAtlasSprite sprite,
            float x1, float x2, float z1, float z2, float y,
            float nx, float ny, float nz,
            float r, float g, float b, float a, int light, int overlay) {
        builder.addVertex(pose, x1, y, z1).setColor(r, g, b, a)
                .setUv(sprite.getU(x1), sprite.getV(z1))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x2, y, z1).setColor(r, g, b, a)
                .setUv(sprite.getU(x2), sprite.getV(z1))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x2, y, z2).setColor(r, g, b, a)
                .setUv(sprite.getU(x2), sprite.getV(z2))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        builder.addVertex(pose, x1, y, z2).setColor(r, g, b, a)
                .setUv(sprite.getU(x1), sprite.getV(z2))
                .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** Compute U coordinate from position. For N/S faces (nx==0) U comes from X;
     *  for E/W faces (nx!=0) U comes from Z. */
    private static float posU(TextureAtlasSprite sprite, float nx, float x, float z) {
        return sprite.getU(nx != 0 ? z : x);
    }

    /** Compute V coordinate from Y position (1-y to flip top-to-bottom). */
    private static float posV(TextureAtlasSprite sprite, float y) {
        return sprite.getV(1.0f - y);
    }
}
