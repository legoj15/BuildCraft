/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import net.minecraft.core.Direction;

/**
 * Sampling math for the Zone Planner's in-world front-face map preview — the little terrain "screen" on
 * the block, drawn by {@code RenderZonePlanner}. Kept pure (no client/GL types) so it can be unit-tested
 * and so the one place that decides <em>which world column each screen cell shows</em> is verifiable in
 * isolation, exactly like {@link ZoneMapCamera} does for the GUI viewport.
 *
 * <p>This is a faithful port of 1.12.2's {@code RenderZonePlanner.createTexture}: a {@link #GRID_W}&times;
 * {@link #GRID_H} grid of cells, each cell covering {@link #SCALE} blocks, sampled on a window centred on
 * the planner and <b>rotated with the block's facing</b> (so the map's forward direction tracks the way
 * the screen points). Only the terrain map colour is shown — no zones, no relief — matching the original.
 */
public final class ZoneFacePreview {
    private ZoneFacePreview() {}

    /** Screen grid width in cells (horizontal). 1.12.2 {@code TEXTURE_WIDTH}. */
    public static final int GRID_W = 10;
    /** Screen grid height in cells (vertical). 1.12.2 {@code TEXTURE_HEIGHT}. */
    public static final int GRID_H = 8;
    /** World blocks per cell — the downsample factor. 1.12.2 {@code scale}. */
    public static final int SCALE = 4;

    /**
     * The world column ({@code [worldX, worldZ]}) sampled by screen cell {@code (sx, sy)} for a planner at
     * {@code (tileX, tileZ)} facing {@code facing} (the direction the screen points — the modern block's
     * {@code FACING} already is that direction). Ported sign-for-sign from 1.12.2 so the rotate-with-block
     * behaviour matches the original. {@code facing} is expected to be horizontal (N/E/S/W); a vertical
     * facing falls back to the planner's own column.
     */
    public static int[] worldColumn(int tileX, int tileZ, Direction facing, int sx, int sy) {
        int o1 = (sx - GRID_W / 2) * SCALE; // horizontal cell → one world axis
        int o2 = (sy - GRID_H / 2) * SCALE; // vertical cell   → the other world axis
        return switch (facing) {
            case NORTH -> new int[]{ tileX + o1, tileZ - o2 };
            case EAST  -> new int[]{ tileX + o2, tileZ + o1 };
            case SOUTH -> new int[]{ tileX + o1, tileZ + o2 };
            case WEST  -> new int[]{ tileX - o2, tileZ + o1 };
            default    -> new int[]{ tileX, tileZ };
        };
    }
}
