/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.gametest.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.10 && <26.1 {
/^import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;

import com.google.common.collect.ConcurrentHashMultiset;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.fabricmc.fabric.impl.client.gametest.threading.NetworkSynchronizer;
^///?}

/^*
 * Closes a lost-wakeup race in Fabric API's client game-test runner that failed about one run in ten with
 * {@code Network synchronizer in invalid state} (the game-test mod only, never the shipped jar; #174).
 * <p>
 * {@code NetworkSynchronizer.waitForPacketHandlers} runs at the end of every client and server tick: while a packet is
 * in flight and no handler is queued it parks on {@code morePacketsCondition} for up to ten seconds. It decides to park
 * from {@code inFlightPackets.get() > 0}, read <em>before</em> it takes {@code morePacketsLock}, and the Netty thread
 * that handles the packet decrements the counter and calls {@code signal()} under that lock. A packet that finishes
 * between the read and the {@code await} leaves nobody waiting to hear the signal: the wait runs its full ten seconds
 * with the counter already at zero, times out, marks the synchronizer invalid and crashes the server (or client)
 * thread. The window is a few microseconds, but a player in the world sends a packet nearly every tick, so it opens
 * often enough to matter. A crash ten seconds after a hang with the counter at zero and nothing queued is this race;
 * a counter stuck above zero would be a packet that never arrived.
 * <p>
 * This reads the same condition again under the lock, right before the {@code await}: the signalling thread cannot
 * signal until the {@code await} has released the lock, so a packet that finished earlier is seen by the second read
 * and one that finishes later wakes the wait. Nothing else about the synchronizer changes; the ten seconds still apply
 * to a packet that really is lost.
 * <p>
 * Patched on 1.21.10 and 1.21.11 only. Fabric API for 26.1 still ships the class but leaves it off unless
 * {@code -Dfabric.client.gametest.disableNetworkSynchronizer=false} (fabric-api PR 5281), and 26.2 removed it (PR 5484);
 * 1.21.1 has no client game-test runner (the shim in {@code gametest} stands in). On those lines this is an empty mixin
 * on {@code Minecraft}. Upstream has since dropped the synchronizer, so the race was not reported there (#178).
 *^/
//? if >=1.21.10 && <26.1 {
/^@Mixin(value = NetworkSynchronizer.class, remap = false)
abstract class NetworkSynchronizerWakeupMixin {

    @Shadow
    @Final
    private AtomicInteger inFlightPackets;

    @Shadow
    @Final
    private ConcurrentHashMultiset<?> mainThreadPacketHandlers;

    // The caller holds morePacketsLock here. Answering true ("signalled in time") sends it back round its loop, which
    // reads the counter again and carries on when it is zero.
    @WrapOperation(method = "waitForPacketHandlers",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/Condition;await(JLjava/util/concurrent/TimeUnit;)Z"))
    private boolean buildcraft$awaitOnlyWhileStillUnsettled(Condition condition, long time, TimeUnit unit,
            Operation<Boolean> original) {
        if (inFlightPackets.get() <= 0 || !mainThreadPacketHandlers.isEmpty()) {
            return true;
        }
        return original.call(condition, time, unit);
    }
}
^///?} else {
@Mixin(net.minecraft.client.Minecraft.class)
abstract class NetworkSynchronizerWakeupMixin {
}
//?}
*///?}
