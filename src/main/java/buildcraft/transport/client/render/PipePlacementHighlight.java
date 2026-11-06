/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.client.render;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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
 * <p>Registered as a block-outline override ({@link buildcraft.platform.client.ClientHooks#onBlockOutline}) by
 * {@code BCTransportClient.initClient}; the loader side draws the returned shape in place of the vanilla outline.
 */
public final class PipePlacementHighlight {
    private PipePlacementHighlight() {}

    /** The outline to draw for the pipe under the crosshair, or {@code null} to leave the default outline
     *  (not a pipe, no local player, or the held item places nothing there). */
    @Nullable
    public static VoxelShape outline(Level level, BlockPos pos, BlockState state, BlockHitResult hit) {
        if (!(state.getBlock() instanceof BlockPipeHolder)) {
            return null;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TilePipeHolder tile) || tile.getPipe() == null) {
            return null;
        }
        return previewShape(tile, hit, player);
    }

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
            // A stack the item would refuse (a blank facade, #129) places nothing, so promise nothing.
            if (!item.canPlace(pluggableStack)) {
                return null;
            }
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
}
