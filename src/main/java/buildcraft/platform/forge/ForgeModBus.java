/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import net.minecraftforge.eventbus.api.bus.BusGroup;

// BuildCraft's mod bus on MinecraftForge, for the Forge platform implementations only (the Forge twin of
// NeoForgeModBus). On EventBus 7 (Forge for 1.21.6 and later) there is no bus object to pass around: a game event is
// reached through its own static BUS field, and a mod-lifecycle or registration event through
// `SomeEvent.getBus(ForgeModBus.group())`. The entrypoint binds the group in its constructor, before any platform
// implementation is asked for; nothing outside buildcraft.platform.forge may name this class.
public final class ForgeModBus {
    private static BusGroup group;

    private ForgeModBus() {}

    // Called once by the Forge entrypoint with FMLJavaModLoadingContext#getModBusGroup().
    public static void bind(BusGroup modBusGroup) {
        if (group != null) {
            throw new IllegalStateException("BuildCraft's Forge mod bus is already bound");
        }
        group = modBusGroup;
    }

    public static BusGroup group() {
        if (group == null) {
            throw new IllegalStateException("BuildCraft's Forge mod bus is read before the entrypoint bound it");
        }
        return group;
    }
}
*///?}
