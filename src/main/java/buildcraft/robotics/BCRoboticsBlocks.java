package buildcraft.robotics;

import net.neoforged.neoforge.registries.DeferredRegister;
import buildcraft.lib.misc.RegistrationUtilBC;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.robotics.block.BlockZonePlanner;

public class BCRoboticsBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCRobotics.MODID);

    // The Zone Planner block. The in-GUI 3D map viewport is deferred (see todos.md), but the block,
    // its GUI/slots, and item<->layer zone transfer are live, so it ships unconditionally now.
    // 1.12.2 Material.IRON → pickaxe required (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    public static final DeferredBlock<BlockZonePlanner> ZONE_PLANNER;

    static {
        ZONE_PLANNER = RegistrationUtilBC.registerBlock(BLOCKS, "zone_planner", BlockZonePlanner::new,
                () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    }

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
