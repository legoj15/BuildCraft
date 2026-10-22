/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Which mouse buttons start a paint stroke on the Zone Planner map. Left paints and right erases; every other button
 * (middle, the side buttons, an unknown one) used to fall through to "paint", so a stray middle click with a brush
 * in hand painted a cell.
 */
public class ZonePaintInputTest {

    /** The first number past left, right and middle, on any line's numbering (0 to 2 up to 26.2, 1 to 3 on 26.3). */
    private static final int SIDE = 1 + Math.max(InputConstants.MOUSE_BUTTON_LEFT,
            Math.max(InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.MOUSE_BUTTON_MIDDLE));

    @Test
    void leftButtonPaintsAndRightButtonErases() {
        Assertions.assertEquals(ZonePaintInput.PAINT, ZonePaintInput.strokeFor(InputConstants.MOUSE_BUTTON_LEFT),
                "left paints");
        Assertions.assertEquals(ZonePaintInput.ERASE, ZonePaintInput.strokeFor(InputConstants.MOUSE_BUTTON_RIGHT),
                "right erases");
    }

    @Test
    void middleAndExtraButtonsStartNoStroke() {
        Assertions.assertEquals(ZonePaintInput.NONE, ZonePaintInput.strokeFor(InputConstants.MOUSE_BUTTON_MIDDLE),
                "middle must not paint");
        for (int button = SIDE; button < SIDE + 5; button++) {
            Assertions.assertEquals(ZonePaintInput.NONE, ZonePaintInput.strokeFor(button),
                    "extra mouse button " + button + " must not paint");
        }
        Assertions.assertEquals(ZonePaintInput.NONE, ZonePaintInput.strokeFor(-1), "an unknown button must not paint");
    }
}
