/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.transport.pipe.IPipeFlowRenderer;

import buildcraft.lib.misc.FluidUtilBC;

import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.flow.PipeFlowFluids;

/** Renders fluids flowing through fluid pipes.
 *
 * <p>Geometry stays at the actual pipe position; UV coordinates are shifted by
 * the flow offset to produce a scrolling animation. Matches the 1.12.2 visual.
 *
 * <p><b>Allocation hot path:</b> every fluid pipe in render distance runs
 * through this each frame, so the inner loop must be allocation-free. We:
 * <ul>
 *   <li>Cache the resolved sprite identifier / tint / translucent flag on
 *       {@link PipeFlowFluids} per fluid-type change (the sprite itself is
 *       still looked up per frame — that's a cheap HashMap lookup and naturally
 *       survives atlas reloads).</li>
 *   <li>Use thread-local scratch buffers for the interpolated amounts and
 *       offsets so the per-frame {@code double[7]} / {@code Vec3[7]} allocation
 *       from the old getters is gone.</li>
 *   <li>Carry all geometry as primitives (no {@code Vec3} / {@code AABB} /
 *       {@code UvFaceData} / {@code Vector3f} allocations during the cuboid
 *       loop).</li>
 *   <li>Emit vertices directly to the {@link VertexConsumer} instead of
 *       building a {@link buildcraft.lib.client.model.MutableQuad}, removing
 *       the per-quad object plus the ~13 {@code Vector3f}s
 *       {@code ModelUtil.createFace} allocated internally.</li>
 * </ul>
 *
 * <p>Vertex output is bit-identical to the previous {@code MutableQuad}-based
 * path: the same 4 corners are emitted in the same winding order, with the
 * same sprite-mapped UVs (the per-face dispatch tables in {@link #emitFace}
 * mirror {@link buildcraft.lib.client.model.ModelUtil#createFace} after
 * inlining {@code getPointsForFace} + {@code addOrNegate}, and the per-face
 * UV mapping mirrors {@code ModelUtil.mapBoxToUvs}). */
public enum PipeFlowRendererFluids implements IPipeFlowRenderer<PipeFlowFluids> {
    INSTANCE;

    // Scratch buffers reused across render() calls. The BER render thread is
    // single-threaded in practice; ThreadLocal protects any future off-thread
    // submission.
    private static final ThreadLocal<double[]> SCRATCH_AMOUNTS =
        ThreadLocal.withInitial(() -> new double[7]);
    private static final ThreadLocal<double[]> SCRATCH_OFF_X =
        ThreadLocal.withInitial(() -> new double[7]);
    private static final ThreadLocal<double[]> SCRATCH_OFF_Y =
        ThreadLocal.withInitial(() -> new double[7]);
    private static final ThreadLocal<double[]> SCRATCH_OFF_Z =
        ThreadLocal.withInitial(() -> new double[7]);

    // Scratch vectors for vertex transforms. Replace the per-vertex allocations
    // that PoseStack.transformPosition / Matrix3f.transform require.
    private static final ThreadLocal<Vector3f> TL_POS =
        ThreadLocal.withInitial(Vector3f::new);
    private static final ThreadLocal<Vector3f> TL_NORM =
        ThreadLocal.withInitial(Vector3f::new);

    @Override
    public void render(PipeFlowFluids flow, double x, double y, double z,
                        float partialTicks, VertexConsumer bb, PoseStack.Pose pose) {
        FluidStack forRender = flow.getFluidStackForRender();
        if (forRender == null || forRender.isEmpty()) {
            return;
        }

        // Cache sprite Identifier / tint / translucent flag per fluid-type change.
        ensureRenderCache(flow, forRender);
        Identifier stillTexture = flow.renderCacheSpriteId;
        if (stillTexture == null) return;
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(stillTexture);

        int tR = flow.renderCacheTintR;
        int tG = flow.renderCacheTintG;
        int tB = flow.renderCacheTintB;
        int tA = flow.renderCacheTintA;

        // Translucent for vanilla water (the texture has alpha pixels);
        // cutout for BC fluids (which reuse water as a tint base but should be
        // opaque).
        MultiBufferSource.BufferSource bufferSource =
            Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer fluidBB = bufferSource.getBuffer(
            flow.renderCacheTranslucent
                ? RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
                : RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));

        double[] amounts = SCRATCH_AMOUNTS.get();
        double[] offX = SCRATCH_OFF_X.get();
        double[] offY = SCRATCH_OFF_Y.get();
        double[] offZ = SCRATCH_OFF_Z.get();
        flow.writeAmountsForRender(partialTicks, amounts);
        flow.writeOffsetsForRender(partialTicks, offX, offY, offZ);

        boolean gas = forRender.getFluid().getFluidType().isLighterThanAir();
        boolean horizontal = false;
        boolean vertical = flow.pipe.isConnected(gas ? Direction.DOWN : Direction.UP);

        // --- Per-face sections ---
        for (Direction face : Direction.values()) {
            double size = ((Pipe) flow.pipe).getConnectedDist(face);
            int fi = face.ordinal();
            double amount = amounts[fi];
            if (face.getAxis() != Axis.Y) {
                horizontal |= flow.pipe.isConnected(face) && amount > 0;
            }
            if (amount <= 0) continue;

            // center = (0.5, 0.5, 0.5) shifted along `face` by (0.245 + size/2).
            double centerShift = 0.245 + size / 2;
            double cx = 0.5 + face.getStepX() * centerShift;
            double cy = 0.5 + face.getStepY() * centerShift;
            double cz = 0.5 + face.getStepZ() * centerShift;

            // radius = (0.24, 0.24, 0.24) with the face-axis component replaced.
            double rx = 0.24, ry = 0.24, rz = 0.24;
            double faceAxisRadius = 0.005 + size / 2;
            switch (face.getAxis()) {
                case X -> rx = faceAxisRadius;
                case Y -> ry = faceAxisRadius;
                case Z -> rz = faceAxisRadius;
            }
            if (face.getAxis() == Axis.Y) {
                double perc = Math.sqrt(amount / flow.capacity);
                rx = perc * 0.24;
                rz = perc * 0.24;
            }

            double minX = cx - rx, minY = cy - ry, minZ = cz - rz;
            double maxX = cx + rx, maxY = cy + ry, maxZ = cz + rz;
            double ox = offX[fi], oy = offY[fi], oz = offZ[fi];

            double cuboidAmount;
            double cuboidCapacity;
            if (face.getAxis() == Axis.Y) {
                // Y-faces have their geometry already scaled by `perc` above —
                // pass amount=capacity=1 so renderFluidCuboid doesn't scale again.
                cuboidAmount = 1;
                cuboidCapacity = 1;
            } else {
                cuboidAmount = amount;
                cuboidCapacity = flow.capacity;
            }
            renderFluidCuboid(
                minX, minY, minZ, maxX, maxY, maxZ,
                minX + ox, minY + oy, minZ + oz,
                maxX + ox, maxY + oy, maxZ + oz,
                cuboidAmount, cuboidCapacity, gas,
                sprite, tR, tG, tB, tA, fluidBB, pose);
        }

        // --- Centre section ---
        int ci = EnumPipePart.CENTER.getIndex();
        double centerAmount = amounts[ci];
        double cox = offX[ci];
        double coy = offY[ci];
        double coz = offZ[ci];

        double horizPos = 0.26;
        if (horizontal || !vertical) {
            double minX = 0.26, minY = 0.26, minZ = 0.26;
            double maxX = 0.74, maxY = 0.74, maxZ = 0.74;
            renderFluidCuboid(
                minX, minY, minZ, maxX, maxY, maxZ,
                minX + cox, minY + coy, minZ + coz,
                maxX + cox, maxY + coy, maxZ + coz,
                centerAmount, flow.capacity, gas,
                sprite, tR, tG, tB, tA, fluidBB, pose);
            horizPos += (maxY - minY) * centerAmount / flow.capacity;
        }

        if (vertical && horizPos < 0.74) {
            double perc = Math.sqrt(centerAmount / flow.capacity);
            double minXZ = 0.5 - 0.24 * perc;
            double maxXZ = 0.5 + 0.24 * perc;
            double yMin = gas ? 0.26 : horizPos;
            double yMax = gas ? 1 - horizPos : 0.74;

            renderFluidCuboid(
                minXZ, yMin, minXZ, maxXZ, yMax, maxXZ,
                minXZ + cox, yMin + coy, minXZ + coz,
                maxXZ + cox, yMax + coy, maxXZ + coz,
                1, 1, gas,
                sprite, tR, tG, tB, tA, fluidBB, pose);
        }
    }

    /** Resolves the sprite Identifier, tint colour, and translucent flag for the
     *  current fluid and caches them on the flow. Reference-comparing the {@link Fluid}
     *  singleton works because vanilla and modded fluids are registered as singletons.
     *  The atlas sprite itself is looked up per frame from the cached id, which
     *  naturally survives atlas reloads. */
    private static void ensureRenderCache(PipeFlowFluids flow, FluidStack fluidStack) {
        Fluid current = fluidStack.getFluid();
        if (current == flow.renderCacheFluid) return;
        flow.renderCacheFluid = current;
        flow.renderCacheSpriteId = FluidUtilBC.getFluidTexture(fluidStack);
        int color = FluidUtilBC.getFluidColor(fluidStack);
        flow.renderCacheTintR = (color >> 16) & 0xFF;
        flow.renderCacheTintG = (color >> 8) & 0xFF;
        flow.renderCacheTintB = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        flow.renderCacheTintA = a == 0 ? 0xFF : a;
        flow.renderCacheTranslucent = FluidUtilBC.shouldRenderTranslucent(fluidStack);
    }

    /** Renders a fluid cuboid by emitting vertices directly to {@code bb}.
     *
     * <p>Geometry is the box [{@code min*}, {@code max*}]; UV space is the box
     * [{@code uvMin*}, {@code uvMax*}] which may be offset relative to geometry
     * to drive the scrolling animation. When the UV box crosses an integer cell
     * boundary the geometry is split so each piece maps to its own [0,1] UV cell
     * (with the offset accounted for via {@code offX/Y/Z}); typically the offset
     * is wrapped to [-0.5, 0.5] in {@code Section.tickClient} so only one cell
     * is hit per axis. */
    private static void renderFluidCuboid(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double uvMinX, double uvMinY, double uvMinZ,
            double uvMaxX, double uvMaxY, double uvMaxZ,
            double amount, double capacity, boolean gas,
            TextureAtlasSprite sprite,
            int tR, int tG, int tB, int tA,
            VertexConsumer bb, PoseStack.Pose pose) {
        if (amount <= 0 || capacity <= 0) return;

        // Scale height by fill level. Gaseous fluids fill from the top down;
        // normal fluids fill from the bottom up.
        double height = Math.min(amount / capacity, 1.0);
        double realMinX, realMinY, realMinZ, realMaxX, realMaxY, realMaxZ;
        double realUvMinX, realUvMinY, realUvMinZ, realUvMaxX, realUvMaxY, realUvMaxZ;
        if (gas) {
            realMinX = minX; realMinZ = minZ;
            realMinY = maxY - (maxY - minY) * height;
            realMaxX = maxX; realMaxY = maxY; realMaxZ = maxZ;
            realUvMinX = uvMinX; realUvMinZ = uvMinZ;
            realUvMinY = uvMaxY - (uvMaxY - uvMinY) * height;
            realUvMaxX = uvMaxX; realUvMaxY = uvMaxY; realUvMaxZ = uvMaxZ;
        } else {
            realMinX = minX; realMinY = minY; realMinZ = minZ;
            realMaxX = maxX; realMaxZ = maxZ;
            realMaxY = minY + (maxY - minY) * height;
            realUvMinX = uvMinX; realUvMinY = uvMinY; realUvMinZ = uvMinZ;
            realUvMaxX = uvMaxX; realUvMaxZ = uvMaxZ;
            realUvMaxY = uvMinY + (uvMaxY - uvMinY) * height;
        }

        // offsetVec = UV-space - geometry-space.
        double offX = realUvMinX - realMinX;
        double offY = realUvMinY - realMinY;
        double offZ = realUvMinZ - realMinZ;

        // movedBox = geometry box translated into UV space.
        double mvMinX = realMinX + offX;
        double mvMinY = realMinY + offY;
        double mvMinZ = realMinZ + offZ;
        double mvMaxX = realMaxX + offX;
        double mvMaxY = realMaxY + offY;
        double mvMaxZ = realMaxZ + offZ;

        int fMinX = (int) Math.floor(mvMinX);
        int fMaxX = (int) Math.floor(mvMaxX);
        int fMinY = (int) Math.floor(mvMinY);
        int fMaxY = (int) Math.floor(mvMaxY);
        int fMinZ = (int) Math.floor(mvMinZ);
        int fMaxZ = (int) Math.floor(mvMaxZ);

        for (Direction face : Direction.values()) {
            for (int i = fMinX; i <= fMaxX; i++) {
                for (int j = fMinY; j <= fMaxY; j++) {
                    for (int k = fMinZ; k <= fMaxZ; k++) {
                        // Clip movedBox to cell (i,j,k).
                        double pMinX = Math.max(mvMinX, i);
                        double pMaxX = Math.min(mvMaxX, i + 1);
                        double pMinY = Math.max(mvMinY, j);
                        double pMaxY = Math.min(mvMaxY, j + 1);
                        double pMinZ = Math.max(mvMinZ, k);
                        double pMaxZ = Math.min(mvMaxZ, k + 1);

                        if (pMinX >= pMaxX || pMinY >= pMaxY || pMinZ >= pMaxZ) continue;

                        // Each face emits only at its own edge of the box.
                        if (face == Direction.WEST  && pMinX > mvMinX + 1e-4) continue;
                        if (face == Direction.EAST  && pMaxX < mvMaxX - 1e-4) continue;
                        if (face == Direction.DOWN  && pMinY > mvMinY + 1e-4) continue;
                        if (face == Direction.UP    && pMaxY < mvMaxY - 1e-4) continue;
                        if (face == Direction.NORTH && pMinZ > mvMinZ + 1e-4) continue;
                        if (face == Direction.SOUTH && pMaxZ < mvMaxZ - 1e-4) continue;

                        // Cell-local UV box (0..1 within the cell). Mirrors
                        // ModelUtil.mapBoxToUvs.
                        float uvBoxMinX = (float)(pMinX - i);
                        float uvBoxMinY = (float)(pMinY - j);
                        float uvBoxMinZ = (float)(pMinZ - k);
                        float uvBoxMaxX = (float)(pMaxX - i);
                        float uvBoxMaxY = (float)(pMaxY - j);
                        float uvBoxMaxZ = (float)(pMaxZ - k);

                        float uMin, uMax, vMin, vMax;
                        switch (face) {
                            case WEST  -> { uMin = uvBoxMinZ;        uMax = uvBoxMaxZ;        vMin = 1f - uvBoxMaxY; vMax = 1f - uvBoxMinY; }
                            case EAST  -> { uMin = 1f - uvBoxMinZ;   uMax = 1f - uvBoxMaxZ;   vMin = 1f - uvBoxMaxY; vMax = 1f - uvBoxMinY; }
                            case DOWN  -> { uMin = uvBoxMinX;        uMax = uvBoxMaxX;        vMin = 1f - uvBoxMaxZ; vMax = 1f - uvBoxMinZ; }
                            case UP    -> { uMin = uvBoxMinX;        uMax = uvBoxMaxX;        vMin = uvBoxMaxZ;      vMax = uvBoxMinZ;      }
                            case NORTH -> { uMin = 1f - uvBoxMinX;   uMax = 1f - uvBoxMaxX;   vMin = 1f - uvBoxMaxY; vMax = 1f - uvBoxMinY; }
                            case SOUTH -> { uMin = uvBoxMinX;        uMax = uvBoxMaxX;        vMin = 1f - uvBoxMaxY; vMax = 1f - uvBoxMinY; }
                            default    -> { continue; }
                        }

                        // Geometry coordinates = UV coordinates - offset.
                        float gMinX = (float)(pMinX - offX);
                        float gMaxX = (float)(pMaxX - offX);
                        float gMinY = (float)(pMinY - offY);
                        float gMaxY = (float)(pMaxY - offY);
                        float gMinZ = (float)(pMinZ - offZ);
                        float gMaxZ = (float)(pMaxZ - offZ);

                        if (gMaxX <= gMinX || gMaxY <= gMinY || gMaxZ <= gMinZ) continue;

                        // Atlas-mapped UVs.
                        float sUMin = sprite.getU(uMin);
                        float sUMax = sprite.getU(uMax);
                        float sVMin = sprite.getV(vMin);
                        float sVMax = sprite.getV(vMax);

                        emitFace(face,
                            gMinX, gMinY, gMinZ, gMaxX, gMaxY, gMaxZ,
                            sUMin, sVMin, sUMax, sVMax,
                            tR, tG, tB, tA, bb, pose);
                    }
                }
            }
        }
    }

    /** Fills {@code out} (length 20) with 4 vertices × 5 floats (x, y, z, u, v)
     *  in the winding order / UV pairing that the old {@code ModelUtil.createFace}
     *  + {@code MutableQuad.render} pipeline produced. Winding must stay stable for
     *  back-face culling, and the UV pairing must stay stable for the scrolling
     *  flow animation.
     *
     *  <p>Per-face dispatch is hand-traced from {@code ModelUtil.createFace} →
     *  {@code getPointsForFace} → {@code getPoints} → {@code addOrNegate}, then
     *  walked through {@code MutableQuad.render}'s vertex_0..vertex_3 iteration
     *  order under both the inverted and non-inverted branches of
     *  {@code ModelUtil.shouldInvertForRender}.
     *
     *  <p>Package-private for {@link PipeFlowRendererFluidsGeometryTester}. */
    static void computeFaceVertices(Direction face,
                                      float gMinX, float gMinY, float gMinZ,
                                      float gMaxX, float gMaxY, float gMaxZ,
                                      float uMin, float vMin, float uMax, float vMax,
                                      float[] out) {
        switch (face) {
            case UP -> {
                // POSITIVE Y, non-inverted: emit d, c, b, a
                set(out, 0, gMaxX, gMaxY, gMaxZ, uMax, vMin);
                set(out, 1, gMaxX, gMaxY, gMinZ, uMax, vMax);
                set(out, 2, gMinX, gMaxY, gMinZ, uMin, vMax);
                set(out, 3, gMinX, gMaxY, gMaxZ, uMin, vMin);
            }
            case DOWN -> {
                // NEGATIVE Y, inverted: emit a, b, c, d
                set(out, 0, gMinX, gMinY, gMaxZ, uMin, vMin);
                set(out, 1, gMinX, gMinY, gMinZ, uMin, vMax);
                set(out, 2, gMaxX, gMinY, gMinZ, uMax, vMax);
                set(out, 3, gMaxX, gMinY, gMaxZ, uMax, vMin);
            }
            case NORTH -> {
                // NEGATIVE Z, non-inverted (Z axis flips the invert flag): emit d, c, b, a
                set(out, 0, gMaxX, gMaxY, gMinZ, uMax, vMin);
                set(out, 1, gMaxX, gMinY, gMinZ, uMax, vMax);
                set(out, 2, gMinX, gMinY, gMinZ, uMin, vMax);
                set(out, 3, gMinX, gMaxY, gMinZ, uMin, vMin);
            }
            case SOUTH -> {
                // POSITIVE Z, inverted: emit a, b, c, d
                set(out, 0, gMinX, gMaxY, gMaxZ, uMin, vMin);
                set(out, 1, gMinX, gMinY, gMaxZ, uMin, vMax);
                set(out, 2, gMaxX, gMinY, gMaxZ, uMax, vMax);
                set(out, 3, gMaxX, gMaxY, gMaxZ, uMax, vMin);
            }
            case WEST -> {
                // NEGATIVE X, inverted: emit a, b, c, d
                set(out, 0, gMinX, gMaxY, gMinZ, uMin, vMin);
                set(out, 1, gMinX, gMinY, gMinZ, uMin, vMax);
                set(out, 2, gMinX, gMinY, gMaxZ, uMax, vMax);
                set(out, 3, gMinX, gMaxY, gMaxZ, uMax, vMin);
            }
            case EAST -> {
                // POSITIVE X, non-inverted: emit d, c, b, a
                set(out, 0, gMaxX, gMaxY, gMaxZ, uMax, vMin);
                set(out, 1, gMaxX, gMinY, gMaxZ, uMax, vMax);
                set(out, 2, gMaxX, gMinY, gMinZ, uMin, vMax);
                set(out, 3, gMaxX, gMaxY, gMinZ, uMin, vMin);
            }
        }
    }

    private static void set(float[] out, int v, float x, float y, float z, float u, float vUv) {
        int base = v * 5;
        out[base] = x;
        out[base + 1] = y;
        out[base + 2] = z;
        out[base + 3] = u;
        out[base + 4] = vUv;
    }

    private static final ThreadLocal<float[]> TL_FACE_VERTS =
        ThreadLocal.withInitial(() -> new float[20]);

    /** Emits the 4 vertices of one face of a cuboid in the winding order /
     *  UV pairing that the old {@code MutableQuad.render} pipeline produced. */
    private static void emitFace(Direction face,
                                  float gMinX, float gMinY, float gMinZ,
                                  float gMaxX, float gMaxY, float gMaxZ,
                                  float uMin, float vMin, float uMax, float vMax,
                                  int tR, int tG, int tB, int tA,
                                  VertexConsumer bb, PoseStack.Pose pose) {
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();
        float[] verts = TL_FACE_VERTS.get();
        computeFaceVertices(face, gMinX, gMinY, gMinZ, gMaxX, gMaxY, gMaxZ,
                            uMin, vMin, uMax, vMax, verts);
        for (int v = 0; v < 4; v++) {
            int base = v * 5;
            emitVertex(bb, pose,
                verts[base], verts[base + 1], verts[base + 2],
                verts[base + 3], verts[base + 4],
                nx, ny, nz, tR, tG, tB, tA);
        }
    }

    /** Writes one vertex to the buffer with full-bright lighting (the fluid is
     *  drawn as if emissive — matches the prior behaviour of {@code lighti(15, 15)}).
     *  Uses thread-local {@link Vector3f} scratch so no per-vertex allocations. */
    private static void emitVertex(VertexConsumer bb, PoseStack.Pose pose,
                                    float x, float y, float z,
                                    float u, float v,
                                    float nx, float ny, float nz,
                                    int tR, int tG, int tB, int tA) {
        Vector3f pos = TL_POS.get();
        pos.set(x, y, z);
        pose.pose().transformPosition(pos);
        Vector3f norm = TL_NORM.get();
        norm.set(nx, ny, nz);
        pose.normal().transform(norm);
        bb.addVertex(pos.x, pos.y, pos.z)
          .setColor(tR, tG, tB, tA)
          .setUv(u, v)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setUv2(15 << 4, 15 << 4)
          .setNormal(norm.x, norm.y, norm.z);
    }
}
