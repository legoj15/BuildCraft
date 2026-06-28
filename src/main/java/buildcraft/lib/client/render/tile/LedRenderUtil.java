/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared constants and helpers for the small status-LED cubes rendered on the
 * pump, mining well, and quarry. Anything block-specific (battery semantics,
 * "is running" predicate, the per-machine LED count) stays in the per-block BER;
 * this class holds only the parts that are genuinely identical across them.
 * <p>
 * Colours are stored in <strong>ABGR</strong> byte order — Minecraft's vertex
 * pipeline packs colour bytes in native little-endian, so {@code 0xFF_77_DD_77}
 * decodes as {@code alpha=0xFF, blue=0x77, green=0xDD, red=0x77} (a light
 * green).
 */
public final class LedRenderUtil {
    /** Off-state colour — a near-black so the LED is visibly inert when its predicate is false. */
    public static final int COLOUR_OFF = 0xFF_1f_10_1b;

    /** On-state colour for "active / running / status OK". */
    public static final int COLOUR_GREEN_ON = 0xFF_77_DD_77;

    /** On-state colour for "alert / no power / done". */
    public static final int COLOUR_RED_ON = 0xFF_22_22_DD;

    /**
     * Sets the centre of a {@link RenderPartCube} on a given horizontal block face.
     * The LED cube is placed at the face plane (offset slightly outward by its half-size)
     * and shifted along the face's tangent so that multiple LEDs can be lined up on the
     * same face.
     *
     * @param led         the cube whose centre to mutate; only {@link RenderPartCube#center} is touched
     * @param face        which block face the LED sits on ({@link Direction#NORTH}/{@code SOUTH}/{@code EAST}/{@code WEST})
     * @param insetBlocks distance from the face edge to the LED's centre, in block units
     *                    (so {@code 0.4 / 16.0} places the centre 0.4 px inside the face plane;
     *                    the LED's 1-px cube then protrudes ~0.1 px past the face)
     * @param sideOffset  signed offset from the face's centre along the face's tangent axis,
     *                    in block units (positive = away from the face's "left" edge for that face's
     *                    natural orientation). Two LEDs at e.g. {@code 1.5/16} and {@code 3.5/16}
     *                    sit on the same face spaced 2 px apart.
     * @param y           vertical position on the block, in block units (0..1)
     */
    public static void setFacePosition(RenderPartCube led, Direction face, double insetBlocks,
                                       double sideOffset, double y) {
        final double ledX, ledZ;
        final int dX, dZ;
        if (face.getAxis() == Direction.Axis.X) {
            dX = 0;
            dZ = face.getAxisDirection().getStep();
            ledZ = 0.5;
            ledX = (face == Direction.EAST) ? 1.0 - insetBlocks : insetBlocks;
        } else {
            dX = -face.getAxisDirection().getStep();
            dZ = 0;
            ledX = 0.5;
            ledZ = (face == Direction.SOUTH) ? 1.0 - insetBlocks : insetBlocks;
        }
        led.center.positiond(ledX + dX * sideOffset, y, ledZ + dZ * sideOffset);
    }

    /**
     * Whether the {@code face} of the block at {@code pos} is visible — i.e. not hidden by the
     * neighbouring block. Delegates to vanilla's {@link Block#shouldRenderFace} so a block-entity-drawn
     * decal (an LED, the zone planner's screen) appears under <em>exactly</em> the same condition the
     * block model's own face does: shown when the face is exposed, culled when an opaque neighbour
     * buries it. Without this a BER draws unconditionally, leaving a decal floating on a face the chunk
     * mesher has already culled (e.g. a planner stacked against another block).
     *
     * <p>Call once per face per frame before emitting that face's decal; it's a single neighbour lookup
     * plus vanilla's cached occlusion-shape test, the same work the mesher already does.
     */
    public static boolean isFaceVisible(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        BlockPos neighbourPos = pos.relative(face);
        //? if >=1.21.10 {
        return Block.shouldRenderFace(level, pos, state, level.getBlockState(neighbourPos), face);
        //?} else {
        /*return Block.shouldRenderFace(state, level, pos, face, neighbourPos);*/
        //?}
    }

    private LedRenderUtil() {}
}
