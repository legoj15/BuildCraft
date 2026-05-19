/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.EnumWirePart;

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
 */
public class WireSystemTester {

    private static TilePipeHolder placeStructurePipe(GameTestHelper helper, BlockPos relPos) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
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
}
