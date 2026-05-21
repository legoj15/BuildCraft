package buildcraft.robotics;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.lib.BCLib;
import buildcraft.robotics.block.BlockZonePlanner;

public class BCRoboticsBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCRobotics.MODID);

    // Dev-only — the Zone Planner was never finished (incomplete in 1.12.2, and its port
    // never completed either). Gated behind -Dbuildcraft.dev=true; null in public releases.
    // 1.12.2 Material.IRON → pickaxe required (parity restored via
    // requiresCorrectToolForDrops + minecraft:mineable/pickaxe tag).
    public static final DeferredBlock<BlockZonePlanner> ZONE_PLANNER;

    static {
        ZONE_PLANNER = BCLib.DEV
                ? BLOCKS.registerBlock("zone_planner", BlockZonePlanner::new,
                        () -> BlockBehaviour.Properties.of().strength(5.0f, 10.0f).sound(SoundType.METAL).requiresCorrectToolForDrops())
                : null;
    }

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
