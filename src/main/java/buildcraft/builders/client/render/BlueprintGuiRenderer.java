/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render;

// 1.21.1-ONLY: the modern nodes (>=1.21.10) render the rotating blueprint/template preview through the
// 1.21.5+ PiP offscreen pipeline (BlueprintPipRenderer). That pipeline does not exist on 1.21.1, so here
// we draw the structure straight into the GUI buffer source with a real 3D PoseStack (on 1.21.1
// GuiGraphics.pose() IS a com.mojang.blaze3d.vertex.PoseStack, not the 2D Matrix3x2fStack of 26.1),
// following vanilla InventoryScreen.renderEntityInInventory. The whole file is gated out on >=1.21.10
// (BlueprintPipRenderer is the path there). The per-cell logic mirrors BlueprintPipRenderer.renderToTexture
// — blocks as 3D item-model cubes, fluids as textured cubes, templates as translucent ghost cubes, pipes
// rebuilt connection-aware from captured NBT — but uses the classic ItemRenderer.renderStatic item path
// (1.21.1 has no TrackingItemStackRenderState/SubmitNodeStorage) and vanilla inventory lighting instead of
// the PiP renderer's custom light-direction UBO (so side faces shift slightly as it spins; acceptable).
//? if <1.21.10 {
/*import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.builders.client.render.pip.PreviewBlockModelRenderer;
import buildcraft.builders.client.render.pip.PipePreviewModel;
import buildcraft.builders.client.render.pip.PipePreviewPluggables;
import buildcraft.builders.client.render.pip.TemplateGhostGeometry;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Template;
import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.gui.BCGraphics;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.transport.client.model.ModelPipe;
import buildcraft.transport.client.model.key.PipeModelKey;

@SuppressWarnings("deprecation")
public final class BlueprintGuiRenderer {

    private BlueprintGuiRenderer() {}

    // Camera-above isometric pitch, then a time-based yaw spin. Same cadence/sign as the PiP renderer.
    private static final float PITCH_DEG = 20.0f;
    private static final long YAW_PERIOD_MS = 3600L;
    private static final int FULL_BRIGHT = 15728880;
    // Same scan sprite + alphas the PiP template/pipe paths use.
    private static final Identifier SCAN_TEXTURE = Identifier.parse("buildcraftunofficial:textures/block/scan.png");
    private static final int TEMPLATE_GHOST_ALPHA = 128;
    private static final int PIPE_PAINT_ALPHA = 76;
    private static final Vector3f GHOST_CENTER = new Vector3f(0.5f, 0.5f, 0.5f);
    private static final Vector3f GHOST_RADIUS = new Vector3f(0.5f, 0.5f, 0.5f);
    private static final ModelUtil.UvFaceData GHOST_UVS = new ModelUtil.UvFaceData(0f, 0f, 1f, 1f);
    // Safety margin on the 3D diagonal so the structure stays inside the viewport at any yaw/pitch.
    private static final float FIT_ENVELOPE = 1.05f;
    // GUI depth the structure centre sits at. The projected half-depth is at most ~viewportSpan/2, and
    // these preview panels are small, so 100 keeps every cell well inside the GUI's item-render depth
    // band (vanilla items render at z=150, entities at z=50). May want tuning if a huge preview clips.
    private static final float CENTER_DEPTH = 100.0f;

    // Entry point from BlueprintRenderer.renderSnapshot on 1.21.1. Draws the snapshot as a rotating 3D
    // model clipped to the given GUI-pixel viewport. nowMs drives the yaw so the spin matches wall time.
    public static void render(BCGraphics graphics, Snapshot snapshot,
                              int viewportX, int viewportY, int viewportWidth, int viewportHeight, long nowMs) {
        if (snapshot == null) {
            return;
        }
        int sizeX = Math.max(1, snapshot.size.getX());
        int sizeY = Math.max(1, snapshot.size.getY());
        int sizeZ = Math.max(1, snapshot.size.getZ());
        float diagonal = (float) Math.sqrt((double) sizeX * sizeX + (double) sizeY * sizeY + (double) sizeZ * sizeZ);
        float viewportSpan = Math.min(viewportWidth, viewportHeight);
        // pixels span the diagonal*envelope; the GUI PoseStack is already in GUI-pixel units.
        float scale = viewportSpan / (diagonal * FIT_ENVELOPE);

        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = graphics.raw.pose();
        MultiBufferSource.BufferSource buffers = graphics.raw.bufferSource();

        // Clip the spinning model to its panel.
        graphics.raw.enableScissor(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight);
        // Tooltip previews draw at RenderTooltipEvent.Pre, AFTER the GUI + slot items have written depth
        // into this screen region; a 3D structure then depth-tests against that stale depth and is culled
        // (the Architect renders in the background phase into a fresh region, which is why IT already
        // works). Clear the depth buffer inside our scissor so the structure has a clean slate — the same
        // thing the modern PiP path gets from its own offscreen depth buffer. glClear honours the GL
        // scissor box enableScissor just set, so only the preview panel's depth is touched.
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        pose.pushPose();
        // Origin at the viewport centre, pushed into the GUI depth band. scale(s, s, -s): s GUI pixels
        // per world unit; the negative Z matches the convention vanilla 3D-in-GUI rendering expects.
        pose.translate(viewportX + viewportWidth / 2.0f, viewportY + viewportHeight / 2.0f, CENTER_DEPTH);
        pose.scale(scale, scale, -scale);
        // Y/Z flip, exactly as BlueprintPipRenderer: block models are authored +Y up (GUI +Y is down),
        // and the extra Z flip keeps the cumulative determinant negative so item-model front faces aren't
        // culled inside-out. Combined with the line above this is the scale(1,-1,-1) the PiP path applies.
        pose.scale(1.0f, -1.0f, -1.0f);
        float yaw = (nowMs % YAW_PERIOD_MS) / (float) YAW_PERIOD_MS * 360.0f;
        pose.mulPose(Axis.XP.rotationDegrees(PITCH_DEG));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        // Rotate about the structure's bounding-box centre.
        pose.translate(-sizeX / 2.0f, -sizeY / 2.0f, -sizeZ / 2.0f);

        // 3D-in-GUI needs depth so nearer cells occlude farther ones, plus directional lighting so cube
        // faces read as 3D. Mirrors InventoryScreen.renderEntityInInventory's setup/teardown.
        RenderSystem.enableDepthTest();
        Lighting.setupForEntityInInventory();

        ItemRenderer itemRenderer = mc.getItemRenderer();
        Blueprint blueprint = snapshot instanceof Blueprint ? (Blueprint) snapshot : null;
        Template template = snapshot instanceof Template ? (Template) snapshot : null;
        BlockPos size = snapshot.size;
        // Cache the item stack per distinct BlockState so we don't rebuild it for every cell.
        Map<BlockState, ItemStack> stackCache = new HashMap<>();

        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int z = 0; z < sizeZ; z++) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    cur.set(x, y, z);
                    int dataIndex = Snapshot.posToIndex(size, cur);

                    if (template != null) {
                        // BitSet fill bit -> translucent green ghost cube; interior faces culled.
                        if (template.data != null && template.data.get(dataIndex)) {
                            submitTemplateGhostCube(buffers, pose, template, size, x, y, z);
                        }
                        continue;
                    }
                    if (blueprint == null) {
                        continue;
                    }
                    int index = blueprint.data[dataIndex];
                    if (index < 0 || index >= blueprint.palette.size()) {
                        continue;
                    }
                    ISchematicBlock schBlock = blueprint.palette.get(index);
                    if (schBlock == null || schBlock.isAir()) {
                        continue;
                    }
                    BlockState state = schBlock.getBlockStateForRender();
                    if (state == null || state.isAir()) {
                        continue;
                    }

                    // Pipes share one block (pipe_holder) with everything in the BE, so the generic item
                    // path would resolve every pipe to the last-registered pipe item. Rebuild the real
                    // connection-aware model from captured NBT instead (same as the PiP path).
                    if (PipePreviewModel.isPipe(state)) {
                        PipeModelKey pipeKey = PipePreviewModel.modelKey(schBlock.getTileNbtForRender());
                        if (pipeKey != null) {
                            pose.pushPose();
                            pose.translate(x, y, z);
                            PoseStack.Pose pipePose = pose.last();
                            // Cull render types: the pipe model emits coplanar front/inside quad pairs and
                            // relies on back-face culling to drop the inside quad (NO_CULL would z-fight).
                            ModelPipe.renderDirect(pipeKey, pipePose,
                                    buffers.getBuffer(BCLibRenderTypes.entityCutoutCull(TextureAtlas.LOCATION_BLOCKS)),
                                    FULL_BRIGHT);
                            ModelPipe.renderMaskOverlay(pipeKey, pipePose,
                                    buffers.getBuffer(BCLibRenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS)),
                                    FULL_BRIGHT, PIPE_PAINT_ALPHA);
                            // Pluggables captured on this pipe — reconstructed offline, rendered like the body.
                            PipePreviewPluggables.render(schBlock.getTileNbtForRender(), pose, buffers, FULL_BRIGHT);
                            pose.popPose();
                            continue;
                        }
                    }

                    // Fluids have no item form, so they need the dedicated textured-cube path.
                    FluidState fluidState = state.getFluidState();
                    if (!fluidState.isEmpty()) {
                        submitFluidCube(buffers, pose, blueprint, size, x, y, z, fluidState);
                        continue;
                    }

                    // Render the real block-state model first so facing/axis/shape are honoured —
                    // fixes flat-sprite cells (sugar cane, levers, torches) and lost orientation
                    // (logs, stairs, furnaces). Engines are excluded (empty block model → BER-drawn)
                    // and fall to the item path below, rotated to the captured facing. On 1.21.1 the
                    // helper draws via BlockRenderDispatcher.renderSingleBlock.
                    float[] engineRot = PreviewBlockModelRenderer.engineItemRotation(state);
                    if (engineRot == null) {
                        pose.pushPose();
                        pose.translate(x, y, z);
                        boolean drewBlockModel = PreviewBlockModelRenderer.renderBlock(
                                state, pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                        pose.popPose();
                        if (drewBlockModel) {
                            continue;
                        }
                    }

                    ItemStack stack = stackCache.get(state);
                    if (stack == null) {
                        stack = new ItemStack(state.getBlock());
                        stackCache.put(state, stack);
                    }
                    if (stack.isEmpty()) {
                        // No item form (fire, portal, technical blocks); skip silently like the PiP path.
                        continue;
                    }
                    pose.pushPose();
                    // +0.5 per axis: ItemDisplayContext.NONE renders centred on the origin (an implicit
                    // -0.5 translate inside the item transform), so a cell submitted at pose-origin would
                    // occupy [-0.5,0.5]; shift it to the cell centre so the structure rotates about itself.
                    pose.translate(x + 0.5f, y + 0.5f, z + 0.5f);
                    // Engines: rotate the upright item model to the captured facing. X tilts the trunk
                    // horizontal, then Y (negated, OUTER — see BlueprintPipRenderer) yaws it to the
                    // facing; X must apply first or every horizontal facing collapses to south.
                    if (engineRot != null) {
                        pose.mulPose(Axis.YP.rotationDegrees(-engineRot[1]));
                        pose.mulPose(Axis.XP.rotationDegrees(engineRot[0]));
                    }
                    itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
                    pose.popPose();
                }
            }
        }

        // Flush the queued draws while depth + inventory lighting are still bound, then restore the GUI's
        // normal 3D-item lighting (matches renderEntityInInventory's flush-then-setupFor3DItems order).
        buffers.endBatch();
        pose.popPose();
        Lighting.setupFor3DItems();
        graphics.raw.disableScissor();
    }

    // --- fluid cube (ported from BlueprintPipRenderer.submitFluidCube; bufferSource passed in) ---

    // Emits a textured cube for one fluid cell. Source cells are full height; flowing cells use their
    // own height. Faces shared with a same-type fluid neighbour are culled so a pool draws only its shell.
    private static void submitFluidCube(MultiBufferSource.BufferSource buffers, PoseStack poseStack,
                                        Blueprint blueprint, BlockPos size,
                                        int xCell, int yCell, int zCell, FluidState fluidState) {
        Fluid fluid = fluidState.getType();
        FluidStack stack = new FluidStack(fluid, 1);

        Identifier stillTexture = FluidUtilBC.getFluidTexture(stack);
        if (stillTexture == null) {
            return;
        }
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(stillTexture);
        if (sprite == null) {
            return;
        }

        int color = FluidUtilBC.getFluidColor(stack);
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        if (a <= 0) {
            a = 1.0f;
        }
        float h = fluidState.isSource() ? 1.0f : Math.max(0.125f, fluidState.getOwnHeight());

        boolean cullTop = neighborIsSameFluid(blueprint, size, xCell, yCell + 1, zCell, fluid);
        boolean cullBottom = neighborIsSameFluid(blueprint, size, xCell, yCell - 1, zCell, fluid);
        boolean cullNorth = neighborIsSameFluid(blueprint, size, xCell, yCell, zCell - 1, fluid);
        boolean cullSouth = neighborIsSameFluid(blueprint, size, xCell, yCell, zCell + 1, fluid);
        boolean cullWest = neighborIsSameFluid(blueprint, size, xCell - 1, yCell, zCell, fluid);
        boolean cullEast = neighborIsSameFluid(blueprint, size, xCell + 1, yCell, zCell, fluid);

        VertexConsumer vc = buffers.getBuffer(
                FluidUtilBC.shouldRenderTranslucent(fluid)
                        ? BCLibRenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
                        : BCLibRenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));

        poseStack.pushPose();
        poseStack.translate(xCell, yCell, zCell);
        PoseStack.Pose pose = poseStack.last();
        int overlay = OverlayTexture.NO_OVERLAY;
        int lightmap = FULL_BRIGHT;

        // Top (+Y)
        if (!cullTop) {
            putFluidVertex(vc, pose, 0, h, 0, sprite.getU(0), sprite.getV(0), r, g, b, a, 0, 1, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 0, h, 1, sprite.getU(0), sprite.getV(1), r, g, b, a, 0, 1, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 1, sprite.getU(1), sprite.getV(1), r, g, b, a, 0, 1, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 0, sprite.getU(1), sprite.getV(0), r, g, b, a, 0, 1, 0, lightmap, overlay);
        }
        // Bottom (-Y)
        if (!cullBottom) {
            putFluidVertex(vc, pose, 0, 0, 0, sprite.getU(0), sprite.getV(0), r, g, b, a, 0, -1, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, 0, 0, sprite.getU(1), sprite.getV(0), r, g, b, a, 0, -1, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, 0, 1, sprite.getU(1), sprite.getV(1), r, g, b, a, 0, -1, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 0, 0, 1, sprite.getU(0), sprite.getV(1), r, g, b, a, 0, -1, 0, lightmap, overlay);
        }
        // North (-Z): side V derived from (1-y) so the surface sits at the top of the column.
        if (!cullNorth) {
            putFluidVertex(vc, pose, 0, 0, 0, sprite.getU(0), sprite.getV(1), r, g, b, a, 0, 0, -1, lightmap, overlay);
            putFluidVertex(vc, pose, 0, h, 0, sprite.getU(0), sprite.getV(1 - h), r, g, b, a, 0, 0, -1, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 0, sprite.getU(1), sprite.getV(1 - h), r, g, b, a, 0, 0, -1, lightmap, overlay);
            putFluidVertex(vc, pose, 1, 0, 0, sprite.getU(1), sprite.getV(1), r, g, b, a, 0, 0, -1, lightmap, overlay);
        }
        // South (+Z)
        if (!cullSouth) {
            putFluidVertex(vc, pose, 1, 0, 1, sprite.getU(0), sprite.getV(1), r, g, b, a, 0, 0, 1, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 1, sprite.getU(0), sprite.getV(1 - h), r, g, b, a, 0, 0, 1, lightmap, overlay);
            putFluidVertex(vc, pose, 0, h, 1, sprite.getU(1), sprite.getV(1 - h), r, g, b, a, 0, 0, 1, lightmap, overlay);
            putFluidVertex(vc, pose, 0, 0, 1, sprite.getU(1), sprite.getV(1), r, g, b, a, 0, 0, 1, lightmap, overlay);
        }
        // West (-X)
        if (!cullWest) {
            putFluidVertex(vc, pose, 0, 0, 1, sprite.getU(0), sprite.getV(1), r, g, b, a, -1, 0, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 0, h, 1, sprite.getU(0), sprite.getV(1 - h), r, g, b, a, -1, 0, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 0, h, 0, sprite.getU(1), sprite.getV(1 - h), r, g, b, a, -1, 0, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 0, 0, 0, sprite.getU(1), sprite.getV(1), r, g, b, a, -1, 0, 0, lightmap, overlay);
        }
        // East (+X)
        if (!cullEast) {
            putFluidVertex(vc, pose, 1, 0, 0, sprite.getU(0), sprite.getV(1), r, g, b, a, 1, 0, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 0, sprite.getU(0), sprite.getV(1 - h), r, g, b, a, 1, 0, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, h, 1, sprite.getU(1), sprite.getV(1 - h), r, g, b, a, 1, 0, 0, lightmap, overlay);
            putFluidVertex(vc, pose, 1, 0, 1, sprite.getU(1), sprite.getV(1), r, g, b, a, 1, 0, 0, lightmap, overlay);
        }

        poseStack.popPose();
    }

    // True when the neighbour cell holds a fluid of the same FluidType (so FLOWING_X counts as X).
    // Out-of-bounds returns false so the bounding box's outer faces always draw.
    private static boolean neighborIsSameFluid(Blueprint blueprint, BlockPos size, int nx, int ny, int nz, Fluid fluid) {
        if (nx < 0 || ny < 0 || nz < 0 || nx >= size.getX() || ny >= size.getY() || nz >= size.getZ()) {
            return false;
        }
        int idx = blueprint.data[Snapshot.posToIndex(size, new BlockPos(nx, ny, nz))];
        if (idx < 0 || idx >= blueprint.palette.size()) {
            return false;
        }
        ISchematicBlock schBlock = blueprint.palette.get(idx);
        if (schBlock == null) {
            return false;
        }
        BlockState nState = schBlock.getBlockStateForRender();
        if (nState == null) {
            return false;
        }
        FluidState nFluid = nState.getFluidState();
        return !nFluid.isEmpty() && nFluid.getType().getFluidType() == fluid.getFluidType();
    }

    private static void putFluidVertex(VertexConsumer vc, PoseStack.Pose pose,
                                       float x, float y, float z, float u, float v,
                                       float r, float g, float b, float a,
                                       float nx, float ny, float nz, int light, int overlay) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    // --- template ghost cube (ported from BlueprintPipRenderer.submitTemplateGhostCube) ---

    // Emits a translucent green ghost cube for one filled Template cell; only faces exposed to a
    // non-filled neighbour are drawn, so a solid template collapses to its outer shell.
    private static void submitTemplateGhostCube(MultiBufferSource.BufferSource buffers, PoseStack poseStack,
                                                Template template, BlockPos size, int xCell, int yCell, int zCell) {
        EnumSet<Direction> faces = TemplateGhostGeometry.visibleFaces(template, size, xCell, yCell, zCell);
        if (faces.isEmpty()) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(BCLibRenderTypes.entityTranslucent(SCAN_TEXTURE));
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
}*/
//?}
