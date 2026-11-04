/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

// Written but not compiled yet: it names BuildCraft's client init (buildcraft.core), which the 26.3-fabric node does
// not compile until Phase 2, so bc.fabric.gradle.kts excludes this file from that node's source filter.
//? if fabric {
/*import net.fabricmc.api.ClientModInitializer;

import buildcraft.core.BCCoreClient;

/^*
 * BuildCraft's Fabric client initializer: the client init {@code BCNeoForgeEntrypoint} runs on a physical client.
 * Fabric runs every mod initializer before any client initializer, so it follows {@code BCFabricEntrypoint}.
 *^/
public final class BCFabricClientEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BCCoreClient.init();
    }
}
*///?}
