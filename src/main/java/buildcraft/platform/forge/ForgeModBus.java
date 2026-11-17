/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//? if >=1.21.10 {
import net.minecraftforge.eventbus.api.bus.BusGroup;
//?} else {
/^import net.minecraftforge.eventbus.api.IEventBus;
^///?}

// BuildCraft's mod bus on MinecraftForge, for the Forge platform implementations only (the Forge twin of
// NeoForgeModBus). On EventBus 7 (Forge for 1.21.6 and later) there is no bus object to pass around: a game event is
// reached through its own static BUS field, and a mod-lifecycle or registration event through
// `SomeEvent.getBus(ForgeModBus.group())`. The entrypoint binds the group in its constructor, before any platform
// implementation is asked for; nothing outside buildcraft.platform.forge may name this class.
//
// The mod's loading context rides along too: Forge registers a config through it (there is no container to hand to
// a registrar as on NeoForge), and the static ModLoadingContext.get() that would find it from anywhere is deprecated
// for removal, so the entrypoint keeps the one its constructor received.
//
// On EventBus 6 (Forge 52, the 1.21.1 line) the mod bus IS an object, FMLJavaModLoadingContext#getModEventBus(), and this
// holds that instead of a group. Either way the listeners and the DeferredRegisters reach it through ForgeBus only.
public final class ForgeModBus {
    //? if >=1.21.10 {
    private static BusGroup group;
    //?} else {
    /^private static IEventBus bus;
    ^///?}
    private static FMLJavaModLoadingContext context;

    private ForgeModBus() {}

    //? if >=1.21.10 {
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
    //?} else {
    /^// Called once by the Forge entrypoint with FMLJavaModLoadingContext#getModEventBus().
    public static void bind(IEventBus modBus) {
        if (bus != null) {
            throw new IllegalStateException("BuildCraft's Forge mod bus is already bound");
        }
        bus = modBus;
    }

    public static IEventBus bus() {
        if (bus == null) {
            throw new IllegalStateException("BuildCraft's Forge mod bus is read before the entrypoint bound it");
        }
        return bus;
    }
    ^///?}

    // Called once by the Forge entrypoint, beside bind, with the context its @Mod constructor received.
    public static void bindContext(FMLJavaModLoadingContext modContext) {
        if (context != null) {
            throw new IllegalStateException("BuildCraft's Forge mod loading context is already bound");
        }
        context = modContext;
    }

    public static FMLJavaModLoadingContext context() {
        if (context == null) {
            throw new IllegalStateException("BuildCraft's Forge mod loading context is read before the entrypoint bound it");
        }
        return context;
    }
}
*///?}
