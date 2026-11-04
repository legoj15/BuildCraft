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

    /** Client registration: screens, renderers, particles, HUD layers, tints, pipelines, sprites, reload listeners. */
    public static ClientRegistrationSinks sinks() {
        return SinksHolder.INSTANCE;
    }

    /** Client game hooks: level render, ticks, connection, recipes, tooltips, block outline. */
    public static ClientHooks hooks() {
        return HooksHolder.INSTANCE;
    }

    /** The loader's quad and model additions BC's model code builds with. */
    public static ClientModels models() {
        return ModelsHolder.INSTANCE;
    }

    private static final class ModelsHolder {
        static final ClientModels INSTANCE = create();

        private static ClientModels create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.client.NeoForgeClientModels();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.client.FabricClientModels();
            *///?}
        }
    }

    /** How the loader draws a fluid: its still texture and tint. */
    public static ClientFluids fluids() {
        return FluidsHolder.INSTANCE;
    }

    private static final class FluidsHolder {
        static final ClientFluids INSTANCE = create();

        private static ClientFluids create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.client.NeoForgeClientFluids();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.client.FabricClientFluids();
            *///?}
        }
    }

    private static final class HooksHolder {
        static final ClientHooks INSTANCE = create();

        private static ClientHooks create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.client.NeoForgeClientHooks();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.client.FabricClientHooks();
            *///?}
        }
    }

    private static final class SinksHolder {
        static final ClientRegistrationSinks INSTANCE = create();

        private static ClientRegistrationSinks create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.client.NeoForgeClientRegistrationSinks();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.client.FabricClientRegistrationSinks();
            *///?}
        }
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
