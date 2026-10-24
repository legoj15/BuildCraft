/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

//? if >=26.1 {
import buildcraft.core.marker.PathSavedData;
import buildcraft.core.marker.VolumeSavedData;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.robotics.RobotRegistry;
import buildcraft.robotics.zone.ZoneMapSavedData;
import buildcraft.transport.wire.SavedDataWireSystems;
//?}

/**
 * Keeps every BuildCraft saved-data type behind {@link LegacySavedData#type}, and keeps its id where it has always
 * been. On 26.1+ a saved-data file lives at {@code <namespace>/<name>.dat} instead of the {@code <name>.dat} a 1.21.x
 * build wrote, so a type built without the legacy-aware builder silently orphans the data of every world upgraded
 * from 1.21.x; and changing an id orphans every world already on 26.1+ the same way.
 */
public class LegacySavedDataGuardTester {

    //? if >=26.1 {
    @Test
    public void noSavedDataTypeIsBuiltOutsideTheLegacyAwareBuilder() {
        List<String> offenders = new ArrayList<>();
        int builders = 0;
        for (MainSourceSet.SourceFile file : MainSourceSet.javaFiles()) {
            if (file.relativePath().equals("buildcraft/lib/misc/LegacySavedData.java")) {
                continue;
            }
            String code = MainSourceSet.codeOnly(file.text());
            if (code.contains("new SavedDataType<")) {
                offenders.add(file.relativePath());
            }
            if (code.contains("LegacySavedData.type(")) {
                builders++;
            }
        }
        Assertions.assertTrue(offenders.isEmpty(),
                "These files build a SavedDataType directly " + offenders + " - go through LegacySavedData.type so"
                        + " a world upgraded from 1.21.x keeps the data (see LegacySavedData).");
        // Liveness: the existing types, or "no offenders" is vacuous.
        Assertions.assertTrue(builders >= 6, "the scan lost the types built through LegacySavedData.type (saw "
                + builders + ")");
    }

    /** The ids are the data files' names; they are what the migration looks for under the old layout, and what the
     *  26.1+ storage already wrote for worlds that never saw 1.21.x. */
    @Test
    public void theSavedDataIdsHaveNotMoved() {
        Assertions.assertEquals("minecraft:buildcraft_robot_registry", RobotRegistry.TYPE.id().toString());
        Assertions.assertEquals("minecraft:buildcraft_wire_systems", SavedDataWireSystems.TYPE.id().toString());
        Assertions.assertEquals("minecraft:buildcraft_marker_volume", VolumeSavedData.TYPE.id().toString());
        Assertions.assertEquals("minecraft:buildcraft_marker_path", PathSavedData.TYPE.id().toString());
        Assertions.assertEquals("minecraft:buildcraft_volume_boxes",
                LevelSavedDataVolumeBoxes.createType(null).id().toString());
        Assertions.assertEquals("minecraft:buildcraft_zone_maps", ZoneMapSavedData.TYPE.id().toString());
    }
    //?}
}
