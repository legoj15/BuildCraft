/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.client.render;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
//? if <1.21.10 {
/*import net.minecraft.client.renderer.MultiBufferSource;*/
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
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

//? if >=1.21.10 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;*/
//?}

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.factory.block.BlockHeatExchange;
import buildcraft.factory.tile.TileHeatExchange;
import buildcraft.factory.tile.TileHeatExchange.EnumProgressState;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSectionEnd;
import buildcraft.factory.tile.TileHeatExchange.ExchangeSectionStart;
import buildcraft.lib.fluid.FluidSmoother;
import buildcraft.lib.misc.FluidUtilBC;

/**
 * Block entity renderer for the heat exchanger. Renders fluid in the four
 * tank compartments (start input bottom, start output side, end input side,
 * end output top) and animated flow between start and end sections.
 * Ported from 1.12.2 RenderHeatExchange.
 */
@SuppressWarnings("deprecation")
//? if >=1.21.10 {
public class RenderHeatExchange implements BlockEntityRenderer<TileHeatExchange, HeatExchangeRenderState> {
//?} else {
/*public class RenderHeatExchange implements BlockEntityRenderer<TileHeatExchange> {*/
//?}

    private static final Map<Direction, TankSideData> TANK_SIDES = new EnumMap<>(Direction.class);
    private static final TankBounds TANK_BOTTOM, TANK_TOP;

    static {
        TANK_BOTTOM = new TankBounds(2, 0, 2, 14, 2, 14);
        TANK_TOP = new TankBounds(2, 14, 2, 14, 16, 14);
        TankBounds start = new TankBounds(0, 4, 4, 2, 12, 12);
        TankBounds end = new TankBounds(14, 4, 4, 16, 12, 12);
        TankSideData sides = new TankSideData(start, end);
        Direction face = Direction.EAST;
        for (int i = 0; i < 4; i++) {
            TANK_SIDES.put(face, sides);
            face = face.getClockWise();
            sides = sides.rotateY();
        }
    }

    public RenderHeatExchange(BlockEntityRendererProvider.Context context) {
    }

    //? if >=1.21.10 {
    @Override
    public HeatExchangeRenderState createRenderState() {
        return new HeatExchangeRenderState();
    }

    @Override
    public void submit(HeatExchangeRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        // The render state already carries the world pos — no camera-pos reconstruction needed.
        BlockPos pos = renderState.blockPos;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TileHeatExchange tile)) return;
    //?} else {
    /*// 1.21.1: classic direct BlockEntityRenderer.render — tile + partialTicks are passed, so the
    // camera-pos reconstruction is replaced by reading pos/level off the tile. The passed
    // buffers/packedLight/packedOverlay go unused (the shared body sources its own buffer/light).
    @Override
    public void render(TileHeatExchange tile, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockPos pos = tile.getBlockPos();
        Level level = tile.getLevel();
        if (level == null) return;*/
    //?}

        if (!tile.isStart()) return;

        ExchangeSectionStart section = (ExchangeSectionStart) tile.getSection();
        if (section == null) return;
        ExchangeSectionEnd sectionEnd = section.getEndSection();

        // Fallback: if endSection isn't linked yet, resolve it directly for rendering
        // and trigger the tile to re-check neighbors for permanent linkage
        if (sectionEnd == null) {
            BlockState st = tile.getBlockState();
            if (st.getBlock() instanceof BlockHeatExchange) {
                Direction dir = st.getValue(BlockHeatExchange.FACING).getCounterClockWise();
                for (int i = 1; i < 6; i++) {
                    BlockEntity neighbor = level.getBlockEntity(pos.relative(dir, i));
                    if (neighbor instanceof TileHeatExchange other && other.isEnd()) {
                        sectionEnd = (ExchangeSectionEnd) other.getSection();
                        tile.markCheckNeighbours(); // trigger permanent linkage on next tick
                        break;
                    }
                    if (!(neighbor instanceof TileHeatExchange)) break;
                }
            }
        }

        BlockState state = tile.getBlockState();
        if (!(state.getBlock() instanceof BlockHeatExchange)) return;

        Direction facing = state.getValue(BlockHeatExchange.FACING);
        Direction face = facing.getCounterClockWise();
        TankSideData sideTank = TANK_SIDES.get(face);
        if (sideTank == null) return;

        int light = buildcraft.lib.client.render.LightUtil.getLightCoords(level, pos);

        poseStack.pushPose();

        //? if >=1.21.10 {
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        //?} else {
        /*MultiBufferSource.BufferSource bufferSource =
                Minecraft.getInstance().renderBuffers().bufferSource();*/
        //?}

        //? if >=1.21.10 {
        // Render fluid in start tanks
        renderSmoothedFluid(section.smoothedTankInput, TANK_BOTTOM, poseStack, collector, light, partialTicks);
        renderSmoothedFluid(section.smoothedTankOutput, sideTank.start, poseStack, collector, light, partialTicks);
        //?} else {
        /*// Render fluid in start tanks
        renderSmoothedFluid(section.smoothedTankInput, TANK_BOTTOM, poseStack, bufferSource, light, partialTicks);
        renderSmoothedFluid(section.smoothedTankOutput, sideTank.start, poseStack, bufferSource, light, partialTicks);*/
        //?}

        // Render fluid in end tanks
        if (sectionEnd != null) {
            BlockPos diff = sectionEnd.getTile().getBlockPos().subtract(tile.getBlockPos());
            poseStack.translate(diff.getX(), diff.getY(), diff.getZ());
            //? if >=1.21.10 {
            renderSmoothedFluid(sectionEnd.smoothedTankOutput, TANK_TOP, poseStack, collector, light, partialTicks);
            renderSmoothedFluid(sectionEnd.smoothedTankInput, sideTank.end, poseStack, collector, light, partialTicks);
            //?} else {
            /*renderSmoothedFluid(sectionEnd.smoothedTankOutput, TANK_TOP, poseStack, bufferSource, light, partialTicks);
            renderSmoothedFluid(sectionEnd.smoothedTankInput, sideTank.end, poseStack, bufferSource, light, partialTicks);*/
            //?}
            poseStack.translate(-diff.getX(), -diff.getY(), -diff.getZ());
        }

        // Render flow animation
        int middles = section.middleCount;
        if (middles > 0 && sectionEnd != null) {
            EnumProgressState progressState = section.getProgressState();
            double progress = section.getProgress(partialTicks);
            if (progress > 0) {
                double length = middles + 2 - 4 / 16.0 - 0.02;
                double p0 = 2 / 16.0 + 0.01;
                double p1 = p0 + length - 0.01;
                double progressStart = p0;
                double progressEnd = p0 + length * progress;

                boolean flip = progressState == EnumProgressState.PREPARING;
                flip ^= face.getAxisDirection() == AxisDirection.NEGATIVE;

                if (flip) {
                    progressStart = p1 - length * progress;
                    progressEnd = p1;
                }
                BlockPos diff = BlockPos.ZERO;
                if (face.getAxisDirection() == AxisDirection.NEGATIVE) {
                    diff = diff.relative(face, middles + 1);
                }
                double otherStart = flip ? p0 : p1 - length * progress;
                double otherEnd = flip ? p0 + length * progress : p1;
                Vec3 vDiff = Vec3.atLowerCornerOf(diff);
                // Flow volume is controlled by progress, not tank levels.
                // Use raw fluid type (not smoothed amount) — matches 1.12.2 behavior.
                FluidStack coolantFluid = sectionEnd.smoothedTankInput.getFluid();
                FluidStack heatantFluid = section.smoothedTankInput.getFluid();
                // Render upper flow (coolant)
                if (!coolantFluid.isEmpty()) {
                    //? if >=1.21.10 {
                    renderFlow(vDiff, face, poseStack, collector, progressStart + 0.01, progressEnd - 0.01,
                            coolantFluid, 4, partialTicks, light);
                    //?} else {
                    /*renderFlow(vDiff, face, poseStack, bufferSource, progressStart + 0.01, progressEnd - 0.01,
                            coolantFluid, 4, partialTicks, light);*/
                    //?}
                }
                // Render lower flow (heatant)
                if (!heatantFluid.isEmpty()) {
                    //? if >=1.21.10 {
                    renderFlow(vDiff, face.getOpposite(), poseStack, collector, otherStart, otherEnd,
                            heatantFluid, 2, partialTicks, light);
                    //?} else {
                    /*renderFlow(vDiff, face.getOpposite(), poseStack, bufferSource, otherStart, otherEnd,
                            heatantFluid, 2, partialTicks, light);*/
                    //?}
                }
            }
        }

        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(TileHeatExchange blockEntity, Vec3 cameraPos) {
        return blockEntity.isStart();
    }

    // --- Fluid Rendering ---

    //? if >=1.21.10 {
    private static void renderSmoothedFluid(FluidSmoother smoother, TankBounds bounds, PoseStack poseStack,
                                             SubmitNodeCollector collector, int light, float partialTicks) {
    //?} else {
    /*private static void renderSmoothedFluid(FluidSmoother smoother, TankBounds bounds, PoseStack poseStack,
                                             MultiBufferSource.BufferSource bufferSource, int light, float partialTicks) {*/
    //?}
        FluidSmoother.FluidStackInterp interp = smoother.getFluidForRender(partialTicks);
        if (interp == null || interp.amount() <= 0) return;

        FluidStack fluid = interp.fluid();
        int capacity = smoother.getCapacity();
        if (capacity <= 0) return;

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

        float fillRatio = (float) (interp.amount() / capacity);

        float shrink = 1.0f / 64.0f;
        float minX = bounds.minX / 16.0f + shrink;
        float minY = bounds.minY / 16.0f + shrink;
        float minZ = bounds.minZ / 16.0f + shrink;
        float maxX = bounds.maxX / 16.0f - shrink;
        float maxY = bounds.maxY / 16.0f - shrink;
        float maxZ = bounds.maxZ / 16.0f - shrink;

        boolean gaseous = FluidUtilBC.isGaseous(fluid);
        float fluidTop, fluidBottom;
        if (gaseous) {
            fluidTop = maxY;
            fluidBottom = maxY - (maxY - minY) * fillRatio;
        } else {
            fluidBottom = minY;
            fluidTop = minY + (maxY - minY) * fillRatio;
        }

        // 'a' is reassigned above (0-alpha clamp), so alias it final for the lambda capture.
        final float fa = a;
        // Translucent for vanilla water, cutout for BC fluids (reuse water texture opaquely)
        //? if >=1.21.10 {
        collector.submitCustomGeometry(poseStack,
                FluidUtilBC.shouldRenderTranslucent(fluid)
                    ? net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS) : net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS),
                (pose, buffer) -> drawTankFluid(pose, buffer, sprite,
                        minX, minZ, maxX, maxZ, fluidTop, fluidBottom, r, g, b, fa, light));
        //?} else {
        /*VertexConsumer buffer = bufferSource.getBuffer(
                FluidUtilBC.shouldRenderTranslucent(fluid)
                    ? net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS) : net.minecraft.client.renderer.RenderType.entityCutout(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
        drawTankFluid(poseStack.last(), buffer, sprite,
                minX, minZ, maxX, maxZ, fluidTop, fluidBottom, r, g, b, a, light);*/
        //?}
    }

    /** Node-agnostic core: draws the six faces of a tank's fluid box into {@code buffer}. */
    private static void drawTankFluid(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
            float minX, float minZ, float maxX, float maxZ, float fluidTop, float fluidBottom,
            float r, float g, float b, float a, int light) {
        int overlay = OverlayTexture.NO_OVERLAY;

        // North face (-Z)
        quad(pose, buffer, sprite, minX, fluidTop, minZ, maxX, fluidTop, minZ,
                maxX, fluidBottom, minZ, minX, fluidBottom, minZ,
                0, 0, -1, r, g, b, a, light, overlay);
        // South face (+Z)
        quad(pose, buffer, sprite, minX, fluidBottom, maxZ, maxX, fluidBottom, maxZ,
                maxX, fluidTop, maxZ, minX, fluidTop, maxZ,
                0, 0, 1, r, g, b, a, light, overlay);
        // West face (-X)
        quad(pose, buffer, sprite, minX, fluidBottom, minZ, minX, fluidBottom, maxZ,
                minX, fluidTop, maxZ, minX, fluidTop, minZ,
                -1, 0, 0, r, g, b, a, light, overlay);
        // East face (+X)
        quad(pose, buffer, sprite, maxX, fluidTop, minZ, maxX, fluidTop, maxZ,
                maxX, fluidBottom, maxZ, maxX, fluidBottom, minZ,
                1, 0, 0, r, g, b, a, light, overlay);
        // Top face
        quadHorizontal(pose, buffer, sprite, minX, maxX, maxZ, minZ, fluidTop,
                0, 1, 0, r, g, b, a, light, overlay);
        // Bottom face
        quadHorizontal(pose, buffer, sprite, minX, maxX, maxZ, minZ, fluidBottom,
                0, -1, 0, r, g, b, a, light, overlay);
    }

    // --- Flow Rendering ---

    //? if >=1.21.10 {
    private static void renderFlow(Vec3 diff, Direction face, PoseStack poseStack,
                                    SubmitNodeCollector collector,
                                    double s, double e, FluidStack fluid,
                                    int point, float partialTicks, int light) {
    //?} else {
    /*private static void renderFlow(Vec3 diff, Direction face, PoseStack poseStack,
                                    MultiBufferSource.BufferSource bufferSource,
                                    double s, double e, FluidStack fluid,
                                    int point, float partialTicks, int light) {*/
    //?}
        if (fluid.isEmpty()) return;

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

        // Translucent for vanilla water, cutout for BC fluids
        //? if >=1.21.10 {
        final RenderType renderType =
                FluidUtilBC.shouldRenderTranslucent(fluid)
                    ? net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS) : net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        //?} else {
        /*VertexConsumer buffer = bufferSource.getBuffer(
                FluidUtilBC.shouldRenderTranslucent(fluid)
                    ? net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS) : net.minecraft.client.renderer.RenderType.entityCutout(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));*/
        //?}

        Level level = Minecraft.getInstance().level;
        double tickTime = level != null ? level.getGameTime() : 0;
        double offset = (tickTime + partialTicks) % 31 / 31.0;

        Direction renderFace = face;
        if (face.getAxisDirection() == AxisDirection.NEGATIVE) {
            offset = -offset;
            renderFace = face.getOpposite();
        }
        //? if >=1.21.10 {
        Vec3 dirVec = Vec3.atLowerCornerOf(renderFace.getUnitVec3i());
        //?} else {
        /*Vec3 dirVec = Vec3.atLowerCornerOf(renderFace.getNormal());*/
        //?}
        double ds = (point + 0.1) / 16.0;
        final float minCross = (float) ds;
        final float maxCross = (float) (1 - ds);

        diff = diff.subtract(dirVec.scale(offset));
        s += offset;
        e += offset;
        if (s < 0) {
            s++;
            e++;
            diff = diff.subtract(dirVec);
        }

        for (int i = 0; i <= e; i++) {
            if (i < s - 1) {
                diff = diff.add(dirVec);
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(diff.x, diff.y, diff.z);
            diff = diff.add(dirVec);

            double s1 = s < i ? 0 : (s % 1);
            double e1 = e > i + 1 ? 1 : (e % 1);

            float flowMinX = minCross, flowMaxX = maxCross;
            float flowMinY = minCross, flowMaxY = maxCross;
            float flowMinZ = minCross, flowMaxZ = maxCross;

            // Set the flow axis bounds
            switch (renderFace.getAxis()) {
                case X -> { flowMinX = (float) s1; flowMaxX = (float) e1; }
                case Y -> { flowMinY = (float) s1; flowMaxY = (float) e1; }
                case Z -> { flowMinZ = (float) s1; flowMaxZ = (float) e1; }
            }

            boolean[] sides = new boolean[6];
            Arrays.fill(sides, true);
            if (s < i) sides[renderFace.getOpposite().ordinal()] = false;
            if (e > i + 1) sides[renderFace.ordinal()] = false;

            // Snapshot per-box bounds/visibility into final locals so the modern submit lambda can capture them.
            final float fMinX = flowMinX, fMaxX = flowMaxX;
            final float fMinY = flowMinY, fMaxY = flowMaxY;
            final float fMinZ = flowMinZ, fMaxZ = flowMaxZ;
            final boolean[] visible = sides;
            final float fr = r, fg = g, fb = b, fa = a;
            final TextureAtlasSprite fSprite = sprite;
            final int fLight = light;

            //? if >=1.21.10 {
            collector.submitCustomGeometry(poseStack, renderType,
                    (pose, buffer) -> drawFlowBox(pose, buffer, fSprite, visible,
                            fMinX, fMinY, fMinZ, fMaxX, fMaxY, fMaxZ, fr, fg, fb, fa, fLight));
            //?} else {
            /*drawFlowBox(poseStack.last(), buffer, fSprite, visible,
                    fMinX, fMinY, fMinZ, fMaxX, fMaxY, fMaxZ, fr, fg, fb, fa, fLight);*/
            //?}

            poseStack.popPose();
        }
    }

    /** Node-agnostic core: draws the visible faces of one flow box into {@code buffer}. */
    private static void drawFlowBox(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
            boolean[] sides,
            float flowMinX, float flowMinY, float flowMinZ, float flowMaxX, float flowMaxY, float flowMaxZ,
            float r, float g, float b, float a, int light) {
        int overlay = OverlayTexture.NO_OVERLAY;

        // Render all visible faces
        if (sides[Direction.NORTH.ordinal()]) {
            quad(pose, buffer, sprite, flowMinX, flowMaxY, flowMinZ, flowMaxX, flowMaxY, flowMinZ,
                    flowMaxX, flowMinY, flowMinZ, flowMinX, flowMinY, flowMinZ,
                    0, 0, -1, r, g, b, a, light, overlay);
        }
        if (sides[Direction.SOUTH.ordinal()]) {
            quad(pose, buffer, sprite, flowMinX, flowMinY, flowMaxZ, flowMaxX, flowMinY, flowMaxZ,
                    flowMaxX, flowMaxY, flowMaxZ, flowMinX, flowMaxY, flowMaxZ,
                    0, 0, 1, r, g, b, a, light, overlay);
        }
        if (sides[Direction.WEST.ordinal()]) {
            quad(pose, buffer, sprite, flowMinX, flowMinY, flowMinZ, flowMinX, flowMinY, flowMaxZ,
                    flowMinX, flowMaxY, flowMaxZ, flowMinX, flowMaxY, flowMinZ,
                    -1, 0, 0, r, g, b, a, light, overlay);
        }
        if (sides[Direction.EAST.ordinal()]) {
            quad(pose, buffer, sprite, flowMaxX, flowMaxY, flowMinZ, flowMaxX, flowMaxY, flowMaxZ,
                    flowMaxX, flowMinY, flowMaxZ, flowMaxX, flowMinY, flowMinZ,
                    1, 0, 0, r, g, b, a, light, overlay);
        }
        if (sides[Direction.UP.ordinal()]) {
            quadHorizontal(pose, buffer, sprite, flowMinX, flowMaxX, flowMaxZ, flowMinZ, flowMaxY,
                    0, 1, 0, r, g, b, a, light, overlay);
        }
        if (sides[Direction.DOWN.ordinal()]) {
            quadHorizontal(pose, buffer, sprite, flowMinX, flowMaxX, flowMaxZ, flowMinZ, flowMinY,
                    0, -1, 0, r, g, b, a, light, overlay);
        }
    }

    // --- Quad helpers (same pattern as RenderDistiller) ---

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

    private static float posU(TextureAtlasSprite sprite, float nx, float x, float z) {
        return sprite.getU(nx != 0 ? z : x);
    }

    private static float posV(TextureAtlasSprite sprite, float y) {
        return sprite.getV(1.0f - y);
    }

    // --- Inner classes ---

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

    static class TankSideData {
        final TankBounds start, end;

        TankSideData(TankBounds start, TankBounds end) {
            this.start = start;
            this.end = end;
        }

        TankSideData rotateY() {
            return new TankSideData(start.rotateY(), end.rotateY());
        }
    }
}
