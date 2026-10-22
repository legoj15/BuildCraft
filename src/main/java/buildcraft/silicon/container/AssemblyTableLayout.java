/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.silicon.container;

import buildcraft.silicon.tile.TileAssemblyTable;

/** The Assembly Table's GUI geometry, in one place. Everything that draws or hit-tests the table reads these
 *  numbers rather than restating them: the menu's slots ({@link ContainerAssemblyTable}), the screen
 *  ({@code GuiAssemblyTable}), the JEI category's slot positions, and the JEI click area on the energy bar.
 *  Positions are in the 176x220 texture's pixels, relative to the GUI's top-left corner. Plain ints only — no
 *  client classes — so the server-side menu can read them too. The input grid's dimensions stay on
 *  {@link TileAssemblyTable#INPUT_COLS}/{@code INPUT_ROWS}, which the tile's inventory shares.
 *
 *  <p>Two 3x4 grids flank the energy bar: the real input slots on the left and, on the right, display-only cells
 *  that list the recipes the table can make (and show which are saved). The two are the same size and pitch, so
 *  {@link #inputX}/{@link #inputY} and {@link #displayX}/{@link #displayY} differ only in their origin. */
public final class AssemblyTableLayout {
    private AssemblyTableLayout() {}

    /** The GUI texture's drawn size. */
    public static final int SIZE_X = 176, SIZE_Y = 220;

    /** One slot / display cell is this many pixels across; neighbours sit {@link #CELL_PITCH} apart. */
    public static final int CELL_SIZE = 16, CELL_PITCH = 18;

    /** The shape of both grids, and how many cells each has. */
    public static final int GRID_COLS = TileAssemblyTable.INPUT_COLS, GRID_ROWS = TileAssemblyTable.INPUT_ROWS;
    public static final int GRID_CELLS = GRID_COLS * GRID_ROWS;

    /** The first (top-left) cell of the input slots and of the recipe display cells. */
    public static final int INPUT_X = 8, INPUT_Y = 36;
    public static final int DISPLAY_X = 116, DISPLAY_Y = 36;

    /** Top-left of the player inventory block. */
    public static final int PLAYER_INV_X = 8, PLAYER_INV_Y = 123;

    /** The vertical laser-energy bar between the grids, and where its fill sprite sits in the texture (the strip just
     *  right of the GUI image). */
    public static final int BAR_X = 86, BAR_Y = 36, BAR_W = 4, BAR_H = 70;
    public static final int BAR_FILL_U = SIZE_X, BAR_FILL_V = 48;

    /** The recipe-state markers drawn over a display cell, in the same strip: saved, enough made, enough made and
     *  still running. Each is {@link #CELL_SIZE} square. */
    public static final int STATE_U = SIZE_X;
    public static final int SAVED_V = 0, SAVED_ENOUGH_V = 16, SAVED_ENOUGH_ACTIVE_V = 32;

    /** Left edge of input slot {@code index} (reading order, {@link #GRID_COLS} per row). */
    public static int inputX(int index) {
        return INPUT_X + (index % GRID_COLS) * CELL_PITCH;
    }

    /** Top edge of input slot {@code index}. */
    public static int inputY(int index) {
        return INPUT_Y + (index / GRID_COLS) * CELL_PITCH;
    }

    /** Left edge of display cell {@code index}, in the same reading order. */
    public static int displayX(int index) {
        return DISPLAY_X + (index % GRID_COLS) * CELL_PITCH;
    }

    /** Top edge of display cell {@code index}. */
    public static int displayY(int index) {
        return DISPLAY_Y + (index / GRID_COLS) * CELL_PITCH;
    }

    /** Width of either grid: its cells and the gaps between them, not the gap after the last. */
    public static int gridWidth() {
        return GRID_COLS * CELL_PITCH - (CELL_PITCH - CELL_SIZE);
    }

    /** Height of either grid, likewise. */
    public static int gridHeight() {
        return GRID_ROWS * CELL_PITCH - (CELL_PITCH - CELL_SIZE);
    }
}
