/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import net.neoforged.neoforge.client.gui.ConfigurationScreen;
//? if >=1.21.10 {
import fuzs.forgeconfigapiport.fabric.api.v5.client.ConfigScreenFactoryRegistry;
//?} else {
/^import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.client.ConfigScreenFactoryRegistry;
^///?}

import buildcraft.platform.client.ClientConfigScreens;

/^*
 * {@link ClientConfigScreens} on Fabric: the {@link ConfigurationScreen} Forge Config API Port ships (the one NeoForge
 * generates), offered through its {@link ConfigScreenFactoryRegistry}, which is how Mod Menu finds a mod's config
 * button. There is no screen without Mod Menu installed, as Fabric has no mod list of its own.
 *^/
public final class FabricClientConfigScreens implements ClientConfigScreens {

    @Override
    public void registerConfigScreen() {
        ConfigScreenFactoryRegistry.INSTANCE.register("buildcraftunofficial", ConfigurationScreen::new);
    }
}
*///?}
