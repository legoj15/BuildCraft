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
import buildcraft.lib.misc.FluidUtilBC;

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

        // Scale fluid height by fill ratio, with gaseous fluids at the top
        boolean gaseous = FluidUtilBC.isGaseous(fluid);
        float fluidTop, fluidBottom;
        if (gaseous) {
            // Gaseous: fluid renders at the top of the compartment, filling downward
            fluidTop = maxY;
            fluidBottom = maxY - (maxY - minY) * fillRatio;
        } else {
            // Liquid: fluid renders at the bottom, filling upward
            fluidBottom = minY;
            fluidTop = minY + (maxY - minY) * fillRatio;
        }

        VertexConsumer buffer = bufferSource.getBuffer(
                a < 1.0f ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();
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

        // Select power texture (0=off, 1-6 based on powerAvg in micro-MJ)
        int texIndex;
        if (powerAvg <= 0) {
            texIndex = 0; // off
        } else {
            texIndex = (int) (powerAvg * 6 / TileDistiller_BC8.MAX_MJ_PER_TICK);
            texIndex = Math.max(1, Math.min(texIndex, 6));
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

        // The 1.12.2 model assigns UV regions based on face pixel width:
        //   8-pixel-wide face (wide):  UV [4, 8, 12, 16] in face UV space
        //   4-pixel-wide face (narrow): UV [0, 8, 4, 16] in face UV space
        //   top/bottom faces:          UV [4, 0, 12, 8] in face UV space
        // After rotation, sizeX and sizeZ swap, so N/S face width = sizeX, E/W face width = sizeZ.
        // We select UV based on the actual pixel width, not the face name.

        float sprU0 = sprite.getU0();
        float sprU1 = sprite.getU1();
        float sprV0 = sprite.getV0();
        float sprV1 = sprite.getV1();
        float sprURange = sprU1 - sprU0;
        float sprVRange = sprV1 - sprV0;

        // Sub-region: topHalf uses V [0, 0.5] of sprite, bottomHalf uses V [0.5, 1.0]
        float vBase = topHalf ? 0 : 0.5f;

        // Vertical face V is always the same (lower half of sub-region)
        float sideV0 = sprV0 + sprVRange * (vBase + 8.0f / 32.0f);
        float sideV1 = sprV0 + sprVRange * (vBase + 16.0f / 32.0f);

        // Compute UV for N/S faces (span sizeX pixels wide)
        float nsU0, nsU1;
        if (pcb.sizeX >= 8) {
            // Wide: UV U [4/16, 12/16]
            nsU0 = sprU0 + sprURange * (4.0f / 16.0f);
            nsU1 = sprU0 + sprURange * (12.0f / 16.0f);
        } else {
            // Narrow: UV U [0/16, 4/16]
            nsU0 = sprU0 + sprURange * (0.0f / 16.0f);
            nsU1 = sprU0 + sprURange * (4.0f / 16.0f);
        }

        // Compute UV for E/W faces (span sizeZ pixels wide)
        float ewU0, ewU1;
        if (pcb.sizeZ >= 8) {
            // Wide: UV U [4/16, 12/16]
            ewU0 = sprU0 + sprURange * (4.0f / 16.0f);
            ewU1 = sprU0 + sprURange * (12.0f / 16.0f);
        } else {
            // Narrow: UV U [0/16, 4/16]
            ewU0 = sprU0 + sprURange * (0.0f / 16.0f);
            ewU1 = sprU0 + sprURange * (4.0f / 16.0f);
        }

        // Up/Down face UVs (upper half of sub-region): pixel (4,0)→(12,8)
        float udU0 = sprU0 + sprURange * (4.0f / 16.0f);
        float udU1 = sprU0 + sprURange * (12.0f / 16.0f);
        float udV0 = sprV0 + sprVRange * (vBase + 0.0f / 32.0f);
        float udV1 = sprV0 + sprVRange * (vBase + 8.0f / 32.0f);

        // North face (-Z)
        quadUV(pose, buffer, minX, cubeMaxY, minZ, maxX, cubeMaxY, minZ,
                maxX, cubeMinY, minZ, minX, cubeMinY, minZ,
                0, 0, -1, r, g, b, a, light, overlay,
                nsU0, sideV0, nsU1, sideV0, nsU1, sideV1, nsU0, sideV1);
        // South face (+Z)
        quadUV(pose, buffer, minX, cubeMinY, maxZ, maxX, cubeMinY, maxZ,
                maxX, cubeMaxY, maxZ, minX, cubeMaxY, maxZ,
                0, 0, 1, r, g, b, a, light, overlay,
                nsU0, sideV1, nsU1, sideV1, nsU1, sideV0, nsU0, sideV0);
        // West face (-X)
        quadUV(pose, buffer, minX, cubeMinY, minZ, minX, cubeMinY, maxZ,
                minX, cubeMaxY, maxZ, minX, cubeMaxY, minZ,
                -1, 0, 0, r, g, b, a, light, overlay,
                ewU0, sideV1, ewU1, sideV1, ewU1, sideV0, ewU0, sideV0);
        // East face (+X)
        quadUV(pose, buffer, maxX, cubeMaxY, minZ, maxX, cubeMaxY, maxZ,
                maxX, cubeMinY, maxZ, maxX, cubeMinY, minZ,
                1, 0, 0, r, g, b, a, light, overlay,
                ewU0, sideV0, ewU1, sideV0, ewU1, sideV1, ewU0, sideV1);
        // Top/bottom face UV rotation: in default WEST facing (sizeX=8, sizeZ=4), U maps
        // along X and V along Z. After rotation (sizeX=4, sizeZ=8), we rotate the UV 90°
        // to match how 1.12.2's builtin:rotate_facing transforms the UVs.
        boolean rotated = pcb.sizeX < pcb.sizeZ;

        // Top face (+Y) — CW from above so it faces up
        // Vertices: (minX,maxZ) → (maxX,maxZ) → (maxX,minZ) → (minX,minZ)
        if (!rotated) {
            quadUV(pose, buffer, minX, cubeMaxY, maxZ, maxX, cubeMaxY, maxZ,
                    maxX, cubeMaxY, minZ, minX, cubeMaxY, minZ,
                    0, 1, 0, r, g, b, a, light, overlay,
                    udU0, udV1, udU1, udV1, udU1, udV0, udU0, udV0);
        } else {
            // Rotate UV 90° CW
            quadUV(pose, buffer, minX, cubeMaxY, maxZ, maxX, cubeMaxY, maxZ,
                    maxX, cubeMaxY, minZ, minX, cubeMaxY, minZ,
                    0, 1, 0, r, g, b, a, light, overlay,
                    udU0, udV0, udU0, udV1, udU1, udV1, udU1, udV0);
        }
        // Bottom face (-Y) — CW from below so it faces down
        // Vertices: (minX,minZ) → (maxX,minZ) → (maxX,maxZ) → (minX,maxZ)
        if (!rotated) {
            quadUV(pose, buffer, minX, cubeMinY, minZ, maxX, cubeMinY, minZ,
                    maxX, cubeMinY, maxZ, minX, cubeMinY, maxZ,
                    0, -1, 0, r, g, b, a, light, overlay,
                    udU0, udV0, udU1, udV0, udU1, udV1, udU0, udV1);
        } else {
            quadUV(pose, buffer, minX, cubeMinY, minZ, maxX, cubeMinY, minZ,
                    maxX, cubeMinY, maxZ, minX, cubeMinY, maxZ,
                    0, -1, 0, r, g, b, a, light, overlay,
                    udU1, udV0, udU1, udV1, udU0, udV1, udU0, udV0);
        }
    }

    // --- Quad helpers ---

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
        // Determine which axes map to U and V based on the face normal.
        // N/S faces (nz != 0): U = X, V = Y  (TexMap.XY)
        // E/W faces (nx != 0): U = Z, V = Y  (TexMap.ZY)
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
