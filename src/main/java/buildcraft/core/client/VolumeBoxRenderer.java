/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.client;


import buildcraft.lib.client.render.MarkerRenderer;
import buildcraft.lib.client.render.laser.LaserBoxRenderer;

import buildcraft.core.marker.volume.ClientVolumeBoxes;
import buildcraft.core.marker.volume.VolumeBox;

/**
 * Renders all client-side VolumeBoxes as laser box outlines.
 */
public class VolumeBoxRenderer {

    /** Renders all volume boxes from ClientVolumeBoxes using LaserBoxRenderer. */
    public static void renderAll() {
        for (VolumeBox volumeBox : ClientVolumeBoxes.INSTANCE.volumeBoxes) {
            LaserBoxRenderer.renderLaserBoxStatic(
                    MarkerRenderer.getPoseStack(),
                    volumeBox.box,
                    BuildCraftLaserManager.MARKER_VOLUME_CONNECTED,
                    true, false,
                    MarkerRenderer.getCameraPos());
        }
    }
}
