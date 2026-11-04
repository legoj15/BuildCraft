/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import buildcraft.platform.ChunkTickets;

/**
 * {@link ChunkTickets} on NeoForge: one {@link TicketController}, whose tickets NeoForge persists with the world. It must
 * be registered on the mod event bus ({@link #register}) or NeoForge discards its tickets on load.
 */
public final class NeoForgeChunkTickets implements ChunkTickets {
    private static final TicketController CONTROLLER =
        new TicketController(Identifier.fromNamespaceAndPath("buildcraftunofficial", "chunkloader"));

    @Override
    public void register() {
        // Called from the mod constructor, where the active container is this mod's.
        IEventBus modEventBus = ModLoadingContext.get().getActiveContainer().getEventBus();
        modEventBus.addListener((RegisterTicketControllersEvent event) -> event.register(CONTROLLER));
    }

    @Override
    public void forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add) {
        CONTROLLER.forceChunk(level, owner, chunkX, chunkZ, add, false);
    }
}
//?}
