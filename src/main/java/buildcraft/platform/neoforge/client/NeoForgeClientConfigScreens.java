/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge.client;

//? if neoforge {
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import buildcraft.platform.neoforge.NeoForgeModBus;
import buildcraft.platform.client.ClientConfigScreens;

/** {@link ClientConfigScreens} on NeoForge: NeoForge's generated {@link ConfigurationScreen}. */
public final class NeoForgeClientConfigScreens implements ClientConfigScreens {

    @Override
    public void registerConfigScreen() {
        NeoForgeModBus.container()
                .registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
//?}
