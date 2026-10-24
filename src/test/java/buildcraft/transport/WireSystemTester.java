/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

import buildcraft.api.transport.EnumWirePart;

import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.lib.misc.PositionUtil;

import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.plug.PluggableGate;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;
import buildcraft.transport.wire.SavedDataWireSystems;
import buildcraft.transport.wire.WireSystem;

/**
 * Wire-system signaling regression tests.
 * <p>
 * Background: the {@link SavedDataWireSystems#tick()} loop used to leave {@code gatesChanged}
 * permanently {@code true} as a workaround for a then-broken
 * {@code GateLogic.resolveActions → SavedDataWireSystems.gatesChanged} signal path (the path was
 * gated on {@code BCModules.TRANSPORT.isLoaded()}, which always returned {@code false} in the
 * monolithic mod). That bodge made every server tick redundantly recompute every wire system on
 * the level. With the direct signal restored the reset is back, and these tests pin the new
 * behavior so the bodge can't slip back in.
 * <p>
 * Three coverage points:
 * <ol>
 *   <li><b>Steady-state ticks don't keep marking gates dirty</b> — after the wire system has
 *       processed an initial change, subsequent no-op ticks must leave {@code gatesChanged}
 *       false. If the bodge ever returns, this test fails immediately.</li>
 *   <li><b>Gate emit propagates and the flag resets</b> — manually setting the gate's
 *       broadcast set and marking gates changed must cause the wire to power up on the next
 *       tick, and the flag must be cleared after the recompute.</li>
 *   <li><b>{@code GateLogic.resolveActions} marks gates changed when broadcasts change</b> —
 *       this exercises the direct {@code SavedDataWireSystems.get(level).gatesChanged = true}
 *       call that replaced the dead reflective branch. The test deactivates a previously-set
 *       broadcast and verifies the wire system gets re-woken so the wire de-powers.</li>
 * </ol>
 * <p>
 * A second group pins who receives wire-system state: only players whose chunk tracking view includes a chunk of
 * the system ({@link WireSystem#isPlayerWatching}), and a chunk delivered to a player queues that player for a full
 * resend only when it holds a system ({@link SavedDataWireSystems#onChunkSent}, fed by the {@code ChunkWatchEvent.Sent}
 * listener in {@code BCTransport}). Both used to go to every player of the level.
 */
public class WireSystemTester {

    private static TilePipeHolder placeStructurePipe(GameTestHelper helper, BlockPos relPos) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_STRUCTURE.get()));
        return tile;
    }

    /** Install a fresh 1-slot CLAY_BRICK / AND / NO_MODIFIER gate on {@code side} of {@code tile}.
     *  This is the smallest possible gate variant — exactly one statement pair, no
     *  trigger/action arguments. With no statements configured, {@code resolveActions}
     *  ends each pass with an empty broadcast set, which is exactly the deactivate path
     *  test C exercises. */
    private static PluggableGate installGateOn(TilePipeHolder tile, Direction side) {
        GateVariant variant = new GateVariant(
            EnumGateLogic.AND,
            EnumGateMaterial.CLAY_BRICK,
            EnumGateModifier.NO_MODIFIER
        );
        PluggableGate gate = new PluggableGate(BCSiliconPlugs.gate, tile, side, variant);
        tile.replacePluggable(side, gate);
        return gate;
    }

    /** Reset the wire system to a clean steady state — the gate install above sets
     *  {@code gatesChanged} via {@code markStructureChanged}, so a single tick is needed to
     *  let the system process the structure change and clear the flags before the test
     *  proper begins. */
    private static SavedDataWireSystems flushToSteadyState(GameTestHelper helper) {
        SavedDataWireSystems wireSystems = SavedDataWireSystems.get(helper.getLevel());
        wireSystems.tick();
        helper.assertFalse(wireSystems.gatesChanged,
            "gatesChanged should reset to false after a tick processes the initial structure change");
        helper.assertFalse(wireSystems.structureChanged,
            "structureChanged should reset to false after a tick");
        return wireSystems;
    }

    // ---------- Test #1: steady-state ticks don't keep marking gates dirty ----------

    /**
     * After the wire system has processed an initial structure change, no further work should
     * happen on subsequent ticks. The bodge this test pins out was to never reset
     * {@code gatesChanged}, which meant every tick recomputed every wire system on the level —
     * cheap individually, but quadratic in the size of the player's logistics network.
     * <p>
     * Failure mode if the bodge returns: {@code gatesChanged} stays {@code true} after the
     * second or third tick and the assertion fires immediately.
     */
    public static void testSteadyStateLeavesGatesChangedFalse(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placeStructurePipe(helper, pipePos);
        tile.getWireManager().addPart(EnumWirePart.WEST_UP_NORTH, DyeColor.RED);
        installGateOn(tile, Direction.UP);

        SavedDataWireSystems wireSystems = flushToSteadyState(helper);

        // No emits, no structure changes — three ticks in a row should all leave the
        // flag at false. Three (not one) so we catch any "ping-pong" regression where one
        // pathway re-sets the flag based on the previous tick's state.
        for (int i = 0; i < 3; i++) {
            wireSystems.tick();
            helper.assertFalse(wireSystems.gatesChanged,
                "Tick " + i + ": gatesChanged should remain false in steady state");
        }
        helper.succeed();
    }

    // ---------- Test #2: gate emit propagates and the flag resets ----------

    /**
     * Setting a gate's broadcast set and marking gates changed must cause the wire to power up
     * on the next wire-system tick. After the recompute the flag must be reset to false.
     * <p>
     * This test populates {@code wireBroadcasts} via the {@code IWireEmitter.emitWire} entry
     * point directly (the same entry point {@code ActionPipeSignal.actionActivate} uses) and
     * manually sets {@code gatesChanged = true} on the wire-system store — that's exactly the
     * pair of operations the restored {@code GateLogic.resolveActions} flow performs end-to-end
     * when an action activates.
     */
    public static void testGateEmitPropagatesAndFlagResets(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placeStructurePipe(helper, pipePos);
        EnumWirePart wirePart = EnumWirePart.WEST_UP_NORTH;
        tile.getWireManager().addPart(wirePart, DyeColor.RED);
        PluggableGate gate = installGateOn(tile, Direction.UP);

        SavedDataWireSystems wireSystems = flushToSteadyState(helper);

        // Sanity: before anything emits, the wire is unpowered.
        helper.assertFalse(tile.getWireManager().isPowered(wirePart),
            "Wire should be unpowered before gate emits");

        // Activate the emit. emitWire mirrors what ActionPipeSignal.actionActivate does;
        // gatesChanged = true mirrors what GateLogic.resolveActions does after detecting a
        // change in the broadcast set.
        gate.emitWire(DyeColor.RED);
        wireSystems.gatesChanged = true;

        wireSystems.tick();

        helper.assertTrue(tile.getWireManager().isPowered(wirePart),
            "Wire should be powered after gate emits and wire system ticks");
        helper.assertFalse(wireSystems.gatesChanged,
            "gatesChanged should reset to false after the recompute, not stay true (the old bodge)");
        helper.succeed();
    }

    // ---------- Test #3: resolveActions signals wire system on deactivation ----------

    /**
     * The reverse of test #2: when a previously-emitting gate stops emitting, the wire system
     * must be re-woken so the wire de-powers. Pre-fix, the {@code GateLogic.resolveActions →
     * SavedDataWireSystems.gatesChanged} signal was a dead reflective branch gated on
     * {@code BCModules.TRANSPORT.isLoaded()}. The fix replaced it with a direct call into the
     * wire-system saved-data — this test exercises that call.
     * <p>
     * We seed the gate's broadcast set to RED, run a tick (wire powers up), then call
     * {@code resolveActions()} on a gate with no statements configured. With nothing active,
     * the resolve pass clears {@code wireBroadcasts} to empty; since that differs from the
     * previous {@code [RED]} set, the {@code if (!previousBroadcasts.equals(wireBroadcasts))}
     * block fires and the fix marks {@code gatesChanged} true. We assert that flag and then
     * tick to confirm the wire de-powers.
     */
    public static void testGateResolveActionsClearingMarksGatesChanged(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placeStructurePipe(helper, pipePos);
        EnumWirePart wirePart = EnumWirePart.WEST_UP_NORTH;
        tile.getWireManager().addPart(wirePart, DyeColor.RED);
        PluggableGate gate = installGateOn(tile, Direction.UP);

        SavedDataWireSystems wireSystems = flushToSteadyState(helper);

        // Bring the wire up by pretending an action just activated.
        gate.emitWire(DyeColor.RED);
        wireSystems.gatesChanged = true;
        wireSystems.tick();
        helper.assertTrue(tile.getWireManager().isPowered(wirePart),
            "Precondition: wire should be powered after emit + tick");

        // Now ask the gate to resolve. No statements are configured, so the active-action loop
        // doesn't repopulate wireBroadcasts — it clears to empty. previousBroadcasts ([RED])
        // differs from the new wireBroadcasts ([]), so the fix at GateLogic.java:528 runs and
        // marks the wire system dirty. This is the exact code path that was dead before.
        gate.logic.resolveActions();

        helper.assertTrue(wireSystems.gatesChanged,
            "GateLogic.resolveActions should mark gatesChanged = true when broadcasts change "
                + "(the direct call replaced the BCModules-gated reflective dead branch)");

        wireSystems.tick();

        helper.assertFalse(tile.getWireManager().isPowered(wirePart),
            "Wire should de-power once the gate stops emitting");
        helper.assertFalse(wireSystems.gatesChanged,
            "gatesChanged should reset to false after the second recompute");
        helper.succeed();
    }

    // ---------- Wire visibility: who receives a system's state ----------

    /** Two distinct cached fake players, so a test can watch with one and stand elsewhere with the other. */
    private static final GameProfile WATCHER = fixedProfile("bc_wire_watcher");
    private static final GameProfile BYSTANDER = fixedProfile("bc_wire_bystander");

    /** Blocks from the test's own arena to a chunk no other test (or arena) ever puts a wire in: 4096 chunks. */
    private static final int FAR_BLOCKS = 16 * 4096;

    /** Chunk-tracking radius used for a view that "sees" a chunk; at least 1, a radius of 0 contains nothing. */
    private static final int VIEW_RADIUS = 2;

    private static GameProfile fixedProfile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
    }

    private static ChunkPos chunkOf(BlockPos pos) {
        return PositionUtil.chunkContaining(pos);
    }

    /**
     * Wire-system state must reach only the players whose chunk tracking view includes a chunk of the system. Before
     * the fix the wire tick sent every system to every player of the level, so a player on the far side of the world
     * received wires it could never see.
     * <p>
     * A real wired pipe gives a one-chunk system. One fake player's view is centred on that chunk, the other's far
     * away: only the first is watching. Then the two swap places, and a player whose view is empty (a client that has
     * been sent no chunk yet) is watching nothing. Failure mode if the per-player check is lost: the bystander (or the
     * empty view) reads as watching.
     */
    public static void testWireVisibleOnlyToPlayersWhoseViewHoldsTheSystem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TilePipeHolder tile = placeStructurePipe(helper, new BlockPos(1, 2, 1));
        EnumWirePart part = EnumWirePart.WEST_UP_NORTH;
        tile.getWireManager().addPart(part, DyeColor.RED);

        List<WireSystem> systems = SavedDataWireSystems.get(level)
            .getWireSystemsWithElement(new WireSystem.WireElement(tile.getBlockPos(), part));
        helper.assertTrue(systems.size() == 1, "Precondition: exactly one wire system holds the new wire, got " + systems.size());
        WireSystem system = systems.get(0);

        ChunkPos here = chunkOf(tile.getBlockPos());
        ChunkPos far = chunkOf(tile.getBlockPos().offset(FAR_BLOCKS, 0, 0));

        try (FakePlayerUtil.Lease watcherLease = FakePlayerUtil.lease(level, WATCHER, null);
             FakePlayerUtil.Lease bystanderLease = FakePlayerUtil.lease(level, BYSTANDER, null)) {
            ServerPlayer watcher = watcherLease.player();
            ServerPlayer bystander = bystanderLease.player();
            try {
                watcher.setChunkTrackingView(ChunkTrackingView.of(here, VIEW_RADIUS));
                bystander.setChunkTrackingView(ChunkTrackingView.of(far, VIEW_RADIUS));
                helper.assertTrue(system.isPlayerWatching(watcher),
                    "A player whose view is centred on the system's chunk must be sent it");
                helper.assertFalse(system.isPlayerWatching(bystander),
                    "A player whose view is thousands of chunks away must not be sent the system");

                // The two walk past each other: visibility follows the view, not who was near when it was built.
                watcher.setChunkTrackingView(ChunkTrackingView.of(far, VIEW_RADIUS));
                bystander.setChunkTrackingView(ChunkTrackingView.of(here, VIEW_RADIUS));
                helper.assertFalse(system.isPlayerWatching(watcher),
                    "A player who moved away must stop being sent the system");
                helper.assertTrue(system.isPlayerWatching(bystander),
                    "A player who moved in must start being sent the system");

                watcher.setChunkTrackingView(ChunkTrackingView.EMPTY);
                helper.assertFalse(system.isPlayerWatching(watcher),
                    "A player with an empty view (no chunk sent yet) watches no system");
            } finally {
                watcher.setChunkTrackingView(ChunkTrackingView.EMPTY);
                bystander.setChunkTrackingView(ChunkTrackingView.EMPTY);
            }
        }
        helper.succeed();
    }

    /**
     * A system that spans several chunks is visible to a player who sees ANY one of them (1.12.2's per-player
     * {@code containsPlayer} over each of the system's chunks), and to nobody who sees none. Built straight from two
     * elements 40 chunks apart: {@code isPlayerWatching} reads only the cached chunk set, so no pipe is needed (a
     * real two-chunk system would need the random arena origin to straddle a chunk border).
     */
    public static void testWireSystemSpanningTwoChunksIsSeenFromEither(GameTestHelper helper) {
        BlockPos a = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos b = a.offset(16 * 40, 0, 0);
        BlockPos between = a.offset(16 * 20, 0, 0);
        WireSystem system = new WireSystem(ImmutableList.of(
            new WireSystem.WireElement(a, EnumWirePart.WEST_UP_NORTH),
            new WireSystem.WireElement(b, EnumWirePart.WEST_UP_NORTH)), DyeColor.RED);

        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(helper.getLevel(), WATCHER, null)) {
            ServerPlayer player = lease.player();
            try {
                player.setChunkTrackingView(ChunkTrackingView.of(chunkOf(a), VIEW_RADIUS));
                helper.assertTrue(system.isPlayerWatching(player), "Seeing only the first chunk is enough");
                player.setChunkTrackingView(ChunkTrackingView.of(chunkOf(b), VIEW_RADIUS));
                helper.assertTrue(system.isPlayerWatching(player), "Seeing only the second chunk is enough");
                player.setChunkTrackingView(ChunkTrackingView.of(chunkOf(between), VIEW_RADIUS));
                helper.assertFalse(system.isPlayerWatching(player), "Seeing neither chunk is not enough");
            } finally {
                player.setChunkTrackingView(ChunkTrackingView.EMPTY);
            }
        }
        helper.succeed();
    }

    /**
     * A chunk delivered to a player queues that player for a full wire resend only when a wire system has an element
     * in it. {@code onChunkSent} runs once per chunk delivered (thousands at login), and terrain streaming must never
     * trigger a resend: a chunk with no wires leaves the queue alone. The queue is consumed by the next wire tick.
     */
    public static void testChunkDeliveryQueuesPlayerOnlyForChunksHoldingWires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TilePipeHolder tile = placeStructurePipe(helper, new BlockPos(1, 2, 1));
        tile.getWireManager().addPart(EnumWirePart.WEST_UP_NORTH, DyeColor.RED);
        SavedDataWireSystems wireSystems = flushToSteadyState(helper);

        ChunkPos holding = chunkOf(tile.getBlockPos());
        ChunkPos empty = chunkOf(tile.getBlockPos().offset(FAR_BLOCKS, 0, 0));
        helper.assertFalse(wireSystems.wireSystems.keySet().stream()
                .anyMatch(system -> system.getChunkPosesAsStream().anyMatch(empty::equals)),
            "Precondition: no wire system has an element in the far chunk");

        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, WATCHER, null)) {
            ServerPlayer player = lease.player();
            try {
                helper.assertFalse(wireSystems.changedPlayers.contains(player),
                    "Precondition: the player is not queued before any chunk is delivered");

                wireSystems.onChunkSent(player, empty);
                helper.assertFalse(wireSystems.changedPlayers.contains(player),
                    "A chunk with no wires must not queue the player for a full resend");

                wireSystems.onChunkSent(player, holding);
                helper.assertTrue(wireSystems.changedPlayers.contains(player),
                    "A chunk holding a wire system must queue the player for a full resend");

                wireSystems.tick();
                helper.assertFalse(wireSystems.changedPlayers.contains(player),
                    "The wire tick must consume the queue, or the player is resent every tick");
            } finally {
                wireSystems.changedPlayers.remove(player);
            }
        }
        helper.succeed();
    }

    /**
     * The {@code ChunkWatchEvent.Sent} listener in {@code BCTransport} is what feeds {@code onChunkSent}: posting the
     * real event for a wire-holding chunk queues the player. Pins the wiring (the event, the level, the player and the
     * chunk position all reach {@code onChunkSent}) that a direct call cannot.
     */
    public static void testChunkSentEventQueuesPlayerForWireChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TilePipeHolder tile = placeStructurePipe(helper, new BlockPos(1, 2, 1));
        tile.getWireManager().addPart(EnumWirePart.WEST_UP_NORTH, DyeColor.RED);
        SavedDataWireSystems wireSystems = flushToSteadyState(helper);
        LevelChunk chunk = level.getChunkAt(tile.getBlockPos());

        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, WATCHER, null)) {
            ServerPlayer player = lease.player();
            try {
                helper.assertFalse(wireSystems.changedPlayers.contains(player),
                    "Precondition: the player is not queued before the chunk is sent");

                NeoForge.EVENT_BUS.post(new ChunkWatchEvent.Sent(player, chunk, level));

                helper.assertTrue(wireSystems.changedPlayers.contains(player),
                    "ChunkWatchEvent.Sent for a wire-holding chunk must queue the player through the BCTransport listener");
            } finally {
                wireSystems.changedPlayers.remove(player);
            }
        }
        helper.succeed();
    }
}
