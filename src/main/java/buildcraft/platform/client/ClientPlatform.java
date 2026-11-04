/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

/**
 * The client-only twin of {@link buildcraft.platform.Platform}: reach it only from client initialisation, so a
 * dedicated server never loads it or its implementation.
 */
public final class ClientPlatform {
    private ClientPlatform() {}

    /** The config-screen registration. */
    public static ClientConfigScreens configScreens() {
        return ConfigScreensHolder.INSTANCE;
    }

    private static final class ConfigScreensHolder {
        static final ClientConfigScreens INSTANCE = create();

        private static ClientConfigScreens create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.client.NeoForgeClientConfigScreens();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.client.FabricClientConfigScreens();
            *///?}
        }
    }
}
