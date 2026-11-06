/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

/**
 * A block entity told when it joins a level and when its chunk unloads (NeoForge's {@code onLoad} and
 * {@code onChunkUnloaded}).
 */
public interface LevelLifecycleBlockEntity {
    /**
     * It joined a level: placed, or its chunk loaded. Runs at the head of the level's next block-entity tick (after
     * that tick's scheduled block ticks), as NeoForge's onLoad does.
     */
    void onJoinedLevel();

    /** The chunk holding it unloads (not when it is broken). */
    void onChunkUnload();
}
