/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.nbt;

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

    public static CompoundTag expand(InputStream stream) throws IOException {
        return NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
    }

    /**
     * Like {@link #expand(InputStream)}, but reading more than {@code maxBytes} of NBT (vanilla's heap estimate,
     * counted as tags are read) fails instead of allocating it. For data from outside the game, where a few
     * kilobytes of compressed zeros can stand for gigabytes.
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

    public static void squishVanilla(CompoundTag nbt, OutputStream stream) throws IOException {
        NbtIo.writeCompressed(nbt, stream);
    }
}
