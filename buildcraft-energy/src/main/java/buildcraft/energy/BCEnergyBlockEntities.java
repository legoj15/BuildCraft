package buildcraft.energy;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.tile.TileSpringOil;

public class BCEnergyBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCEnergy.MODID);

    public static final Supplier<BlockEntityType<TileSpringOil>> SPRING_OIL = BLOCK_ENTITIES.register(
            "spring_oil",
            () -> new BlockEntityType<>(TileSpringOil::new, BCCoreBlocks.SPRING_OIL.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
