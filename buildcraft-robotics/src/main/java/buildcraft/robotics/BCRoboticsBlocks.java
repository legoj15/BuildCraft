package buildcraft.robotics;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.robotics.block.BlockZonePlanner;

public class BCRoboticsBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCRobotics.MODID);

    public static final DeferredBlock<BlockZonePlanner> ZONE_PLANNER = BLOCKS.registerBlock(
            "zone_planner",
            BlockZonePlanner::new,
            BlockBehaviour.Properties.of().strength(5.0f, 10.0f));

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
