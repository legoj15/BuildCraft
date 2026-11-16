/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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
 * <p><b>Not yet wired (compile-only node).</b> {@code BCCore} and {@code BCCoreClient} are outside the sources this node
 * compiles (see {@code bc.forge.gradle.kts}), so their calls are the TODO below; naming them would not compile. The
 * mod id is spelled out for the same reason ({@code BCCore.MODID}).
 *^/
@Mod("buildcraftunofficial")
public final class BCForgeEntrypoint {

    public BCForgeEntrypoint(FMLJavaModLoadingContext context) {
        ForgeModBus.bind(context.getModBusGroup());
        // TODO(#209 F3): needs a Forge mixin for the boat tick, see platform/fabric/mixin/AbstractBoatMixin: Forge has no
        // per-entity tick event (NeoForge's EntityTickEvent.Post, which NeoForgeBoatFluids listens to from here), so
        // BoatFluidMotion.tick(boat) has no caller on Forge until a mixin at the end of AbstractBoat.tick calls it.
        // TODO(#209 F3): BCCore is outside the compiled set (the whole node compiles buildcraft/platform/** only); once it
        // is in, replace this block and the string in @Mod with
        //     BCCore.init();
        //     if (Platform.loader().isPhysicalClient()) {
        //         BCCoreClient.init();
        //     }
        // and @Mod(BCCore.MODID).
    }
}
*///?}
