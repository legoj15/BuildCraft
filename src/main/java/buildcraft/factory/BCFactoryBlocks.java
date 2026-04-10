package buildcraft.factory;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
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

public class BCFactoryBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCFactory.MODID);

    public static final DeferredBlock<BlockAutoWorkbenchItems> AUTOWORKBENCH_ITEM = BLOCKS.registerBlock(
            "autoworkbench_item",
            BlockAutoWorkbenchItems::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL));

    public static final DeferredBlock<BlockMiningWell> MINING_WELL = BLOCKS.registerBlock(
            "mining_well",
            BlockMiningWell::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL));

    public static final DeferredBlock<BlockPump> PUMP = BLOCKS.registerBlock(
            "pump",
            BlockPump::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL));

    public static final DeferredBlock<BlockFloodGate> FLOOD_GATE = BLOCKS.registerBlock(
            "flood_gate",
            BlockFloodGate::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL));

    public static final DeferredBlock<BlockTank> TANK = BLOCKS.registerBlock(
            "tank",
            BlockTank::new, () -> BlockBehaviour.Properties.of().strength(0.3f).noOcclusion().sound(SoundType.GLASS));

    public static final DeferredBlock<BlockTube> TUBE = BLOCKS.registerBlock(
            "tube",
            BlockTube::new, () -> BlockBehaviour.Properties.of().destroyTime(-1.0f).noOcclusion().sound(SoundType.METAL));

    public static final DeferredBlock<BlockChute> CHUTE = BLOCKS.registerBlock(
            "chute",
            BlockChute::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().sound(SoundType.METAL));

    public static final DeferredBlock<BlockDistiller> DISTILLER = BLOCKS.registerBlock(
            "distiller",
            BlockDistiller::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().sound(SoundType.METAL));

    public static final DeferredBlock<BlockHeatExchange> HEAT_EXCHANGE = BLOCKS.registerBlock(
            "heat_exchange",
            BlockHeatExchange::new, () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion().sound(SoundType.METAL));

    public static final DeferredBlock<BlockWaterGel> WATER_GEL = BLOCKS.registerBlock(
            "water_gel",
            BlockWaterGel::new, () -> BlockBehaviour.Properties.of()
                    .strength(0.6f)
                    .sound(net.minecraft.world.level.block.SoundType.SLIME_BLOCK));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
