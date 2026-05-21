/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import buildcraft.core.BCCoreStatements;

import buildcraft.lib.statement.TriggerWrapper;

import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.plug.PluggableGate;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Regression coverage for the "Redstone Signal On" gate desync.
 * <p>
 * The server recomputes a gate's display state ({@code isOn}, {@code triggerOn},
 * {@code actionOn}) deterministically every tick in {@link GateLogic#resolveActions()}.
 * The bug was that {@link GateLogic#writeToNbt()} also persisted that runtime state, so the
 * block-entity update tag became a second sync channel: a stale snapshot serialised at an
 * earlier moment could land on a client (via {@code readConfigData}) and clobber the
 * freshly-recomputed value, leaving the gate stuck on/off until the next state change.
 * <ol>
 *   <li><b>{@link #testTriggerTracksRedstoneSignal}</b> — the server recompute itself is
 *       sound and level-driven; pins that the fault is in sync, not in {@code resolveActions}.</li>
 *   <li><b>{@link #testNbtSyncDoesNotClobberLiveState}</b> — reproduces the desync: an
 *       inbound NBT data sync must not resurrect runtime display state over a recompute.</li>
 * </ol>
 */
public class GateRedstoneSyncTester {

    /** Place a structure pipe with a basic (1-slot, CLAY_BRICK / AND) gate on its UP face and
     *  configure slot 0's trigger to "Redstone Signal On" with the ANY-side parameter — the
     *  exact gate configuration from the reported desync. */
    private static PluggableGate placePipeWithRedstoneGate(GameTestHelper helper, BlockPos pipePos) {
        helper.setBlock(pipePos, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder tile = helper.getBlockEntity(pipePos, TilePipeHolder.class);
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_STRUCTURE.get()));

        GateVariant variant = new GateVariant(
            EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER);
        PluggableGate gate = new PluggableGate(BCSiliconPlugs.gate, tile, Direction.UP, variant);
        tile.replacePluggable(Direction.UP, gate);
        gate.logic.statements[0].trigger.set(
            TriggerWrapper.wrap(BCCoreStatements.TRIGGER_REDSTONE_ACTIVE, null));
        return gate;
    }

    /**
     * Server-logic guard: the redstone trigger tracks the adjacent signal on every recompute,
     * including off → on → off → on, with no latching. Proves the desync is a client-sync
     * fault, not a {@code resolveActions} bug. Passes both before and after the fix.
     */
    public static void testTriggerTracksRedstoneSignal(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        BlockPos signalPos = new BlockPos(2, 2, 1);
        PluggableGate gate = placePipeWithRedstoneGate(helper, pipePos);

        gate.logic.resolveActions();
        helper.assertFalse(gate.logic.triggerOn[0],
            "Trigger should be inactive with no redstone source");

        helper.setBlock(signalPos, Blocks.REDSTONE_BLOCK);
        gate.logic.resolveActions();
        helper.assertTrue(gate.logic.triggerOn[0],
            "Trigger should activate with an adjacent redstone source");

        helper.setBlock(signalPos, Blocks.AIR);
        gate.logic.resolveActions();
        helper.assertFalse(gate.logic.triggerOn[0],
            "Trigger should deactivate once the redstone source is removed");

        helper.setBlock(signalPos, Blocks.REDSTONE_BLOCK);
        gate.logic.resolveActions();
        helper.assertTrue(gate.logic.triggerOn[0],
            "Trigger should re-activate — the recompute is level-driven, not a one-way latch");
        helper.succeed();
    }

    /**
     * Reproduces the desync. A stale "gate was on" NBT snapshot — exactly what a block-entity
     * update tag serialised at an earlier powered moment carries — must not overwrite the
     * server's recomputed off-state when it is applied via {@code readConfigData} (the method
     * {@code PluggableGate.readFromNbt} delegates to, invoked by {@code TilePipeHolder
     * .loadAdditional} on the receiving side of every block-entity update).
     * <p>
     * Pre-fix this fails: {@code readConfigData} reads {@code isOn}/{@code triggerOn}/
     * {@code actionOn} back, resurrecting the stale on-state. Post-fix {@code writeToNbt}
     * no longer emits them and {@code readConfigData} no longer consumes them.
     */
    public static void testNbtSyncDoesNotClobberLiveState(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        GateLogic logic = placePipeWithRedstoneGate(helper, pipePos).logic;

        // Snapshot a stale "gate was ON" state into NBT, the way a block-entity update tag
        // serialised while the gate was powered would carry it.
        logic.isOn = true;
        logic.triggerOn[0] = true;
        logic.actionOn[0] = true;
        CompoundTag staleSnapshot = logic.writeToNbt();

        // The server authoritatively recomputes: no redstone source is present, so the gate
        // is OFF. This is the state every server tick converges to.
        logic.resolveActions();
        helper.assertFalse(logic.isOn, "precondition: gate recomputed to OFF");
        helper.assertFalse(logic.triggerOn[0], "precondition: trigger recomputed to OFF");

        // A delayed block-entity update lands carrying the stale snapshot.
        logic.readConfigData(staleSnapshot);

        // Invariant: an inbound data sync must not resurrect runtime display state — only a
        // logic recompute may change it.
        helper.assertFalse(logic.isOn,
            "An NBT data sync clobbered the recomputed gate state — the desync bug");
        helper.assertFalse(logic.triggerOn[0],
            "An NBT data sync clobbered the recomputed trigger state");
        helper.assertFalse(logic.actionOn[0],
            "An NBT data sync clobbered the recomputed action state");
        helper.succeed();
    }
}
