package buildcraft.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import buildcraft.api.enums.EnumSpring;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.lib.misc.data.XorShift128Random;

public class BlockSpring extends Block {
    public static final Property<EnumSpring> SPRING_TYPE = BuildCraftProperties.SPRING_TYPE;
    public static final XorShift128Random rand = new XorShift128Random();

    public BlockSpring(BlockBehaviour.Properties properties) {
        super(properties
                .strength(-1.0F, 3600000.0F) // Unbreakable, very high blast resistance
                .sound(SoundType.STONE)
                .randomTicks() // Equivalent to setTickRandomly(true)
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(SPRING_TYPE, EnumSpring.WATER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPRING_TYPE);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        generateSpringBlock(level, pos, state);
    }

    @Override
    public void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState oldState,
            boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // Schedule the next tick based on the spring type's tick rate
        level.scheduleTick(pos, this, state.getValue(SPRING_TYPE).tickRate);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        generateSpringBlock(level, pos, state);
    }

    private void generateSpringBlock(ServerLevel level, BlockPos pos, BlockState state) {
        EnumSpring spring = state.getValue(SPRING_TYPE);

        // Always reschedule for the continuous active tick
        level.scheduleTick(pos, this, spring.tickRate);

        if (!spring.canGen || spring.liquidBlock == null) {
            return;
        }

        BlockPos upPos = pos.above();
        if (!level.isEmptyBlock(upPos)) {
            return;
        }

        if (spring.chance != -1 && rand.nextInt(spring.chance) != 0) {
            return;
        }

        level.setBlock(upPos, spring.liquidBlock, 3);
    }
}
