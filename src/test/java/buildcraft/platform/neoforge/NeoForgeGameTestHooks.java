/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import buildcraft.BuildCraftGameTests;
import buildcraft.lib.test.TestOnlyBlocks;

/**
 * The NeoForge hook of {@link BuildCraftGameTests}: the test source set is part of BuildCraft's mod here, so this
 * subscriber runs on BuildCraft's mod bus. {@code FMLConstructModEvent} follows the entrypoint's constructor, so the
 * registrars subscribe to the bus {@code NeoForgeModBus} already holds, before any register event. The Fabric twin is
 * {@code platform.fabric.BCFabricGameTestEntrypoint}, and this file is not in the Fabric node's game-test set.
 *
 * <p>Bare {@code @EventBusSubscriber} on every node: FML picks each method's bus from the event type (all mod-bus
 * events here). The 1.21.1 loader still has the legacy {@code bus()} element but is pinned past the build that
 * ignores it at runtime, so it is omitted; 1.21.1 game-test count parity is verified per run.
 */
@EventBusSubscriber(modid = "buildcraftunofficial")
public final class NeoForgeGameTestHooks {

    private NeoForgeGameTestHooks() {}

    /** True in the {@code gameTestServer} run (bc.neoforge), which is where the test-only blocks are wanted. */
    private static boolean gameTestRun() {
        return Boolean.getBoolean("neoforge.enableGameTest");
    }

    //? if >=1.21.10 {
    @SubscribeEvent
    public static void onConstruct(FMLConstructModEvent event) {
        BuildCraftGameTests.register(gameTestRun());
    }
    //?} else {
    /*@SubscribeEvent
    public static void onConstruct(FMLConstructModEvent event) {
        if (gameTestRun()) {
            TestOnlyBlocks.register();
        }
    }

    // 1.21.1 has no dynamic TEST_FUNCTION registry: the shared LegacyGameTestRegistry feeds GameTestRegistry from
    // the same BuildCraftGameTests.registerAll (the Fabric 1.21.1 node's game-test initializer calls it too).
    @SubscribeEvent
    public static void onRegisterGameTests(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        buildcraft.lib.test.LegacyGameTestRegistry.registerAll();
    }*/
    //?}
}
