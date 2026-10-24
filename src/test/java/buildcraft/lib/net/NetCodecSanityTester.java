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

    private static RegistryFriendlyByteBuf markerBuffer(int claimedCount, int actualPositions) {
        RegistryFriendlyByteBuf buf =
            new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        buf.writeBoolean(true);
        buf.writeBoolean(false);
        buf.writeShort(2);
        buf.writeVarInt(claimedCount);
        for (int i = 0; i < actualPositions; i++) {
            buf.writeBlockPos(new BlockPos(i, 64, -i));
        }
        return buf;
    }

    private static List<BlockPos> positions(int count) {
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(new BlockPos(i, 64, -i));
        }
        return positions;
    }

    private static RegistryFriendlyByteBuf encodeMarker(MessageMarker message) {
        RegistryFriendlyByteBuf buf =
            new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        MessageMarker.STREAM_CODEC.encode(buf, message);
        return buf;
    }

    @Test
    public void markerMessageAtTheBoundRoundTrips() {
        MessageMarker message = new MessageMarker(true, false, 2, positions(MessageMarker.MAX_POSITIONS));
        RegistryFriendlyByteBuf buf = encodeMarker(message);

        MessageMarker decoded = MessageMarker.STREAM_CODEC.decode(buf);
        Assertions.assertEquals(message.positions(), decoded.positions());
        Assertions.assertEquals(2, decoded.cacheId());
        Assertions.assertTrue(decoded.add());
        Assertions.assertFalse(decoded.connection());
        Assertions.assertEquals(0, buf.readableBytes());
    }

    @Test
    public void markerMessageOverTheBoundIsRefusedWhole() {
        Assertions.assertThrows(DecoderException.class, () -> MessageMarker.STREAM_CODEC.decode(
            markerBuffer(MessageMarker.MAX_POSITIONS + 1, MessageMarker.MAX_POSITIONS + 1)));
    }

    @Test
    public void markerMessageWithHugeOrNegativeCountIsRefusedBeforeSizing() {
        // A negative count used to reach `new ArrayList<>(count)`, which throws IllegalArgumentException: not the
        // DecoderException every other decoder reports, and not one the connection handles as a bad packet.
        Assertions.assertThrows(DecoderException.class,
            () -> MessageMarker.STREAM_CODEC.decode(markerBuffer(-1, 0)));
        Assertions.assertThrows(DecoderException.class,
            () -> MessageMarker.STREAM_CODEC.decode(markerBuffer(Integer.MIN_VALUE, 0)));
        Assertions.assertThrows(DecoderException.class,
            () -> MessageMarker.STREAM_CODEC.decode(markerBuffer(Integer.MAX_VALUE, 0)));
    }

    @Test
    public void aLongListOfMarkersIsSplitAndArrivesWholeAcrossMessages() {
        // The join sync sends every marker of a cache type in the dimension in one list. The count used to be a signed
        // short, so past 32767 it wrapped negative and broke the sync for every joining client.
        int total = 2 * MessageMarker.MAX_POSITIONS + 5;
        List<BlockPos> sent = positions(total);

        List<MessageMarker> messages = MessageMarker.split(true, false, 2, sent);

        List<BlockPos> received = new ArrayList<>();
        for (MessageMarker message : messages) {
            Assertions.assertTrue(message.positions().size() <= MessageMarker.MAX_POSITIONS);
            Assertions.assertTrue(message.add());
            Assertions.assertFalse(message.connection());
            Assertions.assertEquals(2, message.cacheId());
            RegistryFriendlyByteBuf buf = encodeMarker(message);
            received.addAll(MessageMarker.STREAM_CODEC.decode(buf).positions());
            Assertions.assertEquals(0, buf.readableBytes());
        }
        Assertions.assertEquals(sent, received, "every position, in order, none twice");
    }

    @Test
    public void aListThatFitsIsOneMessage() {
        List<BlockPos> sent = positions(MessageMarker.MAX_POSITIONS);
        List<MessageMarker> messages = MessageMarker.split(false, false, 0, sent);
        Assertions.assertEquals(1, messages.size());
        Assertions.assertEquals(sent, messages.get(0).positions());
    }

    @Test
    public void aConnectionIsNeverCutAcrossMessages() {
        // A connection add names ONE connection through all its positions: cut in two, the receiver would build two,
        // and the second would tear the first down. One that cannot fit is not sent (and not left to fail the encode,
        // which disconnects the player); a removal handles its positions one at a time, so it may be cut.
        List<BlockPos> tooLong = positions(MessageMarker.MAX_POSITIONS + 1);
        Assertions.assertTrue(MessageMarker.split(true, true, 0, tooLong).isEmpty());
        Assertions.assertEquals(2, MessageMarker.split(false, true, 0, tooLong).size());
        Assertions.assertEquals(1, MessageMarker.split(true, true, 0, positions(MessageMarker.MAX_POSITIONS)).size());
    }

    @Test
    public void encodingAnOversizeMarkerMessageIsRefusedNotWrapped() {
        MessageMarker oversize = new MessageMarker(true, false, 0, positions(MessageMarker.MAX_POSITIONS + 1));
        Assertions.assertThrows(IllegalStateException.class, () -> encodeMarker(oversize));
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
