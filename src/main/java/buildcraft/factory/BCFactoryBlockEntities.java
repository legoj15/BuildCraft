package buildcraft.factory;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.lib.misc.BlockEntityTypeUtilBC;
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
                    () -> BlockEntityTypeUtilBC.create(TileAutoWorkbenchItems::new,
                            BCFactoryBlocks.AUTOWORKBENCH_ITEM.get()));

    public static final Supplier<BlockEntityType<TileMiningWell>> MINING_WELL =
            BLOCK_ENTITIES.register("mining_well",
                    () -> BlockEntityTypeUtilBC.create(TileMiningWell::new,
                            BCFactoryBlocks.MINING_WELL.get()));

    public static final Supplier<BlockEntityType<TilePump>> PUMP =
            BLOCK_ENTITIES.register("pump",
                    () -> BlockEntityTypeUtilBC.create(TilePump::new,
                            BCFactoryBlocks.PUMP.get()));

    public static final Supplier<BlockEntityType<TileFloodGate>> FLOOD_GATE =
            BLOCK_ENTITIES.register("flood_gate",
                    () -> BlockEntityTypeUtilBC.create(TileFloodGate::new,
                            BCFactoryBlocks.FLOOD_GATE.get()));

    public static final Supplier<BlockEntityType<TileTank>> TANK =
            BLOCK_ENTITIES.register("tank",
                    () -> BlockEntityTypeUtilBC.create(TileTank::new,
                            BCFactoryBlocks.TANK.get()));

    public static final Supplier<BlockEntityType<TileChute>> CHUTE =
            BLOCK_ENTITIES.register("chute",
                    () -> BlockEntityTypeUtilBC.create(TileChute::new,
                            BCFactoryBlocks.CHUTE.get()));

    public static final Supplier<BlockEntityType<TileDistiller_BC8>> DISTILLER =
            BLOCK_ENTITIES.register("distiller",
                    () -> BlockEntityTypeUtilBC.create(TileDistiller_BC8::new,
                            BCFactoryBlocks.DISTILLER.get()));

    public static final Supplier<BlockEntityType<TileHeatExchange>> HEAT_EXCHANGE =
            BLOCK_ENTITIES.register("heat_exchange",
                    () -> BlockEntityTypeUtilBC.create(TileHeatExchange::new,
                            BCFactoryBlocks.HEAT_EXCHANGE.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
