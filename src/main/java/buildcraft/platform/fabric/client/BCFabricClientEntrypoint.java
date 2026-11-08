/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

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
        FabricClientLoaderHookRoutes.register();
        //? if >=26.1 {
        FabricFluidContainerModel.register();
        //?} else {
        /^FabricLegacyFluidContainerModel.register();
        ^///?}
        BCCoreClient.init();
    }
}
*///?}
