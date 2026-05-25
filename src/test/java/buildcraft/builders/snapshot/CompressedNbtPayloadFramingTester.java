/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;

/**
 * Pins the byte-count symmetry of {@link ArchitectPreviewResponsePayload} and
 * {@link SnapshotResponsePayload}. Both ship the snapshot as compressed NBT via
 * {@code NbtIo.writeCompressed}/{@code readCompressed} on a raw {@code ByteBuf} — and that
 * pair does NOT guarantee the reader consumes exactly the bytes the writer produced
 * (GZIPInputStream's internal 512-byte prefetch can leave the underlying stream a few bytes
 * ahead or behind the gzip member's end). The netty packet decoder then bails with
 * "found N bytes extra whilst reading packet clientbound/minecraft:custom_payload".
 * <p>
 * The contract is simple: after a full encode/decode round-trip on the same buffer,
 * {@code readerIndex} must equal {@code writerIndex}. Anything else is the bug the user hit
 * after a large Architect scan.
 */
public class CompressedNbtPayloadFramingTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** Builds a synthetic Blueprint of given size with alternating stone/dirt cells —
     *  big enough to push the compressed gzip past the GZIPInputStream prefetch window. */
    private static Blueprint makeBlueprint(int sx, int sy, int sz) {
        Blueprint bp = new Blueprint();
        bp.size = new BlockPos(sx, sy, sz);
        bp.facing = Direction.NORTH;
        bp.offset = BlockPos.ZERO;
        int cells = sx * sy * sz;
        bp.data = new int[cells];
        // Palette: index 0 = stone, index 1 = dirt. Alternate so the compressed stream isn't
        // pathologically tiny — RLE-on-zeros would otherwise compress to almost nothing and
        // never exercise multi-fill() reads.
        SchematicBlockDefault stone = new SchematicBlockDefault();
        stone.blockState = Blocks.STONE.defaultBlockState();
        stone.placeBlock = Blocks.STONE;
        SchematicBlockDefault dirt = new SchematicBlockDefault();
        dirt.blockState = Blocks.DIRT.defaultBlockState();
        dirt.placeBlock = Blocks.DIRT;
        bp.palette.add(stone);
        bp.palette.add(dirt);
        for (int i = 0; i < cells; i++) {
            bp.data[i] = i & 1;
        }
        return bp;
    }

    public static void testArchitectPreviewPayloadRoundTripSmall(GameTestHelper helper) {
        assertRoundTripArchitectPreview(makeBlueprint(8, 8, 8));   // 512 cells
        helper.succeed();
    }

    public static void testArchitectPreviewPayloadRoundTripMedium(GameTestHelper helper) {
        assertRoundTripArchitectPreview(makeBlueprint(16, 16, 16)); // 4096 cells
        helper.succeed();
    }

    public static void testArchitectPreviewPayloadRoundTripLarge(GameTestHelper helper) {
        assertRoundTripArchitectPreview(makeBlueprint(32, 16, 32)); // 16384 cells — typical "large architect scan"
        helper.succeed();
    }

    /** "Very large structure" per the user's repro of the netty "found 2 bytes extra" crash —
     *  64×64×64 = 262,144 cells, comparable to the upper end of an architect's scan box. */
    public static void testArchitectPreviewPayloadRoundTripHuge(GameTestHelper helper) {
        assertRoundTripArchitectPreview(makeBlueprint(64, 64, 64));
        helper.succeed();
    }

    /** Exhaustively walks sizes from 1 through 64 in a 1-cell-thick stripe — the goal is to
     *  trip up any specific compressed-output size that lands on a GZIPInputStream prefetch
     *  boundary the codec mishandles. */
    public static void testArchitectPreviewPayloadRoundTripBoundarySweep(GameTestHelper helper) {
        for (int len = 1; len <= 64; len++) {
            assertRoundTripArchitectPreview(makeBlueprint(len, 1, 1));
        }
        helper.succeed();
    }

    public static void testSnapshotResponsePayloadRoundTripMedium(GameTestHelper helper) {
        assertRoundTripSnapshotResponse(makeBlueprint(16, 16, 16));
        helper.succeed();
    }

    public static void testSnapshotResponsePayloadRoundTripLarge(GameTestHelper helper) {
        assertRoundTripSnapshotResponse(makeBlueprint(32, 16, 32));
        helper.succeed();
    }

    private static void assertRoundTripArchitectPreview(Blueprint bp) {
        var payload = new ArchitectPreviewResponsePayload(BlockPos.ZERO, bp);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ArchitectPreviewResponsePayload.STREAM_CODEC.encode(buf, payload);
            int wrote = buf.writerIndex();

            ArchitectPreviewResponsePayload.STREAM_CODEC.decode(buf);
            int read = buf.readerIndex();

            assertTrue(read == wrote,
                    "ArchitectPreviewResponsePayload [" + bp.size + "]: encoder wrote " + wrote
                            + " bytes but decoder consumed " + read + " (delta=" + (wrote - read) + "). "
                            + "Mismatch makes netty drop the custom_payload packet with "
                            + "\"found N bytes extra\".");
        } finally {
            buf.release();
        }
    }

    private static void assertRoundTripSnapshotResponse(Blueprint bp) {
        var payload = new SnapshotResponsePayload(bp);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SnapshotResponsePayload.STREAM_CODEC.encode(buf, payload);
            int wrote = buf.writerIndex();

            SnapshotResponsePayload.STREAM_CODEC.decode(buf);
            int read = buf.readerIndex();

            assertTrue(read == wrote,
                    "SnapshotResponsePayload [" + bp.size + "]: encoder wrote " + wrote
                            + " bytes but decoder consumed " + read + " (delta=" + (wrote - read) + ").");
        } finally {
            buf.release();
        }
    }
}
