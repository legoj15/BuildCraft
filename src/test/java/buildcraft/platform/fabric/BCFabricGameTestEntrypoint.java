/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.fabricmc.api.ModInitializer;

import buildcraft.BuildCraftGameTests;

/^*
 * The initializer of the Fabric node's game-test mod ({@code buildcraftunofficial_gametest}, bc.fabric's
 * {@code gametest} source set), the twin of {@code platform.neoforge.NeoForgeGameTestHooks}: the same
 * {@link BuildCraftGameTests#register} (on 1.21.1 {@code LegacyGameTestRegistry}, as there), run by Fabric API's game-test
 * runner ({@code -Dfabric-api.gametest}).
 *^/
public final class BCFabricGameTestEntrypoint implements ModInitializer {

    @Override
    public void onInitialize() {
        //? if >=1.21.10 {
        BuildCraftGameTests.register(System.getProperty("fabric-api.gametest") != null);
        //?} else {
        /^// 1.21.1: the test-only blocks as register() adds them, and every test into GameTestRegistry (no
        // TEST_FUNCTION registry there), which Fabric API's runner reads once the server starts.
        if (System.getProperty("fabric-api.gametest") != null) {
            buildcraft.lib.test.TestOnlyBlocks.register();
        }
        buildcraft.lib.test.LegacyGameTestRegistry.registerAll();
        // No client game-test module in its Fabric API either: on Loom's clientGameTest run the shim starts the
        // client game test (BCFabricClientGameTest) instead.
        buildcraft.platform.fabric.gametest.ClientGameTestShim.install();
        ^///?}
        // The test-only cart's items, through the entity item lookup (NeoForge: a RegisterCapabilitiesEvent listener).
        buildcraft.lib.test.CapabilityOnlyCart.registerLookup();
    }
}
*///?}
