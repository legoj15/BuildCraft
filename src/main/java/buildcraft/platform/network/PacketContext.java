/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.network;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** What a {@link PacketHandler} knows about the payload it is handling: who it came from, and how to answer. */
public interface PacketContext {

    /**
     * The player on this side of the connection: the sender on the server, the local player on the client. Null only
     * on a client that has no local player at that moment (it left the level, or the connection closed, before the
     * work ran); never null on the server.
     */
    @Nullable
    Player player();

    /** Runs {@code work} on the receiving side's main thread (NeoForge and Forge {@code enqueueWork}, Fabric
     *  {@code execute}). */
    void runOnMain(Runnable work);

    /** Runs {@code work} on the receiving side's main thread with the player on this side, or not at all when
     *  there is none by then (a client that left the level or closed the connection first). */
    default void runOnMainWithPlayer(Consumer<Player> work) {
        runOnMain(() -> {
            Player player = player();
            if (player != null) {
                work.accept(player);
            }
        });
    }

    /** Sends {@code payload} back over the connection this one arrived on. */
    void reply(CustomPacketPayload payload);
}
