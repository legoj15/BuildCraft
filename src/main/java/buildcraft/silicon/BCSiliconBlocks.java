/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import net.neoforged.neoforge.registries.DeferredRegister;
import buildcraft.lib.misc.RegistrationUtilBC;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import buildcraft.silicon.block.BlockLaser;
import buildcraft.silicon.block.BlockLaserTable;

public class BCSiliconBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCSilicon.MODID);

    // 1.12.2 Material.IRON → pickaxe required for drops (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    public static final DeferredBlock<BlockLaser> LASER = RegistrationUtilBC.registerBlock(BLOCKS,
            "laser",
            BlockLaser::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockLaserTable> ASSEMBLY_TABLE = RegistrationUtilBC.registerBlock(BLOCKS,
            "assembly_table",
            props -> new BlockLaserTable(props,
                BCSiliconBlockEntities.ASSEMBLY_TABLE), () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockLaserTable> ADVANCED_CRAFTING_TABLE = RegistrationUtilBC.registerBlock(BLOCKS,
            "advanced_crafting_table",
            props -> new BlockLaserTable(props,
                BCSiliconBlockEntities.ADVANCED_CRAFTING_TABLE), () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().sound(SoundType.METAL).requiresCorrectToolForDrops());

    // The robot Programming Table (Ph7) — board crafting, driven by lasers like its siblings.
    public static final DeferredBlock<BlockLaserTable> PROGRAMMING_TABLE = RegistrationUtilBC.registerBlock(BLOCKS,
            "programming_table",
            props -> new BlockLaserTable(props,
                BCSiliconBlockEntities.PROGRAMMING_TABLE), () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().sound(SoundType.METAL).requiresCorrectToolForDrops());

    // Shipped since Ph7 registered the robot integration recipe — the "no registered integration recipes" reason
    // for the old dev gate is gone. The table programs robots: robot + board in, laser MJ, programmed robot out.
    public static final DeferredBlock<BlockLaserTable> INTEGRATION_TABLE = RegistrationUtilBC.registerBlock(BLOCKS,
            "integration_table",
            props -> new BlockLaserTable(props,
                BCSiliconBlockEntities.INTEGRATION_TABLE), () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
