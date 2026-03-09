/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.builders.block.BlockArchitectTable;
import buildcraft.builders.block.BlockBuilder;
import buildcraft.builders.block.BlockFiller;

public class BCBuildersBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCBuilders.MODID);

    public static final DeferredBlock<BlockFiller> FILLER = BLOCKS.registerBlock(
            "filler",
            BlockFiller::new, BlockBehaviour.Properties.of().strength(5.0f, 10.0f));

    public static final DeferredBlock<BlockBuilder> BUILDER = BLOCKS.registerBlock(
            "builder",
            BlockBuilder::new, BlockBehaviour.Properties.of().strength(5.0f, 10.0f));

    public static final DeferredBlock<BlockArchitectTable> ARCHITECT = BLOCKS.registerBlock(
            "architect",
            BlockArchitectTable::new, BlockBehaviour.Properties.of().strength(5.0f, 10.0f));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
