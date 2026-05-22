/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventPlaced;

import buildcraft.transport.pipe.behaviour.PipeBehaviourDaizuli;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Regression guard for pipe placement firing {@link PipeEventPlaced}.
 * <p>
 * The event class and its {@code @PipeEventHandler} dispatch were carried through the port,
 * but nothing ever constructed or fired the event — {@link TilePipeHolder#onPlacedBy} created
 * the pipe and registered its handlers without emitting it, so every {@code onPlaced} handler
 * (the Daizuli pipe's advancement unlock among them) was dead. This test registers a probe
 * handler on the tile's event bus before placement and asserts the probe receives the event
 * carrying the correct placer and item stack.
 */
public class PipeEventTester {

    public static void testPlacingPipeFiresPlacedEvent(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        helper.setBlock(pipePos, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder tile = helper.getBlockEntity(pipePos, TilePipeHolder.class);

        // The probe must be on the bus before onPlacedBy fires the event; onPlacedBy only
        // ever adds handlers, so a handler registered first survives the placement call.
        PlacedProbe probe = new PlacedProbe();
        tile.eventBus.registerHandler(probe);

        Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack daizuli = new ItemStack(BCTransportItems.PIPE_DAIZULI_ITEM.get());
        tile.onPlacedBy(placer, daizuli);

        helper.assertTrue(probe.fired,
                "Placing a pipe must fire PipeEventPlaced through the pipe's event bus");
        helper.assertTrue(probe.placer == placer,
                "PipeEventPlaced must carry the entity that placed the pipe");
        helper.assertTrue(probe.placeStack != null
                        && probe.placeStack.getItem() == BCTransportItems.PIPE_DAIZULI_ITEM.get(),
                "PipeEventPlaced must carry the placed pipe's item stack");
        helper.assertTrue(tile.getPipe() != null
                        && tile.getPipe().getBehaviour() instanceof PipeBehaviourDaizuli,
                "A Daizuli pipe item must produce a PipeBehaviourDaizuli");
        helper.succeed();
    }

    /** Test-only {@code @PipeEventHandler} that records the first {@link PipeEventPlaced} it sees. */
    public static class PlacedProbe {
        boolean fired = false;
        LivingEntity placer = null;
        ItemStack placeStack = null;

        @PipeEventHandler
        public void onPlaced(PipeEventPlaced event) {
            fired = true;
            placer = event.placer;
            placeStack = event.placeStack;
        }
    }
}
