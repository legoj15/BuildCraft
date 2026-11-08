/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreClient;
import buildcraft.platform.Platform;

/**
 * BuildCraft's NeoForge entrypoint, and the only class FML hands the mod bus and container to. It passes them to the
 * implementations ({@link NeoForgeModBus}) and runs BuildCraft's loader-neutral init: the common init, then the client
 * init on a physical client. Like the {@code @Mod} constructor it replaces, it runs before FML's automatic event
 * subscribers.
 */
@Mod(BCCore.MODID)
public final class BCNeoForgeEntrypoint {

    public BCNeoForgeEntrypoint(IEventBus modEventBus, ModContainer modContainer) {
        NeoForgeModBus.set(modEventBus, modContainer);
        // BuildCraft's only RegisterCapabilitiesEvent listener on this bus, which runs the common init's listeners in
        // the order it adds them (each subsystem used to add its own here, in that same order).
        modEventBus.addListener(RegisterCapabilitiesEvent.class, CapabilityListeners::fire);
        // The boat drag has no loader-neutral hook (Fabric takes it from a boat mixin), so it is registered here.
        NeoForgeBoatFluids.register();

        BCCore.init();
        if (Platform.loader().isPhysicalClient()) {
            BCCoreClient.init();
        }
    }
}
//?}
