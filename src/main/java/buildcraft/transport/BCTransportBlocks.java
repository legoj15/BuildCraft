/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import buildcraft.transport.block.BlockFilteredBuffer;
import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCBlockHolder;

public class BCTransportBlocks {
    public static final BCRegistrar.Blocks BLOCKS = BCRegistrar.createBlocks(BCTransport.MODID);

    public static final BCBlockHolder<BlockFilteredBuffer> FILTERED_BUFFER = BLOCKS.registerBlock(
            "filtered_buffer",
            BlockFilteredBuffer::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    // Pipes are intentionally hand-breakable: a bare-hand break drops the pipe + its cargo +
    // pluggables + wires. The mineable/pickaxe tag stays (so pickaxes still get the break-speed
    // bonus) but requiresCorrectToolForDrops is deliberately NOT set. Player breaks drop via code
    // (BlockPipeHolder#playerWillDestroy → TilePipeHolder#dropPipeItems); the pipe_holder loot table
    // exists so NON-player removals (wither, /…destroy commands, explosions) return the pipe's
    // hardware too, through BlockPipeHolder#getDrops — see that method for the dynamic-drop wiring.
    public static final BCBlockHolder<BlockPipeHolder> PIPE_HOLDER = BLOCKS.registerBlock(
            "pipe_holder",
            BlockPipeHolder::new, () -> BlockBehaviour.Properties.of()
                .strength(0.25f, 3.0f)
                .noOcclusion()
                .dynamicShape()
                .sound(SoundType.METAL));

    public static void init(IEventBus modEventBus) {
        BLOCKS.subscribe();
    }
}
