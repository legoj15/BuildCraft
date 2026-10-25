/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.net.PacketBufferBC;
import buildcraft.robotics.client.zone.ZonePlannerMapChunk;

/**
 * Pins the Zone Planner's far-map region store: what a server records about a chunk must come back the same from its
 * file, a chunk never recorded must stay unknown (the client renders those as gaps, not as flat ground at y=0), and a
 * file this build cannot read must not hand back garbage terrain.
 */
public class ZoneSurveyRegionTest {

    private static short[] heights(int base) {
        short[] h = new short[ZoneSurveyRegion.SAMPLES];
        for (int i = 0; i < h.length; i++) {
            h[i] = (short) (base + i);
        }
        h[3] = ZoneSurveyRegion.NO_DATA;
        return h;
    }

    private static byte[] colours(int base) {
        byte[] c = new byte[ZoneSurveyRegion.SAMPLES];
        for (int i = 0; i < c.length; i++) {
            c[i] = (byte) ((base + i) & 63);
        }
        return c;
    }

    private static ZoneSurveyRegion roundTrip(ZoneSurveyRegion region) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        region.write(new DataOutputStream(bytes));
        return ZoneSurveyRegion.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    @Test
    void recordedChunksSurviveTheFileAndUnrecordedOnesStayUnknown() throws IOException {
        ZoneSurveyRegion region = new ZoneSurveyRegion();
        // Negative chunk coordinates land in the region by their low five bits, as world chunk -1 is local 31.
        int a = ZoneSurveyRegion.chunkIndex(-1, 5);
        int b = ZoneSurveyRegion.chunkIndex(32, -32);
        region.put(a, heights(-60), colours(1));
        region.put(b, heights(300), colours(40));

        ZoneSurveyRegion back = roundTrip(region);
        Assertions.assertTrue(back.has(a));
        Assertions.assertTrue(back.has(b));
        Assertions.assertFalse(back.has(ZoneSurveyRegion.chunkIndex(1, 1)));
        for (int s = 0; s < ZoneSurveyRegion.SAMPLES; s++) {
            Assertions.assertEquals(region.height(a, s), back.height(a, s));
            Assertions.assertEquals(region.colour(a, s), back.colour(a, s));
            Assertions.assertEquals(region.height(b, s), back.height(b, s));
            Assertions.assertEquals(region.colour(b, s), back.colour(b, s));
        }
        Assertions.assertEquals(ZoneSurveyRegion.NO_DATA, back.height(a, 3));
        Assertions.assertEquals(-60, back.height(a, 0));
        Assertions.assertEquals(55, back.colour(b, 15));
    }

    @Test
    void unknownFileVersionReadsAsAnEmptyRegion() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(ZoneSurveyRegion.FILE_VERSION + 1);
        out.write(new byte[4096]);
        ZoneSurveyRegion back = ZoneSurveyRegion.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        for (int i = 0; i < ZoneSurveyRegion.CHUNKS; i++) {
            Assertions.assertFalse(back.has(i));
        }
    }

    @Test
    void wireMessageRestoresNegativeChunkCoordinates() {
        // Region -1 holds chunks -32..-1; local index (31, 0) is world chunk (-1, -32).
        PacketBufferBC buf = new PacketBufferBC(Unpooled.buffer());
        buf.writeInt(-1);
        buf.writeInt(-1);
        buf.writeVarInt(1);
        buf.writeShort(ZoneSurveyRegion.chunkIndex(-1, -32));
        short[] h = heights(10);
        byte[] c = colours(5);
        for (short v : h) {
            buf.writeShort(v);
        }
        buf.writeBytes(c);
        List<int[]> got = new ArrayList<>();
        ZoneSurveyMap.decode(buf, (cx, cz, heights, colourIds) -> {
            got.add(new int[] {cx, cz});
            Assertions.assertArrayEquals(h, heights);
            Assertions.assertArrayEquals(c, colourIds);
        });
        buf.release();
        Assertions.assertEquals(1, got.size());
        Assertions.assertArrayEquals(new int[] {-1, -32}, got.get(0));
    }

    @Test
    void clientFarChunkReadsEachColumnFromTheSampleTheServerFiledItUnder() {
        // The server files samples by ZoneSurveyRegion.sampleIndex; the client's coarse chunk must read every column
        // back from that same sample, or the far map draws shifted or transposed terrain.
        short[] h = new short[ZoneSurveyRegion.SAMPLES];
        for (int s = 0; s < h.length; s++) {
            h[s] = (short) (s * 3);
        }
        ZonePlannerMapChunk chunk = new ZonePlannerMapChunk(h, new byte[ZoneSurveyRegion.SAMPLES]);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Assertions.assertEquals(h[ZoneSurveyRegion.sampleIndex(x, z)], chunk.getSurfaceY(x, z), x + "," + z);
            }
        }
    }

    @Test
    void sampleCellsCoverTheirFourByFourBlocks() {
        // A chunk's samples are a 4x4 grid of 4x4-block cells, the lowest corner of each cell sampled; every column
        // of a cell reads the cell's sample.
        Assertions.assertEquals(ZoneSurveyRegion.sampleIndex(0, 0), ZoneSurveyRegion.sampleIndex(3, 3));
        Assertions.assertEquals(ZoneSurveyRegion.sampleIndex(4, 8), ZoneSurveyRegion.sampleIndex(7, 11));
        Assertions.assertNotEquals(ZoneSurveyRegion.sampleIndex(3, 0), ZoneSurveyRegion.sampleIndex(4, 0));
        Assertions.assertNotEquals(ZoneSurveyRegion.sampleIndex(0, 3), ZoneSurveyRegion.sampleIndex(0, 4));
        Assertions.assertEquals(ZoneSurveyRegion.SAMPLES - 1, ZoneSurveyRegion.sampleIndex(15, 15));
    }
}
