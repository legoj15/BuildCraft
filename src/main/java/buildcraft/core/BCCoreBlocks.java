/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package buildcraft.core;

import buildcraft.lib.misc.SoundUtil;
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
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCBlockHolder;

public class BCCoreBlocks {
    public static final BCRegistrar.Blocks BLOCKS = BCRegistrar.createBlocks(BCCore.MODID);

    public static final BCBlockHolder<BlockSpring> SPRING_WATER = BLOCKS.registerBlock("spring_water",
            props -> new BlockSpring(EnumSpring.WATER, props), () -> BlockBehaviour.Properties.of().sound(SoundType.STONE));

    public static final BCBlockHolder<BlockSpring> SPRING_OIL = BLOCKS.registerBlock("spring_oil",
            props -> new BlockSpring(EnumSpring.OIL, props), () -> BlockBehaviour.Properties.of().sound(SoundType.STONE));

    // Decorated blocks — mirrors 1.12.2's buildcraftcore:decorated meta variants.
    // 1.12.2 Material.IRON → pickaxe required for drops (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    //
    // Only the LASER variant ever had a survival recipe in 1.12.2 (8 obsidian + redstone
    // block → 16); the other five 1.12.2 variants were creative-tab only and are not ported.
    public static final BCBlockHolder<Block> DECORATED_LASER = BLOCKS.registerSimpleBlock(
            "decorated_laser", () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    // Markers were Material.CIRCUITS in 1.12.2 → hand-breakable (no tool gate); torch-like, so they sound like a torch pitched up.
    private static final SoundType MARKER_SOUND = SoundUtil.pitched(SoundType.WOOD, 1.3f);

    public static final BCBlockHolder<buildcraft.core.block.BlockMarkerVolume> MARKER_VOLUME = BLOCKS.registerBlock(
            "marker_volume",
            buildcraft.core.block.BlockMarkerVolume::new, () -> BlockBehaviour.Properties.of().sound(MARKER_SOUND));

    public static final BCBlockHolder<BlockMarkerPath> MARKER_PATH = BLOCKS.registerBlock(
            "marker_path",
            BlockMarkerPath::new, () -> BlockBehaviour.Properties.of().sound(MARKER_SOUND));

    public static final BCBlockHolder<BlockEngineRedstone_BC8> ENGINE_REDSTONE = BLOCKS.registerBlock(
            "engine_redstone",
            BlockEngineRedstone_BC8::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.WOOD).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockEngineCreative> ENGINE_CREATIVE = BLOCKS.registerBlock(
            "engine_creative",
            BlockEngineCreative::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    // Debug block that sinks MJ power — also the standard MJ sink in the engine/pipe
    // game tests. 1.12.2 Material.IRON → pickaxe-required, metal sounds.
    public static final BCBlockHolder<BlockPowerConsumerTester> POWER_TESTER = BLOCKS.registerBlock(
            "power_tester",
            BlockPowerConsumerTester::new,
            () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static void init(IEventBus modEventBus) {
        BLOCKS.subscribe();
    }
}
