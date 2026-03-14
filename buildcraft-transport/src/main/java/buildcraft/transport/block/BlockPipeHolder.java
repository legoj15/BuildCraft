/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.tile.TilePipeHolder;

public class BlockPipeHolder extends Block implements EntityBlock {

    // Center box: 4/16 → 12/16 (i.e. 0.25→0.75)
    private static final VoxelShape CENTER = Block.box(4, 4, 4, 12, 12, 12);
    // Connection arms for each direction
    private static final VoxelShape ARM_DOWN  = Block.box(4, 0, 4, 12, 4, 12);
    private static final VoxelShape ARM_UP    = Block.box(4, 12, 4, 12, 16, 12);
    private static final VoxelShape ARM_NORTH = Block.box(4, 4, 0, 12, 12, 4);
    private static final VoxelShape ARM_SOUTH = Block.box(4, 4, 12, 12, 12, 16);
    private static final VoxelShape ARM_WEST  = Block.box(0, 4, 4, 4, 12, 12);
    private static final VoxelShape ARM_EAST  = Block.box(12, 4, 4, 16, 12, 12);
    private static final VoxelShape[] ARMS = { ARM_DOWN, ARM_UP, ARM_NORTH, ARM_SOUTH, ARM_WEST, ARM_EAST };

    public BlockPipeHolder(Properties props) {
        super(props);
    }

    // Block Entity

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TilePipeHolder(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == BCTransportBlockEntities.PIPE_HOLDER.get()) {
            return (lvl, pos, st, be) -> ((TilePipeHolder) be).tick();
        }
        return null;
    }

    // Shape

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TilePipeHolder tile && tile.getPipe() != null) {
            VoxelShape shape = CENTER;
            var pipe = tile.getPipe();
            for (Direction dir : Direction.values()) {
                if (pipe.isConnected(dir)) {
                    shape = Shapes.or(shape, ARMS[dir.ordinal()]);
                }
            }
            return shape;
        }
        return CENTER;
    }

    // Rendering — invisible (no baked model), rendered by BER later
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    // Placement

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TilePipeHolder tile) {
            tile.onPlacedBy(placer, stack);
        }
    }

    // Interaction

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TilePipeHolder tile && tile.getPipe() != null) {
            var pipe = tile.getPipe();
            if (pipe.getBehaviour().onPipeActivate(player, hitResult, 
                    (float) hitResult.getLocation().x, (float) hitResult.getLocation().y, 
                    (float) hitResult.getLocation().z, 
                    buildcraft.api.core.EnumPipePart.CENTER)) {
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    // Block removal — drops pipe item
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TilePipeHolder tile && !level.isClientSide() && !player.isCreative()) {
            tile.dropPipeItems(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // Neighbour changes → update pipe connections
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, 
                                @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TilePipeHolder tile && tile.getPipe() != null) {
            tile.getPipe().markForUpdate();
        }
    }

    // Pick block (middle click) — return the correct pipe item
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TilePipeHolder tile && tile.getPipe() != null) {
            Pipe pipe = tile.getPipe();
            PipeDefinition def = pipe.getDefinition();
            Item item = (Item) PipeApi.pipeRegistry.getItemForPipe(def);
            if (item != null) {
                return new ItemStack(item);
            }
        }
        return super.getCloneItemStack(level, pos, state, includeData, player);
    }

    // Sprint particles — pipes use INVISIBLE render shape so vanilla won't create particles.
    // We override to spawn them manually, matching 1.12.2 behaviour.
    @Override
    public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TilePipeHolder tile && tile.getPipe() != null) {
                // Spawn a BLOCK particle using the block state — the client particle engine
                // will use the pipe's particle texture (set via our block model).
                var random = level.getRandom();
                double x = entity.getX() + (random.nextFloat() - 0.5) * entity.getBbWidth();
                double y = entity.getBoundingBox().minY + 0.1;
                double z = entity.getZ() + (random.nextFloat() - 0.5) * entity.getBbWidth();
                level.addParticle(
                    new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                    x, y, z,
                    -entity.getDeltaMovement().x * 4.0, 1.5, -entity.getDeltaMovement().z * 4.0
                );
                return true;
            }
        }
        return false;
    }
}
