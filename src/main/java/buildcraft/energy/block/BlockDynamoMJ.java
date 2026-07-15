package buildcraft.energy.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.tools.IToolWrench;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.energy.tile.TileDynamoMJ;
import buildcraft.lib.engine.BlockEngineBase_BC8;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.EntityUtil;

/**
 * The MJ Dynamo shares the engine block skeleton — facing state, wrench rotation, shape, ticker
 * hookup, owner placement and neighbour updates all live on {@link BlockEngineBase_BC8} (its tile
 * {@link TileDynamoMJ} already extends {@link TileEngineBase_BC8}). Only the tile type, the wrench /
 * GUI interaction and the upgrade-slot drops differ, so those are the only overrides here — mirroring
 * how {@link BlockEngineFE} extends the same base.
 */
public class BlockDynamoMJ extends BlockEngineBase_BC8 {

    public BlockDynamoMJ(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileDynamoMJ(pos, state);
    }

    /**
     * Dynamo never overheats (heat capped at 200). Wrench priority:
     *   1. Pipe in hand → place it if it's a kinesis pipe or a wooden FE pipe (the pipe types
     *      that connect to an MJ dynamo); otherwise — or if placement is obstructed — open GUI.
     *   2. Crouch → open GUI (overrides wrench; unified with Stone/Iron/FE).
     *   3. Wrench (non-crouch) → PASS if there's an alternate receiver (wrench.useOn dispatches
     *      to this block's ICustomRotationHandler, plays slide sound, grants `wrenched`);
     *      otherwise tripwire-armed sound + CONSUME.
     *   4. Default → open GUI.
     */
    @Override
    //? if >=1.21.10 {
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
    //?} else {
    /*protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {*/
    //?}
        if (stack.getItem() instanceof IItemPipe pipe) {
            InteractionResult placed = EnginePipeInteraction.tryPlacePipe(
                    pipe, stack, level, player, hand, hitResult, PipeApi.flowPower, PipeApi.flowRf);
            return BlockUtil.itemUseFrom(placed != null ? placed : openGui(state, level, pos, player));
        }

        if (player.isShiftKeyDown()) {
            return BlockUtil.itemUseFrom(openGui(state, level, pos, player));
        }

        if (EntityUtil.isWrench(stack)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileEngineBase_BC8 engine && engine.hasAlternateReceiver()) {
                // BuildCraft's own wrench rotates via its useOn (ICustomRotationHandler); a foreign
                // tag-only wrench has no such hook, so drive the rotation block-side here.
                if (stack.getItem() instanceof IToolWrench) {
                    return BlockUtil.itemUsePass();
                }
                return BlockUtil.itemUseFrom(
                        BlockUtil.rotateByForeignWrench(level, pos, state, player, hand, hitResult.getDirection()));
            }
            if (!level.isClientSide()) {
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 1.3f);
            }
            player.swing(hand);
            return BlockUtil.itemUseConsume();
        }

        return BlockUtil.itemUseFrom(openGui(state, level, pos, player));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        return openGui(state, level, pos, player);
    }

    private InteractionResult openGui(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileDynamoMJ dynamo && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(dynamo);
        }
        return InteractionResult.SUCCESS;
    }

    /** Drops the upgrade slots regardless of the tool used to break the block. The upgrades
     *  inventory is a raw ItemHandlerSimple, not registered with ItemHandlerManager. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TileDynamoMJ dynamo) {
            buildcraft.lib.misc.BlockDropsUtil.dropItems(level, pos, dynamo.upgrades);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
