/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render.pip;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EnumSet;
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
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Template;
import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.transport.client.model.ModelPipe;
import buildcraft.transport.client.model.key.PipeModelKey;

/**
 * PiP renderer that paints a {@link Snapshot} into an offscreen texture, then the base class blits
 * the texture into the GUI. This is the 1.21 port of the cohesive rotating preview used in 1.12.2,
 * replacing the old 2D sprite-stack which sheared axis-aligned sprites around without actually
 * rotating them.
 * <p>
 * <b>Two snapshot kinds, two cell-render paths:</b> a {@link Blueprint} draws each palette block as
 * a real 3D block-item model (plus a dedicated cube path for fluid cells). A {@link Template} has
 * no block palette — only a fill bit per cell — so it instead draws a translucent green ghost shell
 * styled like the Architect Table's scan cubes (see {@link #submitTemplateGhostCube}). 1.12.2
 * rendered template cells as solid quartz blocks; the translucent shell is the modern replacement.
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
@SuppressWarnings("deprecation")
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

    /** Texture for the {@link Template} ghost preview — the same "digitizing" sprite the Architect
     *  Table paints onto in-world scan cubes (see {@code BCBuildersEventDist#renderDigitizingCubes}).
     *  Bound directly through the render type rather than via the block atlas. */
    private static final Identifier SCAN_TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/block/scan.png");

    /** Per-vertex alpha for the {@link Template} ghost cubes: 50% opacity (128/255). The scan
     *  texture supplies the green tint; the white vertex colour leaves it unmodified. */
    private static final int TEMPLATE_GHOST_ALPHA = 128;

    /** Tint intensity for the painted-pipe colour overlay in the preview, matching the in-world
     *  pipe renderer's 30% stained-glass tint (overlay_stained.png — see {@code ModelPipe}). */
    private static final int PIPE_PAINT_ALPHA = 76;

    /** Unit-cube face geometry reused for every {@link Template} ghost cell. {@link ModelUtil#createFace}
     *  reads these without mutating them, so sharing single instances across all cells is safe. */
    private static final Vector3f GHOST_CENTER = new Vector3f(0.5f, 0.5f, 0.5f);
    private static final Vector3f GHOST_RADIUS = new Vector3f(0.5f, 0.5f, 0.5f);
    private static final ModelUtil.UvFaceData GHOST_UVS =
            new ModelUtil.UvFaceData(0f, 0f, 1f, 1f);

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
    // int (not long): MC 1.21.10's GpuDevice.createBuffer/GpuBuffer.slice take int sizes; 1.21.11+ take
    // long (an int widens fine). UBO sizes are well within int range, so one field type serves all nodes.
    private int lightingBufferPaddedSize;

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
        Snapshot snapshot = renderState.snapshot();
        BlockPos size = snapshot.size;
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
        int submittedFluid = 0;
        int submittedTemplate = 0;
        int submittedPipe = 0;
        int skippedNoItem = 0;
        int skippedAirOrEmpty = 0;
        String sampleClassName = "n/a";

        // Exactly one of these is non-null — Blueprint and Template are the only two Snapshot
        // subclasses. The Template path draws a translucent ghost shell; the Blueprint path draws
        // 3D item-model cubes plus the dedicated fluid-cube path.
        Blueprint blueprint = snapshot instanceof Blueprint bp ? bp : null;
        Template template = snapshot instanceof Template tp ? tp : null;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < sizeZ; z++) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    pos.set(x, y, z);
                    int dataIndex = Snapshot.posToIndex(size, pos);

                    if (template != null) {
                        // Templates store one fill bit per cell — no block identity. 1.12.2 painted
                        // each set bit as a solid quartz cube; here we draw a translucent green ghost
                        // cube matching the Architect Table's scan cubes. Faces shared with another
                        // filled cell are culled inside the helper, so a solid template reads as a
                        // clean outer shell rather than a haze of stacked translucent quads.
                        if (template.data != null && template.data.get(dataIndex)) {
                            submitTemplateGhostCube(poseStack, template, size, x, y, z);
                            submittedTemplate++;
                        } else {
                            skippedAirOrEmpty++;
                        }
                        continue;
                    }

                    if (blueprint == null) {
                        // Unknown snapshot type (neither Blueprint nor Template); nothing to draw.
                        skippedAirOrEmpty++;
                        continue;
                    }

                    int index = blueprint.data[dataIndex];
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

                    // Pipes all share one block (pipe_holder) with their type/colour/connections
                    // living in the block entity, so the generic new ItemStack(state.getBlock())
                    // path below resolves EVERY pipe to the last-registered pipe item (the Wooden
                    // Diamond FE Pipe) and draws its connectionless vertical item model. Instead,
                    // rebuild the pipe's real model from its captured tile NBT and emit the same
                    // connection-aware block-model quads it shows in-world. Falls through to the
                    // generic path if the capture has no usable pipe data.
                    if (PipePreviewModel.isPipe(state)) {
                        PipeModelKey pipeKey = PipePreviewModel.modelKey(schBlock.getTileNbtForRender());
                        if (pipeKey != null) {
                            poseStack.pushPose();
                            poseStack.translate(x, y, z);
                            PoseStack.Pose pipePose = poseStack.last();
                            // Back-face CULLING render types are essential here: the pipe model
                            // emits front + inverted-"inside" coplanar quad pairs (the dupDarker
                            // pass in PipeBaseModelGenStandard) and relies on culling to drop the
                            // inside quad. Vanilla's entityCutout/entityTranslucent are NO_CULL, so
                            // both quads rendered coplanar and the cutout pair z-fought (flickering
                            // bright/dark as the preview spins). The in-world pipe avoids this by
                            // rendering through the culling block sheet.
                            ModelPipe.renderDirect(pipeKey, pipePose,
                                    this.bufferSource.getBuffer(
                                            BCLibRenderTypes.entityCutoutCull(TextureAtlas.LOCATION_BLOCKS)),
                                    FULL_BRIGHT);
                            ModelPipe.renderMaskOverlay(pipeKey, pipePose,
                                    this.bufferSource.getBuffer(
                                            BCLibRenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS)),
                                    FULL_BRIGHT, PIPE_PAINT_ALPHA);
                            poseStack.popPose();
                            submittedPipe++;
                            continue;
                        }
                    }

                    // Fluid cells need a dedicated textured-cube path: LiquidBlock has no item
                    // form, so the item-model path below would resolve to an empty ItemStack and
                    // silently skip every water/lava/oil cell. Matches 1.12.2 behaviour where
                    // both source and flowing cells appeared in the preview.
                    FluidState fluidState = state.getFluidState();
                    if (!fluidState.isEmpty()) {
                        submitFluidCube(poseStack, blueprint, size, x, y, z, fluidState, FULL_BRIGHT);
                        submittedFluid++;
                        continue;
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
                    // Offset by +0.5 per axis so the block's geometric center lands at the
                    // cell's center. Vanilla's ItemTransform.NO_TRANSFORM does an implicit
                    // translate(-0.5, -0.5, -0.5) inside ItemStackRenderState.LayerRenderState.submit
                    // (see ItemTransform.apply for the NO_TRANSFORM branch), so an item submitted
                    // at pose-origin renders in [-0.5, 0.5]^3 — centered on the origin, NOT with
                    // its BSW corner there. Without this +0.5, a 1×1×1 blueprint rotates around
                    // a corner instead of its center, and larger blueprints are offset by half
                    // a cell; the bug hides for larger structures because the offset is small
                    // compared to the overall extent. Fluid cubes don't need this (submitFluidCube
                    // emits vertices in [0, 1]^3 directly without going through ItemTransform).
                    poseStack.translate(x + 0.5f, y + 0.5f, z + 0.5f);
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

        if (LOGGED_SNAPSHOTS.add(System.identityHashCode(snapshot))) {
            LOGGER.info("renderToTexture: type={} size={}x{}x{} submitted={} submittedFluid={} submittedTemplate={} submittedPipe={} skippedNoItem={} skippedAirOrEmpty={} sampleSchBlock={} distinctStates={}",
                    snapshot.getClass().getSimpleName(), sizeX, sizeY, sizeZ, submitted, submittedFluid,
                    submittedTemplate, submittedPipe, skippedNoItem, skippedAirOrEmpty, sampleClassName, stateCache.size());
        }
    }

    /**
     * Emits a textured cube for a single fluid cell into the shared buffer source. Source fluids
     * get a full 1.0-height cube; flowing cells use {@link FluidState#getOwnHeight()} so short
     * streams read as shorter cubes (matching the classic 1.12.2 preview aesthetic of "looks
     * like an in-game snippet"). Faces shared with same-type fluid neighbours are culled — a
     * pool interior draws only its outer shell, avoiding both fill waste and (for translucent
     * water) the visual mess of double-blended interior faces.
     * <p>
     * Winding is CCW-from-outside in world space. The base class's {@code scale(s, s, -s)} plus
     * our own {@code scale(1, -1, -1)} produce a determinant of {@code -s³}, which is the
     * handedness the item-rendering pipeline (and the active CullStateShard) expects, so
     * standard block-authored winding works without inversion.
     * <p>
     * UVs come from vertex position via {@link TextureAtlasSprite#getU}/{@code getV} so a
     * half-height flowing cube naturally shows the top half of the still sprite — same
     * position-based mapping used by {@link buildcraft.factory.client.render.RenderTank}.
     */
    private void submitFluidCube(
            PoseStack poseStack, Blueprint blueprint, BlockPos size,
            int xCell, int yCell, int zCell,
            FluidState fluidState, int lightmap) {
        Fluid fluid = fluidState.getType();
        // FluidUtilBC expects a FluidStack; the amount doesn't matter here — only the fluid
        // identity is used to look up texture + colour.
        FluidStack stack = new FluidStack(fluid, 1);

        Identifier stillTexture = FluidUtilBC.getFluidTexture(stack);
        if (stillTexture == null) return;

        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(stillTexture);
        if (sprite == null) return;

        int color = FluidUtilBC.getFluidColor(stack);
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >>  8) & 0xFF) / 255.0f;
        float b = ( color        & 0xFF) / 255.0f;
        // Some fluids return ARGB with zero alpha from the no-arg tint getter; treat those as
        // fully opaque rather than invisible. (Vanilla water/lava return proper alpha; BC
        // energy fluids and anything else defaulting to 0xFFFFFFFF already match this.)
        if (a <= 0) a = 1.0f;

        // Source blocks render as full cubes. Flowing blocks use the level-derived height;
        // Fluids.getOwnHeight() returns (8/9)f for max flow down to (1/9)f for thinnest. Floor
        // at 1/8 so near-dry streams still have enough thickness to be visible in a rotating
        // preview — the alternative, clamp-to-zero cells, would flicker at certain viewing
        // angles because of z-fighting with empty cells.
        float h = fluidState.isSource() ? 1.0f : Math.max(0.125f, fluidState.getOwnHeight());

        // Face culling against same-type fluid neighbours. Uses FluidType equality so a WATER
        // source and its FLOWING_WATER neighbour count as adjacent for culling purposes —
        // otherwise you'd see ghost interior faces at every source/flowing boundary of a pour.
        boolean cullTop    = neighborIsSameFluid(blueprint, size, xCell,   yCell + 1, zCell,   fluid);
        boolean cullBottom = neighborIsSameFluid(blueprint, size, xCell,   yCell - 1, zCell,   fluid);
        boolean cullNorth  = neighborIsSameFluid(blueprint, size, xCell,   yCell,     zCell - 1, fluid);
        boolean cullSouth  = neighborIsSameFluid(blueprint, size, xCell,   yCell,     zCell + 1, fluid);
        boolean cullWest   = neighborIsSameFluid(blueprint, size, xCell - 1, yCell,   zCell,   fluid);
        boolean cullEast   = neighborIsSameFluid(blueprint, size, xCell + 1, yCell,   zCell,   fluid);

        // Water reuses the entity-translucent sheet so its semi-transparent pixels blend; every
        // other fluid (including BC energy fluids that tint the water sprite) uses entity-cutout
        // so the alpha is clamped to binary and doesn't bleed the background through.
        VertexConsumer vc = this.bufferSource.getBuffer(
                FluidUtilBC.shouldRenderTranslucent(fluid)
                        ? BCLibRenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
                        : BCLibRenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));

        poseStack.pushPose();
        poseStack.translate(xCell, yCell, zCell);
        PoseStack.Pose pose = poseStack.last();
        int overlay = OverlayTexture.NO_OVERLAY;

        // Top (+Y): CCW viewed from above.
        if (!cullTop) {
            putFluidVertex(vc, pose, 0, h, 0, sprite.getU(0), sprite.getV(0), r, g, b, a,  0,  1,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 0, h, 1, sprite.getU(0), sprite.getV(1), r, g, b, a,  0,  1,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 1, sprite.getU(1), sprite.getV(1), r, g, b, a,  0,  1,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 0, sprite.getU(1), sprite.getV(0), r, g, b, a,  0,  1,  0, lightmap, overlay);
        }
        // Bottom (-Y): CCW viewed from below.
        if (!cullBottom) {
            putFluidVertex(vc, pose, 0, 0, 0, sprite.getU(0), sprite.getV(0), r, g, b, a,  0, -1,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, 0, 0, sprite.getU(1), sprite.getV(0), r, g, b, a,  0, -1,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, 0, 1, sprite.getU(1), sprite.getV(1), r, g, b, a,  0, -1,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 0, 0, 1, sprite.getU(0), sprite.getV(1), r, g, b, a,  0, -1,  0, lightmap, overlay);
        }
        // North (-Z): CCW viewed from -Z. Side-face V is derived from (1-y) so the water
        // surface (low V on the sprite) sits at the top of the column — matches how still-
        // water textures read in-world.
        if (!cullNorth) {
            putFluidVertex(vc, pose, 0, 0, 0, sprite.getU(0), sprite.getV(1),     r, g, b, a,  0,  0, -1, lightmap, overlay);
            putFluidVertex(vc, pose, 0, h, 0, sprite.getU(0), sprite.getV(1 - h), r, g, b, a,  0,  0, -1, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 0, sprite.getU(1), sprite.getV(1 - h), r, g, b, a,  0,  0, -1, lightmap, overlay);
            putFluidVertex(vc, pose, 1, 0, 0, sprite.getU(1), sprite.getV(1),     r, g, b, a,  0,  0, -1, lightmap, overlay);
        }
        // South (+Z): CCW viewed from +Z.
        if (!cullSouth) {
            putFluidVertex(vc, pose, 1, 0, 1, sprite.getU(0), sprite.getV(1),     r, g, b, a,  0,  0,  1, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 1, sprite.getU(0), sprite.getV(1 - h), r, g, b, a,  0,  0,  1, lightmap, overlay);
            putFluidVertex(vc, pose, 0, h, 1, sprite.getU(1), sprite.getV(1 - h), r, g, b, a,  0,  0,  1, lightmap, overlay);
            putFluidVertex(vc, pose, 0, 0, 1, sprite.getU(1), sprite.getV(1),     r, g, b, a,  0,  0,  1, lightmap, overlay);
        }
        // West (-X): CCW viewed from -X.
        if (!cullWest) {
            putFluidVertex(vc, pose, 0, 0, 1, sprite.getU(0), sprite.getV(1),     r, g, b, a, -1,  0,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 0, h, 1, sprite.getU(0), sprite.getV(1 - h), r, g, b, a, -1,  0,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 0, h, 0, sprite.getU(1), sprite.getV(1 - h), r, g, b, a, -1,  0,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 0, 0, 0, sprite.getU(1), sprite.getV(1),     r, g, b, a, -1,  0,  0, lightmap, overlay);
        }
        // East (+X): CCW viewed from +X.
        if (!cullEast) {
            putFluidVertex(vc, pose, 1, 0, 0, sprite.getU(0), sprite.getV(1),     r, g, b, a,  1,  0,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 0, sprite.getU(0), sprite.getV(1 - h), r, g, b, a,  1,  0,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 1, sprite.getU(1), sprite.getV(1 - h), r, g, b, a,  1,  0,  0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, 0, 1, sprite.getU(1), sprite.getV(1),     r, g, b, a,  1,  0,  0, lightmap, overlay);
        }

        poseStack.popPose();
    }

    /**
     * True when the cell at the given palette coordinate holds a fluid schematic of the same
     * {@link net.neoforged.neoforge.fluids.FluidType} as {@code fluid}. Out-of-bounds cells
     * return false so exterior faces of the bounding box always render.
     */
    private static boolean neighborIsSameFluid(
            Blueprint blueprint, BlockPos size,
            int nx, int ny, int nz, Fluid fluid) {
        if (nx < 0 || ny < 0 || nz < 0
                || nx >= size.getX() || ny >= size.getY() || nz >= size.getZ()) {
            return false;
        }
        int idx = blueprint.data[Snapshot.posToIndex(size, new BlockPos(nx, ny, nz))];
        if (idx < 0 || idx >= blueprint.palette.size()) return false;
        ISchematicBlock schBlock = blueprint.palette.get(idx);
        if (schBlock == null) return false;
        BlockState nState = schBlock.getBlockStateForRender();
        if (nState == null) return false;
        FluidState nFluid = nState.getFluidState();
        // FluidType equality treats FLOWING_X and X as the same fluid for adjacency purposes,
        // so a source next to its own flow doesn't render an interior wall.
        return !nFluid.isEmpty() && nFluid.getType().getFluidType() == fluid.getFluidType();
    }

    private static void putFluidVertex(
            VertexConsumer vc, PoseStack.Pose pose,
            float x, float y, float z, float u, float v,
            float r, float g, float b, float a,
            float nx, float ny, float nz,
            int light, int overlay) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * Emits a translucent green ghost cube for one filled {@link Template} cell into the shared
     * buffer source, styled like the Architect Table's scan cubes ({@code scan.png}, drawn through
     * {@link RenderTypes#entityTranslucent}). A face is drawn only when the neighbouring cell in
     * that direction is <i>not</i> also filled, so a solid template collapses to its outer shell —
     * no interior overdraw, no muddy stacks of blended quads.
     * <p>
     * Geometry comes from {@link ModelUtil#createFace}, authored CCW-from-outside with an outward
     * face normal. The renderer's {@code scale(1, -1, -1)} yields the same {@code -s³} determinant
     * the fluid path relies on, so this standard block winding renders right-side-out without
     * inversion, and the outward normals pick up the same model-space diffuse lighting as the rest
     * of the preview. Cube vertices live in cell-local {@code [0, 1]^3} after the per-cell translate.
     */
    private void submitTemplateGhostCube(PoseStack poseStack, Template template, BlockPos size,
                                         int xCell, int yCell, int zCell) {
        // Cull faces shared with an adjacent filled cell up front — see TemplateGhostGeometry.
        EnumSet<Direction> faces = TemplateGhostGeometry.visibleFaces(template, size, xCell, yCell, zCell);
        if (faces.isEmpty()) {
            // Fully-enclosed interior cell: nothing exposed, so skip the buffer churn entirely.
            return;
        }
        VertexConsumer vc = this.bufferSource.getBuffer(BCLibRenderTypes.entityTranslucent(SCAN_TEXTURE));

        poseStack.pushPose();
        poseStack.translate(xCell, yCell, zCell);
        PoseStack.Pose pose = poseStack.last();
        for (Direction face : faces) {
            ModelUtil.createFace(face, GHOST_CENTER, GHOST_RADIUS, GHOST_UVS)
                    .lighti(15, 15)
                    .colouri(255, 255, 255, TEMPLATE_GHOST_ALPHA)
                    .render(pose, vc);
        }
        poseStack.popPose();
    }

    /**
     * Sentinel in the per-state cache marking BlockStates whose {@link ItemStack} form is empty.
     * Using a non-null marker lets us skip the resolution retry on every subsequent cell that
     * shares the same state, without risking NPEs from a real {@link TrackingItemStackRenderState}.
     */
    private static final TrackingItemStackRenderState MARKER_EMPTY = new TrackingItemStackRenderState();
}
