/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package buildcraft.factory;

import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import buildcraft.factory.block.BlockAutoWorkbenchItems;
import buildcraft.factory.block.BlockMiningWell;
import buildcraft.factory.block.BlockFloodGate;
import buildcraft.factory.block.BlockPump;
import buildcraft.factory.block.BlockTank;
import buildcraft.factory.block.BlockTube;
import buildcraft.factory.block.BlockChute;
import buildcraft.factory.block.BlockDistiller;
import buildcraft.factory.block.BlockHeatExchange;
import buildcraft.factory.block.BlockWaterGel;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCBlockHolder;

public class BCFactoryBlocks {
    public static final BCRegistrar.Blocks BLOCKS = BCRegistrar.createBlocks(BCFactory.MODID);

    // 1.12.2 Material.ROCK → pickaxe required.
    public static final BCBlockHolder<BlockAutoWorkbenchItems> AUTOWORKBENCH_ITEM = BLOCKS.registerBlock(
            "autoworkbench_item",
            BlockAutoWorkbenchItems::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    // 1.12.2 Material.IRON → pickaxe required (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    public static final BCBlockHolder<BlockMiningWell> MINING_WELL = BLOCKS.registerBlock(
            "mining_well",
            BlockMiningWell::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockPump> PUMP = BLOCKS.registerBlock(
            "pump",
            BlockPump::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockFloodGate> FLOOD_GATE = BLOCKS.registerBlock(
            "flood_gate",
            BlockFloodGate::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockTank> TANK = BLOCKS.registerBlock(
            "tank",
            BlockTank::new, () -> BlockBehaviour.Properties.of().strength(0.3f).noOcclusion().sound(SoundType.GLASS).requiresCorrectToolForDrops());

    // Tube is owned by the mining well / pump and stays unbreakable in survival
    // (destroyTime(-1.0f)); no tool gate needed.
    public static final BCBlockHolder<BlockTube> TUBE = BLOCKS.registerBlock(
            "tube",
            BlockTube::new, () -> BlockBehaviour.Properties.of().destroyTime(-1.0f).noOcclusion().sound(SoundType.METAL));

    public static final BCBlockHolder<BlockChute> CHUTE = BLOCKS.registerBlock(
            "chute",
            BlockChute::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockDistiller> DISTILLER = BLOCKS.registerBlock(
            "distiller",
            BlockDistiller::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final BCBlockHolder<BlockHeatExchange> HEAT_EXCHANGE = BLOCKS.registerBlock(
            "heat_exchange",
            BlockHeatExchange::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().sound(SoundType.METAL).requiresCorrectToolForDrops());

    // 1.12.2 Material.CLAY → hand-breakable; getDrops returns the gelled_water item.
    public static final BCBlockHolder<BlockWaterGel> WATER_GEL = BLOCKS.registerBlock(
            "water_gel",
            BlockWaterGel::new, () -> BlockBehaviour.Properties.of()
                    .strength(0.6f)
                    .sound(net.minecraft.world.level.block.SoundType.SLIME_BLOCK));

    public static void init(IEventBus modEventBus) {
        BLOCKS.subscribe();
    }
}
