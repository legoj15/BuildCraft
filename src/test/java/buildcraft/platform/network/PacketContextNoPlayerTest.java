/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.network;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * The rule every clientbound handler leans on ({@link PacketContext#runOnMainWithPlayer}): work that needs the player
 * is handed to the main thread, the player is looked up there and not when the packet arrives, and with no player by
 * then (a client that left the level or lost the connection first) the work is skipped. It is neither run with a null
 * player nor allowed to throw. A real client cannot be made to lose its player between a packet's arrival and its
 * handler, so this is the only place the case is pinned.
 */
public class PacketContextNoPlayerTest {

    /** A context with no player, whose main thread is a queue the test drains by hand. */
    private static final class NoPlayer implements PacketContext {
        Runnable queued;
        boolean onMain;
        int asksOffMain;
        int asksOnMain;

        @Override
        public Player player() {
            if (onMain) {
                asksOnMain++;
            } else {
                asksOffMain++;
            }
            return null;
        }

        @Override
        public void runOnMain(Runnable work) {
            queued = work;
        }

        @Override
        public void reply(CustomPacketPayload payload) {
            throw new AssertionError("nothing here answers the sender");
        }

        void drain() {
            onMain = true;
            try {
                queued.run();
            } finally {
                onMain = false;
            }
        }
    }

    @Test
    public void workThatNeedsThePlayerIsSkippedWhenThereIsNoneOnTheMainThread() {
        NoPlayer context = new NoPlayer();
        boolean[] ran = { false };
        context.runOnMainWithPlayer(player -> ran[0] = true);

        Assertions.assertNotNull(context.queued,
            "the work must be handed to the main thread, not run where the packet arrived");
        Assertions.assertEquals(0, context.asksOffMain,
            "the player must not be looked up when the packet arrives: it can be gone by the time the work runs");
        Assertions.assertFalse(ran[0], "nothing may run before the main thread takes the work");

        context.drain();
        Assertions.assertEquals(1, context.asksOnMain, "the player is looked up once, on the main thread");
        Assertions.assertFalse(ran[0], "with no player on this side the work is skipped, not run with none");
    }
}
