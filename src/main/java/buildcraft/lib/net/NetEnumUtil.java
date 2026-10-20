/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Enum-as-varint-ordinal wire helpers, replacing {@code FriendlyByteBuf#writeEnum} / {@code #readEnum}, which 26.3
 * deprecated. The bytes are identical to what those methods wrote on every node (a varint of {@link Enum#ordinal()}),
 * so mixed old/new traffic stays compatible. This is the plain one-varint form; the bit-packed
 * {@link PacketBufferBC#writeEnumValue} is a different wire format and not interchangeable.
 *
 * <p>Ordinals are positional: reordering or inserting enum constants changes the wire format, exactly as it did with
 * the vanilla methods.
 */
public final class NetEnumUtil {
    private NetEnumUtil() {}

    /** Writes {@code value} as a varint of its ordinal. */
    public static void write(FriendlyByteBuf buf, Enum<?> value) {
        buf.writeVarInt(value.ordinal());
    }

    /** Reads a varint ordinal and returns the matching constant of {@code clazz}. Throws
     *  {@link ArrayIndexOutOfBoundsException} on an out-of-range ordinal, as the vanilla method did. */
    public static <E extends Enum<E>> E read(FriendlyByteBuf buf, Class<E> clazz) {
        return clazz.getEnumConstants()[buf.readVarInt()];
    }
}
