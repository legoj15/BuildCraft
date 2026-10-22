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
     * Verifies the full download cycle: with a used snapshot in invDownIn and invDownOut empty, the item moves to
     * invDownOut once the 50-tick cycle completes. Times the cycle from its first counted tick rather than from a
     * fixed tick, so a tile that starts ticking late only starts the cycle late.
     */
    public static void testDownloadCycle(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());
        //? if >=1.21.10 {
        TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
        //?} else {
        /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
        //?}
        if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);

        long[] started = {-1};
        runSteps(helper, DOWNLOAD_CYCLE_TICKS, List.of(
            new Step("the library to be seeded", () -> true, () -> {
                assertTrue(tile.progressDown == -1, "progressDown should be idle (-1) initially");
                Snapshot.Header header = fakeHeader();
                tile.invDownIn.setStackInSlot(0, BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(header));
            }),
            new Step("the download to start counting", () -> tile.progressDown >= 0,
                () -> started[0] = helper.getTick()),
            new Step("the download to deliver the item into invDownOut",
                () -> !tile.invDownOut.getStackInSlot(0).isEmpty(), () -> {
                // The cycle is 50 ticks from its first counted tick; the start is observed within a tick of it.
                long cycle = helper.getTick() - started[0];
                assertTrue(cycle >= 45 && cycle <= 55,
                    "the download should take about 50 ticks from its start, took " + cycle);
                assertTrue(tile.invDownIn.getStackInSlot(0).isEmpty(),
                    "invDownIn should be empty after download completes");
                assertTrue(tile.invDownOut.getStackInSlot(0).getItem() == BCBuildersItems.BLUEPRINT_USED.get(),
                    "invDownOut should contain the used blueprint");
                assertTrue(tile.progressDown == -1,
                    "progressDown should return to idle after download");
            })), () -> {});
    }

    /**
     * Verifies that progressUp increments each tick when selected key + invUpIn item are present,
     * and that the counter does not complete in fewer than 50 ticks.
     */
    public static void testUploadProgressIncrements(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());
        //? if >=1.21.10 {
        TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
        //?} else {
        /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
        //?}
        if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);

        int[] started = {-1};
        runSteps(helper, UPLOAD_PROGRESS_TICKS, List.of(
            // Seed: selected key + clean blueprint in invUpIn (simulates player picked a snapshot
            // and inserted a blank item to write to)
            new Step("the library to be seeded", () -> true, () -> {
                tile.selected = new Snapshot.Key(new CompoundTag());
                tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.BLUEPRINT_CLEAN.get()));
            }),
            new Step("progressUp to start counting", () -> tile.progressUp > 0, () -> started[0] = tile.progressUp),
            new Step("progressUp to climb ten ticks further", () -> tile.progressUp >= started[0] + 10, () -> {
                // Still counting after the climb, and nowhere near the 50 ticks a request takes.
                assertTrue(tile.progressUp < 50,
                    "progressUp should not complete in just ten ticks past " + started[0] + ", got: " + tile.progressUp);
            })), () -> {});
    }

    /**
     * Verifies that progressDown stays at -1 when invDownIn is empty (idle condition).
     * And that progressDown resets to -1 when invDownIn becomes empty mid-cycle.
     */
    public static void testDownloadIdleWhenEmpty(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());
        //? if >=1.21.10 {
        TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
        //?} else {
        /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
        //?}
        if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);

        runSteps(helper, DOWNLOAD_IDLE_TICKS, List.of(
            // Give the library a few ticks on empty input first, so a tile that starts counting on its own fails.
            new Step("the library to tick on empty input", () -> helper.getTick() >= 5, () -> {
                // Nothing in invDownIn: progress must stay idle
                assertTrue(tile.progressDown == -1,
                    "progressDown should be -1 (idle) with empty invDownIn, got: " + tile.progressDown);
                assertTrue(tile.progressUp == -1,
                    "progressUp should be -1 (idle) with no conditions met, got: " + tile.progressUp);
                // Seed, wait for the cycle to start, remove the item — progress should reset
                Snapshot.Header header = fakeHeader();
                tile.invDownIn.setStackInSlot(0, BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(header));
            }),
            new Step("progressDown to start after seeding", () -> tile.progressDown > 0,
                () -> tile.invDownIn.setStackInSlot(0, ItemStack.EMPTY)),
            new Step("progressDown to reset to -1 after invDownIn was cleared", () -> tile.progressDown == -1, () -> {
                assertTrue(tile.invDownOut.getStackInSlot(0).isEmpty(),
                    "clearing invDownIn mid-cycle aborts the download: nothing may come out");
            })), () -> {});
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

    /** Send one chunk the way the client answers the request the menu is waiting on (tagged with its id; 0, which no
     *  request carries, while it waits on none). */
    private static void sendChunk(ContainerElectronicLibrary menu, boolean last, byte[] chunk) {
        sendChunk(menu, menu.getUploadRequestId(), last, chunk);
    }

    /** Send one chunk tagged as the answer to {@code request}: an earlier request's, for a stream still in flight. */
    private static void sendChunk(ContainerElectronicLibrary menu, int request, boolean last, byte[] chunk) {
        send(menu, ContainerElectronicLibrary.NET_UPLOAD_DATA, buf -> {
            buf.writeVarInt(request);
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

    /** Send {@code snapshot} the way the client does: squashed, in three chunks, each tagged with the id of the
     *  request the menu is waiting on when the answer starts. */
    private static void sendUpload(ContainerElectronicLibrary menu, Snapshot snapshot) throws IOException {
        ByteArrayOutputStream squished = new ByteArrayOutputStream();
        NbtSquisher.squishVanilla(Snapshot.writeToNBT(snapshot), squished);
        byte[] whole = squished.toByteArray();
        int third = whole.length / 3;
        int request = menu.getUploadRequestId();
        sendChunk(menu, request, false, Arrays.copyOfRange(whole, 0, third));
        sendChunk(menu, request, false, Arrays.copyOfRange(whole, third, 2 * third));
        sendChunk(menu, request, true, Arrays.copyOfRange(whole, 2 * third, whole.length));
    }

    /** Poll budgets of the three library-cycle tests, each well past the 50-tick cycle it waits for and under its
     *  manifest's {@code max_ticks} (200). */
    private static final int DOWNLOAD_CYCLE_TICKS = 150;
    private static final int UPLOAD_PROGRESS_TICKS = 150;
    private static final int DOWNLOAD_IDLE_TICKS = 150;

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
        // The selection names the build; the answer is the same content under a header the sender made up (an
        // overlong name and another owner). The item must carry the selection's header, not the answer's.
        Snapshot.Key asked = new Snapshot.Key(wanted.key,
            new Snapshot.Header(wanted.key, new UUID(3, 4), new Date(1234), "house"));
        Template answer = wanted.copy();
        answer.key = new Snapshot.Key(wanted.key, new Snapshot.Header(wanted.key, new UUID(9, 9), new Date(999),
            "x".repeat(ContainerElectronicLibrary.MAX_SELECTED_NAME_LENGTH * 4)));
        Runnable upload = () -> {
            try {
                sendUpload(menu, answer);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };

        tile.selected = asked;
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
                Snapshot.Header madeHeader = ItemSnapshot.getHeader(made[0]);
                assertTrue(madeHeader != null && "house".equals(madeHeader.name),
                    "the item is named by the request, not the upload");
                assertTrue(new UUID(3, 4).equals(madeHeader.owner), "and owned by the request's owner");
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

    private static TileElectronicLibrary libraryAt(GameTestHelper helper, BlockPos pos) {
        //? if >=1.21.10 {
        TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
        //?} else {
        /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
        //?}
        if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);
        return tile;
    }

    /** A mock player with a library menu open on {@code tile}, watching it the way a real one does. */
    private static ContainerElectronicLibrary watcher(GameTestHelper helper, TileElectronicLibrary tile,
                                                      Player player) {
        ContainerElectronicLibrary menu = new ContainerElectronicLibrary(0, player.getInventory(), tile);
        player.containerMenu = menu;
        tile.onPlayerOpen(player);
        return menu;
    }

    /**
     * A watcher's half-sent upload does not outlive the request it answers: when the tile withdraws the request
     * (the selection changed) or gives up on it (nothing arrived for the answer timeout) the chunks the menu holds
     * are freed at once, not at the next chunk or when the menu closes, and the rest of that stream is ignored when
     * it arrives rather than glued onto the next upload.
     */
    public static void testWithdrawnRequestReleasesBuffers(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());
        TileElectronicLibrary tile = libraryAt(helper, pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ContainerElectronicLibrary menu = watcher(helper, tile, player);

        Snapshot.Key asked = libraryKey(1);
        Snapshot.Key other = libraryKey(2);
        byte[] chunk = new byte[LibraryUploadBuffer.CHUNK_BYTES];
        int[] stream = {0}; // the request id the stream in flight answers
        tile.selected = asked;
        tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get()));

        List<Step> steps = List.of(
            new Step("the first request", tile::isUploadRequested, () -> {
                stream[0] = menu.getUploadRequestId();
                sendChunk(menu, false, chunk);
                sendChunk(menu, false, chunk);
                assertTrue(menu.getBufferedUploadBytes() == 2 * chunk.length,
                    "a requested upload is buffered, held " + menu.getBufferedUploadBytes());
                tile.selected = other; // the request is no longer wanted
            }),
            new Step("the withdrawn request", () -> !tile.isUploadRequested(), () -> {
                assertTrue(menu.getBufferedUploadBytes() == 0,
                    "a withdrawn request frees what its stream held, held " + menu.getBufferedUploadBytes());
                // The rest of the stream was already on its way.
                sendChunk(menu, stream[0], false, chunk);
                sendChunk(menu, stream[0], true, chunk);
                assertTrue(menu.getBufferedUploadBytes() == 0, "and is not buffered when it arrives");
                assertTrue(tile.invUpOut.getStackInSlot(0).isEmpty(), "nor turned into an item");
                assertFalse(tile.isUploadRefused(), "nor counted against the build");
                tile.selected = asked;
            }),
            new Step("the second request", tile::isUploadRequested, () -> {
                stream[0] = menu.getUploadRequestId();
                sendChunk(menu, false, chunk);
                assertTrue(menu.getBufferedUploadBytes() == chunk.length, "the new request is buffered cleanly, held "
                    + menu.getBufferedUploadBytes());
                // ...and the sender goes quiet.
            }),
            new Step("the unanswered request timing out", () -> !tile.isUploadRequested(), () -> {
                assertTrue(menu.getBufferedUploadBytes() == 0,
                    "a request that timed out frees what its stream held, held " + menu.getBufferedUploadBytes());
                sendChunk(menu, stream[0], true, chunk);
                assertTrue(menu.getBufferedUploadBytes() == 0, "and its late tail is ignored");
                assertTrue(tile.invUpOut.getStackInSlot(0).isEmpty(), "making no item");
            }));
        runSteps(helper, 330, steps, () -> tile.onPlayerClose(player));
    }

    /**
     * A withdrawn request whose stream never finishes (a modified or wedged client sends its first chunks and then
     * nothing) does not poison the menu: the menu once ignored the tail of a withdrawn stream up to its final chunk,
     * and a final chunk that never came left that ignoring armed for good, so the next request's honest upload was
     * swallowed. Each request now carries its own id, echoed on every chunk of its answer: the next upload of the
     * same menu makes its item.
     */
    public static void testWedgedWithdrawnStreamDoesNotEatTheNextUpload(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());
        TileElectronicLibrary tile = libraryAt(helper, pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ContainerElectronicLibrary menu = watcher(helper, tile, player);

        Template wanted = template(0, 2, 4);
        Template other = template(1);
        GlobalSavedDataSnapshots store = GlobalSavedDataSnapshots.get(helper.getLevel());
        byte[] chunk = new byte[LibraryUploadBuffer.CHUNK_BYTES];
        tile.selected = wanted.key;
        tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get()));

        List<Step> steps = List.of(
            new Step("the first request", tile::isUploadRequested, () -> {
                sendChunk(menu, false, chunk);
                sendChunk(menu, false, chunk);
                tile.selected = other.key; // the request is no longer wanted, and the client never finishes its stream
            }),
            new Step("the withdrawn request", () -> !tile.isUploadRequested(), () -> {
                assertTrue(menu.getBufferedUploadBytes() == 0, "the withdrawn stream's chunks are freed");
                tile.selected = wanted.key;
            }),
            new Step("the second request", tile::isUploadRequested, () -> {
                try {
                    sendUpload(menu, wanted);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                assertFalse(tile.isUploadRefused(), "an honest upload is not refused for what came before it");
                assertFalse(tile.invUpOut.getStackInSlot(0).isEmpty(),
                    "the honest upload of the next request made its item, not swallowed by the unfinished stream");
                assertFalse(tile.isUploadRequested(), "and answered the request");
            }));
        runSteps(helper, 330, steps, () -> {
            tile.onPlayerClose(player);
            store.removeSnapshot(wanted.key);
            store.removeSnapshot(other.key);
        });
    }

    /**
     * The tail of a stream whose request is over can still be on the wire when the next request goes out (a slow
     * uplink still sending an upload the server already refused for its size): it is not the answer to the new
     * request, so it is ignored, and the watcher's real answer to that request makes the item. Without a request id
     * on each chunk the server cannot tell the old tail from the new stream: the old chunks landed in the new buffer,
     * the old final chunk completed a non-snapshot, the watcher was refused for the new request and its real answer
     * then skipped.
     */
    public static void testStaleTailDoesNotSpoilTheNextRequest(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());
        TileElectronicLibrary tile = libraryAt(helper, pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ContainerElectronicLibrary menu = watcher(helper, tile, player);

        Snapshot.Key huge = libraryKey(5);
        Template wanted = template(0, 2, 4);
        GlobalSavedDataSnapshots store = GlobalSavedDataSnapshots.get(helper.getLevel());
        byte[] chunk = new byte[LibraryUploadBuffer.CHUNK_BYTES];
        int[] stale = {0}; // the request id the oversized stream answers
        tile.selected = huge;
        tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get()));

        List<Step> steps = List.of(
            new Step("the request for the oversized build", tile::isUploadRequested, () -> {
                stale[0] = menu.getUploadRequestId();
                int pastTheLimit = menu.getUploadLimits().maxUploadBytes() / LibraryUploadBuffer.CHUNK_BYTES + 2;
                for (int i = 0; i < pastTheLimit; i++) sendChunk(menu, stale[0], false, chunk);
                assertTrue(tile.isUploadRefused(), "the oversized upload is refused");
                assertFalse(tile.isUploadRequested(), "and its request is over");
                tile.selected = wanted.key; // the owner picks another build while the old stream is still arriving
            }),
            new Step("the request for the other build", tile::isUploadRequested, () -> {
                assertTrue(menu.getUploadRequestId() != stale[0], "the new request has its own id");
                // The rest of the refused stream, final chunk included, lands only now.
                sendChunk(menu, stale[0], false, chunk);
                sendChunk(menu, stale[0], true, new byte[] {1, 2, 3});
                assertTrue(menu.getBufferedUploadBytes() == 0, "the old stream's tail is not buffered for the new one");
                assertTrue(tile.isUploadRequested(), "nor does its final chunk answer (or spoil) the new request");
                assertFalse(tile.isUploadRefused(), "nor get the watcher refused for it");
                try {
                    sendUpload(menu, wanted);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                assertFalse(tile.invUpOut.getStackInSlot(0).isEmpty(), "the watcher's real answer made the item");
                assertFalse(tile.isUploadRequested(), "and answered the request");
            }));
        runSteps(helper, 330, steps, () -> {
            tile.onPlayerClose(player);
            store.removeSnapshot(wanted.key);
        });
    }

    /**
     * An unreadable or wrong upload from one watcher ends that watcher's answer, not the tile's request: another
     * watcher's good answer still makes the item, the first watcher's later answers to the same request are not
     * taken, and only when every watcher has been refused (or has left) is the build marked refused.
     */
    public static void testBadStreamAbortsOnlyItsWatcher(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());
        TileElectronicLibrary tile = libraryAt(helper, pos);
        Player first = helper.makeMockPlayer(GameType.SURVIVAL);
        Player second = helper.makeMockPlayer(GameType.SURVIVAL);
        ContainerElectronicLibrary firstMenu = watcher(helper, tile, first);
        ContainerElectronicLibrary secondMenu = watcher(helper, tile, second);

        Template wanted = template(0, 2, 4);
        Template other = template(1);
        GlobalSavedDataSnapshots store = GlobalSavedDataSnapshots.get(helper.getLevel());
        byte[] garbage = {1, 2, 3};
        tile.selected = wanted.key;
        tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get()));

        List<Step> steps = List.of(
            new Step("the first request", tile::isUploadRequested, () -> {
                sendChunk(firstMenu, true, garbage);
                assertTrue(tile.isUploadRequested(), "one watcher's unreadable stream does not end the request");
                assertFalse(tile.isUploadRefused(), "nor mark the build refused while another watcher may answer");
                assertTrue(firstMenu.getBufferedUploadBytes() == 0, "its own stream is dropped");

                // That watcher's later answers to the same request are not taken, even a good one...
                try {
                    sendUpload(firstMenu, wanted);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                assertTrue(tile.invUpOut.getStackInSlot(0).isEmpty(), "a refused watcher's second answer is ignored");
                assertTrue(tile.isUploadRequested(), "and the request is still waiting");

                // ...but another watcher's is.
                try {
                    sendUpload(secondMenu, wanted);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                assertFalse(tile.invUpOut.getStackInSlot(0).isEmpty(), "the other watcher's answer made the item");
                assertFalse(tile.isUploadRequested(), "and ended the request");
                assertTrue(store.getSnapshot(wanted.key) != null, "which was stored");
                tile.invUpOut.setStackInSlot(0, ItemStack.EMPTY);
                tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get()));
            }),
            new Step("the request for the next blank", tile::isUploadRequested, () -> {
                try {
                    sendUpload(firstMenu, other); // the wrong build: this watcher is refused
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                assertTrue(tile.isUploadRequested(), "the wrong build from one watcher leaves the request waiting");
                assertFalse(tile.isUploadRefused(), "and the build unrefused");
                sendChunk(secondMenu, true, garbage); // the last watcher is refused too
                assertTrue(tile.isUploadRefused(), "once every watcher is refused, so is the build");
                assertFalse(tile.isUploadRequested(), "and it is not asked for again");
                assertTrue(tile.invUpOut.getStackInSlot(0).isEmpty(), "no item came of any of it");
                assertFalse(tile.invUpIn.getStackInSlot(0).isEmpty(), "and the blank is still there");
                tile.invUpIn.setStackInSlot(0, ItemStack.EMPTY);
            }),
            new Step("taking the blank out to clear the refusal", () -> !tile.isUploadRefused(),
                () -> tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get()))),
            new Step("the request for the third blank", tile::isUploadRequested, () -> {
                sendChunk(firstMenu, true, garbage);
                assertTrue(tile.isUploadRequested(), "one refused, one still to answer");
                tile.onPlayerClose(second);
                assertTrue(tile.isUploadRefused(), "the watcher left standing is refused: so is the build");
                assertFalse(tile.isUploadRequested(), "and nobody is left to ask");
            }));
        runSteps(helper, 380, steps, () -> {
            tile.onPlayerClose(first);
            tile.onPlayerClose(second);
            store.removeSnapshot(wanted.key);
            store.removeSnapshot(other.key);
        });
    }
}
