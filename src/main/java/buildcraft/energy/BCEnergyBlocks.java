/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import buildcraft.energy.block.BlockEngineStone_BC8;
import buildcraft.energy.block.BlockEngineIron_BC8;
import buildcraft.energy.block.BlockEngineFE;
import buildcraft.energy.block.BlockDynamoMJ;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCBlockHolder;

public class BCEnergyBlocks {
    public static final BCRegistrar.Blocks BLOCKS = BCRegistrar.createBlocks(BCEnergy.MODID);

    // 1.12.2 Material.IRON → pickaxe required for drops (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    public static final BCBlockHolder<BlockEngineStone_BC8> ENGINE_STONE = BLOCKS.registerBlock(
            "engine_stone",
            BlockEngineStone_BC8::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.STONE).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockEngineIron_BC8> ENGINE_IRON = BLOCKS.registerBlock(
            "engine_iron",
            BlockEngineIron_BC8::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockEngineFE> ENGINE_FE = BLOCKS.registerBlock(
            "engine_rf",
            BlockEngineFE::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockDynamoMJ> DYNAMO_MJ = BLOCKS.registerBlock(
            "mj_dynamo",
            BlockDynamoMJ::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static void init() {
        BLOCKS.subscribe();
    }
}
