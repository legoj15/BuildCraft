/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

/**
 * The compile-time binder: one static accessor per platform service. Each accessor reads a {@code static final} field
 * of its own lazy holder class, so asking for one service never loads another's implementation (see the package
 * documentation for the rules the holders follow).
 */
public final class Platform {
    private Platform() {}

    /** The loader services: side, mod list, directories and the current server. */
    public static LoaderServices loader() {
        return LoaderHolder.INSTANCE;
    }

    /** Config registration: maps a {@link buildcraft.platform.config.BCConfigType} onto the loader's own config types. */
    public static buildcraft.platform.config.ConfigRegistrar configRegistrar() {
        return ConfigRegistrarHolder.INSTANCE;
    }

    /** Config load and reload listeners. */
    public static buildcraft.platform.config.ConfigHooks configHooks() {
        return ConfigHooksHolder.INSTANCE;
    }

    private static final class LoaderHolder {
        static final LoaderServices INSTANCE = create();

        private static LoaderServices create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeLoaderServices();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricLoaderServices();
            *///?}
        }
    }

    private static final class ConfigRegistrarHolder {
        static final buildcraft.platform.config.ConfigRegistrar INSTANCE = create();

        private static buildcraft.platform.config.ConfigRegistrar create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeConfigRegistrar();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricConfigRegistrar();
            *///?}
        }
    }

    private static final class ConfigHooksHolder {
        static final buildcraft.platform.config.ConfigHooks INSTANCE = create();

        private static buildcraft.platform.config.ConfigHooks create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeConfigHooks();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricConfigHooks();
            *///?}
        }
    }
}
