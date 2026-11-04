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

    public static SoundServices sounds() {
        return SoundHolder.INSTANCE;
    }

    private static final class SoundHolder {
        static final SoundServices INSTANCE = create();

        private static SoundServices create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeSoundServices();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricSoundServices();
            *///?}
        }
    }

    public static ChunkTickets chunkTickets() {
        return ChunkTicketsHolder.INSTANCE;
    }

    private static final class ChunkTicketsHolder {
        static final ChunkTickets INSTANCE = create();

        private static ChunkTickets create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeChunkTickets();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricChunkTickets();
            *///?}
        }
    }

    public static CraftingServices crafting() {
        return CraftingHolder.INSTANCE;
    }

    private static final class CraftingHolder {
        static final CraftingServices INSTANCE = create();

        private static CraftingServices create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeCraftingServices();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricCraftingServices();
            *///?}
        }
    }

    public static ShearServices shearing() {
        return ShearHolder.INSTANCE;
    }

    private static final class ShearHolder {
        static final ShearServices INSTANCE = create();

        private static ShearServices create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeShearServices();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricShearServices();
            *///?}
        }
    }

    public static LootServices loot() {
        return LootHolder.INSTANCE;
    }

    private static final class LootHolder {
        static final LootServices INSTANCE = create();

        private static LootServices create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeLootServices();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricLootServices();
            *///?}
        }
    }
}
