/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.container;

import buildcraft.silicon.tile.TileProgrammingTable;

/** The Programming Table's GUI geometry, in one place. Everything that draws or hit-tests the table reads these
 *  numbers rather than restating them: the menu's slots ({@link ContainerProgrammingTable}), the screen
 *  ({@code GuiProgrammingTable}), the JEI category's crop and slot positions, and the JEI click area on the energy
 *  bar. Positions are in the 176x207 texture's pixels, relative to the GUI's top-left corner. Plain ints only — no
 *  client classes — so the server-side menu can read them too. The option grid's dimensions stay on
 *  {@link TileProgrammingTable#OPTION_COLS}/{@code OPTION_ROWS}, which the tile and the JEI collector share. */
public final class ProgrammingTableLayout {
    private ProgrammingTableLayout() {}

    /** The GUI texture's drawn size. */
    public static final int SIZE_X = 176, SIZE_Y = 207;

    /** One item slot / option cell is this many pixels across; neighbours sit {@link #CELL_PITCH} apart. */
    public static final int CELL_SIZE = 16, CELL_PITCH = 18;

    /** The board going in (top-left) and the programmed board coming out (below it). */
    public static final int BOARD_X = 8, BOARD_Y = 36;
    public static final int RESULT_X = 8, RESULT_Y = 90;

    /** The first (top-left) cell of the option grid. */
    public static final int GRID_X = 43, GRID_Y = 36;

    /** Top-left of the player inventory block. */
    public static final int PLAYER_INV_X = 8, PLAYER_INV_Y = 123;

    /** The selection frame's cell in the texture (it is {@link #CELL_SIZE} square); 7.1.x's icon-strip quad. */
    public static final int SELECTED_U = 196, SELECTED_V = 1;

    /** The vertical laser-energy bar on the GUI, and where its fill sprite sits in the texture. */
    public static final int BAR_X = 164, BAR_Y = 36, BAR_W = 4, BAR_H = 70;
    public static final int BAR_FILL_U = 176, BAR_FILL_V = 18;

    /** Left edge of option cell {@code index} (reading order, {@link TileProgrammingTable#OPTION_COLS} per row). */
    public static int cellX(int index) {
        return GRID_X + (index % TileProgrammingTable.OPTION_COLS) * CELL_PITCH;
    }

    /** Top edge of option cell {@code index}. */
    public static int cellY(int index) {
        return GRID_Y + (index / TileProgrammingTable.OPTION_COLS) * CELL_PITCH;
    }

    /** Width of the whole option grid: its cells and the gaps between them, not the gap after the last. */
    public static int gridWidth() {
        return TileProgrammingTable.OPTION_COLS * CELL_PITCH - (CELL_PITCH - CELL_SIZE);
    }

    /** Height of the whole option grid, likewise. */
    public static int gridHeight() {
        return TileProgrammingTable.OPTION_ROWS * CELL_PITCH - (CELL_PITCH - CELL_SIZE);
    }
}
