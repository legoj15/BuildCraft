/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** What a {@link PacketHandler} knows about the payload it is handling: who it came from, and how to answer. */
public interface PacketContext {

    /** The player on this side of the connection: the sender on the server, the local player on the client. */
    Player player();

    /** Runs {@code work} on the receiving side's main thread (NeoForge {@code enqueueWork}, Fabric {@code execute}). */
    void runOnMain(Runnable work);

    /** Sends {@code payload} back over the connection this one arrived on. */
    void reply(CustomPacketPayload payload);
}
