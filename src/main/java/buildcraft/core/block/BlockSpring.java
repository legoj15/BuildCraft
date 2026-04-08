package buildcraft.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.enums.EnumSpring;
import buildcraft.lib.misc.data.XorShift128Random;

public class BlockSpring extends Block {
    public static final XorShift128Random rand = new XorShift128Random();

    private final EnumSpring springType;

    public BlockSpring(EnumSpring springType, BlockBehaviour.Properties properties) {
        super(properties
                .strength(-1.0F, 3600000.0F) // Unbreakable, very high blast resistance
                .sound(SoundType.STONE)
                .randomTicks() // Equivalent to setTickRandomly(true)
        );
        this.springType = springType;
    }

    public EnumSpring getSpringType() {
        return springType;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        generateSpringBlock(level, pos);
    }

    @Override
    public void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState oldState,
            boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // Schedule the next tick based on the spring type's tick rate
        level.scheduleTick(pos, this, springType.tickRate);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        generateSpringBlock(level, pos);
    }

    private void generateSpringBlock(ServerLevel level, BlockPos pos) {
        // Always reschedule for the continuous active tick
        level.scheduleTick(pos, this, springType.tickRate);

        if (!springType.canGen || springType.liquidBlock == null) {
            return;
        }

        BlockPos upPos = pos.above();
        if (!level.isEmptyBlock(upPos)) {
            return;
        }

        if (springType.chance != -1 && rand.nextInt(springType.chance) != 0) {
            return;
        }

        level.setBlock(upPos, springType.liquidBlock, 3);
    }
}
