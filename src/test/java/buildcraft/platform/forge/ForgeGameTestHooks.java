/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

import buildcraft.BuildCraftGameTests;
import buildcraft.lib.test.CapabilityOnlyCart;

/^*
 * The MinecraftForge hook of {@link BuildCraftGameTests}, the twin of {@code platform.neoforge.NeoForgeGameTestHooks}
 * and {@code platform.fabric.BCFabricGameTestEntrypoint}. The test source set is part of BuildCraft's own mod in the
 * Forge game-test run (the {@code runTestGameTestServer} task puts both source sets in the one mod file), so this
 * subscriber rides BuildCraft's mod bus group: FML's automatic subscribers are injected right after the {@code @Mod}
 * constructor ({@code BCForgeEntrypoint}, which has already bound {@code ForgeModBus}), and {@code FMLConstructModEvent}
 * follows them, so the registrars subscribe before any register event.
 *
 * <p>The test-only blocks are wanted only where Forge's own game-test entry ({@code net.minecraft.gametest.Main}) ran:
 * it sets {@code forge.gameTestServer} before it loads any mod. A plain {@code runTestServer} with the test source set
 * on its classpath registers the tests' functions but not those blocks.
 *^/
@Mod.EventBusSubscriber(modid = "buildcraftunofficial", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeGameTestHooks {

    private ForgeGameTestHooks() {}

    @SubscribeEvent
    public static void onConstruct(FMLConstructModEvent event) {
        BuildCraftGameTests.register(Boolean.getBoolean("forge.gameTestServer"));
        // The test-only cart's items, through the entity capability provider Forge's entity events attach (NeoForge: a
        // RegisterCapabilitiesEvent listener; Fabric: the entity item lookup, from the game-test mod's initializer).
        CapabilityOnlyCart.registerLookup();
    }
}
*///?}
