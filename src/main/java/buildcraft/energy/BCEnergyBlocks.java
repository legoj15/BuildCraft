package buildcraft.energy;

import net.neoforged.neoforge.registries.DeferredRegister;
import buildcraft.lib.misc.RegistrationUtilBC;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import buildcraft.energy.block.BlockEngineStone_BC8;
import buildcraft.energy.block.BlockEngineIron_BC8;
import buildcraft.energy.block.BlockEngineFE;
import buildcraft.energy.block.BlockDynamoMJ;

public class BCEnergyBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCEnergy.MODID);

    // 1.12.2 Material.IRON → pickaxe required for drops (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    public static final DeferredBlock<BlockEngineStone_BC8> ENGINE_STONE = RegistrationUtilBC.registerBlock(BLOCKS,
            "engine_stone",
            BlockEngineStone_BC8::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockEngineIron_BC8> ENGINE_IRON = RegistrationUtilBC.registerBlock(BLOCKS,
            "engine_iron",
            BlockEngineIron_BC8::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockEngineFE> ENGINE_FE = RegistrationUtilBC.registerBlock(BLOCKS,
            "engine_rf",
            BlockEngineFE::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockDynamoMJ> DYNAMO_MJ = RegistrationUtilBC.registerBlock(BLOCKS,
            "mj_dynamo",
            BlockDynamoMJ::new, () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
