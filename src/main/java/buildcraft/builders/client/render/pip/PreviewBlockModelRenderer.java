/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render.pip;

//? if >=26.1 {
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import buildcraft.lib.client.render.BCLibRenderTypes;
//?}

//? if >=26.2 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;*/
//?} else {
import net.minecraft.client.renderer.MultiBufferSource;
//?}

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.lib.engine.BlockEngineBase_BC8;

/**
 * Renders a blueprint/architect preview cell from its real <b>block-state model</b> — honouring
 * facing, axis, slab/stairs shape, etc. — instead of the block's flat inventory <b>item model</b>.
 * This is the fix for cells that used to draw as 2D sprites (sugar cane, levers, torches, rails,
 * redstone) or lose their orientation (logs, stairs, furnaces, every directional block): the item
 * model carries none of the placed block's state, so the old {@code new ItemStack(block)} path drew
 * the inventory icon, not the placed geometry.
 *
 * <p>Two strategies across the render cliff (the heavy half — cross-node model resolution and quad
 * extraction — mirrors {@link buildcraft.silicon.client.FacadeDeduplicator}):
 * <ul>
 *   <li><b>&lt;26.1</b> (1.21.1 / 1.21.10 / 1.21.11): {@code BlockRenderDispatcher.renderSingleBlock}
 *       — one vanilla call that resolves the state model, selects render types, and applies block
 *       tint.</li>
 *   <li><b>&gt;=26.1</b> (26.1.2 / 26.2): {@code renderSingleBlock} was removed, so resolve the
 *       {@link BlockStateModel} from the {@code BlockStateModelSet}, {@code collectParts} its quads,
 *       and emit them through one block-atlas cutout sheet ({@code BCLibRenderTypes.cutoutBlockSheet})
 *       with per-quad block tint. 26.2 submits via the deferred collector; 26.1 writes the buffer
 *       source directly. (One sheet for all quads means genuinely-translucent blocks — stained glass,
 *       ice — render as cutout in the preview; acceptable for a build preview.)</li>
 * </ul>
 *
 * <p><b>Engines are the deliberate exception.</b> A BuildCraft engine's block model is empty (only a
 * particle texture — its body is drawn by {@link buildcraft.lib.client.render.tile.RenderEngine_BC8},
 * which the preview never invokes), so its state model has zero quads and the block-model path returns
 * {@code false}. {@link #engineItemRotation} lets the caller keep the engine on its faithful 3D
 * <i>item</i> model but rotate it to the captured facing — matching the per-facing variant rotations
 * in the engine blockstate JSON — so a previewed engine points the way it will in-world.
 */
public final class PreviewBlockModelRenderer {

    private PreviewBlockModelRenderer() {}

    //? if >=26.1 {
    /** Deterministic seed so model variant selection is stable frame-to-frame (no preview flicker). */
    private static final RandomSource RANDOM = RandomSource.create(42L);
    //?}

    /**
     * Renders {@code state}'s block-state model at the current pose. The caller must have translated
     * {@code poseStack} to the cell origin (block models are authored in cell-local {@code [0,1]^3}).
     *
     * @return {@code true} if a model was drawn; {@code false} if the block has no usable block model
     *         (e.g. an engine — empty model), so the caller should fall back to the item path.
     */
    //? if >=26.2 {
    /*public static boolean renderBlock(BlockState state, PoseStack poseStack,
            SubmitNodeCollector sink, int light, int overlay) {*/
    //?} else {
    public static boolean renderBlock(BlockState state, PoseStack poseStack,
            MultiBufferSource.BufferSource sink, int light, int overlay) {
    //?}
        //? if >=26.1 {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RANDOM, parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction dir : Direction.values()) {
                quads.addAll(part.getQuads(dir));
            }
            quads.addAll(part.getQuads(null));
        }
        if (quads.isEmpty()) {
            return false;
        }
        //? if >=26.2 {
        /*sink.submitCustomGeometry(poseStack, BCLibRenderTypes.cutoutBlockSheet(),
                (pose, vc) -> emitBlockQuads(vc, pose, quads, state, light, overlay));*/
        //?} else {
        emitBlockQuads(sink.getBuffer(BCLibRenderTypes.cutoutBlockSheet()), poseStack.last(),
                quads, state, light, overlay);
        //?}
        return true;
        //?} else {
        /*Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, sink, light, overlay);
        return true;*/
        //?}
    }

    //? if >=26.1 {
    /** Emits each model quad with its block tint (white when untinted) into one block-atlas sheet. */
    private static void emitBlockQuads(VertexConsumer vc, PoseStack.Pose pose, List<BakedQuad> quads,
            BlockState state, int light, int overlay) {
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        // 26.x replaced BlockColors.getColor(state,level,pos,tint) with a BlockTintSource per layer;
        // its level-free color(state) is what we want for a worldless preview (grass/leaves/redstone
        // /sugar-cane tint). Untinted quads (tintIndex -1) or layers with no source render white.
        BlockColors colors = Minecraft.getInstance().getBlockColors();
        for (BakedQuad quad : quads) {
            int tintIndex = quad.materialInfo().tintIndex();
            int color = -1;
            if (tintIndex >= 0) {
                BlockTintSource src = colors.getTintSource(state, tintIndex);
                if (src != null) {
                    color = 0xFF000000 | src.color(state);
                }
            }
            instance.setColor(color);
            vc.putBakedQuad(pose, quad, instance);
        }
    }
    //?}

    /**
     * Per-facing {@code {xDeg, yDeg}} rotation to orient an engine's upright item model to its captured
     * facing, or {@code null} if {@code state} is not an engine (the caller renders it normally). The
     * values mirror the engine blockstate JSON variants so the preview matches the in-world engine.
     * The caller applies them, after translating to the cell centre, as
     * {@code mulPose(Axis.YP.rotationDegrees(-y))} then {@code mulPose(Axis.XP.rotationDegrees(x))} —
     * Y outer and negated (vanilla blockstate y is the opposite sense to JOML {@code YP}), so X tilts
     * the trunk to horizontal first and Y then yaws it to the facing. (Applying Y first collapses every
     * horizontal facing to south.)
     */
    public static float[] engineItemRotation(BlockState state) {
        if (!(state.getBlock() instanceof BlockEngineBase_BC8)
                || !state.hasProperty(BuildCraftProperties.BLOCK_FACING_6)) {
            return null;
        }
        switch (state.getValue(BuildCraftProperties.BLOCK_FACING_6)) {
            case DOWN:
                return new float[] { 180f, 0f };
            case NORTH:
                return new float[] { 90f, 180f };
            case SOUTH:
                return new float[] { 90f, 0f };
            case WEST:
                return new float[] { 90f, 90f };
            case EAST:
                return new float[] { 90f, 270f };
            case UP:
            default:
                return new float[] { 0f, 0f };
        }
    }
}
