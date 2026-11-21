/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge {
/*import buildcraft.platform.client.ClientConfigScreens;

/^*
 * {@link ClientConfigScreens} on MinecraftForge (not NeoForge). Gap: Forge ships no screen generated from a config spec on
 * either line, 26.3 or 1.21.1 (NeoForge has {@code ConfigurationScreen}, Fabric gets the same one from Forge Config API
 * Port), only the hook to register a hand-built one, so the mod list shows no config button and the configs are edited as
 * files.
 *^/
public final class ForgeClientConfigScreens implements ClientConfigScreens {
    @Override
    public void registerConfigScreen() {
        // TODO(#209 F6): a config screen (Forge generates none, so it would be hand-built), see platform/fabric/client/FabricClientConfigScreens
    }
}
*///?}
