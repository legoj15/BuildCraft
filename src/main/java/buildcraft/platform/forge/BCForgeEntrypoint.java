/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreClient;
import buildcraft.platform.Platform;

/^*
 * BuildCraft's MinecraftForge entrypoint (not NeoForge), the twin of {@code BCNeoForgeEntrypoint} and the only class FML
 * hands the mod bus group to. It binds the group for the implementations ({@link ForgeModBus}) and then runs
 * BuildCraft's loader-neutral init: the common init, then the client init on a physical client. Like the {@code @Mod}
 * constructor it replaces, it runs before FML's automatic event subscribers.
 *
 * <p><b>What differs from NeoForge's.</b> Forge's {@code @Mod} constructor takes the loading context
 * ({@link FMLJavaModLoadingContext}), not a bus and a container, and on EventBus 7 the mod bus is a
 * {@code BusGroup} that mod-lifecycle events are reached through ({@code SomeEvent.getBus(group)}). The capability
 * registration listener of NeoForge's entrypoint has no Forge 26.x twin (Forge has no {@code RegisterCapabilitiesEvent}
 * there: exposure is {@code AttachCapabilitiesEvent} per object, the transfer seam's concern), and none of Fabric's
 * extra steps is needed: FML runs the registry events and the setup phases itself, so there are no pending registrations
 * to flush and no queue of setup actions to run.
 *
 * <p>The boat drag needs no step here: {@code ForgeFlowingFluids} registers its listeners when the fluids are
 * registered (Forge has no per-entity tick event, which is why NeoForge's entrypoint does it by hand).
 *^/
@Mod(BCCore.MODID)
public final class BCForgeEntrypoint {

    public BCForgeEntrypoint(FMLJavaModLoadingContext context) {
        ForgeModBus.bind(context.getModBusGroup());
        ForgeModBus.bindContext(context);
        // The components ingredient's serializer, ahead of the registry event: the gate recipes name it.
        ForgeCraftingServices.registerSerializers();

        BCCore.init();
        if (Platform.loader().isPhysicalClient()) {
            BCCoreClient.init();
        }
    }
}
*///?}
