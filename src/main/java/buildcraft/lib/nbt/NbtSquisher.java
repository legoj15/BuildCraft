/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;

/**
 * Simple NbtSquisher implementation that delegates to Minecraft's NbtIo.
 * In 1.12.2 this had custom compression formats, but for now we use vanilla NBT I/O.
 */
public class NbtSquisher {

    /**
     * Reads compressed NBT, failing instead of allocating once it has read more than {@code maxBytes} of NBT
     * (vanilla's heap estimate, counted as tags are read). There is deliberately no unbounded overload: the data comes
     * from outside the game, where a few kilobytes of compressed zeros can stand for gigabytes.
     *
     * @throws IOException if the data is unreadable or would unpack past {@code maxBytes}
     */
    public static CompoundTag expand(InputStream stream, long maxBytes) throws IOException {
        try {
            return NbtIo.readCompressed(stream, NbtAccounter.create(maxBytes));
        } catch (RuntimeException e) {
            // The accounter reports an oversize read as an unchecked exception.
            throw new IOException("Unreadable or oversized NBT: " + e.getMessage(), e);
        }
    }

    /**
     * Check that {@code nbt}, once written out, would read back under {@link #expand(InputStream, long)} with
     * {@code maxBytes}. It is written and read again, so the check is the reader's own count and not an estimate of
     * it (vanilla's {@code sizeInBytes} charges strings and compound keys differently from the read).
     * <p>
     * The bytes written for the check are capped, at {@link #writtenCap}: a stream longer than that cannot read back
     * under {@code maxBytes}, so the write is abandoned at the cap instead of buffering a tag of any size (the tag is
     * built from an upload, and this runs on the server thread). Within the cap they are held in chunks, with no
     * doubling and no copy, so an accepted tag costs about its own written size in transient memory.
     *
     * @throws IOException if it would not
     */
    public static void requireExpandsWithin(CompoundTag nbt, long maxBytes) throws IOException {
        CappedByteBuffer bytes = new CappedByteBuffer(writtenCap(maxBytes));
        try {
            NbtIo.write(nbt, new DataOutputStream(bytes));
        } catch (CappedByteBuffer.Overflow e) {
            // Reported against the caller's limit: the write cap is derived from it, not a figure anyone set.
            throw new IOException("NBT cannot read back within " + maxBytes + " bytes: it writes too much", e);
        } catch (IOException e) {
            // A string too long to write at all (NBT strings are 64 KiB at most).
            throw new IOException("NBT cannot read back within " + maxBytes + " bytes: " + e.getMessage(), e);
        }
        try {
            // readCompressed reads the decompressed stream with this same call, so the count is the same.
            NbtIo.read(new DataInputStream(bytes.openRead()), NbtAccounter.create(maxBytes));
        } catch (RuntimeException e) {
            throw new IOException("NBT would read back past " + maxBytes + " bytes: " + e.getMessage(), e);
        }
    }

    /**
     * The most bytes a tag may write and still read back under {@code maxBytes}: a stream longer than this has no
     * chance. The reader charges at least two thirds of a byte for every byte written (vanilla: two per string
     * character against at most three written; NeoForge: the written length itself; everything else is charged an
     * object header or more), so the stream of a tag it accepts is at most three halves of the limit. The slack costs
     * nothing when the tag is accepted, whose stream is smaller still, and is what keeps the cap from ever refusing
     * a tag the reader would take; {@code NbtSquisherTester} measures the ratio on every node. A negative limit
     * admits nothing, so its cap is zero (the reader refuses every tag under it).
     */
    static long writtenCap(long maxBytes) {
        if (maxBytes <= 0) {
            return 0;
        }
        long slack = maxBytes / 2;
        return maxBytes > Long.MAX_VALUE - slack ? Long.MAX_VALUE : maxBytes + slack;
    }

    public static void squishVanilla(CompoundTag nbt, OutputStream stream) throws IOException {
        NbtIo.writeCompressed(nbt, stream);
    }
}
