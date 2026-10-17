/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import buildcraft.builders.container.ContainerElectronicLibrary;
import buildcraft.builders.container.LibraryUploadBuffer;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Template;
import buildcraft.builders.tile.TileElectronicLibrary;
import buildcraft.lib.misc.HashUtil;
import buildcraft.lib.nbt.NbtSquisher;
import buildcraft.lib.net.PacketBufferBC;

/**
 * Verifies the Electronic Library tile entity:
 * - invDownIn slot filter (only used snapshots)
 * - invUpIn slot filter (any snapshot, used or clean)
 * - Download cycle: item transfers from invDownIn → invDownOut after 50 ticks
 * - Upload cycle: progressUp increments when selected + invUpIn item present
 */
public class ElectronicLibraryTester {

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + msg);
    }

    private static void assertFalse(boolean condition, String msg) {
        if (condition) throw new IllegalStateException("Assertion failed (expected false): " + msg);
    }

    private static Snapshot.Header fakeHeader() {
        Snapshot.Key key = new Snapshot.Key(new CompoundTag());
        return new Snapshot.Header(key, new UUID(0, 0), new Date(0), "TestSnapshot");
    }

    /**
     * Verifies slot filter rules:
     * - invDownIn accepts used blueprints/templates, rejects clean ones.
     * - invUpIn accepts all snapshot items.
     * (Output-only behaviour of invDownOut/invUpOut is an EnumAccess.EXTRACT concern, not a
     * canSet filter — those slots carry no filter, so canSet is intentionally accept-all.)
     */
    public static void testSlotFiltering(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
            //?} else {
            /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
            //?}
            if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);

            Snapshot.Header header = fakeHeader();
            ItemStack usedBlueprint = BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(header);
            ItemStack usedTemplate  = BCBuildersItems.TEMPLATE_USED.get().createUsedStack(header);
            ItemStack cleanBlueprint = new ItemStack(BCBuildersItems.BLUEPRINT_CLEAN.get());
            ItemStack cleanTemplate  = new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get());

            // invDownIn: only used snapshots
            assertTrue(tile.invDownIn.canSet(0, usedBlueprint),  "invDownIn must accept used blueprint");
            assertTrue(tile.invDownIn.canSet(0, usedTemplate),   "invDownIn must accept used template");
            assertFalse(tile.invDownIn.canSet(0, cleanBlueprint), "invDownIn must reject clean blueprint");
            assertFalse(tile.invDownIn.canSet(0, cleanTemplate),  "invDownIn must reject clean template");

            // invUpIn: any snapshot item
            assertTrue(tile.invUpIn.canSet(0, usedBlueprint),   "invUpIn must accept used blueprint");
            assertTrue(tile.invUpIn.canSet(0, usedTemplate),    "invUpIn must accept used template");
            assertTrue(tile.invUpIn.canSet(0, cleanBlueprint),  "invUpIn must accept clean blueprint");
            assertTrue(tile.invUpIn.canSet(0, cleanTemplate),   "invUpIn must accept clean template");

            helper.succeed();
        });
    }

    /**
     * Verifies the full download cycle: after 50 ticks with a used snapshot in invDownIn
     * and invDownOut empty, the item moves to invDownOut.
     */
    public static void testDownloadCycle(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
            //?} else {
            /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
            //?}
            if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);

            assertTrue(tile.progressDown == -1, "progressDown should be idle (-1) initially");

            Snapshot.Header header = fakeHeader();
            ItemStack usedBlueprint = BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(header);
            tile.invDownIn.setStackInSlot(0, usedBlueprint);

            // Wait long enough for the 50-tick download cycle to complete
            helper.runAfterDelay(55, () -> {
                assertTrue(tile.invDownIn.getStackInSlot(0).isEmpty(),
                    "invDownIn should be empty after download completes");
                assertFalse(tile.invDownOut.getStackInSlot(0).isEmpty(),
                    "invDownOut should hold the item after download completes");
                assertTrue(tile.invDownOut.getStackInSlot(0).getItem() == BCBuildersItems.BLUEPRINT_USED.get(),
                    "invDownOut should contain the used blueprint");
                assertTrue(tile.progressDown == -1,
                    "progressDown should return to idle after download");
                helper.succeed();
            });
        });
    }

    /**
     * Verifies that progressUp increments each tick when selected key + invUpIn item are present,
     * and that the counter does not complete in fewer than 50 ticks.
     */
    public static void testUploadProgressIncrements(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
            //?} else {
            /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
            //?}
            if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);

            // Seed: selected key + clean blueprint in invUpIn (simulates player picked a snapshot
            // and inserted a blank item to write to)
            tile.selected = new Snapshot.Key(new CompoundTag());
            tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.BLUEPRINT_CLEAN.get()));

            helper.runAfterDelay(10, () -> {
                // After 10 ticks: progressUp should be somewhere between 1 and 49
                assertTrue(tile.progressUp > 0,
                    "progressUp should be > 0 after 10 ticks, got: " + tile.progressUp);
                assertTrue(tile.progressUp < 50,
                    "progressUp should not complete in just 10 ticks, got: " + tile.progressUp);
                helper.succeed();
            });
        });
    }

    /**
     * Verifies that progressDown stays at -1 when invDownIn is empty (idle condition).
     * And that progressDown resets to -1 when invDownIn becomes empty mid-cycle.
     */
    public static void testDownloadIdleWhenEmpty(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());

        helper.runAfterDelay(5, () -> {
            //? if >=1.21.10 {
            TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
            //?} else {
            /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
            //?}
            if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);

            // Nothing in invDownIn: progress must stay idle
            assertTrue(tile.progressDown == -1,
                "progressDown should be -1 (idle) with empty invDownIn, got: " + tile.progressDown);
            assertTrue(tile.progressUp == -1,
                "progressUp should be -1 (idle) with no conditions met, got: " + tile.progressUp);

            // Seed, wait a bit, remove item, wait again — progress should reset
            Snapshot.Header header = fakeHeader();
            ItemStack usedBlueprint = BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(header);
            tile.invDownIn.setStackInSlot(0, usedBlueprint);

            helper.runAfterDelay(10, () -> {
                assertTrue(tile.progressDown > 0, "progressDown should have started after seeding");
                // Remove the item — progress should reset
                tile.invDownIn.setStackInSlot(0, ItemStack.EMPTY);
                helper.runAfterDelay(2, () -> {
                    assertTrue(tile.progressDown == -1,
                        "progressDown should reset to -1 after invDownIn cleared, got: " + tile.progressDown);
                    helper.succeed();
                });
            });
        });
    }

    // ── Client-driven menu messages ─────────────────────────────────────────

    /** Replays a client's container message into the server-side menu, the way the payload handler would (the mock
     *  player is not a ServerPlayer, so the handler itself cannot run; it only forwards to {@code readMessage}). */
    private static void send(ContainerElectronicLibrary menu, int id, Consumer<PacketBufferBC> writer) {
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        writer.accept(buffer);
        menu.readMessage(id, buffer, false, null);
        buffer.release();
    }

    private static void sendSelected(ContainerElectronicLibrary menu, Snapshot.Key key) {
        send(menu, ContainerElectronicLibrary.NET_SELECTED, buf -> {
            buf.writeByte(key != null ? 1 : 0);
            if (key != null) key.writeToByteBuf(buf);
        });
    }

    private static void sendChunk(ContainerElectronicLibrary menu, boolean last, byte[] chunk) {
        send(menu, ContainerElectronicLibrary.NET_UPLOAD_DATA, buf -> {
            buf.writeByte(last ? 1 : 0);
            buf.writeByteArray(chunk);
        });
    }

    private static Snapshot.Key libraryKey(int fill) {
        byte[] hash = new byte[HashUtil.HASH_BYTES];
        Arrays.fill(hash, (byte) fill);
        return new Snapshot.Key(new Snapshot.Key(), hash);
    }

    /**
     * The two inputs a client drives on the Electronic Library menu are checked by the server: a selection that
     * cannot be a library key (wrong hash length, cut off) leaves the earlier selection alone, and an upload the tile
     * did not ask for is neither buffered nor turned into an item (see {@link #testUploadsAnswerOnlyTheirRequest}
     * for the asked-for ones).
     */
    public static void testClientMessagesAreChecked(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());
        //? if >=1.21.10 {
        TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
        //?} else {
        /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
        //?}
        if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ContainerElectronicLibrary menu = new ContainerElectronicLibrary(0, player.getInventory(), tile);

        // --- Selection ---
        Snapshot.Key good = libraryKey(7);
        sendSelected(menu, good);
        assertTrue(good.equals(tile.selected), "a well-formed key is selected");

        sendSelected(menu, new Snapshot.Key(new Snapshot.Key(), new byte[] {1, 2, 3}));
        assertTrue(good.equals(tile.selected), "a key that is not a digest leaves the selection alone");

        Snapshot.Key hugeName = new Snapshot.Key(good, new Snapshot.Header(good, new UUID(1, 2), new Date(0),
            "x".repeat(ContainerElectronicLibrary.MAX_SELECTED_NAME_LENGTH + 1)));
        sendSelected(menu, hugeName);
        assertTrue(good.equals(tile.selected), "a key with an overlong name leaves the selection alone");

        send(menu, ContainerElectronicLibrary.NET_SELECTED, buf -> {
            buf.writeByte(1);
            buf.writeVarInt(HashUtil.HASH_BYTES); // promises a hash, sends none
        });
        assertTrue(good.equals(tile.selected), "a cut-off key leaves the selection alone");

        Snapshot.Key named = new Snapshot.Key(good, new Snapshot.Header(good, new UUID(1, 2), new Date(0), "house"));
        sendSelected(menu, named);
        assertTrue(named.equals(tile.selected), "a named library key is selected");
        sendSelected(menu, null);
        assertTrue(tile.selected == null, "clearing the selection still works");

        // --- Uploads nobody asked for change nothing ---
        // A modded client can send NET_UPLOAD_DATA at any time. Without an outstanding request it must not overwrite
        // a finished item, consume a blank, or store anything.
        Template template = template(1, 3);
        tile.selected = template.key; // even with everything else in place
        ItemStack finished = BCBuildersItems.TEMPLATE_USED.get().createUsedStack(fakeHeader());
        tile.invUpOut.setStackInSlot(0, finished);
        tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get()));
        GlobalSavedDataSnapshots store = GlobalSavedDataSnapshots.get(helper.getLevel());
        try {
            sendUpload(menu, template);
            assertTrue(tile.invUpOut.getStackInSlot(0) == finished, "an unasked-for upload leaves the output alone");
            assertFalse(tile.invUpIn.getStackInSlot(0).isEmpty(), "and the blank");
            assertTrue(store.getSnapshot(template.key) == null, "and stores nothing");

            tile.invUpOut.setStackInSlot(0, ItemStack.EMPTY);
            sendUpload(menu, template);
            assertTrue(tile.invUpOut.getStackInSlot(0).isEmpty(), "an empty output does not make it asked for");
            assertTrue(store.getSnapshot(template.key) == null, "nor stores anything");

            byte[] chunk = new byte[LibraryUploadBuffer.CHUNK_BYTES];
            for (int i = 0; i < 8; i++) sendChunk(menu, false, chunk);
            assertTrue(menu.getBufferedUploadBytes() == 0, "an unasked-for stream is not even buffered");
            sendChunk(menu, true, chunk);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            store.removeSnapshot(template.key);
        }
        helper.succeed();
    }

    private static Template template(int... set) {
        Template template = new Template();
        template.size = new BlockPos(5, 1, 1);
        template.facing = Direction.NORTH;
        template.offset = BlockPos.ZERO;
        template.data = new BitSet();
        for (int i : set) template.data.set(i);
        template.computeKey();
        return template;
    }

    /** Send {@code snapshot} the way the client does: squashed, in three chunks. */
    private static void sendUpload(ContainerElectronicLibrary menu, Snapshot snapshot) throws IOException {
        ByteArrayOutputStream squished = new ByteArrayOutputStream();
        NbtSquisher.squishVanilla(Snapshot.writeToNBT(snapshot), squished);
        byte[] whole = squished.toByteArray();
        int third = whole.length / 3;
        sendChunk(menu, false, Arrays.copyOfRange(whole, 0, third));
        sendChunk(menu, false, Arrays.copyOfRange(whole, third, 2 * third));
        sendChunk(menu, true, Arrays.copyOfRange(whole, 2 * third, whole.length));
    }

    /** One step of {@link #runSteps}: wait until {@code ready}, then run {@code action}. */
    private record Step(String waitingFor, BooleanSupplier ready, Runnable action) {}

    /**
     * Run {@code steps} in order, each on the first tick its condition holds, then succeed; fail naming the step
     * still waiting after {@code maxTicks}. Every tick's poll is scheduled up front (as {@code EntityArenaUtil.tickUntil}
     * does): the framework must not be scheduled from inside its own runnables.
     */
    private static void runSteps(GameTestHelper helper, int maxTicks, List<Step> steps, Runnable cleanup) {
        int[] next = {0};
        boolean[] done = {false};
        for (int t = 1; t <= maxTicks; t++) {
            final int tick = t;
            helper.runAfterDelay(tick, () -> {
                if (done[0]) return;
                try {
                    while (next[0] < steps.size() && steps.get(next[0]).ready().getAsBoolean()) {
                        steps.get(next[0]++).action().run();
                    }
                    if (next[0] == steps.size()) {
                        done[0] = true;
                        cleanup.run();
                        helper.succeed();
                    } else if (tick == maxTicks) {
                        done[0] = true;
                        cleanup.run();
                        helper.fail("Still waiting for: " + steps.get(next[0]).waitingFor()
                            + " (waited " + maxTicks + " ticks)");
                    }
                } catch (RuntimeException e) {
                    done[0] = true;
                    cleanup.run();
                    throw e;
                }
            });
        }
    }

    /**
     * An upload is accepted only as the answer to the tile's own request, for the build it asked for, and once: the
     * wrong build or an oversized stream is refused and not asked for again until the blank changes, a matching
     * upload makes one item from one blank, and a second answer to the same request makes nothing.
     */
    public static void testUploadsAnswerOnlyTheirRequest(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());
        //? if >=1.21.10 {
        TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
        //?} else {
        /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
        //?}
        if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ContainerElectronicLibrary menu = new ContainerElectronicLibrary(0, player.getInventory(), tile);
        // The tile asks only the players watching its menu.
        player.containerMenu = menu;
        tile.onPlayerOpen(player);

        Template wanted = template(0, 2, 4);
        Template other = template(1);
        GlobalSavedDataSnapshots store = GlobalSavedDataSnapshots.get(helper.getLevel());
        long[] refusedAt = {0};
        ItemStack[] made = {ItemStack.EMPTY};
        Runnable upload = () -> {
            try {
                sendUpload(menu, wanted);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };

        tile.selected = wanted.key;
        tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get()));

        List<Step> steps = List.of(
            new Step("the first request", tile::isUploadRequested, () -> {
                try {
                    sendUpload(menu, other);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                assertTrue(tile.isUploadRefused(), "an upload of another build is refused");
                assertFalse(tile.isUploadRequested(), "and ends the request");
                assertTrue(tile.invUpOut.getStackInSlot(0).isEmpty(), "and makes no item");
                assertFalse(tile.invUpIn.getStackInSlot(0).isEmpty(), "nor takes the blank");
                assertTrue(store.getSnapshot(other.key) == null, "nor is stored");
                refusedAt[0] = helper.getLevel().getGameTime();
            }),
            new Step("a request cycle and more to pass after the refusal",
                () -> helper.getLevel().getGameTime() >= refusedAt[0] + 70,
                () -> {
                    assertFalse(tile.isUploadRequested(), "a refused upload is not asked for again");
                    assertTrue(tile.isUploadRefused(), "it stays refused while nothing changes");
                    assertTrue(tile.progressUp == -1, "and the upload sits idle, got " + tile.progressUp);
                    tile.invUpIn.setStackInSlot(0, ItemStack.EMPTY);
                }),
            new Step("taking the blank out to clear the refusal", () -> !tile.isUploadRefused(),
                () -> tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get()))),
            new Step("the request for the new blank", tile::isUploadRequested, () -> {
                byte[] chunk = new byte[LibraryUploadBuffer.CHUNK_BYTES];
                int limit = menu.getUploadLimits().maxUploadBytes();
                int pastTheLimit = limit / LibraryUploadBuffer.CHUNK_BYTES + 4;
                for (int i = 0; i < pastTheLimit; i++) {
                    sendChunk(menu, false, chunk);
                    assertTrue(menu.getBufferedUploadBytes() <= limit,
                        "the server never holds more than the limit, held " + menu.getBufferedUploadBytes());
                }
                sendChunk(menu, true, new byte[] {1, 2, 3});
                assertTrue(menu.getBufferedUploadBytes() == 0, "the overflowing upload was dropped, not kept");
                assertTrue(tile.isUploadRefused(), "an oversized upload is refused");
                assertFalse(tile.isUploadRequested(), "and not asked for again");
                assertTrue(tile.invUpOut.getStackInSlot(0).isEmpty(), "and makes no item");
                assertFalse(tile.invUpIn.getStackInSlot(0).isEmpty(), "nor takes the blank");
                tile.invUpIn.setStackInSlot(0, ItemStack.EMPTY);
            }),
            new Step("taking the blank out again", () -> !tile.isUploadRefused(),
                () -> tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get()))),
            new Step("the request for the third blank", tile::isUploadRequested, () -> {
                upload.run();
                made[0] = tile.invUpOut.getStackInSlot(0);
                assertFalse(made[0].isEmpty(), "the asked-for upload made its item");
                assertTrue(tile.invUpIn.getStackInSlot(0).isEmpty(), "from the blank");
                assertTrue(store.getSnapshot(wanted.key) != null, "and was stored on the server");
                assertFalse(tile.isUploadRequested(), "the request is answered");

                // A second watcher's answer to the same request: no blank to take, an item to keep.
                upload.run();
                assertTrue(tile.invUpOut.getStackInSlot(0) == made[0], "a second answer leaves the item alone");
                tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get()));
                upload.run();
                assertTrue(tile.invUpOut.getStackInSlot(0) == made[0], "even with a new blank waiting");
                assertFalse(tile.invUpIn.getStackInSlot(0).isEmpty(), "which it does not take");
            }));
        runSteps(helper, 380, steps, () -> {
            tile.onPlayerClose(player);
            store.removeSnapshot(wanted.key);
            store.removeSnapshot(other.key);
        });
    }
}
