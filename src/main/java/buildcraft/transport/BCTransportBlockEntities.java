/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.lib.misc.BlockEntityTypeUtilBC;
import buildcraft.transport.tile.TileFilteredBuffer;
import buildcraft.transport.tile.TilePipeHolder;

public class BCTransportBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCTransport.MODID);

    public static final Supplier<BlockEntityType<TileFilteredBuffer>> FILTERED_BUFFER =
            BLOCK_ENTITIES.register("filtered_buffer",
                    () -> BlockEntityTypeUtilBC.create(TileFilteredBuffer::new,
                            BCTransportBlocks.FILTERED_BUFFER.get()));

    public static final Supplier<BlockEntityType<TilePipeHolder>> PIPE_HOLDER =
            BLOCK_ENTITIES.register("pipe_holder",
                    () -> BlockEntityTypeUtilBC.create(TilePipeHolder::new,
                            BCTransportBlocks.PIPE_HOLDER.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
