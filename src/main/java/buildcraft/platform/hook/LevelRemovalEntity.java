/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

/**
 * An entity told when it leaves a level for any reason, its chunk unloading included (NeoForge's
 * {@code onRemovedFromLevel}; Fabric: the server's entity-unload event).
 */
public interface LevelRemovalEntity {
    /** It left the level it was in: removed, or its chunk unloaded. */
    void onLeftLevel();
}
