/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.transport.client.render;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
//? if <26.2 {
import com.mojang.blaze3d.vertex.VertexConsumer;
//?}

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=26.2 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;*/
//?}
//? if >=1.21.10 && <26.2 {
import net.minecraft.client.renderer.ShapeRenderer;
//?} elif <1.21.10 {
/*import net.minecraft.client.renderer.LevelRenderer;*/
//?}
import buildcraft.lib.client.render.BCLibRenderTypes;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
//?} elif >=1.21.10 {
/*import net.minecraft.client.renderer.state.BlockOutlineRenderState;*/
//?}
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?} elif >=1.21.10 {
/*import net.minecraft.client.renderer.state.LevelRenderState;*/
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
//? if >=1.21.10 {
import net.minecraft.util.ARGB;
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if <1.21.10 {
/*import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;*/
//?}
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

//? if >=1.21.10 {
import net.neoforged.neoforge.client.CustomBlockOutlineRenderer;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
//?} else {
/*import net.neoforged.neoforge.client.event.RenderHighlightEvent;*/
//?}

import buildcraft.api.transport.EnumWirePart;
import buildcraft.api.transport.IItemPluggable;

import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.item.ItemWire;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Placement-preview outline for things mounted on a pipe — pluggables (gates, lenses, ...) and wires.
 *
 * <p>A pipe's normal selection outline ({@link BlockPipeHolder#getShape}) highlights the hovered
 * <em>segment</em> — for a hit anywhere on the 8³ core that is the whole core cube. But placement
 * targets something finer: a pluggable goes on a specific <em>face</em>
 * ({@link BlockPipeHolder#resolveTargetFace}), a wire goes on a specific <em>corner</em>
 * ({@link BlockPipeHolder#resolveTargetWirePart}). A uniform cube outline can express neither.
 *
 * <p>While the player holds a placeable item, this replaces the segment outline with an outline of
 * exactly what would be placed — using the same resolver {@code useItemOn} uses, so the highlight
 * and the placement always agree. Pluggables size their own outline via
 * {@link IItemPluggable#getPlacementBoundingBox}, so a facade traces the full block face, a power
 * adaptor traces its 10×10×4 box, and a gate keeps the 6×6 panel — instead of one fixed slab that
 * larger pluggables would visually swallow.
 *
 * <p>Registered on the game event bus by {@code BCTransportClient.initClient}.
 */
public final class PipePlacementHighlight {
    private PipePlacementHighlight() {}

    //? if >=1.21.10 {
    /** {@link ExtractBlockOutlineRenderStateEvent} listener (game event bus, client only). */
    public static void onExtractBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
        if (!(event.getBlockState().getBlock() instanceof BlockPipeHolder)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        BlockEntity be = event.getLevel().getBlockEntity(event.getBlockPos());
        if (!(be instanceof TilePipeHolder tile) || tile.getPipe() == null) {
            return;
        }
        VoxelShape preview = previewShape(tile, event.getHitResult(), player);
        if (preview != null) {
            event.addCustomRenderer(new PreviewRenderer(preview));
        }
    }
    //?} else {
    /*// RenderHighlightEvent.Block listener (game event bus, client only). 1.21.1 has no
    // outline-render-state extraction: draw the preview inline and cancel the vanilla outline.
    public static void onRenderHighlightBlock(RenderHighlightEvent.Block event) {
        BlockHitResult hit = event.getTarget();
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockPos blockPos = hit.getBlockPos();
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof BlockPipeHolder)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        BlockEntity be = level.getBlockEntity(blockPos);
        if (!(be instanceof TilePipeHolder tile) || tile.getPipe() == null) {
            return;
        }
        VoxelShape preview = previewShape(tile, hit, player);
        if (preview == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(BCLibRenderTypes.lines());
        LevelRenderer.renderVoxelShape(poseStack, lines, preview,
                blockPos.getX() - cam.x, blockPos.getY() - cam.y, blockPos.getZ() - cam.z,
                0.0F, 0.0F, 0.0F, 0.4F, false);
        event.setCanceled(true);
    }*/
    //?}

    /** The outline to draw for what the held item would place, or {@code null} to leave the
     *  default outline in place (the held item places nothing on a pipe, or the resolved target
     *  is already occupied so placement would no-op). */
    @Nullable
    private static VoxelShape previewShape(TilePipeHolder tile, BlockHitResult hit, LocalPlayer player) {
        ItemStack pluggableStack = heldStackOf(player, IItemPluggable.class);
        if (pluggableStack != null) {
            Direction face = BlockPipeHolder.resolveTargetFace(tile, hit);
            // An occupied face means placement would no-op; the default outline already
            // highlights the existing pluggable there, so leave it.
            if (tile.getPluggable(face) != null) {
                return null;
            }
            IItemPluggable item = (IItemPluggable) pluggableStack.getItem();
            return Shapes.create(item.getPlacementBoundingBox(pluggableStack, face));
        }
        if (heldStackOf(player, ItemWire.class) != null) {
            EnumWirePart part = BlockPipeHolder.resolveTargetWirePart(hit);
            if (tile.getWireManager().parts.containsKey(part)) {
                return null;
            }
            // Sized to match the existing-wire hover highlight (see BlockPipeHolder#getShape).
            return Shapes.create(part.boundingBox.inflate(BlockPipeHolder.WIRE_HIT_INFLATE));
        }
        return null;
    }

    /** First held stack whose item is an instance of {@code itemType}, checking main hand before
     *  offhand — matches the order vanilla picks for {@code useItemOn}. Returns {@code null} when
     *  neither hand qualifies, so the caller can branch without re-checking the item type. */
    @Nullable
    private static ItemStack heldStackOf(LocalPlayer player, Class<?> itemType) {
        ItemStack main = player.getMainHandItem();
        if (itemType.isInstance(main.getItem())) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (itemType.isInstance(off.getItem())) {
            return off;
        }
        return null;
    }

    //? if >=1.21.10 {
    /** Draws the preview outline in place of the vanilla outline. Captures only the immutable
     *  shape — never the level — as {@link CustomBlockOutlineRenderer} requires. */
    private record PreviewRenderer(VoxelShape shape) implements CustomBlockOutlineRenderer {
        //? if >=26.2 {
        /*@Override
        public boolean render(BlockOutlineRenderState renderState, SubmitNodeCollector submitNodeCollector,
                PoseStack poseStack, LevelRenderState levelRenderState) {
            // 26.2: outline geometry goes through the retained-mode submit system. There is no
            // per-pass callback anymore — submitShapeOutline takes an `afterTerrain` flag
            // (= renderState.isTranslucent()) that defers the draw to the right pass, exactly as
            // LevelRenderer.submitBlockOutline does for the vanilla outline.
            Vec3 cam = levelRenderState.cameraRenderState.pos;
            BlockPos pos = renderState.pos();
            poseStack.pushPose();
            poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            submitNodeCollector.submitShapeOutline(poseStack, shape, BCLibRenderTypes.lines(),
                    ARGB.black(102), 2.5F, renderState.isTranslucent());
            poseStack.popPose();
            return true;
        }*/
        //?} else {
        @Override
        public boolean render(BlockOutlineRenderState renderState, MultiBufferSource.BufferSource buffer,
                PoseStack poseStack, boolean translucentPass, LevelRenderState levelRenderState) {
            // Draw — and suppress the vanilla outline — only in the pass vanilla itself would
            // have drawn the outline. Mirrors LevelRenderer.renderBlockOutline's pass gating.
            if (translucentPass != renderState.isTranslucent()) {
                return false;
            }
            Vec3 cam = levelRenderState.cameraRenderState.pos;
            BlockPos pos = renderState.pos();
            VertexConsumer lines = buffer.getBuffer(BCLibRenderTypes.lines());
            ShapeRenderer.renderShape(poseStack, lines, shape,
                    pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                    //? if >=1.21.11 {
                    ARGB.black(102), 2.5F);
                    //?} else {
                    /*0x66000000);*/
                    //?}
            buffer.endLastBatch();
            return true;
        }
        //?}
    }
    //?}
}
