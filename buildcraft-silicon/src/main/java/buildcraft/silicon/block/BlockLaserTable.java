package buildcraft.silicon.block;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.api.mj.ILaserTargetBlock;
import buildcraft.silicon.tile.TileLaserTableBase;

/**
 * Block for laser tables (Assembly Table, Advanced Crafting Table, Integration Table).
 * Implements ILaserTargetBlock so lasers can find and target it, and EntityBlock
 * so each variant creates the correct block entity.
 */
public class BlockLaserTable extends Block implements ILaserTargetBlock, EntityBlock {
    /** The 1.12 bounding box: full width, 9/16 height. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);

    private final Supplier<? extends BlockEntityType<? extends TileLaserTableBase>> beTypeSupplier;

    public BlockLaserTable(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<? extends TileLaserTableBase>> beTypeSupplier) {
        super(properties);
        this.beTypeSupplier = beTypeSupplier;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beTypeSupplier.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof TileLaserTableBase table) {
                table.serverTick();
            }
        };
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.player.Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileLaserTableBase table) {
                NonNullList<ItemStack> drops = NonNullList.create();
                table.addDrops(drops, 0);
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        Block.popResource(level, pos, drop);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
