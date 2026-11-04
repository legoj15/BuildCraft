/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

// Written but not compiled yet: it names BuildCraft's common init (buildcraft.core), which the 26.3-fabric node does
// not compile until Phase 2, so bc.fabric.gradle.kts excludes this file from that node's source filter.
//? if fabric {
/*import net.fabricmc.api.ModInitializer;

import buildcraft.core.BCCore;
import buildcraft.platform.Platform;

/^*
 * BuildCraft's Fabric mod initializer, the twin of {@code BCNeoForgeEntrypoint}: the server-tracking listeners first
 * (so no server start is missed), then the same common init, then the setup and load-complete actions that init
 * queued, which NeoForge runs from its later loading phases and Fabric has no phases for.
 *^/
public final class BCFabricEntrypoint implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricLoaderServices.registerLifecycleListeners();
        BCCore.init();
        // NeoForge adds these from biome-modifier JSON, which the Fabric resources drop (FabricDataServices).
        FabricDataServices.addTaggedBiomeFeatures();
        ((FabricLifecycleHooks) Platform.lifecycle()).finishInit();
    }
}
*///?}
