/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import net.neoforged.neoforge.common.ModConfigSpec;

import buildcraft.platform.config.ConfigHooks;

/^*
 * {@link ConfigHooks} on Fabric: Forge Config API Port's per-mod loading and reloading events, filtered to the spec,
 * as the NeoForge implementation filters {@code ModConfigEvent}.
 *
 * <p>Forge Config API Port reads a common or client config inside its register call, so the loading event has fired
 * (to no listener yet) by the time {@link #onLoad} can be called for a spec registered just before. The event is not
 * replayed, so {@link #onLoad} runs its action at once for a spec that is already loaded.
 *^/
public final class FabricConfigHooks implements ConfigHooks {

    @Override
    public void onLoad(Object spec, Runnable action) {
        ModConfigEvents.loading(FabricConfigRegistrar.MOD_ID).register(config -> {
            if (config.getSpec() == spec) {
                action.run();
            }
        });
        // The file was read when the spec was registered, before this listener existed (see the class documentation).
        if (spec instanceof ModConfigSpec modSpec && modSpec.isLoaded()) {
            action.run();
        }
    }

    @Override
    public void onReload(Object spec, Runnable action) {
        ModConfigEvents.reloading(FabricConfigRegistrar.MOD_ID).register(config -> {
            if (config.getSpec() == spec) {
                action.run();
            }
        });
    }
}
*///?}
