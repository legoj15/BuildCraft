package buildcraft.energy;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.tile.TileSpringOil;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.energy.tile.TileEngineIron_BC8;

public class BCEnergyBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCEnergy.MODID);

    public static final Supplier<BlockEntityType<TileSpringOil>> SPRING_OIL = BLOCK_ENTITIES.register(
            "spring_oil",
            () -> new BlockEntityType<>(TileSpringOil::new, BCCoreBlocks.SPRING_OIL.get()));

    public static final Supplier<BlockEntityType<TileEngineStone_BC8>> ENGINE_STONE = BLOCK_ENTITIES.register(
            "engine_stone",
            () -> new BlockEntityType<>(TileEngineStone_BC8::new, BCEnergyBlocks.ENGINE_STONE.get()));

    public static final Supplier<BlockEntityType<TileEngineIron_BC8>> ENGINE_IRON = BLOCK_ENTITIES.register(
            "engine_iron",
            () -> new BlockEntityType<>(TileEngineIron_BC8::new, BCEnergyBlocks.ENGINE_IRON.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}

