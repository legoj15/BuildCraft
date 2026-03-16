/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.api.blocks.ICustomPaintHandler;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.tile.TilePipeHolder;

public class BlockPipeHolder extends Block implements EntityBlock, ICustomPaintHandler {

    // Center box: 4/16 → 12/16 (i.e. 0.25→0.75)
    private static final VoxelShape CENTER = Block.box(4, 4, 4, 12, 12, 12);
    // Connection arms for each direction.
    // A 0.01-pixel epsilon gap prevents Shapes.or() from merging arms with the center,
    // giving the wireframe outline visible segment edges like 1.12.2.
    private static final double E = 0.01;
    private static final VoxelShape ARM_DOWN  = Block.box(4, 0, 4, 12, 4 - E, 12);
    private static final VoxelShape ARM_UP    = Block.box(4, 12 + E, 4, 12, 16, 12);
    private static final VoxelShape ARM_NORTH = Block.box(4, 4, 0, 12, 12, 4 - E);
    private static final VoxelShape ARM_SOUTH = Block.box(4, 4, 12 + E, 12, 12, 16);
    private static final VoxelShape ARM_WEST  = Block.box(0, 4, 4, 4 - E, 12, 12);
    private static final VoxelShape ARM_EAST  = Block.box(12 + E, 4, 4, 16, 12, 12);
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

    /**
     * Returns the full composite shape (center + all connected arms).
     * Used for entity collision and server-side logic.
     */
    private VoxelShape getFullShape(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TilePipeHolder tile && tile.getPipe() != null) {
            VoxelShape shape = CENTER;
            var pipe = tile.getPipe();
            for (Direction dir : Direction.values()) {
                if (pipe.isConnected(dir)) {
                    shape = Shapes.or(shape, ARMS[dir.ordinal()]);
                }
                // Include pluggable bounding boxes
                PipePluggable plug = tile.getPluggable(dir);
                if (plug != null) {
                    AABB box = plug.getBoundingBox();
                    shape = Shapes.or(shape, Shapes.create(box));
                }
            }
            return shape;
        }
        return CENTER;
    }

    /**
     * Selection/outline shape. On the client, returns only the hovered segment's shape
     * so that the wireframe outline highlights just that segment (like 1.12.2).
     * Falls back to the full composite for ray tracing when no segment is targeted.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TilePipeHolder tile && tile.getPipe() != null) {
            // Client-side per-segment highlighting
            if (level instanceof net.minecraft.world.level.Level realLevel && realLevel.isClientSide()) {
                net.minecraft.world.phys.HitResult hit = net.minecraft.client.Minecraft.getInstance().hitResult;
                if (hit instanceof BlockHitResult blockHit && pos.equals(blockHit.getBlockPos())) {
                    var pipe = tile.getPipe();
                    double lx = blockHit.getLocation().x - pos.getX();
                    double ly = blockHit.getLocation().y - pos.getY();
                    double lz = blockHit.getLocation().z - pos.getZ();

                    // Check pluggables first — they overlap with arm regions
                    for (Direction dir : Direction.values()) {
                        PipePluggable plug = tile.getPluggable(dir);
                        if (plug != null) {
                            AABB box = plug.getBoundingBox();
                            if (lx >= box.minX && lx <= box.maxX
                                && ly >= box.minY && ly <= box.maxY
                                && lz >= box.minZ && lz <= box.maxZ) {
                                return Shapes.create(box);
                            }
                        }
                    }

                    // If the hit point is outside the center's 0.25–0.75 range, it's in an arm
                    if (ly < 0.25 && pipe.isConnected(Direction.DOWN))  return ARMS[Direction.DOWN.ordinal()];
                    if (ly > 0.75 && pipe.isConnected(Direction.UP))    return ARMS[Direction.UP.ordinal()];
                    if (lz < 0.25 && pipe.isConnected(Direction.NORTH)) return ARMS[Direction.NORTH.ordinal()];
                    if (lz > 0.75 && pipe.isConnected(Direction.SOUTH)) return ARMS[Direction.SOUTH.ordinal()];
                    if (lx < 0.25 && pipe.isConnected(Direction.WEST))  return ARMS[Direction.WEST.ordinal()];
                    if (lx > 0.75 && pipe.isConnected(Direction.EAST))  return ARMS[Direction.EAST.ordinal()];

                    // Hit is in the center region — return just the center
                    return CENTER;
                }
            }
        }
        // Server-side or no hit info: return full composite for ray tracing
        return getFullShape(level, pos);
    }

    /**
     * Collision shape — always the full composite so entities collide with all segments.
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getFullShape(level, pos);
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
            buildcraft.api.core.EnumPipePart hitPart = getHitPart(tile, hitResult);

            // Check if clicking on a pluggable — activate it
            if (hitPart != buildcraft.api.core.EnumPipePart.CENTER && hitPart.face != null) {
                buildcraft.api.transport.pluggable.PipePluggable existing = tile.getPluggable(hitPart.face);
                if (existing != null) {
                    if (existing.onPluggableActivate(player, hitResult,
                            (float) hitResult.getLocation().x, (float) hitResult.getLocation().y,
                            (float) hitResult.getLocation().z)) {
                        return InteractionResult.SUCCESS;
                    }
                }
            }

            if (pipe.getBehaviour().onPipeActivate(player, hitResult, 
                    (float) hitResult.getLocation().x, (float) hitResult.getLocation().y, 
                    (float) hitResult.getLocation().z, 
                    hitPart)) {
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TilePipeHolder tile) || tile.getPipe() == null) {
            return InteractionResult.PASS;
        }

        // Determine which face was clicked
        Direction realSide = getHitFace(tile, hitResult);
        if (realSide == null) {
            realSide = hitResult.getDirection();
        }

        // Try to place a pluggable
        if (stack.getItem() instanceof buildcraft.api.transport.IItemPluggable itemPlug) {
            buildcraft.api.transport.pluggable.PipePluggable existing = tile.getPluggable(realSide);
            if (existing == null) {
                if (!level.isClientSide()) {
                    buildcraft.api.transport.pluggable.PipePluggable plug = 
                            itemPlug.onPlace(stack, tile, realSide, player, hand);
                    if (plug != null) {
                        tile.replacePluggable(realSide, plug);
                        plug.onPlacedBy(player);
                        if (!player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * Determines the Direction face the player clicked on (arm or null for center).
     */
    @Nullable
    private static Direction getHitFace(TilePipeHolder tile, BlockHitResult hitResult) {
        double lx = hitResult.getLocation().x - hitResult.getBlockPos().getX();
        double ly = hitResult.getLocation().y - hitResult.getBlockPos().getY();
        double lz = hitResult.getLocation().z - hitResult.getBlockPos().getZ();
        var pipe = tile.getPipe();
        if (pipe != null) {
            if (ly < 0.25 && pipe.isConnected(Direction.DOWN))  return Direction.DOWN;
            if (ly > 0.75 && pipe.isConnected(Direction.UP))    return Direction.UP;
            if (lz < 0.25 && pipe.isConnected(Direction.NORTH)) return Direction.NORTH;
            if (lz > 0.75 && pipe.isConnected(Direction.SOUTH)) return Direction.SOUTH;
            if (lx < 0.25 && pipe.isConnected(Direction.WEST))  return Direction.WEST;
            if (lx > 0.75 && pipe.isConnected(Direction.EAST))  return Direction.EAST;
        }
        return null; // Center
    }

    /**
     * Determines which pipe segment (center or directional arm) the player clicked on.
     * The center occupies 0.25–0.75 in all axes; if the hit point is outside that range
     * in any axis, it must be in the arm extending in that direction.
     */
    private static buildcraft.api.core.EnumPipePart getHitPart(TilePipeHolder tile, BlockHitResult hitResult) {
        double lx = hitResult.getLocation().x - hitResult.getBlockPos().getX();
        double ly = hitResult.getLocation().y - hitResult.getBlockPos().getY();
        double lz = hitResult.getLocation().z - hitResult.getBlockPos().getZ();

        var pipe = tile.getPipe();
        if (pipe != null) {
            if (ly < 0.25 && pipe.isConnected(Direction.DOWN))  return buildcraft.api.core.EnumPipePart.fromFacing(Direction.DOWN);
            if (ly > 0.75 && pipe.isConnected(Direction.UP))    return buildcraft.api.core.EnumPipePart.fromFacing(Direction.UP);
            if (lz < 0.25 && pipe.isConnected(Direction.NORTH)) return buildcraft.api.core.EnumPipePart.fromFacing(Direction.NORTH);
            if (lz > 0.75 && pipe.isConnected(Direction.SOUTH)) return buildcraft.api.core.EnumPipePart.fromFacing(Direction.SOUTH);
            if (lx < 0.25 && pipe.isConnected(Direction.WEST))  return buildcraft.api.core.EnumPipePart.fromFacing(Direction.WEST);
            if (lx > 0.75 && pipe.isConnected(Direction.EAST))  return buildcraft.api.core.EnumPipePart.fromFacing(Direction.EAST);
        }
        return buildcraft.api.core.EnumPipePart.CENTER;
    }

    /**
     * Returns the Direction of the pluggable that the given local hit coordinates fall within,
     * or null if no pluggable is hit.
     */
    @Nullable
    private static Direction getHitPluggable(TilePipeHolder tile, double lx, double ly, double lz) {
        for (Direction dir : Direction.values()) {
            PipePluggable plug = tile.getPluggable(dir);
            if (plug != null) {
                AABB box = plug.getBoundingBox();
                if (lx >= box.minX && lx <= box.maxX
                    && ly >= box.minY && ly <= box.maxY
                    && lz >= box.minZ && lz <= box.maxZ) {
                    return dir;
                }
            }
        }
        return null;
    }

    // NeoForge hook: returning false prevents the block from being destroyed.
    // If the player targets a pluggable, remove only the pluggable and return false.
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       ItemStack toolStack, boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TilePipeHolder tile) {
                net.minecraft.world.phys.HitResult hit = player.pick(5.0, 0.0f, false);
                if (hit instanceof BlockHitResult blockHit && pos.equals(blockHit.getBlockPos())) {
                    double lx = blockHit.getLocation().x - pos.getX();
                    double ly = blockHit.getLocation().y - pos.getY();
                    double lz = blockHit.getLocation().z - pos.getZ();
                    Direction plugDir = getHitPluggable(tile, lx, ly, lz);
                    if (plugDir != null) {
                        PipePluggable plug = tile.getPluggable(plugDir);
                        if (plug != null) {
                            ItemStack drop = plug.getPickStack();
                            tile.replacePluggable(plugDir, null);
                            if (!player.isCreative() && !drop.isEmpty()) {
                                Block.popResource(level, pos, drop);
                            }
                        }
                        // Immediately sync the updated state to the client
                        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                        return false; // Don't destroy the pipe
                    }
                }
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
    }

    // Block removal — drops pipe item (only called when the pipe is actually being destroyed)
    // Must check if a pluggable is targeted, because playerWillDestroy runs BEFORE
    // onDestroyedByPlayer which cancels the pipe break.
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TilePipeHolder tile && !level.isClientSide() && !player.isCreative()) {
            // Don't drop pipe items if a pluggable is being targeted — 
            // onDestroyedByPlayer will handle the pluggable and cancel the break
            net.minecraft.world.phys.HitResult hit = player.pick(5.0, 0.0f, false);
            boolean hittingPluggable = false;
            if (hit instanceof BlockHitResult blockHit && pos.equals(blockHit.getBlockPos())) {
                double lx = blockHit.getLocation().x - pos.getX();
                double ly = blockHit.getLocation().y - pos.getY();
                double lz = blockHit.getLocation().z - pos.getZ();
                hittingPluggable = getHitPluggable(tile, lx, ly, lz) != null;
            }
            if (!hittingPluggable) {
                tile.dropPipeItems(level, pos);
            }
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

    // Pick block (middle click) — return the correct item for what's targeted
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TilePipeHolder tile) {
            // Use player.pick to get the hit result (works on both client and server via player entity)
            net.minecraft.world.phys.HitResult hit = player.pick(5.0, 0.0f, false);
            if (hit instanceof BlockHitResult blockHit && pos.equals(blockHit.getBlockPos())) {
                double lx = blockHit.getLocation().x - pos.getX();
                double ly = blockHit.getLocation().y - pos.getY();
                double lz = blockHit.getLocation().z - pos.getZ();
                Direction plugDir = getHitPluggable(tile, lx, ly, lz);
                if (plugDir != null) {
                    PipePluggable plug = tile.getPluggable(plugDir);
                    if (plug != null) {
                        return plug.getPickStack();
                    }
                }
            }
            // Default: return pipe item
            if (tile.getPipe() != null) {
                Pipe pipe = tile.getPipe();
                PipeDefinition def = pipe.getDefinition();
                Item item = (Item) PipeApi.pipeRegistry.getItemForPipe(def);
                if (item != null) {
                    return new ItemStack(item);
                }
            }
        }
        return super.getCloneItemStack(level, pos, state, includeData, player);
    }

    // Sprint particles — pipes use INVISIBLE render shape so vanilla won't create particles.
    // We delegate to PipeHolderClientExtensions which resolves the correct pipe-specific texture.
    @Override
    public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide()) {
            return buildcraft.transport.client.PipeHolderClientExtensions.spawnRunningParticle(
                level, pos,
                entity.getX(), entity.getZ(), entity.getBbWidth(),
                entity.getDeltaMovement().x, entity.getDeltaMovement().z,
                entity.getBoundingBox().minY
            );
        }
        return false;
    }

    // ICustomPaintHandler — paint pipes with paintbrush

    @Override
    public InteractionResult attemptPaint(Level world, BlockPos pos, BlockState state, Vec3 hitPos,
                                          @Nullable Direction hitSide, @Nullable DyeColor paintColour) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof TilePipeHolder tile)) {
            return InteractionResult.PASS;
        }
        Pipe pipe = tile.getPipe();
        if (pipe == null) {
            return InteractionResult.FAIL;
        }
        if (pipe.getColour() == paintColour || !pipe.getDefinition().canBeColoured) {
            return InteractionResult.FAIL;
        }
        pipe.setColour(paintColour);
        return InteractionResult.SUCCESS;
    }
}
