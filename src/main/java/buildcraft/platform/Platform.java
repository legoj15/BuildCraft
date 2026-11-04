/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import buildcraft.platform.event.PlatformProtection;

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

    /** Protection: the permission events a machine posts for its fake player (break, place, attack, clicks). */
    public static PlatformProtection protection() {
        return ProtectionHolder.INSTANCE;
    }

    /** The loader's fake-player class. */
    public static FakePlayers fakePlayers() {
        return FakePlayersHolder.INSTANCE;
    }

    private static final class ProtectionHolder {
        static final PlatformProtection INSTANCE = create();

        private static PlatformProtection create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeProtection();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricProtection();
            *///?}
        }
    }

    private static final class FakePlayersHolder {
        static final FakePlayers INSTANCE = create();

        private static FakePlayers create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeFakePlayers();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricFakePlayers();
            *///?}
        }
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
}
