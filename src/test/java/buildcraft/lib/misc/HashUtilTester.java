package buildcraft.lib.misc;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import buildcraft.lib.misc.HashUtil;

public class HashUtilTester {

    @Test
    public void testHashToString() {
        byte[] hash = { 0, 1, 5, 9, (byte) 0xff, (byte) 0xbc };
        String expected = "00010509ffbc";
        String str = HashUtil.convertHashToString(hash);
        Assertions.assertEquals(expected, str);
    }

    @Test
    public void testComputeHashIsSha256OfTheWrittenBytes() throws Exception {
        // Snapshot keys are stored hashes: the digest must stay byte-identical to a SHA-256 over the tag as NbtIo
        // writes it, whatever way it is fed to the digest.
        CompoundTag tag = new CompoundTag();
        tag.putString("text", "中".repeat(5_000));
        tag.putByteArray("bytes", new byte[100_000]);
        tag.putIntArray("ints", new int[] {1, 2, 3, Integer.MIN_VALUE});

        ByteArrayOutputStream written = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(written));
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(written.toByteArray());

        Assertions.assertArrayEquals(expected, HashUtil.computeHash(tag));
        Assertions.assertEquals(HashUtil.HASH_BYTES, expected.length);
    }

    @Test
    public void testComputeHash() {
        CompoundTag tag1 = new CompoundTag();
        tag1.putString("test", "value");
        tag1.putInt("num", 42);

        CompoundTag tag2 = new CompoundTag();
        tag2.putString("test", "value");
        tag2.putInt("num", 42);

        byte[] hash1 = HashUtil.computeHash(tag1);
        byte[] hash2 = HashUtil.computeHash(tag2);

        Assertions.assertArrayEquals(hash1, hash2, "Identical NBT should yield identical hash");

        tag2.putInt("num", 43);
        byte[] hash3 = HashUtil.computeHash(tag2);

        Assertions.assertFalse(Arrays.equals(hash1, hash3), "Different NBT should yield different hash");
    }
}
