/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.gui;

/** The gate GUI's row geometry, in coordinates relative to the GUI's top-left corner: where each trigger/action
 *  pair sits and where the vertical connector between two pairs is. {@link GuiGate} lays out its slot widgets,
 *  draws the wiring and hit-tests connector clicks from this one class, so the three can't drift apart.
 *
 *  <p>Pure integer maths on purpose: no client classes, so it is unit-tested and shared by every Stonecutter
 *  branch of the screen's input handling. */
final class GateRowLayout {

    /** Width of one slot cell. */
    static final int CELL = 18;
    /** Top of the first row, below the title strip. */
    static final int FIRST_ROW_Y = 16;
    /** The vertical connector starts this far into the row it hangs from. */
    static final int CONNECTOR_Y_OFFSET = 9;

    final int horizontalSlotCount;
    final int verticalSlotCount;
    final int numTriggerArgs;
    final int numActionArgs;
    private final int slotPairWidth;
    private final int slotPairStart;

    /** @param twoColumns whether the slots are split into two side-by-side columns
     *  @param numSlots total trigger/action pairs, across both columns */
    GateRowLayout(boolean twoColumns, int numSlots, int numTriggerArgs, int numActionArgs) {
        this.horizontalSlotCount = twoColumns ? 2 : 1;
        this.verticalSlotCount = numSlots / horizontalSlotCount;
        this.numTriggerArgs = numTriggerArgs;
        this.numActionArgs = numActionArgs;
        this.slotPairWidth = CELL * (3 + numTriggerArgs + numActionArgs);
        this.slotPairStart = (162 - (slotPairWidth + (twoColumns ? slotPairWidth + CELL : 0))) / 2;
    }

    /** Index of the pair at {@code row} of {@code col}; the second column continues the first column's numbering. */
    int pairIndex(int row, int col) {
        return row + col * verticalSlotCount;
    }

    /** Left edge of the trigger slot of column {@code col}. */
    int slotBaseX(int col) {
        return slotPairStart + 7 + col * (CELL + slotPairWidth);
    }

    /** Top edge of row {@code row}. */
    int slotBaseY(int row) {
        return FIRST_ROW_Y + row * CELL;
    }

    /** Left edge of the connector column of {@code col}: right of the trigger slot and its arguments. */
    int connectorX(int col) {
        return slotBaseX(col) + CELL * (1 + numTriggerArgs);
    }

    /** Top edge of the vertical connector hanging below row {@code row}, to the row after it. */
    int connectorY(int row) {
        return slotBaseY(row) + CONNECTOR_Y_OFFSET;
    }

    /** Left edge of the action slot of column {@code col}. */
    int actionX(int col) {
        return slotBaseX(col) + CELL * (2 + numTriggerArgs);
    }

    /** Whether the GUI-relative point is on the connector hanging below row {@code row} of column {@code col}. */
    boolean connectorContains(int row, int col, int mx, int my) {
        int x = connectorX(col);
        int y = connectorY(row);
        return mx >= x && mx < x + CELL && my >= y && my < y + CELL;
    }

    /** The pair index of the vertical connector under the GUI-relative point, or -1 if the point is on none. The
     *  last row of a column has no connector below it. */
    int connectorPairAt(int mx, int my) {
        for (int row = 0; row < verticalSlotCount - 1; row++) {
            for (int col = 0; col < horizontalSlotCount; col++) {
                if (connectorContains(row, col, mx, my)) {
                    return pairIndex(row, col);
                }
            }
        }
        return -1;
    }
}
