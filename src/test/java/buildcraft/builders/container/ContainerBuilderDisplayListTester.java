/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.container;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * The client side of the Builder's {@code NET_DISPLAY_LIST} container message. The count comes straight off the wire
 * from the server, so it must be bounded before the list is sized: a raw VarInt can name an allocation that throws
 * {@code OutOfMemoryError}, which the container-message handler's {@code catch (Exception)} does not catch.
 */
public class ContainerBuilderDisplayListTester {

    /** A message claiming {@code claimed} stacks and carrying {@code actual} of them, all empty (a single 0 each). */
    private static RegistryFriendlyByteBuf message(int claimed, int actual) {
        RegistryFriendlyByteBuf buf =
            new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        buf.writeVarInt(claimed);
        for (int i = 0; i < actual; i++) {
            buf.writeVarInt(0);
        }
        return buf;
    }

    @Test
    public void aListThePanelCanShowIsReadWhole() {
        int max = ContainerBuilder.MAX_DISPLAY_ENTRIES;
        RegistryFriendlyByteBuf buf = message(max, max);

        // Empty stacks are dropped by the reader; the point is that all of them were consumed.
        Assertions.assertTrue(ContainerBuilder.readDisplayList(buf).isEmpty());
        Assertions.assertEquals(0, buf.readableBytes());
    }

    @Test
    public void aListLongerThanThePanelIsRefused() {
        int tooMany = ContainerBuilder.MAX_DISPLAY_ENTRIES + 1;
        Assertions.assertThrows(DecoderException.class,
            () -> ContainerBuilder.readDisplayList(message(tooMany, tooMany)));
    }

    @Test
    public void aHugeOrNegativeCountIsRefusedBeforeAnythingIsSized() {
        Assertions.assertThrows(DecoderException.class,
            () -> ContainerBuilder.readDisplayList(message(Integer.MAX_VALUE, 0)));
        Assertions.assertThrows(DecoderException.class,
            () -> ContainerBuilder.readDisplayList(message(-1, 0)));
    }
}
