/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

/**
 * Clamps for values read from a packet on the decoding side. Everything here is pure so it can be unit-tested
 * directly; the call sites pass the bound the legitimate sending side already keeps to.
 */
public final class NetCodecSanity {
    private NetCodecSanity() {}

    /**
     * Forces a wire-supplied count or duration into {@code [0, max]}. A decoder pre-sizing a collection (or, worse,
     * handing the value to something that materialises one slot per tick) must never trust the raw VarInt: a few
     * bytes on the wire can name billions.
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
}
