/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.marker.volume;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

public enum ClientVolumeBoxes {
    INSTANCE;

    public final List<VolumeBox> volumeBoxes = new ArrayList<>();

    /** Whether the machine at {@code ownerPos} owns a box: the box then draws its area, not the machine. */
    public boolean hasBoxOwnedBy(BlockPos ownerPos) {
        for (VolumeBox volumeBox : volumeBoxes) {
            if (volumeBox.isOwnedBy(ownerPos)) {
                return true;
            }
        }
        return false;
    }
}
