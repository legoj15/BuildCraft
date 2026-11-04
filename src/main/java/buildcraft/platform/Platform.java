/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import buildcraft.platform.config.BCConfigType;
import buildcraft.platform.config.ConfigHooks;
import buildcraft.platform.config.ConfigRegistrar;
import buildcraft.platform.event.GameHooks;
import buildcraft.platform.event.LifecycleHooks;
import buildcraft.platform.event.PlatformProtection;
import buildcraft.platform.menu.BCMenus;
import buildcraft.platform.network.BCNetwork;
import buildcraft.platform.registry.AttachmentServices;
import buildcraft.platform.registry.DataServices;
import buildcraft.platform.registry.RegistryServices;

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

    /** Networking: payload registration and sending. */
    public static BCNetwork network() {
        return NetworkHolder.INSTANCE;
    }

    /** Menus: menu types that read opening data, and opening a menu with it. */
    public static BCMenus menus() {
        return MenusHolder.INSTANCE;
    }

    /** Protection: the permission events a machine posts for its fake player (break, place, attack, clicks). */
    public static PlatformProtection protection() {
        return ProtectionHolder.INSTANCE;
    }

    /** Game events: ticks, levels and chunks, players, interaction, commands and datapack syncs. */
    public static GameHooks gameHooks() {
        return GameHooksHolder.INSTANCE;
    }

    /** Mod-loading and server lifecycle: setup, load complete, server start and stop, level saves. */
    public static LifecycleHooks lifecycle() {
        return LifecycleHolder.INSTANCE;
    }

    /** The loader's fake-player class. */
    public static FakePlayers fakePlayers() {
        return FakePlayersHolder.INSTANCE;
    }

    /** Config registration: maps a {@link BCConfigType} onto the loader's own config types. */
    public static ConfigRegistrar configRegistrar() {
        return ConfigRegistrarHolder.INSTANCE;
    }

    /** Config load and reload listeners. */
    public static ConfigHooks configHooks() {
        return ConfigHooksHolder.INSTANCE;
    }

    /** Sounds: deferred sound types and fluid bucket sounds. */
    public static SoundServices sounds() {
        return SoundHolder.INSTANCE;
    }

    /** Fluids: what the loader adds to a fluid stack (its display name). */
    public static FluidServices fluids() {
        return FluidsHolder.INSTANCE;
    }

    /** Chunk loading tickets. */
    public static ChunkTickets chunkTickets() {
        return ChunkTicketsHolder.INSTANCE;
    }

    /** Crafting: component-matching ingredients. */
    public static CraftingServices crafting() {
        return CraftingHolder.INSTANCE;
    }

    /** Shearing blocks. */
    public static ShearServices shearing() {
        return ShearHolder.INSTANCE;
    }

    /** Loot context parameters the loader adds. */
    public static LootServices loot() {
        return LootHolder.INSTANCE;
    }

    /** Registries: registrars and the holders they hand back. */
    public static RegistryServices registries() {
        return RegistriesHolder.INSTANCE;
    }

    /** Data-driven hooks: load conditions and biome modifiers. */
    public static DataServices data() {
        return DataHolder.INSTANCE;
    }

    /** Saved entity data attachments. */
    public static AttachmentServices attachments() {
        return AttachmentsHolder.INSTANCE;
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

    private static final class NetworkHolder {
        static final BCNetwork INSTANCE = create();

        private static BCNetwork create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeNetwork();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricNetwork();
            *///?}
        }
    }

    private static final class MenusHolder {
        static final BCMenus INSTANCE = create();

        private static BCMenus create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeMenus();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricMenus();
            *///?}
        }
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

    private static final class GameHooksHolder {
        static final GameHooks INSTANCE = create();

        private static GameHooks create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeGameHooks();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricGameHooks();
            *///?}
        }
    }

    private static final class LifecycleHolder {
        static final LifecycleHooks INSTANCE = create();

        private static LifecycleHooks create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeLifecycleHooks();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricLifecycleHooks();
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

    private static final class ConfigRegistrarHolder {
        static final ConfigRegistrar INSTANCE = create();

        private static ConfigRegistrar create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeConfigRegistrar();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricConfigRegistrar();
            *///?}
        }
    }

    private static final class ConfigHooksHolder {
        static final ConfigHooks INSTANCE = create();

        private static ConfigHooks create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeConfigHooks();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricConfigHooks();
            *///?}
        }
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

    private static final class FluidsHolder {
        static final FluidServices INSTANCE = create();

        private static FluidServices create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeFluids();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricFluids();
            *///?}
        }
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

    private static final class RegistriesHolder {
        static final RegistryServices INSTANCE = create();

        private static RegistryServices create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeRegistryServices();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricRegistryServices();
            *///?}
        }
    }

    private static final class DataHolder {
        static final DataServices INSTANCE = create();

        private static DataServices create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeDataServices();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricDataServices();
            *///?}
        }
    }

    private static final class AttachmentsHolder {
        static final AttachmentServices INSTANCE = create();

        private static AttachmentServices create() {
            //? if neoforge {
            return new buildcraft.platform.neoforge.NeoForgeAttachmentServices();
            //?} elif fabric {
            /*return new buildcraft.platform.fabric.FabricAttachmentServices();
            *///?}
        }
    }
}
