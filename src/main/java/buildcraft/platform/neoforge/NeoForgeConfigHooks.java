/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.config.ModConfigEvent;

import buildcraft.platform.config.ConfigHooks;

/** {@link ConfigHooks} on NeoForge: mod-bus listeners for {@code ModConfigEvent.Loading} and {@code Reloading}. */
public final class NeoForgeConfigHooks implements ConfigHooks {

    @Override
    public void onLoad(Object spec, Runnable action) {
        ModLoadingContext.get().getActiveContainer().getEventBus().addListener((ModConfigEvent.Loading event) -> {
            if (event.getConfig().getSpec() == spec) {
                action.run();
            }
        });
    }

    @Override
    public void onReload(Object spec, Runnable action) {
        ModLoadingContext.get().getActiveContainer().getEventBus().addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == spec) {
                action.run();
            }
        });
    }
}
//?}
