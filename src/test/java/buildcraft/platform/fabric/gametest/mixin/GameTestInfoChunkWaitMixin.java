/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.gametest.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.chat.Component;

/^*
 * A watchdog for the Fabric game-test run (the game-test mod only, never the shipped jar). Vanilla's GameTestInfo waits
 * for its structure's chunks to be entity-ticking before it counts a single tick, with no timeout of its own: a test
 * whose chunks never get there waits forever and the whole run spins. This fails such a test, naming it, after
 * {@link #MAX_CHUNK_WAIT_SECONDS} seconds of waiting, so the run ends and says which test it was. Wall-clock, not
 * ticks: the game-test server runs ticks as fast as it can, and chunk generation on the worker threads can take
 * thousands of those (1200 ticks passed in under a second at the start of a loaded run).
 *^/
@Mixin(GameTestInfo.class)
abstract class GameTestInfoChunkWaitMixin {

    @Unique
    private static final long MAX_CHUNK_WAIT_SECONDS = 120;

    @Shadow
    private boolean chunksLoaded;

    @Unique
    private long buildcraft$chunkWaitStart;

    @Shadow
    public abstract boolean isDone();

    @Shadow
    public abstract void fail(Component message);

    @Inject(method = "tick", at = @At("HEAD"))
    private void buildcraft$failAStuckChunkWait(GameTestRunner runner, CallbackInfo ci) {
        if (isDone() || chunksLoaded) {
            return;
        }
        long now = System.nanoTime();
        if (buildcraft$chunkWaitStart == 0) {
            buildcraft$chunkWaitStart = now;
        } else if (now - buildcraft$chunkWaitStart > MAX_CHUNK_WAIT_SECONDS * 1_000_000_000L) {
            fail(Component.literal("the test's structure chunks were not entity-ticking after " + MAX_CHUNK_WAIT_SECONDS
                    + " s, so it never started (Fabric run watchdog)"));
        }
    }
}
*///?}
