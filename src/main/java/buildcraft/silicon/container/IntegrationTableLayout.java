/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.silicon.container;

/** The Integration Table's GUI geometry, in one place. Everything that draws or hit-tests the table reads these
 *  numbers rather than restating them: the menu's slots ({@link ContainerIntegrationTable}), the screen
 *  ({@code GuiIntegrationTable}), the JEI category's crop and slot positions, and the JEI click area on the energy
 *  bar. Positions are in the 176x191 texture's pixels, relative to the GUI's top-left corner. Plain ints only — no
 *  client classes — so the server-side menu can read them too.
 *
 *  <p>The inputs are a 3x3 ring: the centre cell is the target slot and the other eight, in reading order, are the
 *  expansion slots ({@code TileIntegrationTable.invToIntegrate}). The menu adds all nine cells in reading order, so
 *  the centre is menu slot 4. */
public final class IntegrationTableLayout {
    private IntegrationTableLayout() {}

    /** The GUI texture's drawn size. */
    public static final int SIZE_X = 176, SIZE_Y = 191;

    /** One item slot is this many pixels across. */
    public static final int CELL_SIZE = 16;

    /** The 3x3 input ring: the top-left cell and the distance between neighbouring cells. */
    public static final int RING_X = 19, RING_Y = 24, RING_PITCH = 25;
    public static final int RING_COLS = 3, RING_ROWS = 3;
    /** The centre cell, which holds the target; the cells around it are the ring's expansion slots. */
    public static final int CENTRE_COL = 1, CENTRE_ROW = 1;
    /** Expansion slots: every cell but the centre. */
    public static final int RING_SLOTS = RING_COLS * RING_ROWS - 1;

    /** The display-only preview of the recipe's output. */
    public static final int PREVIEW_X = 101, PREVIEW_Y = 36;
    /** The real output slot. */
    public static final int RESULT_X = 138, RESULT_Y = 49;

    /** Top-left of the player inventory block. */
    public static final int PLAYER_INV_X = 8, PLAYER_INV_Y = 109;

    /** The part of the GUI texture the recipe viewers (JEI and REI) show as the Integration Table category's panel:
     *  the ring, the preview, the result and the energy bar, leaving out the title strip and the player inventory. */
    public static final int VIEWER_U = 12, VIEWER_V = 18, VIEWER_W = 160, VIEWER_H = 78;

    /** The vertical laser-energy bar on the GUI, and where its fill sprite sits in the texture (it is the strip just
     *  right of the GUI image, at the top). */
    public static final int BAR_X = 164, BAR_Y = 22, BAR_W = 4, BAR_H = 70;
    public static final int BAR_FILL_U = SIZE_X, BAR_FILL_V = 0;

    /** Left edge of ring column {@code column} (0-2). */
    public static int ringX(int column) {
        return RING_X + column * RING_PITCH;
    }

    /** Top edge of ring row {@code row} (0-2). */
    public static int ringY(int row) {
        return RING_Y + row * RING_PITCH;
    }

    /** The side length of the square the whole ring covers: three cells and the two gaps between them. */
    public static int ringSpan() {
        return (RING_COLS - 1) * RING_PITCH + CELL_SIZE;
    }

    public static boolean isCentre(int column, int row) {
        return column == CENTRE_COL && row == CENTRE_ROW;
    }

    /** The expansion-slot index ({@code invToIntegrate}) of ring cell ({@code column}, {@code row}), or -1 for the
     *  centre cell, which is the target slot. Slots count in reading order with the centre skipped. */
    public static int ringSlot(int column, int row) {
        if (isCentre(column, row)) {
            return -1;
        }
        int cell = column + row * RING_COLS;
        return cell > CENTRE_COL + CENTRE_ROW * RING_COLS ? cell - 1 : cell;
    }

    /** The ring column of expansion slot {@code slot} — the inverse of {@link #ringSlot}. */
    public static int ringColumn(int slot) {
        return cellOf(slot) % RING_COLS;
    }

    /** The ring row of expansion slot {@code slot}. */
    public static int ringRow(int slot) {
        return cellOf(slot) / RING_COLS;
    }

    private static int cellOf(int slot) {
        int centreCell = CENTRE_COL + CENTRE_ROW * RING_COLS;
        return slot >= centreCell ? slot + 1 : slot;
    }
}
