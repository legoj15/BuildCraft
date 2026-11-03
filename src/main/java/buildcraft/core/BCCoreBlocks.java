/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package buildcraft.core;

import net.neoforged.neoforge.registries.DeferredRegister;
import buildcraft.lib.misc.RegistrationUtilBC;
import buildcraft.lib.misc.SoundUtil;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import buildcraft.api.enums.EnumSpring;
import buildcraft.core.block.BlockMarkerPath;
import buildcraft.core.block.BlockPowerConsumerTester;
import buildcraft.core.block.BlockSpring;
import buildcraft.core.block.BlockEngineRedstone_BC8;
import buildcraft.core.block.BlockEngineCreative;

public class BCCoreBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCCore.MODID);

    public static final DeferredBlock<BlockSpring> SPRING_WATER = RegistrationUtilBC.registerBlock(BLOCKS, "spring_water",
            props -> new BlockSpring(EnumSpring.WATER, props), () -> BlockBehaviour.Properties.of().sound(SoundType.STONE));

    public static final DeferredBlock<BlockSpring> SPRING_OIL = RegistrationUtilBC.registerBlock(BLOCKS, "spring_oil",
            props -> new BlockSpring(EnumSpring.OIL, props), () -> BlockBehaviour.Properties.of().sound(SoundType.STONE));

    // Decorated blocks — mirrors 1.12.2's buildcraftcore:decorated meta variants.
    // 1.12.2 Material.IRON → pickaxe required for drops (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    //
    // Only the LASER variant ever had a survival recipe in 1.12.2 (8 obsidian + redstone
    // block → 16); the other five 1.12.2 variants were creative-tab only and are not ported.
    public static final DeferredBlock<Block> DECORATED_LASER = BLOCKS.registerSimpleBlock(
            //? if >=1.21.10 {
            "decorated_laser", () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());
            //?} else {
            /*"decorated_laser", BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());*/
            //?}

    // Markers were Material.CIRCUITS in 1.12.2 → hand-breakable (no tool gate); torch-like, so they sound like a torch pitched up.
    private static final SoundType MARKER_SOUND = SoundUtil.pitched(SoundType.WOOD, 1.3f);

    public static final DeferredBlock<buildcraft.core.block.BlockMarkerVolume> MARKER_VOLUME = RegistrationUtilBC.registerBlock(BLOCKS,
            "marker_volume",
            buildcraft.core.block.BlockMarkerVolume::new, () -> BlockBehaviour.Properties.of().sound(MARKER_SOUND));

    public static final DeferredBlock<BlockMarkerPath> MARKER_PATH = RegistrationUtilBC.registerBlock(BLOCKS,
            "marker_path",
            BlockMarkerPath::new, () -> BlockBehaviour.Properties.of().sound(MARKER_SOUND));

    public static final DeferredBlock<BlockEngineRedstone_BC8> ENGINE_REDSTONE = RegistrationUtilBC.registerBlock(BLOCKS,
            "engine_redstone",
            BlockEngineRedstone_BC8::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.WOOD).requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockEngineCreative> ENGINE_CREATIVE = RegistrationUtilBC.registerBlock(BLOCKS,
            "engine_creative",
            BlockEngineCreative::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    // Debug block that sinks MJ power — also the standard MJ sink in the engine/pipe
    // game tests. 1.12.2 Material.IRON → pickaxe-required, metal sounds.
    public static final DeferredBlock<BlockPowerConsumerTester> POWER_TESTER = RegistrationUtilBC.registerBlock(BLOCKS,
            "power_tester",
            BlockPowerConsumerTester::new,
            () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
