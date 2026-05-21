/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.transport.client.render;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.neoforged.neoforge.client.CustomBlockOutlineRenderer;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;

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
 * and the placement always agree.
 *
 * <p>Registered on the game event bus by {@code BCTransportClient.initClient}.
 */
public final class PipePlacementHighlight {
    private PipePlacementHighlight() {}

    /** Per-face pluggable preview slabs, indexed by {@link Direction#get3DDataValue()}: a 6×6
     *  panel two pixels deep, mounted just outside the 4–12 pipe core — matching where a gate sits. */
    private static final VoxelShape[] PLUGGABLE_SLABS = new VoxelShape[6];
    static {
        double a = 5, b = 11;                                 // face-plane extent
        double near = 2, nearEnd = 4, far = 12, farEnd = 14;  // depth, just outside the core
        PLUGGABLE_SLABS[Direction.DOWN.get3DDataValue()]  = Block.box(a, near, a, b, nearEnd, b);
        PLUGGABLE_SLABS[Direction.UP.get3DDataValue()]    = Block.box(a, far, a, b, farEnd, b);
        PLUGGABLE_SLABS[Direction.NORTH.get3DDataValue()] = Block.box(a, a, near, b, b, nearEnd);
        PLUGGABLE_SLABS[Direction.SOUTH.get3DDataValue()] = Block.box(a, a, far, b, b, farEnd);
        PLUGGABLE_SLABS[Direction.WEST.get3DDataValue()]  = Block.box(near, a, a, nearEnd, b, b);
        PLUGGABLE_SLABS[Direction.EAST.get3DDataValue()]  = Block.box(far, a, a, farEnd, b, b);
    }

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

    /** The outline to draw for what the held item would place, or {@code null} to leave the
     *  default outline in place (the held item places nothing on a pipe, or the resolved target
     *  is already occupied so placement would no-op). */
    @Nullable
    private static VoxelShape previewShape(TilePipeHolder tile, BlockHitResult hit, LocalPlayer player) {
        if (isHolding(player, IItemPluggable.class)) {
            Direction face = BlockPipeHolder.resolveTargetFace(tile, hit);
            // An occupied face means placement would no-op; the default outline already
            // highlights the existing pluggable there, so leave it.
            return tile.getPluggable(face) == null ? PLUGGABLE_SLABS[face.get3DDataValue()] : null;
        }
        if (isHolding(player, ItemWire.class)) {
            EnumWirePart part = BlockPipeHolder.resolveTargetWirePart(hit);
            if (tile.getWireManager().parts.containsKey(part)) {
                return null;
            }
            // Sized to match the existing-wire hover highlight (see BlockPipeHolder#getShape).
            return Shapes.create(part.boundingBox.inflate(BlockPipeHolder.WIRE_HIT_INFLATE));
        }
        return null;
    }

    private static boolean isHolding(LocalPlayer player, Class<?> itemType) {
        return itemType.isInstance(player.getMainHandItem().getItem())
            || itemType.isInstance(player.getOffhandItem().getItem());
    }

    /** Draws the preview outline in place of the vanilla outline. Captures only the immutable
     *  shape — never the level — as {@link CustomBlockOutlineRenderer} requires. */
    private record PreviewRenderer(VoxelShape shape) implements CustomBlockOutlineRenderer {
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
            VertexConsumer lines = buffer.getBuffer(RenderTypes.lines());
            ShapeRenderer.renderShape(poseStack, lines, shape,
                    pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                    ARGB.black(102), 2.5F);
            buffer.endLastBatch();
            return true;
        }
    }
}
