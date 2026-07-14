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
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.Sheets;
//? if >=1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?}
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} elif >=1.21.10 {
/*import net.minecraft.client.renderer.state.CameraRenderState;*/
//?}
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
//? if >=1.21.10 {
import net.minecraft.util.profiling.Profiler;
//?}
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraft.client.renderer.rendertype.RenderType;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.factory.tile.TileTank;
import buildcraft.lib.client.render.fluid.FluidRenderer;
//? if >=1.21.10 {
import buildcraft.lib.client.render.tile.BCRenderState;
//?}
import buildcraft.lib.misc.FluidUtilBC;

/**
 * Block entity renderer for the tank. Renders the fluid inside the tank
 * volume (2/16 to 14/16 on X/Z), with height proportional to the fill level.
 * Ported from 1.12.2 RenderTank.
 */
@SuppressWarnings("deprecation")
//? if >=1.21.10 {
public class RenderTank implements BlockEntityRenderer<TileTank, BCRenderState> {
//?} else {
/*public class RenderTank implements BlockEntityRenderer<TileTank> {*/
//?}

    private static final float MIN_XZ = 2.0f / 16.0f + 0.01f;
    private static final float MAX_XZ = 14.0f / 16.0f - 0.01f;
    private static final float MIN_Y = 0.01f;
    private static final float MAX_Y = 1.0f - 0.01f;
    private static final float MIN_Y_CONNECTED = 0.0f;
    private static final float MAX_Y_CONNECTED = 1.0f - 1e-5f;

    public RenderTank(BlockEntityRendererProvider.Context context) {
    }

    //? if >=1.21.10 {
    @Override
    public BCRenderState createRenderState() {
        return new BCRenderState();
    }

    @Override
    public void submit(BCRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        ProfilerFiller _profiler = Profiler.get();
        _profiler.push("buildcraft:tank_submit");
        try {
        // The render state already carries the world pos — no camera-pos reconstruction needed.
        BlockPos pos = renderState.blockPos;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileTank tile)) return;

        // Use the FluidSmoother for interpolated rendering — prevents level snapping
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    //?} else {
    /*// 1.21.1: classic direct BlockEntityRenderer.render — the tile + partialTicks are passed, so no
    // camera-pos reconstruction is needed. The passed buffers/packedLight/packedOverlay go unused (the
    // shared body below sources its own buffer/light, exactly as the modern submit path does).
    @Override
    public void render(TileTank tile, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ProfilerFiller _profiler = Minecraft.getInstance().getProfiler();
        _profiler.push("buildcraft:tank_render");
        try {
        BlockPos pos = tile.getBlockPos();
        Level level = tile.getLevel();
        if (level == null) return;*/
    //?}
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

        boolean renderBottom = !connectedDown;
        boolean renderTop = !connectedUp || fillRatio < 1.0f;

        // Translucent for vanilla water, cutout for BC fluids (reuse water texture opaquely)
        //? if >=1.21.10 {
        net.minecraft.client.renderer.rendertype.RenderType renderType =
                FluidUtilBC.shouldRenderTranslucent(fluid)
                    ? net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS) : net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        //?} else {
        /*net.minecraft.client.renderer.RenderType renderType =
                FluidUtilBC.shouldRenderTranslucent(fluid)
                    ? net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS) : net.minecraft.client.renderer.RenderType.entityCutout(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);*/
        //?}

        // 'a' (alpha) is reassigned above (0-alpha clamp), so alias it final for the lambda capture.
        final float fa = a;

        poseStack.pushPose();

        // 26.2 removed immediate-mode rendering (MultiBufferSource / renderBuffers()); route the fluid
        // geometry through the retained-mode submit system. Pre-26.2 nodes (incl. 1.21.1) keep the
        // classic renderBuffers().bufferSource() draw path with identical output.
        //? if >=26.2 {
        /*collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) ->
                renderFluid(pose, consumer, sprite, fluidTop, fluidBottom, gaseous, fillRatio,
                        connectedDown, renderTop, renderBottom, r, g, b, fa, light, overlay));*/
        //?} else {
        MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();
        renderFluid(poseStack.last(), bufferSource.getBuffer(renderType), sprite, fluidTop, fluidBottom,
                gaseous, fillRatio, connectedDown, renderTop, renderBottom, r, g, b, a, light, overlay);
        bufferSource.endBatch();
        //?}

        poseStack.popPose();
        } finally {
            _profiler.pop();
        }
    }

    /** Emit the fluid volume geometry into the given pose/consumer. Node-agnostic: the caller
     *  supplies the {@link PoseStack.Pose} and {@link VertexConsumer} (from the submit collector on
     *  26.2+, or from {@code renderBuffers().bufferSource()} on pre-26.2 nodes). Draw order is
     *  preserved: 4 vertical faces, then the top/bottom horizontals, then the gaseous "open" face. */
    private static void renderFluid(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite sprite,
            float fluidTop, float fluidBottom, boolean gaseous, float fillRatio, boolean connectedDown,
            boolean renderTop, boolean renderBottom,
            float r, float g, float b, float a, int light, int overlay) {
        // North face (facing -Z: CCW from outside)
        FluidRenderer.quad(pose, consumer, sprite, MIN_XZ, fluidTop, MIN_XZ, MAX_XZ, fluidTop, MIN_XZ,
                MAX_XZ, fluidBottom, MIN_XZ, MIN_XZ, fluidBottom, MIN_XZ,
                0, 0, -1, r, g, b, a, light, overlay);
        // South face (facing +Z: CCW from outside)
        FluidRenderer.quad(pose, consumer, sprite, MIN_XZ, fluidBottom, MAX_XZ, MAX_XZ, fluidBottom, MAX_XZ,
                MAX_XZ, fluidTop, MAX_XZ, MIN_XZ, fluidTop, MAX_XZ,
                0, 0, 1, r, g, b, a, light, overlay);
        // West face (facing -X: CCW from outside)
        FluidRenderer.quad(pose, consumer, sprite, MIN_XZ, fluidBottom, MIN_XZ, MIN_XZ, fluidBottom, MAX_XZ,
                MIN_XZ, fluidTop, MAX_XZ, MIN_XZ, fluidTop, MIN_XZ,
                -1, 0, 0, r, g, b, a, light, overlay);
        // East face (facing +X: CCW from outside)
        FluidRenderer.quad(pose, consumer, sprite, MAX_XZ, fluidTop, MIN_XZ, MAX_XZ, fluidTop, MAX_XZ,
                MAX_XZ, fluidBottom, MAX_XZ, MAX_XZ, fluidBottom, MIN_XZ,
                1, 0, 0, r, g, b, a, light, overlay);

        if (renderTop) {
            FluidRenderer.quadHorizontal(pose, consumer, sprite, MIN_XZ, MAX_XZ, MAX_XZ, MIN_XZ, fluidTop,
                    0, 1, 0, r, g, b, a, light, overlay);
        }
        if (renderBottom) {
            FluidRenderer.quadHorizontal(pose, consumer, sprite, MIN_XZ, MAX_XZ, MAX_XZ, MIN_XZ, fluidBottom,
                    0, -1, 0, r, g, b, a, light, overlay);
        }
        // Render the "open" face for non-full gaseous fluid (bottom face visible)
        // or non-full liquid (top face visible when not connected up)
        if (gaseous && fillRatio < 1.0f && !connectedDown) {
            FluidRenderer.quadHorizontal(pose, consumer, sprite, MIN_XZ, MAX_XZ, MAX_XZ, MIN_XZ, fluidBottom,
                    0, -1, 0, r, g, b, a, light, overlay);
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
            FluidStack otherFluid = otherTank.tank.getFluidStack(0);
            FluidStack thisFluid = tile.tank.getFluidStack(0);
            if (otherFluid.isEmpty() || thisFluid.isEmpty()) return false;
            if (!FluidStack.isSameFluidSameComponents(thisFluid, otherFluid)) return false;

            // For gaseous fluids, invert the direction check:
            // a tank below with matching gas connects seamlessly (gas floats up)
            Direction checkDir = FluidUtilBC.isGaseous(thisFluid) ? direction.getOpposite() : direction;
            return otherTank.tank.getAmountMb(0) >= otherTank.tank.getCapacityMb(0)
                    || checkDir == Direction.UP;
        }
        return false;
    }

}
