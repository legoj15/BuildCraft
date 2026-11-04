/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;import net.neoforged.bus.api.IEventBus;

import buildcraft.builders.block.BlockArchitectTable;
import buildcraft.builders.block.BlockBuilder;
import buildcraft.builders.block.BlockElectronicLibrary;
import buildcraft.builders.block.BlockFiller;
import buildcraft.builders.block.BlockFrame;
import buildcraft.builders.block.BlockQuarry;
import buildcraft.builders.block.BlockReplacer;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCBlockHolder;

public class BCBuildersBlocks {
    public static final BCRegistrar.Blocks BLOCKS = BCRegistrar.createBlocks(BCBuilders.MODID);

    // 1.12.2 Material.IRON → pickaxe required for drops (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    // forceSolidOn(): the frame is a non-full-cube block, so by default blocksMotion()==false, which
    // makes FlowingFluid.canHoldFluid() true — flowing fluid (oil/water reaching the quarry) would
    // "wash it away", dropping the frame item (an unobtainable block → infinite-frame exploit).
    // Forcing solid makes blocksMotion()==true so fluid flows AROUND the frame instead of destroying
    // it. No other effect on a non-full block: suffocation/view-blocking/redstone all additionally
    // require a full collision cube, which the frame is not.
    // From 26.3 blocksMotion() is gone and both checks read block tags: the frame keeps this behaviour by
    // being in minecraft:blocks_motion_no_leaves and not in washed_away_by_fluids (BlockMotionTagsTester
    // derives those lists from isSolid(), which forceSolidOn() still drives).
    public static final BCBlockHolder<BlockFrame> FRAME = BLOCKS.registerBlock(
            "frame",
            BlockFrame::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().forceSolidOn().sound(SoundType.COPPER_GRATE).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockFiller> FILLER = BLOCKS.registerBlock(
            "filler",
            BlockFiller::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockBuilder> BUILDER = BLOCKS.registerBlock(
            "builder",
            BlockBuilder::new, () -> BlockBehaviour.Properties.of()
                .strength(5.0f, 10.0f).sound(SoundType.METAL)
                .requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockArchitectTable> ARCHITECT = BLOCKS.registerBlock(
            "architect",
            BlockArchitectTable::new, () -> BlockBehaviour.Properties.of()
                .strength(5.0f, 10.0f).sound(SoundType.METAL)
                .requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockElectronicLibrary> LIBRARY = BLOCKS.registerBlock(
            "library",
            BlockElectronicLibrary::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockReplacer> REPLACER = BLOCKS.registerBlock(
            "replacer",
            BlockReplacer::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockQuarry> QUARRY = BLOCKS.registerBlock(
            "quarry",
            BlockQuarry::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.ANVIL).requiresCorrectToolForDrops());

    public static void init(IEventBus modEventBus) {
        BLOCKS.subscribe();
    }
}
