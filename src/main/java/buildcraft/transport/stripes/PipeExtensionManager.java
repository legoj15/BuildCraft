/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.stripes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import buildcraft.api.core.BCLog;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeExtensionManager;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.lib.misc.InventoryUtil;

import buildcraft.transport.pipe.behaviour.PipeBehaviourStripes;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Extends and retracts a stripes pipe's line (8.0.x's manager, 7.1.x's {@code PipeExtensionListener}): an item pipe
 * that reaches a stripes pipe's open end moves the stripes pipe one cell forward and places the fed pipe in the cell
 * it left; a retraction pipe (the void pipe) moves it back onto the pipe behind, which comes back down the line with
 * the void pipe. Requests are queued by {@link StripesHandlerPipes} during the pipe's flow tick and carried out at
 * the end of the level's tick, when no pipe is mid-tick.
 *
 * <p>The stripes pipe moves as its block entity's saved data (owner, battery, direction, cargo, pluggables, wires),
 * removed from one cell without spilling and loaded into the other. Every cell that changes hands is asked about as
 * the pipe's owner, the way a player's hands would be: break protection for the cell the stripes pipe leaves and for
 * a pipe a retraction takes up, the placement event for the cell the stripes pipe moves into, and the fed pipe is
 * placed by the owner's player clicking its cell (the right-click and placement events). A refusal anywhere puts the
 * line back as it was and sends the pipe stack back down the stripes pipe.
 */
@EventBusSubscriber(modid = "buildcraftunofficial")
public enum PipeExtensionManager implements IPipeExtensionManager {
    INSTANCE;

    private final Map<Level, List<PipeExtensionRequest>> requests = new IdentityHashMap<>();
    private final Set<PipeDefinition> retractionPipeDefs = new HashSet<>();

    @Override
    public boolean requestPipeExtension(Level world, BlockPos pos, Direction dir, IStripesActivator stripes, ItemStack stack) {
        if (world.isClientSide() || stack.isEmpty() || !(stack.getItem() instanceof IItemPipe pipeItem)) {
            return false;
        }
        // Only a stripes pipe standing at pos can be moved; a robot's stripes use names the cell behind its target.
        if (stripesAt(world, pos, stripes) == null) {
            return false;
        }
        return requests.computeIfAbsent(world, key -> new ArrayList<>())
                .add(new PipeExtensionRequest(pos.immutable(), dir, stripes, pipeItem.getDefinition(), stack.copy()));
    }

    @Override
    public void registerRetractionPipe(PipeDefinition pipeDefinition) {
        if (pipeDefinition != null) {
            retractionPipeDefs.add(pipeDefinition);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            INSTANCE.tick(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            INSTANCE.requests.remove(level);
        }
    }

    private void tick(ServerLevel level) {
        List<PipeExtensionRequest> queued = requests.remove(level);
        if (queued == null) {
            return;
        }
        for (PipeExtensionRequest r : queued) {
            if (retractionPipeDefs.contains(r.pipeDef)) {
                retract(level, r);
            } else {
                extend(level, r);
            }
        }
    }

    private void extend(ServerLevel w, PipeExtensionRequest r) {
        TilePipeHolder stripesTile = stripesAt(w, r.pos, r.stripes);
        if (stripesTile == null) {
            BCLog.logger.warn("[transport.stripes] Found an invalid extension request at " + r.pos
                    + ": no stripes pipe there any more");
            InventoryUtil.drop(w, r.pos, r.stack);
            return;
        }
        BlockPos p = r.pos.relative(r.dir);
        if (!canTakeStripesPipe(w.getBlockState(p))) {
            sendBack(w, r.pos, List.of(r.stack), r.dir);
            return;
        }
        GameProfile owner = stripesTile.getOwner();
        // Step 1: the stripes pipe leaves its cell, so break protection decides first (as 8.0.x asked it).
        if (!BlockUtil.canMachineBreak(w, r.pos, owner)) {
            sendBack(w, r.pos, List.of(r.stack), r.dir);
            return;
        }
        Cell stripesCell = Cell.capture(w, r.pos);
        BlockSnapshot aheadBefore = BlockSnapshot.create(w.dimension(), w, p);

        // Step 2: move the stripes pipe forward, and ask for the cell it moved into.
        removeQuietly(w, r.pos);
        stripesCell.placeAt(w, p);
        if (placeRefused(w, aheadBefore, owner, r.dir)) {
            removeQuietly(w, p);
            aheadBefore.restore(Block.UPDATE_ALL);
            stripesCell.placeAt(w, r.pos);
            sendBack(w, r.pos, List.of(r.stack), r.dir);
            return;
        }

        // Step 3: the owner's player places ONE of the fed pipes in the cell the stripes pipe left, clicking it as a
        // player would; what is left of the stack, and anything else the placement left on the player, goes back.
        BlockUtil.ItemUse use;
        List<ItemStack> left;
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(w, owner, p, r.dir.getOpposite())) {
            FakePlayer player = lease.player();
            player.setItemInHand(InteractionHand.MAIN_HAND, r.stack);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(r.pos), r.dir, r.pos, false);
            use = BlockUtil.tryUseItemOnBlock(w, player, InteractionHand.MAIN_HAND, r.stack, hit);
            left = FakePlayerUtil.takeDistinctStacks(player);
        }
        if (use != BlockUtil.ItemUse.USED || !(w.getBlockEntity(r.pos) instanceof TilePipeHolder)) {
            // Nothing was placed: put the stripes pipe back where it was.
            removeQuietly(w, p);
            aheadBefore.restore(Block.UPDATE_ALL);
            if (!w.getBlockState(r.pos).isAir()) {
                BCLog.logger.warn("[transport.stripes] The extension at " + r.pos + " placed something that is not"
                        + " a pipe; dropping it instead of the stripes pipe's cell");
                w.destroyBlock(r.pos, true);
            }
            stripesCell.placeAt(w, r.pos);
            sendBack(w, r.pos, left, r.dir);
            return;
        }
        sendBack(w, p, left, r.dir);
    }

    private void retract(ServerLevel w, PipeExtensionRequest r) {
        TilePipeHolder stripesTile = stripesAt(w, r.pos, r.stripes);
        if (stripesTile == null) {
            BCLog.logger.warn("[transport.stripes] Found an invalid retraction request at " + r.pos
                    + ": no stripes pipe there any more");
            InventoryUtil.drop(w, r.pos, r.stack);
            return;
        }
        Direction retractDir = r.dir.getOpposite();
        if (!isValidRetractionPath(w, r.pos, retractDir)) {
            // check other directions
            List<Direction> possible = new ArrayList<>();
            for (Direction facing : Direction.values()) {
                if (facing.getAxis() != r.dir.getAxis() && isValidRetractionPath(w, r.pos, facing)) {
                    possible.add(facing);
                }
            }
            if (possible.isEmpty()) {
                sendBack(w, r.pos, List.of(r.stack), r.dir);
                return;
            }
            retractDir = possible.get(w.getRandom().nextInt(possible.size()));
        }
        BlockPos p = r.pos.relative(retractDir);
        GameProfile owner = stripesTile.getOwner();
        // Both cells change hands: the pipe behind is taken up and the stripes pipe leaves its own cell.
        if (!BlockUtil.canMachineBreak(w, p, owner) || !BlockUtil.canMachineBreak(w, r.pos, owner)
                || !(w.getBlockEntity(p) instanceof TilePipeHolder behindTile) || behindTile.getPipe() == null) {
            sendBack(w, r.pos, List.of(r.stack), r.dir);
            return;
        }
        Cell stripesCell = Cell.capture(w, r.pos);
        Cell behindCell = Cell.capture(w, p);
        BlockSnapshot behindBefore = BlockSnapshot.create(w.dimension(), w, p);

        // Always send back the catalyst pipe, and everything the pipe taken up held (it is not dropped).
        List<ItemStack> stacksToSendBack = new ArrayList<>();
        stacksToSendBack.add(r.stack);
        behindTile.collectPipeDrops(stacksToSendBack::add);
        NonNullList<ItemStack> cargo = NonNullList.create();
        behindTile.getPipe().addDrops(cargo, 0);
        stacksToSendBack.addAll(cargo);

        removeQuietly(w, p);
        removeQuietly(w, r.pos);
        stripesCell.placeAt(w, p);
        if (placeRefused(w, behindBefore, owner, retractDir)) {
            removeQuietly(w, p);
            behindCell.placeAt(w, p);
            stripesCell.placeAt(w, r.pos);
            sendBack(w, r.pos, List.of(r.stack), r.dir);
            return;
        }
        if (w.getBlockEntity(p) instanceof TilePipeHolder moved
                && moved.getPipe() != null && moved.getPipe().getBehaviour() instanceof PipeBehaviourStripes stripes) {
            stripes.direction = retractDir.getOpposite();
        }
        sendBack(w, p, stacksToSendBack, r.dir);
    }

    /** The stripes pipe standing at {@code pos} whose behaviour is {@code stripes}, or null. */
    @Nullable
    private static TilePipeHolder stripesAt(Level w, BlockPos pos, IStripesActivator stripes) {
        if (w.getBlockEntity(pos) instanceof TilePipeHolder tile && tile.getPipe() != null
                && tile.getPipe().getBehaviour() == stripes) {
            return tile;
        }
        return null;
    }

    /** A cell the stripes pipe may move into: air, or a replaceable block without fluid (grass, not water). */
    private static boolean canTakeStripesPipe(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    /**
     * Takes a pipe out of its cell without its removal spilling anything: its cargo and parts travel on in the saved
     * copy (or come back down the line), so the in-transit fallback is told the drops are handled, and its wires
     * leave their wire systems as a player's break makes them. Its pluggables are told they are detached, as on any
     * removal: the copy carries new ones.
     */
    private static void removeQuietly(ServerLevel w, BlockPos pos) {
        if (w.getBlockEntity(pos) instanceof TilePipeHolder tile) {
            tile.markDropsHandled();
            tile.wireManager.invalidate();
        }
        w.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Posts the placement event for the block just placed where {@code before} was, as the owner; true if refused. */
    private static boolean placeRefused(ServerLevel w, BlockSnapshot before, GameProfile owner, Direction direction) {
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(w, owner, before.getPos())) {
            return EventHooks.onBlockPlace(lease.player(), before, direction);
        }
    }

    /** Sends {@code stacks} down the stripes pipe at {@code pos}, or drops them there when there is none. */
    private static void sendBack(ServerLevel w, BlockPos pos, List<ItemStack> stacks, Direction dir) {
        IStripesActivator stripes = w.getBlockEntity(pos) instanceof TilePipeHolder tile && tile.getPipe() != null
                && tile.getPipe().getBehaviour() instanceof IStripesActivator activator ? activator : null;
        for (ItemStack s : stacks) {
            if (s.isEmpty()) {
                continue;
            }
            s = s.copy();
            if (stripes == null) {
                InventoryUtil.drop(w, pos, s);
            } else if (!stripes.sendItem(s, dir)) {
                stripes.dropItem(s, dir);
            }
        }
    }

    private static boolean isValidRetractionPath(Level w, BlockPos pos, Direction retractDir) {
        IPipe pipe = w.getBlockEntity(pos.relative(retractDir)) instanceof TilePipeHolder tile ? tile.getPipe() : null;
        if (pipe != null) {
            boolean connected = false;
            for (Direction facing : Direction.values()) {
                if (pipe.getConnectedType(facing) == IPipe.ConnectedType.TILE) {
                    return false;
                }
                if (facing == retractDir.getOpposite() && pipe.getConnectedType(facing) != IPipe.ConnectedType.PIPE) {
                    return false;
                }
                if (facing != retractDir.getOpposite() && connected && pipe.getConnectedType(facing) != null) {
                    return false;
                }
                if (facing != retractDir.getOpposite() && !connected && pipe.getConnectedType(facing) != null) {
                    connected = true;
                }
            }
            return true;
        }
        return false;
    }

    /** A cell's block and its block entity's saved data, to put back in the same or another cell. */
    private record Cell(BlockState state, @Nullable CompoundTag nbt) {
        static Cell capture(ServerLevel w, BlockPos pos) {
            BlockEntity be = w.getBlockEntity(pos);
            return new Cell(w.getBlockState(pos), be == null ? null : be.saveWithoutMetadata(w.registryAccess()));
        }

        void placeAt(ServerLevel w, BlockPos pos) {
            w.setBlock(pos, state, Block.UPDATE_ALL);
            if (nbt != null && w.getBlockEntity(pos) instanceof BlockEntity be) {
                //? if >=1.21.10 {
                be.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                        net.minecraft.util.ProblemReporter.DISCARDING, w.registryAccess(), nbt.copy()));
                //?} else {
                /*be.loadWithComponents(nbt.copy(), w.registryAccess());*/
                //?}
                be.setChanged();
            }
        }
    }

    private record PipeExtensionRequest(BlockPos pos, Direction dir, IStripesActivator stripes, PipeDefinition pipeDef,
            ItemStack stack) {}
}
