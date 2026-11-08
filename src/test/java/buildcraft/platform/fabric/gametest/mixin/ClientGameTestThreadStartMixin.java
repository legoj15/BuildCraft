/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.gametest.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.10 {
import java.util.concurrent.TimeUnit;

import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl;
//?}

/^*
 * Closes a start-up race in Fabric API's client game-test runner (the game-test mod only, never the shipped jar; #119).
 * The client thread starts the test thread from its first tick without an overlay ({@code runTestThread}) and the test
 * thread registers itself on the runner's {@code Phaser} as its first act. The client thread then goes on to the end
 * of the frame, arrives at the phaser (alone, so the phase advances at once), sees {@code testThread} set and parks on
 * its task semaphore. If the test thread was not scheduled before that arrival, it registers one phase late, arrives
 * waiting for the client thread, and the client thread waits for a task the test thread never sends: the run hangs at
 * the title screen. A loaded machine makes the late start likely.
 *
 * <p>This holds the client thread at the end of {@code runTestThread} until the test thread has registered, so its
 * first phase is always the one the client thread arrives at next. The test thread's registration needs nothing from
 * the client thread, so the wait cannot itself deadlock; it gives up after {@code MAX_WAIT_SECONDS} seconds anyway.
 *
 * <p>1.21.1's Fabric API has no client game-test runner: there the class is an empty mixin on {@code Minecraft}.
 *^/
//? if >=1.21.10 {
@Mixin(value = ThreadingImpl.class, remap = false)
abstract class ClientGameTestThreadStartMixin {

    @Unique
    private static final long MAX_WAIT_SECONDS = 60;

    /^* The phaser's parties before the test thread was started: the client thread (and a server, if one runs). *^/
    @Unique
    private static int buildcraft$partiesBeforeStart;

    @Inject(method = "runTestThread", at = @At("HEAD"))
    private static void buildcraft$countParties(Runnable test, CallbackInfo ci) {
        buildcraft$partiesBeforeStart = ThreadingImpl.PHASER.getRegisteredParties();
    }

    @Inject(method = "runTestThread", at = @At("TAIL"))
    private static void buildcraft$awaitTestThreadRegistered(Runnable test, CallbackInfo ci) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(MAX_WAIT_SECONDS);
        while (ThreadingImpl.PHASER.getRegisteredParties() <= buildcraft$partiesBeforeStart) {
            Thread thread = ThreadingImpl.testThread;
            if (thread == null || !thread.isAlive() || System.nanoTime() > deadline) {
                return;
            }
            Thread.onSpinWait();
        }
    }
}
//?} else {
/^@Mixin(net.minecraft.client.Minecraft.class)
abstract class ClientGameTestThreadStartMixin {
}
^///?}
*///?}
