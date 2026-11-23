/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge {
/*
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModContainer;

import buildcraft.platform.client.ClientConfigScreens;
import buildcraft.platform.forge.ForgeConfigRegistrar;
import buildcraft.platform.forge.ForgeModBus;

/^*
 * {@link ClientConfigScreens} on MinecraftForge (not NeoForge): Forge generates no screen from a config spec (NeoForge
 * has {@code ConfigurationScreen}, Fabric gets the same one from Forge Config API Port), so the mod list's config button
 * opens the hand-built {@code ForgeConfigScreen}. The factory is a {@code ConfigScreenHandler.ConfigScreenFactory}
 * extension point registered on the mod's own container (the same call on Forge 52, 61, 64, 65 and 66), not through
 * {@code MinecraftForge.registerConfigScreen}, which goes through the for-removal {@code ModLoadingContext.get()}.
 *^/
public final class ForgeClientConfigScreens implements ClientConfigScreens {
    @Override
    public void registerConfigScreen() {
        ModContainer container = ForgeModBus.context().getContainer();
        String modId = container.getModId();
        container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory(
                (minecraft, modsScreen) -> ForgeConfigScreen.root(modsScreen, modId, ForgeConfigRegistrar.registeredSpecs())));
    }
}
*///?}
