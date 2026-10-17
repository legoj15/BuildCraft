/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The gate GUI's row geometry: where each trigger/action pair and the vertical connector between two pairs sit,
 *  and which connector a click lands on. {@code GuiGate} lays out, draws and hit-tests from this one class; the
 *  sweep below pins it against the hit-test loop {@code GuiGate.mouseClicked} used to carry in two copies. */
public class GateRowLayoutTester {

    /** The pre-extraction hit-test, copied from the two {@code GuiGate.mouseClicked} bodies (gui-relative mouse). */
    private static int legacyConnectorHit(boolean twoColumns, int numSlots, int numTriggerArgs, int numActionArgs,
            int mx, int my) {
        int horizontalSlotCount = twoColumns ? 2 : 1;
        int verticalSlotCount = numSlots / horizontalSlotCount;
        int slotPairWidth = 18 * (3 + numTriggerArgs + numActionArgs);
        int slotPairStart = (162 - (slotPairWidth + (twoColumns ? slotPairWidth + 18 : 0))) / 2;
        for (int row = 0; row < verticalSlotCount - 1; row++) {
            for (int col = 0; col < horizontalSlotCount; col++) {
                int connBaseX = slotPairStart + 7 + col * (18 + slotPairWidth) + 18 * (1 + numTriggerArgs);
                int connBaseY = 16 + 9 + row * 18;
                if (mx >= connBaseX && mx < connBaseX + 18 && my >= connBaseY && my < connBaseY + 18) {
                    return row + col * verticalSlotCount;
                }
            }
        }
        return -1;
    }

    @Test
    public void connectorHitMatchesTheLegacyLoopOverEveryRealisticLayout() {
        int checked = 0;
        for (int numSlots = 1; numSlots <= 8; numSlots++) {
            boolean twoColumns = numSlots > 4; // GateLogic.isSplitInTwo
            for (int trig = 0; trig <= 3; trig++) {
                for (int act = 0; act <= 3; act++) {
                    GateRowLayout layout = new GateRowLayout(twoColumns, numSlots, trig, act);
                    for (int mx = -4; mx <= 180; mx++) {
                        for (int my = -4; my <= 200; my++) {
                            int expected = legacyConnectorHit(twoColumns, numSlots, trig, act, mx, my);
                            Assertions.assertEquals(expected, layout.connectorPairAt(mx, my),
                                    "slots=" + numSlots + " split=" + twoColumns + " trigArgs=" + trig
                                            + " actionArgs=" + act + " at (" + mx + "," + my + ")");
                            checked++;
                        }
                    }
                }
            }
        }
        Assertions.assertTrue(checked > 100_000, "the sweep should be exhaustive, ran only " + checked);
    }

    @Test
    public void oneColumnGeometryIsPinned() {
        // 3 slots, one trigger arg, one action arg: pair width 90, start (162-90)/2 = 36, trigger slot at x 43.
        GateRowLayout layout = new GateRowLayout(false, 3, 1, 1);
        Assertions.assertEquals(1, layout.horizontalSlotCount);
        Assertions.assertEquals(3, layout.verticalSlotCount);
        Assertions.assertEquals(43, layout.slotBaseX(0));
        Assertions.assertEquals(16, layout.slotBaseY(0));
        Assertions.assertEquals(34, layout.slotBaseY(1));
        Assertions.assertEquals(79, layout.connectorX(0), "connector sits right of the trigger slot and its arg");
        Assertions.assertEquals(25, layout.connectorY(0), "connector straddles the row boundary, 9 px into the row");
        Assertions.assertEquals(97, layout.actionX(0));
        Assertions.assertEquals(1, layout.pairIndex(1, 0));

        Assertions.assertEquals(0, layout.connectorPairAt(79, 25), "top-left corner is inside");
        Assertions.assertEquals(0, layout.connectorPairAt(96, 42), "bottom-right pixel is inside");
        Assertions.assertEquals(-1, layout.connectorPairAt(97, 30), "one past the right edge is outside");
        Assertions.assertEquals(-1, layout.connectorPairAt(85, 24), "one above the top edge is outside");
        Assertions.assertEquals(1, layout.connectorPairAt(85, 43), "second connector starts 18 px lower");
        Assertions.assertEquals(-1, layout.connectorPairAt(85, 61), "the last row has no connector below it");
    }

    @Test
    public void twoColumnGeometryIsPinned() {
        // 8 slots split in two columns of 4, no args: pair width 54, start (162-126)/2 = 18.
        GateRowLayout layout = new GateRowLayout(true, 8, 0, 0);
        Assertions.assertEquals(2, layout.horizontalSlotCount);
        Assertions.assertEquals(4, layout.verticalSlotCount);
        Assertions.assertEquals(25, layout.slotBaseX(0));
        Assertions.assertEquals(97, layout.slotBaseX(1));
        Assertions.assertEquals(43, layout.connectorX(0));
        Assertions.assertEquals(115, layout.connectorX(1));
        Assertions.assertEquals(4, layout.pairIndex(0, 1), "the second column continues the pair numbering");

        Assertions.assertEquals(0, layout.connectorPairAt(50, 30));
        Assertions.assertEquals(4, layout.connectorPairAt(120, 30));
        Assertions.assertEquals(6, layout.connectorPairAt(120, 25 + 2 * 18 + 3));
        Assertions.assertEquals(-1, layout.connectorPairAt(120, 25 + 3 * 18 + 3), "last row of each column has no connector");
    }
}
