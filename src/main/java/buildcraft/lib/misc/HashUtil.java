/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

public class HashUtil {

    /** Length in bytes of every hash {@link #computeHash} returns (SHA-256). */
    public static final int HASH_BYTES = 32;

    /**
     * Compute a SHA-256 hash of the given CompoundTag's serialized bytes. The bytes are fed to the digest as they are
     * written and never held, so hashing a large snapshot (up to the library's upload ceiling) costs a buffer, not a
     * second copy of it.
     */
    public static byte[] computeHash(CompoundTag nbt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // The buffer batches the many small primitive writes into few digest updates.
            try (DataOutputStream dos = new DataOutputStream(
                    new BufferedOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest), 8192))) {
                NbtIo.write(nbt, dos);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Failed to compute hash", e);
        }
    }

    /** Convert a byte[] hash to a lowercase hex string. */
    public static String convertHashToString(byte[] hash) {
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
