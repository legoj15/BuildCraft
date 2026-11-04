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
 * {@link BuildCraftGameTests#register}, run by Fabric API's game-test runner ({@code -Dfabric-api.gametest}).
 *^/
public final class BCFabricGameTestEntrypoint implements ModInitializer {

    @Override
    public void onInitialize() {
        BuildCraftGameTests.register(System.getProperty("fabric-api.gametest") != null);
    }
}
*///?}
