package buildcraft.lib.misc;

import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

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
