package buildcraft.silicon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A stub block for laser tables (Assembly Table, Advanced Crafting Table, Integration Table).
 * Currently a visual placeholder — tile entity and GUI will be added when the silicon module
 * is fully ported.
 */
public class BlockLaserTable extends Block {
    /** The 1.12 bounding box: full width, 9/16 height. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);

    public BlockLaserTable(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
