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
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;

/** The incremental box sync, built on its own instance so the level's real saved data is never touched. */
public class VolumeBoxSyncTester {
    /**
     * A box that was on disk when the world loaded reaches every joining client in its full snapshot, so removing it
     * must send its id. With an empty baseline after load the removal was never sent and the client kept a ghost box
     * (with its addon's preview) until it rejoined.
     */
    public static void removingABoxLoadedFromDiskReachesClients(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VolumeBox box = new VolumeBox(level, new BlockPos(1, 64, 1));
        ListTag list = new ListTag();
        list.add(box.writeToNBT());
        CompoundTag stored = new CompoundTag();
        stored.put("volumeBoxes", list);

        LevelSavedDataVolumeBoxes loaded = LevelSavedDataVolumeBoxes.fromNbt(stored, level);
        loaded.volumeBoxes.clear();
        MessageVolumeBoxes delta = loaded.takeDelta();

        helper.assertTrue(delta != null && delta.removed().equals(List.of(box.id)) && delta.upserts().isEmpty(),
                "removing the loaded box should send just its id, got " + delta);
        helper.succeed();
    }
}
