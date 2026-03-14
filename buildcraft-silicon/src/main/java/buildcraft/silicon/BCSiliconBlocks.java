package buildcraft.silicon;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.silicon.block.BlockLaser;

public class BCSiliconBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BCSilicon.MODID);

    public static final DeferredBlock<BlockLaser> LASER = BLOCKS.registerBlock(
            "laser",
            BlockLaser::new,
            BlockBehaviour.Properties.of().strength(5.0f, 10.0f).noOcclusion());

    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
