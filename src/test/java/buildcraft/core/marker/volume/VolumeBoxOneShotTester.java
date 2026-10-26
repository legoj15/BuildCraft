/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.marker.volume;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import buildcraft.api.core.IAreaProvider;

/**
 * A box lifted off volume markers is used once: no machine can take it while markers still back it, a machine never
 * locks it, and taking it removes it. Built on its own instance so the level's real saved data is never touched.
 */
public class VolumeBoxOneShotTester {
    public static void markerBoxIsTakenOnceAfterTheMarkersBreak(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos max = helper.absolutePos(new BlockPos(4, 1, 5));
        BlockPos inside = helper.absolutePos(new BlockPos(2, 1, 2));
        LevelSavedDataVolumeBoxes boxes = new LevelSavedDataVolumeBoxes(level);
        VolumeBox lifted = VolumeBox.fromMarkers(level, min, max, List.of(min, max));
        boxes.volumeBoxes.add(lifted);

        helper.assertTrue(boxes.getLockableVolumeBoxAt(inside) == null, "a machine must never lock a one-shot box");
        helper.assertTrue(boxes.getOneShotAreaAt(inside) == null, "no machine may take a box markers still back");

        // The save keeps both the flag and the markers still backing it.
        CompoundTag stored = LevelSavedDataVolumeBoxes.toNbt(boxes);
        VolumeBox reloaded = LevelSavedDataVolumeBoxes.fromNbt(stored, level).volumeBoxes.get(0);
        helper.assertTrue(reloaded.isOneShot() && reloaded.hasSourceMarkers(),
                "the reloaded box lost its one-shot state: " + reloaded.writeToNBT());

        lifted.breakSourceMarkers();
        IAreaProvider area = boxes.getOneShotAreaAt(inside);
        helper.assertTrue(area != null && area.min().equals(min) && area.max().equals(max),
                "a confirmed one-shot box should be offered as its area, got " + area);
        area.removeFromWorld();
        helper.assertTrue(boxes.volumeBoxes.isEmpty(), "taking a one-shot box should remove it");
        helper.succeed();
    }
}
