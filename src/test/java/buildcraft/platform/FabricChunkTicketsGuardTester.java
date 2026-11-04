/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.platform;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.test.MainSourceSet;

/**
 * Pins the Fabric chunk tickets' owner-map lifecycle in the source: no running test can stage it, because a level
 * unload only happens when a server stops (or a dynamic dimension goes away) and the {@link ChunkTickets} seam offers
 * no way to ask for one. The map is keyed by dimension on an instance that lives for the whole JVM, and dimension keys
 * are shared between worlds (an integrated server's next world reuses {@code Level.OVERWORLD}): a set left behind by
 * an unloaded level makes the first-owner check in {@code forceChunk} false, so no ticket is added in the next world
 * and every BuildCraft chunk loader silently stops loading. The wiring this guards against losing is the unload
 * listener the Fabric implementation registers, which must drop the unloading dimension's owner sets — and must ignore
 * client levels, whose unload shares the hook while their server levels stay loaded (a player's dimension change
 * replaces only the client's level).
 *
 * <p>Reads the raw file text, not {@link MainSourceSet#codeOnly}: the implementation lives in a {@code //? if fabric}
 * arm, live code only on the Fabric node (whose tests are compile-only) and a block comment on every node whose tests
 * run — the text is the same in both.
 */
public class FabricChunkTicketsGuardTester {

    private static String fabricChunkTickets() {
        List<MainSourceSet.SourceFile> files = MainSourceSet.javaFiles().stream()
                .filter(f -> f.relativePath().equals("buildcraft/platform/fabric/FabricChunkTickets.java"))
                .toList();
        Assertions.assertEquals(1, files.size(), "FabricChunkTickets.java must be in the main source set");
        return files.get(0).text();
    }

    @Test
    public void levelUnloadDropsTheUnloadingDimensionsOwners() {
        String code = fabricChunkTickets();
        Assertions.assertTrue(code.contains("onLevelUnload("),
                "FabricChunkTickets must hook the platform's level-unload notification: dimension keys are shared"
                        + " between worlds, so an owner set that outlives its level suppresses every ticket in the"
                        + " next world");
        Assertions.assertTrue(code.contains("instanceof ServerLevel"),
                "the unload listener must ignore client levels: their unload shares the hook while their server"
                        + " levels stay loaded");
        Assertions.assertTrue(code.contains("owners.remove(level.dimension())"),
                "the unload listener must drop the unloading dimension's owner sets");
    }
}
