/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.client.render;

/**
 * Quarry rendering is handled by BCBuildersEventDist via RenderLevelStageEvent
 * rather than a BER, because the 1.21.11 BER API uses a render state pattern
 * that is not compatible with the quarry's world-space laser line rendering.
 */
public class RenderQuarry {

    public static void init() {
        // Called to classload this class
    }
}
