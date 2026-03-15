/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.render;

import java.util.Arrays;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipeFlowRenderer;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.ModelUtil.UvFaceData;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.VecUtil;

import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.flow.PipeFlowFluids;

/** Renders fluids flowing through fluid pipes.
 *
 * Ported from 1.12.2 — the geometry calculations are identical but rendering
 * uses {@link MutableQuad}/{@link ModelUtil} instead of the old GL-based
 * {@code FluidRenderer}. */
public enum PipeFlowRendererFluids implements IPipeFlowRenderer<PipeFlowFluids> {
    INSTANCE;

    private static final boolean[] ALL_SIDES = new boolean[6];
    static {
        Arrays.fill(ALL_SIDES, true);
    }

    @Override
    public void render(PipeFlowFluids flow, double x, double y, double z, float partialTicks, VertexConsumer bb, PoseStack.Pose pose) {
        FluidStack forRender = flow.getFluidStackForRender();
        if (forRender == null || forRender.isEmpty()) {
            return;
        }

        // Resolve sprite + tint from the fluid type
        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(forRender.getFluid());
        Identifier stillTexture = fluidExt.getStillTexture(forRender);
        if (stillTexture == null) {
            return;
        }
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(stillTexture);

        int tintColor = fluidExt.getTintColor(forRender);
        int tR = (tintColor >> 16) & 0xFF;
        int tG = (tintColor >> 8) & 0xFF;
        int tB = tintColor & 0xFF;
        int tA = (tintColor >> 24) & 0xFF;
        if (tA == 0) tA = 0xFF; // Default to fully opaque if alpha is 0

        // Select render type: translucent for vanilla water (texture has
        // alpha pixels), cutout for everything else (BC fluids reuse the
        // water texture as a tint base but should be opaque).
        MultiBufferSource.BufferSource bufferSource =
            Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer fluidBB = bufferSource.getBuffer(
            FluidUtilBC.shouldRenderTranslucent(forRender)
                ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockSheet());

        double[] amounts = flow.getAmountsForRender(partialTicks);
        Vec3[] offsets = flow.getOffsetsForRender(partialTicks);


        boolean gas = forRender.getFluid().getFluidType().isLighterThanAir();
        boolean horizontal = false;
        boolean vertical = flow.pipe.isConnected(gas ? Direction.DOWN : Direction.UP);

        // --- Render each face section ---
        for (Direction face : Direction.values()) {
            double size = ((Pipe) flow.pipe).getConnectedDist(face);
            double amount = amounts[face.ordinal()];
            if (face.getAxis() != Axis.Y) {
                horizontal |= flow.pipe.isConnected(face) && amount > 0;
            }

            if (amount <= 0) continue;

            Vec3 center = VecUtil.offset(new Vec3(0.5, 0.5, 0.5), face, 0.245 + size / 2);
            Vec3 radius = new Vec3(0.24, 0.24, 0.24);
            radius = VecUtil.replaceValue(radius, face.getAxis(), 0.005 + size / 2);

            if (face.getAxis() == Axis.Y) {
                double perc = amount / flow.capacity;
                perc = Math.sqrt(perc);
                radius = new Vec3(perc * 0.24, radius.y, perc * 0.24);
            }

            Vec3 offset = offsets[face.ordinal()];
            if (offset == null) offset = Vec3.ZERO;
            center = center.add(offset);

            Vec3 min = center.subtract(radius);
            Vec3 max = center.add(radius);

            if (face.getAxis() == Axis.Y) {
                renderFluidCuboid(min, max, 1, 1, sprite, tR, tG, tB, tA, fluidBB, pose);
            } else {
                renderFluidCuboid(min, max, amount, flow.capacity, sprite, tR, tG, tB, tA, fluidBB, pose);
            }
        }

        // --- Render center section ---
        double centerAmount = amounts[EnumPipePart.CENTER.getIndex()];
        Vec3 centerOffset = offsets[EnumPipePart.CENTER.getIndex()];
        if (centerOffset == null) centerOffset = Vec3.ZERO;

        double horizPos = 0.26;

        if (horizontal || !vertical) {
            Vec3 min = new Vec3(0.26, 0.26, 0.26).add(centerOffset);
            Vec3 max = new Vec3(0.74, 0.74, 0.74).add(centerOffset);

            renderFluidCuboid(min, max, centerAmount, flow.capacity, sprite, tR, tG, tB, tA, fluidBB, pose);
            horizPos += (max.y - min.y) * centerAmount / flow.capacity;
        }

        if (vertical && horizPos < 0.74) {
            double perc = centerAmount / flow.capacity;
            perc = Math.sqrt(perc);
            double minXZ = 0.5 - 0.24 * perc;
            double maxXZ = 0.5 + 0.24 * perc;

            double yMin = gas ? 0.26 : horizPos;
            double yMax = gas ? 1 - horizPos : 0.74;

            Vec3 min = new Vec3(minXZ, yMin, minXZ).add(centerOffset);
            Vec3 max = new Vec3(maxXZ, yMax, maxXZ).add(centerOffset);

            renderFluidCuboid(min, max, 1, 1, sprite, tR, tG, tB, tA, fluidBB, pose);
        }

        bufferSource.endBatch();
    }

    /** Renders a fluid cuboid using {@link MutableQuad}s, with fill-level scaling on the Y axis.
     * This replaces the 1.12.2 {@code FluidRenderer.renderFluid()} call. */
    private static void renderFluidCuboid(Vec3 min, Vec3 max, double amount, double capacity,
            TextureAtlasSprite sprite, int tR, int tG, int tB, int tA, VertexConsumer bb, PoseStack.Pose pose) {
        if (amount <= 0 || capacity <= 0) return;

        // Scale height by fill level (same as 1.12.2 — non-gaseous fluids fill from bottom)
        double height = Math.min(amount / capacity, 1.0);
        Vec3 realMax = new Vec3(max.x, min.y + (max.y - min.y) * height, max.z);

        Vector3f center = new Vector3f(
            (float) (min.x + realMax.x) / 2f,
            (float) (min.y + realMax.y) / 2f,
            (float) (min.z + realMax.z) / 2f
        );
        Vector3f radius = new Vector3f(
            (float) (realMax.x - min.x) / 2f,
            (float) (realMax.y - min.y) / 2f,
            (float) (realMax.z - min.z) / 2f
        );

        if (radius.x <= 0 || radius.y <= 0 || radius.z <= 0) return;

        UvFaceData uvs = new UvFaceData();
        for (Direction face : Direction.values()) {
            // Compute proper UVs from the cuboid's bounding box
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                min.x, min.y, min.z, realMax.x, realMax.y, realMax.z
            );
            ModelUtil.mapBoxToUvs(box, face, uvs);

            MutableQuad quad = ModelUtil.createFace(face, center, radius, uvs);
            quad.texFromSprite(sprite);
            quad.colouri(tR, tG, tB, tA);
            quad.lighti(15, 15);
            quad.render(pose, bb);
        }
    }
}
