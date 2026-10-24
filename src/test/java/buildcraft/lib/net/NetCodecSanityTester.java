/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

import net.neoforged.neoforge.network.connection.ConnectionType;

import buildcraft.builders.snapshot.ArchitectScanPayload;

/**
 * The decoders that pre-size or loop over a list from a wire VarInt check it first: a few bytes on the wire must never
 * name an allocation of gigabytes, and an over-bound count must fail with a clear {@link DecoderException} rather than
 * decode a prefix and leave the rest unread. The scalar clamp ({@link NetCodecSanity#clampNonNegative}, for values such
 * as a pipe item's time-to-destination) is pinned here too.
 */
public class NetCodecSanityTester {

    private void assertClamp(int value, int max, int expected) {
        Assertions.assertEquals(expected, NetCodecSanity.clampNonNegative(value, max),
            "clampNonNegative(" + value + ", " + max + ")");
    }

    @Test
    public void inRangeValuesPassThrough() {
        assertClamp(0, 10, 0);
        assertClamp(5, 10, 5);
        assertClamp(10, 10, 10);
        assertClamp(1, Short.MAX_VALUE, 1);
        assertClamp(12345, Short.MAX_VALUE, 12345);
    }

    @Test
    public void negativesFoldToZero() {
        assertClamp(-1, 10, 0);
        assertClamp(-1000000, 10, 0);
        assertClamp(Integer.MIN_VALUE, 10, 0);
        assertClamp(Integer.MIN_VALUE, Short.MAX_VALUE, 0);
    }

    @Test
    public void oversizedValuesStopAtTheBound() {
        assertClamp(11, 10, 10);
        assertClamp(Integer.MAX_VALUE, 10, 10);
        // The pipe-item arrival bound: the sending side (PipeFlowItems.sendItemDataToClient) caps at Short.MAX_VALUE,
        // so anything above it off the wire is not a value this game sent.
        assertClamp(Short.MAX_VALUE + 1, Short.MAX_VALUE, Short.MAX_VALUE);
        assertClamp(Integer.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE);
    }

    @Test
    public void countsOutOfRangeAreRefusedNotClamped() {
        Assertions.assertThrows(DecoderException.class, () -> NetCodecSanity.checkedCount(11, 10, "Widget"));
        Assertions.assertThrows(DecoderException.class, () -> NetCodecSanity.checkedCount(-1, 10, "Widget"));
        Assertions.assertThrows(DecoderException.class, () -> NetCodecSanity.checkedCount(Integer.MIN_VALUE, 10, "Widget"));
        Assertions.assertThrows(DecoderException.class, () -> NetCodecSanity.checkedCount(Integer.MAX_VALUE, 10, "Widget"));
    }

    private static FriendlyByteBuf scanBuffer(int claimedCount, int actualPositions) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(claimedCount);
        for (int i = 0; i < actualPositions; i++) {
            buf.writeBlockPos(new BlockPos(i, 64, -i));
        }
        return buf;
    }

    @Test
    public void scanPayloadAtTheBoundRoundTrips() {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < ArchitectScanPayload.MAX_POSITIONS; i++) {
            positions.add(new BlockPos(i, 64, -i));
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ArchitectScanPayload.STREAM_CODEC.encode(buf, new ArchitectScanPayload(positions));

        ArchitectScanPayload decoded = ArchitectScanPayload.STREAM_CODEC.decode(buf);
        Assertions.assertEquals(positions, decoded.positions());
        Assertions.assertEquals(0, buf.readableBytes(), "a full batch is read to the last byte");
    }

    @Test
    public void scanPayloadOverTheBoundIsRefusedWhole() {
        // The bytes are all there, so a decoder that clamps the count would return MAX_POSITIONS entries and leave one
        // position unread (which the connection reports as a confusing "extra bytes" failure).
        FriendlyByteBuf buf = scanBuffer(ArchitectScanPayload.MAX_POSITIONS + 1, ArchitectScanPayload.MAX_POSITIONS + 1);
        Assertions.assertThrows(DecoderException.class, () -> ArchitectScanPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    public void scanPayloadWithHugeOrNegativeCountIsRefusedBeforeSizing() {
        Assertions.assertThrows(DecoderException.class,
            () -> ArchitectScanPayload.STREAM_CODEC.decode(scanBuffer(Integer.MAX_VALUE, 0)));
        Assertions.assertThrows(DecoderException.class,
            () -> ArchitectScanPayload.STREAM_CODEC.decode(scanBuffer(-1, 0)));
    }

    private static RegistryFriendlyByteBuf debugBuffer(int leftClaimed, int leftActual, int rightClaimed, int rightActual) {
        RegistryFriendlyByteBuf buf =
            new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        buf.writeVarInt(leftClaimed);
        for (int i = 0; i < leftActual; i++) {
            buf.writeUtf("l" + i);
        }
        buf.writeVarInt(rightClaimed);
        for (int i = 0; i < rightActual; i++) {
            buf.writeUtf("r" + i);
        }
        return buf;
    }

    @Test
    public void debugResponseAtTheBoundRoundTrips() {
        int max = MessageDebugResponse.MAX_LINES_PER_SIDE;
        RegistryFriendlyByteBuf buf = debugBuffer(max, max, max, max);

        MessageDebugResponse decoded = MessageDebugResponse.STREAM_CODEC.decode(buf);
        Assertions.assertEquals(max, decoded.left().size());
        Assertions.assertEquals(max, decoded.right().size());
        Assertions.assertEquals("l" + (max - 1), decoded.left().get(max - 1));
        Assertions.assertEquals("r" + (max - 1), decoded.right().get(max - 1));
        Assertions.assertEquals(0, buf.readableBytes());
    }

    @Test
    public void debugResponseOverTheBoundOnEitherSideIsRefusedWhole() {
        int max = MessageDebugResponse.MAX_LINES_PER_SIDE;
        Assertions.assertThrows(DecoderException.class,
            () -> MessageDebugResponse.STREAM_CODEC.decode(debugBuffer(max + 1, max + 1, 0, 0)),
            "left side");
        Assertions.assertThrows(DecoderException.class,
            () -> MessageDebugResponse.STREAM_CODEC.decode(debugBuffer(0, 0, max + 1, max + 1)),
            "right side");
        Assertions.assertThrows(DecoderException.class,
            () -> MessageDebugResponse.STREAM_CODEC.decode(debugBuffer(-1, 0, 0, 0)),
            "negative count");
    }
}
