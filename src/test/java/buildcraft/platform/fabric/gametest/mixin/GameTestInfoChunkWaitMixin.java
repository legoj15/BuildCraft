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
 *
 * <p>Only the time between two of this test's ticks counts, and at most {@link #MAX_TICK_GAP_NANOS} of each gap. A
 * synchronous chunk load on the server thread (a structure placed on fresh ground, a test that loads a far chunk)
 * stops every test's ticks at once, and on a loaded machine one such load took 109 s: counted whole, it would fail
 * every test of the batch whose chunks had not been seen loaded before it, though nothing was wrong with them (#114).
 *^/
@Mixin(GameTestInfo.class)
abstract class GameTestInfoChunkWaitMixin {

    @Unique
    private static final long MAX_CHUNK_WAIT_SECONDS = 120;

    /^* The most one gap between two of this test's ticks adds to its wait: a longer gap is a blocked server thread. ^/
    @Unique
    private static final long MAX_TICK_GAP_NANOS = 1_000_000_000L;

    @Shadow
    private boolean chunksLoaded;

    /^* When this test last ticked while waiting, or 0 before its first tick. ^/
    @Unique
    private long buildcraft$lastWaitTick;

    @Unique
    private long buildcraft$waitedNanos;

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
        if (buildcraft$lastWaitTick != 0) {
            buildcraft$waitedNanos += Math.min(now - buildcraft$lastWaitTick, MAX_TICK_GAP_NANOS);
        }
        buildcraft$lastWaitTick = now;
        if (buildcraft$waitedNanos > MAX_CHUNK_WAIT_SECONDS * 1_000_000_000L) {
            fail(Component.literal("the test's structure chunks were not entity-ticking after " + MAX_CHUNK_WAIT_SECONDS
                    + " s of ticking, so it never started (Fabric run watchdog)"));
        }
    }
}
*///?}
