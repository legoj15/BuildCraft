/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import io.netty.handler.codec.DecoderException;

/**
 * Bounds for values read from a packet on the decoding side. Everything here is pure so it can be unit-tested
 * directly; the call sites pass the bound the legitimate sending side already keeps to.
 */
public final class NetCodecSanity {
    private NetCodecSanity() {}

    /**
     * Forces a wire-supplied scalar (a duration, say, handed to something that materialises one slot per tick) into
     * {@code [0, max]}. For the element count of a list use {@link #checkedCount} instead: a count that has to be
     * clamped did not come from this game's sender, and decoding only a prefix of it leaves the rest of the packet
     * unread.
     *
     * @param value the value as read off the wire, of any sign
     * @param max   the largest value the legitimate senders produce
     * @return {@code value} when it already lies in {@code [0, max]}; {@code 0} or {@code max} otherwise
     */
    public static int clampNonNegative(int value, int max) {
        if (max < 0) {
            throw new IllegalArgumentException("max must not be negative: " + max);
        }
        return Math.min(Math.max(value, 0), max);
    }

    /**
     * Validates a wire-supplied element count before anything is sized or looped from it. A decoder pre-sizing a
     * collection must never trust the raw VarInt: a few bytes on the wire can name billions, and the resulting
     * {@code OutOfMemoryError} is an {@code Error}, which the callers' {@code catch (Exception)} does not stop.
     *
     * @param count the count as read off the wire, of any sign
     * @param max   the largest count the legitimate sender produces
     * @param what  what is being counted, for the failure message
     * @return {@code count}, which is in {@code [0, max]}
     * @throws DecoderException if {@code count} is negative or above {@code max}
     */
    public static int checkedCount(int count, int max, String what) {
        if (max < 0) {
            throw new IllegalArgumentException("max must not be negative: " + max);
        }
        if (count < 0 || count > max) {
            throw new DecoderException(what + " count " + count + " is outside [0, " + max + "]");
        }
        return count;
    }
}
