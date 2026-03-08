package buildcraft.energy;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.energy.block.BlockEngineStone_BC8;
import buildcraft.energy.block.BlockEngineIron_BC8;

public class BCEnergyBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCEnergy.MODID);

    public static final DeferredBlock<BlockEngineStone_BC8> ENGINE_STONE = BLOCKS.registerBlock(
            "engine_stone",
            BlockEngineStone_BC8::new, BlockBehaviour.Properties.of().strength(3.0f));

    public static final DeferredBlock<BlockEngineIron_BC8> ENGINE_IRON = BLOCKS.registerBlock(
            "engine_iron",
            BlockEngineIron_BC8::new, BlockBehaviour.Properties.of().strength(3.0f));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
