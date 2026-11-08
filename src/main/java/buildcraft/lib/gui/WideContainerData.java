/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Menu data that survives vanilla's 16-bit menu-data channel.
 * <p>
 * {@code ClientboundContainerSetDataPacket} carries each data slot as a {@code short}. NeoForge replaces it with its
 * full-int {@code AdvancedContainerSetDataPayload} for a client that has that channel; Fabric does not, so on Fabric
 * a plain int slot loses everything above bit 15 — an engine's hi/lo-split power read back as a few thousandths of
 * an MJ and its float-bits heat as 0 °C. This class spreads each value over as many 16-bit slots as it needs (int and
 * float: 2, long: 4, boolean: 1), each slot holding a value already inside short range, so both loaders send it
 * through the vanilla packet unchanged.
 * <p>
 * Declare the same values in the same order on both sides (the menu constructor runs on both), then
 * {@link Builder#build(boolean)} with whether this is the server copy. The server copy reads the sources live; the
 * client copy stores what the packets deliver. The returned handles read either copy, so a menu's getters work on
 * both sides. Add the result with {@code addDataSlots}.
 * <p>
 * Vanilla sends only the slots that changed, which is enough: an unchanged piece already holds its current value on
 * the client. Pieces arrive one packet each, so between two packets of one tick's batch a value (and a client sink)
 * can briefly see a mix of old and new pieces; the batch completes before the next frame.
 */
public final class WideContainerData implements ContainerData {
    private static final int BITS = 16;
    private static final long PIECE_MASK = 0xFFFFL;

    private final Channel[] channelOfSlot;
    private final int[] pieceOfSlot;
    /** True: reads the channels' live sources. False: values live in {@link #clientSlots}. */
    private final boolean server;
    private final int[] clientSlots;

    private WideContainerData(List<Channel> channels, int slotCount, boolean server) {
        this.server = server;
        this.channelOfSlot = new Channel[slotCount];
        this.pieceOfSlot = new int[slotCount];
        this.clientSlots = new int[slotCount];
        for (Channel channel : channels) {
            for (int p = 0; p < channel.width; p++) {
                channelOfSlot[channel.base + p] = channel;
                pieceOfSlot[channel.base + p] = p;
            }
            channel.owner = this;
            if (!server) {
                writePieces(channel, channel.clientInitial);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int get(int index) {
        if (!server) {
            return clientSlots[index];
        }
        Channel channel = channelOfSlot[index];
        return piece(channel.source.getAsLong(), pieceOfSlot[index]);
    }

    @Override
    public void set(int index, int value) {
        if (server) {
            return; // the server copy is read-only; values flow server -> client
        }
        clientSlots[index] = value;
        Channel channel = channelOfSlot[index];
        if (channel.clientSink != null) {
            channel.clientSink.accept(read(channel));
        }
    }

    @Override
    public int getCount() {
        return clientSlots.length;
    }

    /** One 16-bit piece of {@code value}, sign-extended so it is always inside short range. */
    private static int piece(long value, int piece) {
        return (short) (value >>> (BITS * piece));
    }

    private void writePieces(Channel channel, long value) {
        for (int p = 0; p < channel.width; p++) {
            clientSlots[channel.base + p] = piece(value, p);
        }
    }

    private long read(Channel channel) {
        if (server) {
            return channel.source.getAsLong();
        }
        long value = 0;
        for (int p = 0; p < channel.width; p++) {
            value |= (clientSlots[channel.base + p] & PIECE_MASK) << (BITS * p);
        }
        // Sign-extend from the channel's width (an int channel carries a negative int as 32 bits).
        int shift = 64 - BITS * channel.width;
        return (value << shift) >> shift;
    }

    private static final class Channel {
        final int base;
        final int width;
        final LongSupplier source;
        final long clientInitial;
        @Nullable
        final LongConsumer clientSink;
        WideContainerData owner;

        Channel(int base, int width, LongSupplier source, long clientInitial, @Nullable LongConsumer clientSink) {
            this.base = base;
            this.width = width;
            this.source = source;
            this.clientInitial = clientInitial;
            this.clientSink = clientSink;
        }

        long read() {
            if (owner == null) {
                throw new IllegalStateException("WideContainerData value read before build()");
            }
            return owner.read(this);
        }
    }

    public static final class Builder {
        private final List<Channel> channels = new ArrayList<>();
        private int slots;
        private boolean built;

        private Builder() {}

        private Channel add(int width, LongSupplier source, long clientInitial, @Nullable LongConsumer sink) {
            Channel channel = new Channel(slots, width, source, clientInitial, sink);
            channels.add(channel);
            slots += width;
            return channel;
        }

        public IntValue addInt(IntSupplier source) {
            Channel channel = add(2, source::getAsInt, 0, null);
            return () -> (int) channel.read();
        }

        public LongValue addLong(LongSupplier source) {
            return addLong(source, null);
        }

        /** @param clientSink called on the client copy with the reassembled value each time one of its slots arrives,
         *                   for a menu that mirrors the value into its client-side tile. */
        public LongValue addLong(LongSupplier source, @Nullable LongConsumer clientSink) {
            Channel channel = add(4, source, 0, clientSink);
            return channel::read;
        }

        /** @param clientInitial what the client copy reads before the first sync (e.g. an engine's resting heat). */
        public FloatValue addFloat(DoubleSupplier source, float clientInitial) {
            Channel channel = add(2, () -> Float.floatToIntBits((float) source.getAsDouble()),
                    Float.floatToIntBits(clientInitial), null);
            return () -> Float.intBitsToFloat((int) channel.read());
        }

        public BooleanValue addBoolean(BooleanSupplier source) {
            Channel channel = add(1, () -> source.getAsBoolean() ? 1 : 0, 0, null);
            return () -> channel.read() != 0;
        }

        public WideContainerData build(boolean serverSide) {
            // The handles bind to the copy built here; a second build would silently re-point them.
            if (built) {
                throw new IllegalStateException("WideContainerData.Builder is single-use");
            }
            built = true;
            return new WideContainerData(channels, slots, serverSide);
        }

        /** {@link #build(boolean)} for a tile-backed menu: the server copy iff {@code tile} sits in a server level.
         *  The client copy covers a missing tile too (the client resolver may hand back {@code null}). */
        public WideContainerData buildFor(@Nullable BlockEntity tile) {
            Level level = tile == null ? null : tile.getLevel();
            return build(level != null && !level.isClientSide());
        }
    }

    @FunctionalInterface
    public interface IntValue {
        int get();
    }

    @FunctionalInterface
    public interface LongValue {
        long get();
    }

    @FunctionalInterface
    public interface FloatValue {
        float get();
    }

    @FunctionalInterface
    public interface BooleanValue {
        boolean get();
    }
}
