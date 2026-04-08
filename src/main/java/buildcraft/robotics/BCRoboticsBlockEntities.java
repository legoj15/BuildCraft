package buildcraft.robotics;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.robotics.tile.TileZonePlanner;

public class BCRoboticsBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCRobotics.MODID);

    public static final Supplier<BlockEntityType<TileZonePlanner>> ZONE_PLANNER =
            BLOCK_ENTITIES.register("zone_planner",
                    () -> new BlockEntityType<>(TileZonePlanner::new,
                            BCRoboticsBlocks.ZONE_PLANNER.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
