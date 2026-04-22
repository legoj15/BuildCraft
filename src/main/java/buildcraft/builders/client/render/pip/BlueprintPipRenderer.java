/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render.pip;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.Snapshot;

/**
 * PiP renderer that paints a {@link Blueprint} into an offscreen texture as a real 3D scene of
 * block-item models, then the base class blits the texture into the GUI. This is the 1.21 port
 * of the cohesive rotating-blueprint preview used in 1.12.2, replacing the old 2D sprite-stack
 * which sheared axis-aligned sprites around without actually rotating them.
 * <p>
 * <b>Why item-model rendering and not the block-model path:</b> 1.21's block-model API
 * ({@link net.minecraft.client.renderer.block.ModelBlockRenderer#tesselateBlock}) requires a
 * {@code BlockAndTintGetter} (a live world view) plus a pre-collected {@code BlockStateModel};
 * it's designed for chunk tesselation, not single-block in-GUI previews. The item-model path
 * — {@link TrackingItemStackRenderState} via
 * {@link net.minecraft.client.renderer.item.ItemModelResolver#updateForTopItem} — is what vanilla
 * itself uses from {@code OversizedItemRenderer}. Block items have 3D cube models in their
 * {@link ItemDisplayContext#NONE} transform, so each cell renders as a real cube. The trade-off:
 * blocks without an item form (fire, nether portal, technical blocks) render as an empty stack;
 * we skip those silently and a BuildCraft logger line records the count.
 * <p>
 * <b>Pose convention after the base class:</b>
 * {@link PictureInPictureRenderer#prepare} applies {@code translate(width/2, translateY, 0)}
 * then {@code scale(s, s, -s)}. Two things are wrong with that for our purposes and we fix them
 * with a single {@code scale(1, -1, -1)}:
 * <ul>
 *   <li><b>Y flip (fix upside-down):</b> screen-space +Y is down but block models are authored
 *       with +Y up.</li>
 *   <li><b>Z flip (fix winding):</b> the base's one sign-flip leaves the cumulative determinant
 *       negative — which is what the item render pipeline expects for front-face culling. If we
 *       only flipped Y, the determinant would go positive and every triangle's winding would
 *       invert, making the preview look inside-out. {@code OversizedItemRenderer} uses the same
 *       {@code scale(1, -1, -1)} pattern for the same reason.</li>
 * </ul>
 */
public class BlueprintPipRenderer extends PictureInPictureRenderer<BlueprintPipRenderState> {

    private static final Logger LOGGER = LogManager.getLogger("BCBlueprintPipRenderer");

    /**
     * Tracks which snapshots we've already logged diagnostic info for. Keyed by identity so the
     * per-frame redraw from the tooltip doesn't spam the log, while still logging once for each
     * freshly-loaded Blueprint instance.
     */
    private static final Set<Integer> LOGGED_SNAPSHOTS =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * Degrees of pitch applied before yaw. We want the classic "camera above, looking slightly
     * down" isometric pose — top of the structure tilted <i>away</i> from the viewer so the top
     * face is visible.
     * <p>
     * Sign: {@code Axis.XP.rotationDegrees(θ)} rotates {@code (0,1,0)} to
     * {@code (0, cos θ, sin θ)}. "Away from viewer" in our post-scale frame is world +Z (the
     * {@code scale(1, -1, -1)} combined with the base class's {@code scale(s, s, -s)} maps world
     * +Z to screen +Z = into screen). So we need {@code sin θ > 0}, hence a positive angle. If
     * the Z sign-flip ever gets removed/changed, this sign will need to flip too — the earlier
     * version of this code used {@code -20°} when the Y-only flip still kept world +Z toward
     * viewer.
     */
    private static final float PITCH_DEG = 20.0f;

    /** Milliseconds for one full yaw revolution. Matches the old 2D renderer so visual cadence
     *  is preserved. */
    private static final long YAW_PERIOD_MS = 3600L;

    /** Packed lightmap coords for full-bright rendering, identical to the constant vanilla PiP
     *  renderers use (e.g. {@code GuiBookModelRenderer}). Avoids an import on the LightTexture
     *  class, which has moved between 1.21 subversions. */
    private static final int FULL_BRIGHT = 15728880;

    /**
     * Model-space light directions. Chosen such that:
     * <ul>
     *   <li>Top face (+Y) receives dot contributions from both lights summing to ~1.15 →
     *       shader's {@code lightAccum} clamps at 1.0 → top renders at full baked brightness.</li>
     *   <li>All four horizontal sides receive an identical dot sum of ~0.58 → ~75% lightAccum
     *       on every side regardless of which one is currently facing the viewer. No pulsing.</li>
     *   <li>Bottom face (−Y) receives 0 → ambient only (40% lightAccum).</li>
     * </ul>
     * Combined with the blocks' baked per-face shade (top=1.0, N/S=0.8, E/W=0.6, bottom=0.5)
     * this produces roughly 100% / 60% / 45% / 20% visible brightness — close to 1.12.2's
     * 100% / 80% / 60% / 50% without the ITEMS_3D directional pulsing.
     * <p>
     * These are in <b>model space</b>; each frame they're transformed into camera space by the
     * current pose's normal matrix before being written to the UBO (see the comment inside
     * {@code renderToTexture} for why this preserves the dot products under rotation).
     */
    private static final Vector3f LIGHT0_MODEL_SPACE = new Vector3f(1.0f, 1.0f, 1.0f).normalize();
    private static final Vector3f LIGHT1_MODEL_SPACE = new Vector3f(-1.0f, 1.0f, -1.0f).normalize();

    /**
     * Diffuse-lighting UBO reused across frames. Contents are rewritten every frame in
     * {@code renderToTexture} with yaw-compensated light directions; the same buffer handle
     * is rebound without reallocating.
     * <p>
     * Lazy-initialised on first use: the render-system device isn't guaranteed to exist when
     * this renderer is constructed (registration happens during client setup, before the GPU
     * context is fully wired up). Once created, the buffer is reused for the life of the game.
     */
    private GpuBuffer lightingBuffer;
    private long lightingBufferPaddedSize;

    public BlueprintPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    /**
     * Allocate the lighting UBO the first time we need it. Layout matches vanilla's
     * {@code Lighting} UBO shape exactly — two {@code vec3}s, std140-packed — so the same
     * shader slot reads it the same way; we're only swapping the data.
     */
    private void ensureLightingBufferAllocated() {
        if (lightingBuffer != null) return;
        GpuDevice device = RenderSystem.getDevice();
        lightingBufferPaddedSize = Mth.roundToward(Lighting.UBO_SIZE, device.getUniformOffsetAlignment());
        lightingBuffer = device.createBuffer(
                () -> "BCBlueprintPipLighting", 136, lightingBufferPaddedSize);
    }

    /**
     * Upload a pair of light-direction vectors into the UBO. Called once per render with the
     * current pose's camera-space light directions.
     */
    private void writeLightDirections(Vector3f light0, Vector3f light1) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer bb = Std140Builder.onStack(stack, Lighting.UBO_SIZE)
                    .putVec3(light0)
                    .putVec3(light1)
                    .get();
            RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(lightingBuffer.slice(0, lightingBufferPaddedSize), bb);
        }
    }

    @Override
    public Class<BlueprintPipRenderState> getRenderStateClass() {
        return BlueprintPipRenderState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "buildcraft_blueprint_preview";
    }

    /**
     * Put the pose origin at the <b>center</b> of the offscreen texture. The base-class default
     * puts it at {@code height} (the bottom), which suits book models that sit on a virtual
     * table; for a centered rotating blueprint we need the middle.
     */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0f;
    }

    @Override
    protected void renderToTexture(BlueprintPipRenderState renderState, PoseStack poseStack) {
        Blueprint blueprint = renderState.blueprint();
        BlockPos size = blueprint.size;
        int sizeX = Math.max(1, size.getX());
        int sizeY = Math.max(1, size.getY());
        int sizeZ = Math.max(1, size.getZ());

        // Y flip + Z flip: two effects, one scale.
        //
        // (1) Y: GUI screen-space has +Y pointing DOWN, but block models are authored with +Y
        //     pointing UP. The base class's scale(s, s, -s) left Y in screen convention, which
        //     rendered the preview upside down until we added this flip.
        //
        // (2) Z: the BC render pipeline culls back faces based on screen-space winding order.
        //     Winding order depends on the determinant of the full cumulative scale. Base class
        //     contributes det = -s³ (one sign flip). If we add just a Y flip here, the net
        //     determinant becomes +s³ — handedness matches world, but the rasterizer sees every
        //     triangle with the opposite winding and the object looks inside-out (front faces
        //     culled, back faces drawn). Adding a Z flip brings us back to det = -s³, matching
        //     the handedness the item render pipeline expects. This is exactly the pattern
        //     {@code OversizedItemRenderer} uses ({@code scale(1, -1, -1)}) — inherit from the
        //     one vanilla PiP renderer that also does 3D block-model rendering.
        //
        // The Z flip also reverses "which side of the cube faces the viewer at yaw=0", but since
        // the preview is continuously rotating, that's visually invisible.
        poseStack.scale(1.0f, -1.0f, -1.0f);

        // Apply rotation before translating to origin-at-center so the whole structure rotates
        // around its bounding-box center. Rotations operate in the pre-scale world frame, so
        // their signs are the natural right-hand-rule signs — see PITCH_DEG javadoc.
        float yaw = (System.currentTimeMillis() % YAW_PERIOD_MS) / (float) YAW_PERIOD_MS * 360.0f;
        poseStack.mulPose(Axis.XP.rotationDegrees(PITCH_DEG));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-sizeX / 2.0f, -sizeY / 2.0f, -sizeZ / 2.0f);

        // Bind a custom lighting UBO in place of the usual ITEMS_3D preset. ITEMS_3D bakes a
        // fixed camera-space light direction into its buffer; since modern MC transforms
        // vertex normals into camera space on the CPU (VertexConsumer#putBakedQuad calls
        // pose.transformNormal on each face normal), the dot product in the shader varies
        // with our yaw rotation — producing the "pulse" on side faces.
        //
        // Instead, we define two light directions in MODEL space and each frame transform
        // them through the current pose's normal matrix before writing the UBO. Because the
        // same normal matrix is what transforms the face normals on the CPU side, the
        // shader's dot(light_camera, normal_camera) comes out equal to
        // dot(light_model, normal_model) — effectively evaluating lighting in model space,
        // i.e. locked to the blueprint itself rather than the camera. Rotations no longer
        // change the per-face illumination, so the sides hold steady as the preview spins.
        //
        // We save the old shader-lights slice and restore it after flushing the draws so the
        // rest of the game sees its normal lighting binding. The flush happens inside this
        // method (before the restore) because the base class calls endBatch AFTER our method
        // returns, which would otherwise execute the queued draws with the restored ITEMS_3D
        // binding instead of ours.
        Minecraft mc = Minecraft.getInstance();
        Vector3f light0Camera = poseStack.last().transformNormal(
                LIGHT0_MODEL_SPACE.x(), LIGHT0_MODEL_SPACE.y(), LIGHT0_MODEL_SPACE.z(),
                new Vector3f());
        Vector3f light1Camera = poseStack.last().transformNormal(
                LIGHT1_MODEL_SPACE.x(), LIGHT1_MODEL_SPACE.y(), LIGHT1_MODEL_SPACE.z(),
                new Vector3f());
        ensureLightingBufferAllocated();
        writeLightDirections(light0Camera, light1Camera);
        GpuBufferSlice savedShaderLights = RenderSystem.getShaderLights();
        RenderSystem.setShaderLights(lightingBuffer.slice(0, Lighting.UBO_SIZE));

        FeatureRenderDispatcher featureRenderDispatcher = mc.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeStorage submitNodeStorage = featureRenderDispatcher.getSubmitNodeStorage();

        // Cache the TrackingItemStackRenderState per distinct BlockState so we don't re-resolve
        // the item model for every single cell. Updating for the top item is non-trivial
        // (creates model identities, reads components, etc).
        Map<BlockState, TrackingItemStackRenderState> stateCache = new HashMap<>();

        int submitted = 0;
        int skippedNoItem = 0;
        int skippedAirOrEmpty = 0;
        String sampleClassName = "n/a";

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < sizeZ; z++) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    pos.set(x, y, z);
                    int index = blueprint.data[Snapshot.posToIndex(size, pos)];
                    if (index < 0 || index >= blueprint.palette.size()) {
                        skippedAirOrEmpty++;
                        continue;
                    }
                    ISchematicBlock schBlock = blueprint.palette.get(index);
                    if (schBlock == null || schBlock.isAir()) {
                        skippedAirOrEmpty++;
                        continue;
                    }
                    BlockState state = schBlock.getBlockStateForRender();
                    if (state == null || state.isAir()) {
                        skippedAirOrEmpty++;
                        continue;
                    }
                    if (sampleClassName.equals("n/a")) {
                        sampleClassName = schBlock.getClass().getSimpleName();
                    }

                    TrackingItemStackRenderState itemRenderState = stateCache.get(state);
                    if (itemRenderState == null) {
                        ItemStack stack = new ItemStack(state.getBlock());
                        if (stack.isEmpty()) {
                            // Some blocks (fire, nether portal, ...) have no item form. Record
                            // a null sentinel so we don't retry resolution every frame.
                            stateCache.put(state, MARKER_EMPTY);
                            skippedNoItem++;
                            continue;
                        }
                        itemRenderState = new TrackingItemStackRenderState();
                        mc.getItemModelResolver().updateForTopItem(
                                itemRenderState, stack,
                                // NONE (not GUI) — the GUI context bakes in the 30°/225°/0°
                                // isometric rotation from the item model, which would compound
                                // with our own yaw/pitch and make the preview a blurry mess.
                                ItemDisplayContext.NONE,
                                mc.level, null, 0);
                        stateCache.put(state, itemRenderState);
                    } else if (itemRenderState == MARKER_EMPTY) {
                        skippedNoItem++;
                        continue;
                    }

                    poseStack.pushPose();
                    poseStack.translate(x, y, z);
                    itemRenderState.submit(poseStack, submitNodeStorage, FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, 0);
                    poseStack.popPose();
                    submitted++;
                }
            }
        }

        // Process all feature submissions — this queues the actual GPU draw calls into the PiP
        // buffer source, but does NOT flush them to the GPU yet.
        featureRenderDispatcher.renderAllFeatures();

        // Flush the queued draws to the GPU *before* we restore the lights binding. The base
        // class calls `bufferSource.endBatch()` for us after this method returns, but by then
        // our restore would already have swapped the lighting UBO back to ITEMS_3D and the
        // blueprint would render with the pulsing directional light we're trying to avoid.
        // Forcing the flush inside our lights scope ensures the zero-diffuse UBO is what's
        // bound at the actual draw moment. The base class's subsequent endBatch is a harmless
        // no-op because we already drained the buffer source.
        this.bufferSource.endBatch();

        // Restore whatever lights binding was active before we took over, now that all draws
        // using our custom UBO have been submitted to the GPU.
        RenderSystem.setShaderLights(savedShaderLights);

        if (LOGGED_SNAPSHOTS.add(System.identityHashCode(blueprint))) {
            LOGGER.info("renderToTexture: size={}x{}x{} submitted={} skippedNoItem={} skippedAirOrEmpty={} sampleSchBlock={} distinctStates={}",
                    sizeX, sizeY, sizeZ, submitted, skippedNoItem, skippedAirOrEmpty,
                    sampleClassName, stateCache.size());
        }
    }

    /**
     * Sentinel in the per-state cache marking BlockStates whose {@link ItemStack} form is empty.
     * Using a non-null marker lets us skip the resolution retry on every subsequent cell that
     * shares the same state, without risking NPEs from a real {@link TrackingItemStackRenderState}.
     */
    private static final TrackingItemStackRenderState MARKER_EMPTY = new TrackingItemStackRenderState();
}
