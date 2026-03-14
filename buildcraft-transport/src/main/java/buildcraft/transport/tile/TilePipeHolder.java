/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.tile;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeEvent;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.PipeEventBus;

public class TilePipeHolder extends BlockEntity implements IPipeHolder, IDebuggable {
    /** ModelData property that passes this tile reference to ModelPipe for baked model generation. */
    public static final net.neoforged.neoforge.model.data.ModelProperty<TilePipeHolder> PIPE_MODEL_DATA =
        new net.neoforged.neoforge.model.data.ModelProperty<>();

    public final PipeEventBus eventBus = new PipeEventBus();
    private Pipe pipe;
    private boolean scheduleRenderUpdate = true;
    private final Set<PipeMessageReceiver> networkUpdates = EnumSet.noneOf(PipeMessageReceiver.class);
    private final Set<PipeMessageReceiver> networkGuiUpdates = EnumSet.noneOf(PipeMessageReceiver.class);

    // CompoundTag cache for pipe data (used because Pipe uses CompoundTag, not ValueOutput)
    private CompoundTag pipeSaveCache;


    public TilePipeHolder(BlockPos pos, BlockState state) {
        super(BCTransportBlockEntities.PIPE_HOLDER.get(), pos, state);
    }

    // --- Read / Write ---

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        if (pipe != null) {
            output.store("pipe", CompoundTag.CODEC, pipe.writeToNbt());
        }
    }

    @Override
    public void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        input.read("pipe", CompoundTag.CODEC).ifPresent(pipeTag -> {
            try {
                pipe = new Pipe(this, pipeTag);
                eventBus.registerHandler(pipe.behaviour);
                eventBus.registerHandler(pipe.flow);
            } catch (InvalidInputDataException e) {
                e.printStackTrace();
                pipe = null;
            }
        });
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Refresh model data so the baked model has access to this tile on chunk load.
        // Without this, pipes loaded from disk are invisible until broken and replaced.
        requestModelDataUpdate();
        scheduleRenderUpdate = true;
    }

    // --- Client sync ---

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Placement ---

    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof IItemPipe) {
            PipeDefinition definition = ((IItemPipe) item).getDefinition();
            this.pipe = new Pipe(this, definition);
            eventBus.registerHandler(pipe.behaviour);
            eventBus.registerHandler(pipe.flow);
        }
        scheduleRenderUpdate();
        setChanged();
    }

    // --- Tick ---

    public void tick() {
        if (pipe != null) {
            pipe.onTick();
        }
        if (pipe != null) {
            pipe.postPluggableTick();
        }

        // Schedule render update
        if (scheduleRenderUpdate && level != null) {
            scheduleRenderUpdate = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }

        if (level != null && !level.isClientSide()) {
            setChanged();
        }
    }

    // --- Drops ---

    public void dropPipeItems(Level lvl, BlockPos pos) {
        if (pipe != null) {
            // Drop the pipe item itself
            PipeDefinition def = pipe.getDefinition();
            Item pipeItem = (Item) PipeApi.pipeRegistry.getItemForPipe(def);
            if (pipeItem != null) {
                Block.popResource(lvl, pos, new ItemStack(pipeItem));
            }
            // Drop flow/behaviour items
            NonNullList<ItemStack> drops = NonNullList.create();
            pipe.addDrops(drops, 0);
            for (ItemStack drop : drops) {
                Block.popResource(lvl, pos, drop);
            }
        }
    }

    // --- IPipeHolder ---

    @Override
    public Level getPipeWorld() {
        return getLevel();
    }

    @Override
    public BlockPos getPipePos() {
        return getBlockPos();
    }

    @Override
    public BlockEntity getPipeTile() {
        return this;
    }

    @Override
    public Pipe getPipe() {
        return pipe;
    }

    @Override
    public boolean canPlayerInteract(Player player) {
        if (level == null) return false;
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Nullable
    @Override
    public PipePluggable getPluggable(Direction side) {
        return null; // Pluggables not yet ported
    }

    @Nullable
    @Override
    public BlockEntity getNeighbourTile(Direction side) {
        if (level == null) return null;
        return level.getBlockEntity(worldPosition.relative(side));
    }

    @Nullable
    @Override
    public IPipe getNeighbourPipe(Direction side) {
        BlockEntity neighbour = getNeighbourTile(side);
        if (neighbour instanceof TilePipeHolder other) {
            return other.getPipe();
        }
        return null;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapabilityFromPipe(Direction side, @Nonnull Object capability) {
        if (level == null || side == null) return null;
        BlockPos neighborPos = worldPosition.relative(side);
        if (capability instanceof net.neoforged.neoforge.capabilities.BlockCapability<?, ?> blockCap) {
            // Route block capabilities to the NeoForge capability system
            try {
                return (T) level.getCapability(
                    (net.neoforged.neoforge.capabilities.BlockCapability) blockCap,
                    neighborPos, side.getOpposite());
            } catch (ClassCastException e) {
                return null;
            }
        }
        // For non-BlockCapability tokens (e.g. MjAPI.CAP_RECEIVER),
        // check if the neighboring tile implements the requested interface directly
        BlockEntity neighbor = level.getBlockEntity(neighborPos);
        if (neighbor == null) return null;
        // MJ capabilities: check if the neighbor pipe's flow exposes them
        IPipe neighborPipe = getNeighbourPipe(side);
        if (neighborPipe != null && neighborPipe.getFlow() != null) {
            T result = neighborPipe.getFlow().getCapability(capability, side.getOpposite());
            if (result != null) return result;
        }
        return null;
    }

    @Override
    public IWireManager getWireManager() {
        // Stub — wires not yet ported
        return null;
    }

    @Override
    public GameProfile getOwner() {
        return null; // Owner tracking not yet ported
    }

    @Override
    public boolean fireEvent(PipeEvent event) {
        return eventBus.fireEvent(event);
    }

    @Override
    public void scheduleRenderUpdate() {
        scheduleRenderUpdate = true;
    }

    @Override
    public void scheduleNetworkUpdate(PipeMessageReceiver... parts) {
        Collections.addAll(networkUpdates, parts);
        scheduleRenderUpdate = true;
    }

    @Override
    public void scheduleNetworkGuiUpdate(PipeMessageReceiver... parts) {
        Collections.addAll(networkGuiUpdates, parts);
    }

    @Override
    public void sendMessage(PipeMessageReceiver to, IWriter writer) {
        // Networking not yet ported — no-op
    }

    @Override
    public void sendGuiMessage(PipeMessageReceiver to, IWriter writer) {
        // Networking not yet ported — no-op
    }

    @Override
    public void onPlayerOpen(Player player) {
    }

    @Override
    public void onPlayerClose(Player player) {
    }

    // --- IRedstoneStatementContainer ---

    @Override
    public int getRedstoneInput(Direction side) {
        if (level == null) return 0;
        if (side == null) {
            return level.getBestNeighborSignal(worldPosition);
        }
        return level.getSignal(worldPosition.relative(side), side);
    }

    @Override
    public boolean setRedstoneOutput(Direction side, int value) {
        return false; // Redstone output not yet ported
    }

    // --- IDebuggable ---

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        if (pipe == null) {
            left.add("Pipe = null");
        } else {
            left.add("Pipe:");
            pipe.getDebugInfo(left, right, side);
        }
    }

    @Override
    public net.neoforged.neoforge.model.data.ModelData getModelData() {
        return net.neoforged.neoforge.model.data.ModelData.builder()
            .with(PIPE_MODEL_DATA, this)
            .build();
    }
}
