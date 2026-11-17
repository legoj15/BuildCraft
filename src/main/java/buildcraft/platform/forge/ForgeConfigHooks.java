/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import net.minecraftforge.fml.event.config.ModConfigEvent;

import buildcraft.platform.config.ConfigHooks;

/^*
 * {@link ConfigHooks} on MinecraftForge: listeners on the mod bus group for {@code ModConfigEvent.Loading} and
 * {@code Reloading}, filtered to the spec, as the NeoForge twin does on its bus.
 *
 * <p>The listeners go on the mod bus through {@link ForgeBus#listenMod} (EventBus 7's per-event bus in the mod's group,
 * EventBus 6's mod bus on 1.21.1). Forge loads a config in its config-load phase, after the mod constructors
 * have run, so a listener subscribed while the constructor runs hears the first load and there is nothing to replay
 * (the Fabric twin has to, as its library reads the file inside the register call).
 *
 * <p>As on NeoForge, {@code Reloading} can fire on any thread (it follows a change of the file on disk).
 *^/
public final class ForgeConfigHooks implements ConfigHooks {

    @Override
    public void onLoad(Object spec, Runnable action) {
        ForgeBus.listenMod(ModConfigEvent.Loading.class, event -> {
            Object loaded = event.getConfig().getSpec();
            if (loaded == spec) {
                action.run();
            }
        });
    }

    @Override
    public void onReload(Object spec, Runnable action) {
        ForgeBus.listenMod(ModConfigEvent.Reloading.class, event -> {
            Object reloaded = event.getConfig().getSpec();
            if (reloaded == spec) {
                action.run();
            }
        });
    }
}
*///?}
