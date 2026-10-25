/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.tiles.IControllable.Mode;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersStatements;
import buildcraft.builders.filler.FillPreview;
import buildcraft.builders.snapshot.Template;
import buildcraft.lib.test.FakeLevels;
import buildcraft.lib.test.ValueIoTestUtil;

/**
 * The cells a Filler's preview draws come from the building info of the Filler a client holds. That copy is only ever
 * refreshed by the tile data the server pushes (every five ticks while a build runs), and used to be built exactly
 * once: change the pattern, a parameter, the invert flag or the box and a client kept drawing the first shape forever.
 * These run the server's saved data through a tile whose level answers {@code isClientSide() == true}, the same way
 * every sync does.
 */
public class TileFillerPreviewTest {

    private static final BlockPos MIN = new BlockPos(10, 64, 10);
    private static final BlockPos MAX = new BlockPos(12, 66, 12);
    private static final BlockPos CENTRE = new BlockPos(11, 65, 11);

    private static TileFiller newFiller() {
        return new TileFiller(BlockPos.ZERO, BCBuildersBlocks.FILLER.get().defaultBlockState());
    }

    /** The Filler the server owns: a 3x3x3 box, filled solid. */
    private static TileFiller server() {
        TileFiller server = newFiller();
        server.box.reset();
        server.box.setMin(MIN);
        server.box.setMax(MAX);
        server.patternStatement.set(BCBuildersStatements.PATTERN_FILL);
        return server;
    }

    /** The Filler a client holds: the same block entity, in a level that says it is the client's. */
    private static TileFiller client() {
        TileFiller client = newFiller();
        client.setLevel(FakeLevels.fake(true, Level.OVERWORLD));
        return client;
    }

    /** What the server pushes: the tile's data as it stands. */
    private static void sync(TileFiller server, TileFiller client) {
        sync(server, client, data -> {});
    }

    /** {@link #sync(TileFiller, TileFiller)} with {@code tweak} applied to the data first, for a state the server only
     *  reaches by ticking a build (a finished flag). */
    private static void sync(TileFiller server, TileFiller client, Consumer<CompoundTag> tweak) {
        CompoundTag data = ValueIoTestUtil.write(server::writeData);
        tweak.accept(data);
        client.readData(ValueIoTestUtil.inputOf(data));
    }

    /** What a build in progress leaves on a client: a placement in the queue. Added after the sync, which replaces the
     *  queue with the server's own (empty here). */
    private static void queueAPlacement(TileFiller client) {
        client.builder.clientPlaceTasks.add(client.builder.new PlaceTask(MIN, null, 0L));
    }

    private static Set<BlockPos> previewCells(TileFiller client) {
        Template.BuildingInfo info = client.getTemplateBuildingInfo();
        Assertions.assertNotNull(info, "the client copy should hold the Filler's building info");
        return new HashSet<>(FillPreview.collect(info, pos -> true));
    }

    private static Set<BlockPos> cellsOf(BlockPos min, BlockPos max) {
        Set<BlockPos> cells = new HashSet<>();
        BlockPos.betweenClosed(min, max).forEach(pos -> cells.add(pos.immutable()));
        return cells;
    }

    @Test
    public void thePreviewCellsAreTheOnesThePatternFills() {
        TileFiller server = server();
        TileFiller client = client();

        sync(server, client);

        Assertions.assertEquals(cellsOf(MIN, MAX), previewCells(client), "a solid fill covers the whole box");
    }

    @Test
    public void aChangedPatternReachesTheClientsPreview() {
        TileFiller server = server();
        TileFiller client = client();
        sync(server, client);
        Assertions.assertTrue(previewCells(client).contains(CENTRE), "a solid fill includes the centre");

        server.patternStatement.set(BCBuildersStatements.PATTERN_FRAME);
        sync(server, client);

        Set<BlockPos> frame = previewCells(client);
        Assertions.assertFalse(frame.contains(CENTRE), "a frame leaves the centre empty; the client still shows the fill");
        Assertions.assertTrue(frame.contains(MIN) && frame.contains(MAX), "a frame keeps the corners");
        Assertions.assertEquals(20, frame.size(), "the frame of a 3x3x3 box is its 8 corners and 12 edge cells");
    }

    @Test
    public void aChangedInvertFlagReachesTheClientsPreview() {
        TileFiller server = server();
        server.patternStatement.set(BCBuildersStatements.PATTERN_FRAME);
        TileFiller client = client();
        sync(server, client);
        Set<BlockPos> frame = previewCells(client);

        server.inverted = true;
        sync(server, client);

        Set<BlockPos> inverse = previewCells(client);
        Set<BlockPos> expected = cellsOf(MIN, MAX);
        expected.removeAll(frame);
        Assertions.assertEquals(expected, inverse, "inverted, the preview is every cell the frame leaves out");
    }

    @Test
    public void aChangedBoxReachesTheClientsPreview() {
        TileFiller server = server();
        TileFiller client = client();
        sync(server, client);

        BlockPos widerMax = new BlockPos(14, 66, 12);
        server.box.reset();
        server.box.setMin(MIN);
        server.box.setMax(widerMax);
        sync(server, client);

        Assertions.assertEquals(cellsOf(MIN, widerMax), previewCells(client), "the box grew by two columns");
    }

    @Test
    public void aRepeatedSyncKeepsTheClientsBuildingInfo() {
        TileFiller server = server();
        TileFiller client = client();
        sync(server, client);
        Template.BuildingInfo first = client.getTemplateBuildingInfo();

        sync(server, client);

        // Rebuilding on every sync would cancel the client builder's tasks every five ticks of a running build.
        Assertions.assertSame(first, client.getTemplateBuildingInfo(), "nothing changed, so nothing should be rebuilt");
    }

    @Test
    public void aQueueAloneIsNotWorkOnlyARunningPoweredFillerWithOneIs() {
        TileFiller server = server();
        TileFiller client = client();

        sync(server, client);
        queueAPlacement(client);
        Assertions.assertFalse(client.hasWorkInFlight(), "no power in the battery: the queue is waiting, not placing");

        server.getBattery().setStored(5 * MjAPI.MJ);
        sync(server, client);
        queueAPlacement(client);
        Assertions.assertTrue(client.hasWorkInFlight(), "a running, powered Filler with a block queued is working");

        sync(server, client);
        Assertions.assertFalse(client.hasWorkInFlight(), "nothing queued: nothing is being placed");

        server.setControlMode(Mode.OFF);
        sync(server, client);
        queueAPlacement(client);
        Assertions.assertFalse(client.hasWorkInFlight(),
                "switched OFF mid-build: the synced queue stays, but nothing is placed, so the preview must show");

        server.setControlMode(Mode.ON);
        sync(server, client, data -> data.putBoolean("finished", true));
        queueAPlacement(client);
        Assertions.assertFalse(client.hasWorkInFlight(), "a finished Filler places nothing, whatever its queue holds");
    }

    @Test
    public void thePreviewToggleIsSavedAndSyncedAndDefaultsOff() {
        TileFiller server = server();
        TileFiller client = client();
        sync(server, client);
        Assertions.assertFalse(client.isPreviewEnabled(), "the preview starts off");

        server.setPreviewEnabled(true);
        sync(server, client);

        Assertions.assertTrue(client.isPreviewEnabled(), "the client sees the toggle");
        TileFiller reloaded = newFiller();
        reloaded.readData(ValueIoTestUtil.inputOf(ValueIoTestUtil.write(server::writeData)));
        Assertions.assertTrue(reloaded.isPreviewEnabled(), "the toggle survives a save and load");
    }
}
