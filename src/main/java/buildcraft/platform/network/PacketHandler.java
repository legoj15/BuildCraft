/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Handles one received payload of type {@code T}. Registered through {@link BCNetwork}. */
@FunctionalInterface
public interface PacketHandler<T extends CustomPacketPayload> {
    void handle(T payload, PacketContext ctx);
}
