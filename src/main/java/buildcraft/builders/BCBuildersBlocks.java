/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import buildcraft.lib.misc.RegistrationUtilBC;

import buildcraft.builders.block.BlockArchitectTable;
import buildcraft.builders.block.BlockBuilder;
import buildcraft.builders.block.BlockElectronicLibrary;
import buildcraft.builders.block.BlockFiller;
import buildcraft.builders.block.BlockFrame;
import buildcraft.builders.block.BlockQuarry;
import buildcraft.builders.block.BlockReplacer;

public class BCBuildersBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCBuilders.MODID);

    // 1.12.2 Material.IRON → pickaxe required for drops (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    // forceSolidOn(): the frame is a non-full-cube block, so by default blocksMotion()==false, which
    // makes FlowingFluid.canHoldFluid() true — flowing fluid (oil/water reaching the quarry) would
    // "wash it away", dropping the frame item (an unobtainable block → infinite-frame exploit).
    // Forcing solid makes blocksMotion()==true so fluid flows AROUND the frame instead of destroying
    // it. No other effect on a non-full block: suffocation/view-blocking/redstone all additionally
    // require a full collision cube, which the frame is not.
    public static final DeferredBlock<BlockFrame> FRAME = RegistrationUtilBC.registerBlock(BLOCKS,
            "frame",
            BlockFrame::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().forceSolidOn().sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockFiller> FILLER = RegistrationUtilBC.registerBlock(BLOCKS,
            "filler",
            BlockFiller::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockBuilder> BUILDER = RegistrationUtilBC.registerBlock(BLOCKS,
            "builder",
            BlockBuilder::new, () -> BlockBehaviour.Properties.of()
                .strength(5.0f, 10.0f).sound(SoundType.METAL)
                .requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockArchitectTable> ARCHITECT = RegistrationUtilBC.registerBlock(BLOCKS,
            "architect",
            BlockArchitectTable::new, () -> BlockBehaviour.Properties.of()
                .strength(5.0f, 10.0f).sound(SoundType.METAL)
                .requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockElectronicLibrary> LIBRARY = RegistrationUtilBC.registerBlock(BLOCKS,
            "library",
            BlockElectronicLibrary::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockReplacer> REPLACER = RegistrationUtilBC.registerBlock(BLOCKS,
            "replacer",
            BlockReplacer::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockQuarry> QUARRY = RegistrationUtilBC.registerBlock(BLOCKS,
            "quarry",
            BlockQuarry::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.ANVIL).requiresCorrectToolForDrops());

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
