package buildcraft.factory;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.factory.tile.TileMiningWell;
import buildcraft.factory.tile.TileFloodGate;
import buildcraft.factory.tile.TilePump;
import buildcraft.factory.tile.TileTank;
import buildcraft.factory.tile.TileChute;
import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.factory.tile.TileHeatExchange;

public class BCFactoryBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCFactory.MODID);

    public static final Supplier<BlockEntityType<TileAutoWorkbenchItems>> AUTO_WORKBENCH_ITEMS =
            BLOCK_ENTITIES.register("autoworkbench_item",
                    () -> new BlockEntityType<>(TileAutoWorkbenchItems::new,
                            BCFactoryBlocks.AUTOWORKBENCH_ITEM.get()));

    public static final Supplier<BlockEntityType<TileMiningWell>> MINING_WELL =
            BLOCK_ENTITIES.register("mining_well",
                    () -> new BlockEntityType<>(TileMiningWell::new,
                            BCFactoryBlocks.MINING_WELL.get()));

    public static final Supplier<BlockEntityType<TilePump>> PUMP =
            BLOCK_ENTITIES.register("pump",
                    () -> new BlockEntityType<>(TilePump::new,
                            BCFactoryBlocks.PUMP.get()));

    public static final Supplier<BlockEntityType<TileFloodGate>> FLOOD_GATE =
            BLOCK_ENTITIES.register("flood_gate",
                    () -> new BlockEntityType<>(TileFloodGate::new,
                            BCFactoryBlocks.FLOOD_GATE.get()));

    public static final Supplier<BlockEntityType<TileTank>> TANK =
            BLOCK_ENTITIES.register("tank",
                    () -> new BlockEntityType<>(TileTank::new,
                            BCFactoryBlocks.TANK.get()));

    public static final Supplier<BlockEntityType<TileChute>> CHUTE =
            BLOCK_ENTITIES.register("chute",
                    () -> new BlockEntityType<>(TileChute::new,
                            BCFactoryBlocks.CHUTE.get()));

    public static final Supplier<BlockEntityType<TileDistiller_BC8>> DISTILLER =
            BLOCK_ENTITIES.register("distiller",
                    () -> new BlockEntityType<>(TileDistiller_BC8::new,
                            BCFactoryBlocks.DISTILLER.get()));

    public static final Supplier<BlockEntityType<TileHeatExchange>> HEAT_EXCHANGE =
            BLOCK_ENTITIES.register("heat_exchange",
                    () -> new BlockEntityType<>(TileHeatExchange::new,
                            BCFactoryBlocks.HEAT_EXCHANGE.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
