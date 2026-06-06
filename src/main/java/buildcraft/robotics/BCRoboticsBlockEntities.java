package buildcraft.robotics;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.lib.BCLib;
import buildcraft.lib.misc.BlockEntityTypeUtilBC;
import buildcraft.robotics.tile.TileZonePlanner;

public class BCRoboticsBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCRobotics.MODID);

    // Dev-only — mirrors BCRoboticsBlocks.ZONE_PLANNER. Null in public releases.
    public static final Supplier<BlockEntityType<TileZonePlanner>> ZONE_PLANNER;

    static {
        ZONE_PLANNER = (BCLib.DEV && BCRoboticsBlocks.ZONE_PLANNER != null)
                ? BLOCK_ENTITIES.register("zone_planner",
                        () -> BlockEntityTypeUtilBC.create(TileZonePlanner::new, BCRoboticsBlocks.ZONE_PLANNER.get()))
                : null;
    }

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
