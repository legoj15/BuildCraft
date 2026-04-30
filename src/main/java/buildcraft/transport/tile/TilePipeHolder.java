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

import io.netty.buffer.Unpooled;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pluggable.PluggableDefinition;
import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeEvent;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.net.MessagePipePayload;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.wire.WireManager;
import buildcraft.transport.pipe.PipeEventBus;

public class TilePipeHolder extends BlockEntity implements IPipeHolder, IDebuggable {
    private static final net.minecraft.resources.Identifier ADVANCEMENT_PIPE_DREAM
        = net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_dream");
    private static final net.minecraft.resources.Identifier ADVANCEMENT_PIPE_DIVERSIFICATION
        = net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_diversification");
    private static final net.minecraft.resources.Identifier ADVANCEMENT_PIPE_FANATIC
        = net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_fanatic");

    /** ModelData property that passes this tile reference to ModelPipe for baked model generation. */
    public static final net.neoforged.neoforge.model.data.ModelProperty<TilePipeHolder> PIPE_MODEL_DATA =
        new net.neoforged.neoforge.model.data.ModelProperty<>();

    public final PipeEventBus eventBus = new PipeEventBus();
    private Pipe pipe;
    public final WireManager wireManager = new WireManager(this);
    private final PipePluggable[] pluggables = new PipePluggable[6];
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
        CompoundTag wireTag = wireManager.writeToNbt();
        if (!wireTag.isEmpty()) {
            output.store("wires", CompoundTag.CODEC, wireTag);
        }
        // Save pluggables
        CompoundTag plugTag = new CompoundTag();
        for (Direction face : Direction.values()) {
            PipePluggable plug = pluggables[face.ordinal()];
            if (plug != null) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", plug.definition.identifier.toString());
                entry.put("data", plug.writeToNbt());
                plugTag.put(face.getName(), entry);
            }
        }
        if (!plugTag.isEmpty()) {
            output.store("plugs", CompoundTag.CODEC, plugTag);
        }
    }

    @Override
    public void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        input.read("pipe", CompoundTag.CODEC).ifPresent(pipeTag -> {
            try {
                if (pipe != null) {
                    // Pipe already exists — just update its data from the incoming NBT
                    // instead of recreating from scratch. This prevents the pipe being
                    // destroyed/rebuilt every time the server sends a block entity update.
                    pipe.readFromNbt(pipeTag);
                } else {
                    pipe = new Pipe(this, pipeTag);
                    eventBus.registerHandler(pipe.behaviour);
                    eventBus.registerHandler(pipe.flow);
                }
            } catch (InvalidInputDataException e) {
                pipe = null;
            }
        });
        // Load pluggables
        input.read("plugs", CompoundTag.CODEC).ifPresentOrElse(plugTag -> {
            for (Direction face : Direction.values()) {
                if (plugTag.contains(face.getName())) {
                    CompoundTag entry = plugTag.getCompound(face.getName()).orElse(new CompoundTag());
                    String id = entry.getString("id").orElse("");
                    if (!id.isEmpty()) {
                        net.minecraft.resources.Identifier plugId = net.minecraft.resources.Identifier.parse(id);
                        PluggableDefinition def = PipeApi.pluggableRegistry != null
                                ? PipeApi.pluggableRegistry.getDefinition(plugId) : null;
                        if (def != null) {
                            CompoundTag data = entry.getCompound("data").orElse(new CompoundTag());
                            // Reuse existing pluggable if the definition matches, to preserve
                            // live references held by open GUI containers (e.g. ContainerGate → GateLogic)
                            PipePluggable existing = pluggables[face.ordinal()];
                            if (existing != null && existing.definition.identifier.equals(plugId)
                                    && existing.readFromNbt(data)) {
                                // Updated in-place — keep the existing instance
                            } else {
                                pluggables[face.ordinal()] = def.readFromNbt(this, face, data);
                            }
                        } else {
                            pluggables[face.ordinal()] = null;
                        }
                    } else {
                        pluggables[face.ordinal()] = null;
                    }
                } else {
                    pluggables[face.ordinal()] = null;
                }
            }
        }, () -> {
            // No "plugs" tag means all pluggables were removed — clear them
            for (int i = 0; i < pluggables.length; i++) {
                pluggables[i] = null;
            }
        });
        // Re-register all loaded pluggables with the event bus so their
        // @PipeEventHandler methods (e.g. PluggableTimer.addInternalTriggers) fire
        for (PipePluggable plug : pluggables) {
            eventBus.unregisterHandler(plug); // avoid duplicate registration
            eventBus.registerHandler(plug);
        }
        // Load wire data
        input.read("wires", CompoundTag.CODEC).ifPresent(wireTag -> {
            wireManager.readFromNbt(wireTag);
        });
        // After data sync (e.g. colour change), refresh the model on the client
        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
            scheduleRenderUpdate = true;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (pipe != null) {
            pipe.onLoad();
        }
        // Refresh model data and schedule render update
        requestModelDataUpdate();
        scheduleRenderUpdate = true;
    }

    // --- Client sync ---

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public void handleUpdateTag(net.minecraft.world.level.storage.ValueInput input) {
        super.handleUpdateTag(input);
        // Schedule client-side render refresh after receiving updated state.
        // UPDATE_CLIENTS (not UPDATE_ALL): client doesn't need the UPDATE_NEIGHBORS bit, and on
        // the server side it would re-fire updateNeighborsAt redundantly with tick()'s explicit one.
        requestModelDataUpdate();
        if (level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.world.level.storage.ValueInput input) {
        super.onDataPacket(net, input);
        requestModelDataUpdate();
        if (level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
            // Restore paint colour from the placed item
            DyeColor col = stack.get(BCTransportItems.PIPE_COLOUR.get());
            if (col != null) {
                pipe.setColour(col);
            }
        }
        if (placer instanceof Player player && level != null && !level.isClientSide()) {
            AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_PIPE_DREAM);
            if (pipe != null) {
                PipeDefinition def = pipe.getDefinition();
                // Pipe diversification: award criterion by flow type
                String flowCriterion = getFlowTypeCriterion(def);
                if (flowCriterion != null) {
                    AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_PIPE_DIVERSIFICATION, flowCriterion);
                }
                // Pipe fanatic: award criterion by pipe identifier
                AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_PIPE_FANATIC, def.identifier);
            }
        }
        scheduleRenderUpdate();
        setChanged();
    }

    private static String getFlowTypeCriterion(PipeDefinition def) {
        if (def.flowType == PipeApi.flowItems) return "item_pipe";
        if (def.flowType == PipeApi.flowFluids) return "fluid_pipe";
        if (def.flowType == PipeApi.flowPower) return "power_pipe";
        if (def.flowType == PipeApi.flowStructure) return "structure_pipe";
        return null;
    }

    // --- Tick ---

    public void tick() {
        // Prepare redstone outputs for this tick
        java.util.Arrays.fill(redstoneOutputsThisTick, 0);

        wireManager.tick();
        if (pipe != null) {
            pipe.onTick();
        }
        // Tick pluggables
        for (PipePluggable plug : pluggables) {
            if (plug != null) {
                plug.onTick();
            }
        }
        if (pipe != null) {
            pipe.postPluggableTick();
        }

        // Commit redstone outputs
        boolean redstoneChanged = false;
        for (int i = 0; i < 6; i++) {
            if (redstoneOutputs[i] != redstoneOutputsThisTick[i]) {
                redstoneOutputs[i] = redstoneOutputsThisTick[i];
                redstoneChanged = true;
            }
        }
        if (redstoneChanged && level != null && !level.isClientSide()) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }

        // Schedule render update — server side only, to push block entity data to clients.
        // UPDATE_CLIENTS (not UPDATE_ALL): the explicit updateNeighborsAt above already covered
        // the redstone-changed case, and a model-only resync (e.g. pulsar pulse animation) does
        // not need to re-notify neighbors.
        if (scheduleRenderUpdate && level != null && !level.isClientSide()) {
            scheduleRenderUpdate = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
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
                ItemStack pipeStack = new ItemStack(pipeItem);
                DyeColor col = pipe.getColour();
                if (col != null) {
                    pipeStack.set(BCTransportItems.PIPE_COLOUR.get(), col);
                }
                Block.popResource(lvl, pos, pipeStack);
            }
            // Drop flow/behaviour items
            NonNullList<ItemStack> drops = NonNullList.create();
            pipe.addDrops(drops, 0);
            for (ItemStack drop : drops) {
                Block.popResource(lvl, pos, drop);
            }
        }
        // Drop pluggables
        for (int i = 0; i < 6; i++) {
            PipePluggable plug = pluggables[i];
            if (plug != null) {
                NonNullList<ItemStack> plugDrops = NonNullList.create();
                plug.addDrops(plugDrops, 0);
                for (ItemStack drop : plugDrops) {
                    Block.popResource(lvl, pos, drop);
                }
                plug.onRemove();
                pluggables[i] = null;
            }
        }
        // Drop wires
        for (DyeColor color : wireManager.parts.values()) {
            if (color != null) {
                Item wireItem = buildcraft.transport.BCTransportItems.WIRE_ITEMS.get(color).get();
                if (wireItem != null) {
                    Block.popResource(lvl, pos, new ItemStack(wireItem));
                }
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
        if (side == null) return null;
        return pluggables[side.ordinal()];
    }

    /** Replaces (or removes) the pluggable on the given side.
     *  @return The previously installed pluggable (or null). */
    @Nullable
    public PipePluggable replacePluggable(Direction side, @Nullable PipePluggable with) {
        PipePluggable old = pluggables[side.ordinal()];
        pluggables[side.ordinal()] = with;

        // Register/unregister with the event bus so @PipeEventHandler methods fire
        eventBus.unregisterHandler(old);
        eventBus.registerHandler(with);

        if (pipe != null) {
            pipe.markForUpdate();
        }
        // Also notify the neighbor pipe to recalculate its connections
        IPipe neighbourPipe = getNeighbourPipe(side);
        if (neighbourPipe != null) {
            neighbourPipe.markForUpdate();
        }
        if (level != null && !level.isClientSide()) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            for (Direction dir : Direction.values()) {
                BlockPos npos = worldPosition.relative(dir);
                BlockState nstate = level.getBlockState(npos);
                if (!nstate.isAir()) {
                    BlockState res = nstate.updateShape(level, level, npos, dir.getOpposite(), worldPosition, getBlockState(), level.getRandom());
                    if (res != nstate) {
                        Block.updateOrDestroy(nstate, res, level, npos, Block.UPDATE_ALL);
                    }
                }
            }
            // If the pluggable being added or removed is a wire emitter (e.g. a gate with a
            // Pipe Signal action), the wire system needs to re-walk its elements so it picks
            // up (or drops) this emitter side. Without this, placing a gate AFTER the wires
            // already exist means the wire system was built with no emitter elements and the
            // wires never glow even when the gate fires — visible only after the player breaks
            // and re-places a wire (which forces WireManager.addPart → buildAndAddWireSystem).
            boolean oldWasEmitter = old instanceof buildcraft.api.transport.IWireEmitter;
            boolean newIsEmitter = with instanceof buildcraft.api.transport.IWireEmitter;
            if (oldWasEmitter || newIsEmitter) {
                buildcraft.transport.wire.SavedDataWireSystems wireSystems =
                    buildcraft.transport.wire.SavedDataWireSystems.get(level);
                wireSystems.rebuildWireSystemsAround(this);
                // Also rebuild systems anchored on connected neighbour pipes — wires on a
                // neighbour can extend through this pipe to reach the new/removed emitter.
                for (Direction dir : Direction.values()) {
                    IPipe neighbour = getNeighbourPipe(dir);
                    if (neighbour != null) {
                        wireSystems.rebuildWireSystemsAround(neighbour.getHolder());
                    }
                }
            }
        }
        scheduleRenderUpdate();
        setChanged();
        return old;
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
        // Check pluggable on this side first (mirrors 1.12.2 logic)
        PipePluggable plug = getPluggable(side);
        if (plug != null) {
            T t = plug.getInternalCapability(capability);
            if (t != null) {
                return t;
            }
            if (plug.isBlocking()) {
                return null;
            }
        }
        // Only look up neighbor capability if we have a pipe
        if (pipe == null) {
            return null;
        }
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
    public WireManager getWireManager() {
        return wireManager;
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
        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        } else {
            scheduleRenderUpdate = true;
        }
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
        if (level == null || level.isClientSide()) return;
        // Serialize the writer's output into a byte array
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        try {
            writer.write(buffer);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            MessagePipePayload payload = new MessagePipePayload(worldPosition, to.ordinal(), data);
            // Send to all players tracking this chunk
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(
                        serverLevel, new net.minecraft.world.level.ChunkPos(worldPosition.getX() >> 4, worldPosition.getZ() >> 4), payload);
            }
        } catch (Exception e) {
            BCLog.logger.warn("[transport] Failed to send pipe message at " + worldPosition, e);
        } finally {
            buffer.release();
        }
    }

    @Override
    public void sendGuiMessage(PipeMessageReceiver to, IWriter writer) {
        sendMessage(to, writer);
    }

    @Override
    public void onPlayerOpen(Player player) {
    }

    @Override
    public void onPlayerClose(Player player) {
    }

    // --- IRedstoneStatementContainer ---

    private final int[] redstoneOutputs = new int[Direction.values().length];
    private final int[] redstoneOutputsThisTick = new int[Direction.values().length];

    @Override
    public int getRedstoneInput(Direction side) {
        if (level == null) return 0;
        if (side == null) {
            return level.getBestNeighborSignal(worldPosition);
        }
        return level.getSignal(worldPosition.relative(side), side);
    }

    public int getRedstoneOutput(Direction side) {
        if (side == null) return 0;
        return redstoneOutputs[side.ordinal()];
    }

    @Override
    public boolean setRedstoneOutput(Direction side, int value) {
        if (side == null) {
            boolean changed = false;
            for (int i = 0; i < 6; i++) {
                if (redstoneOutputsThisTick[i] < value) {
                    redstoneOutputsThisTick[i] = value;
                    changed = true;
                }
            }
            return changed;
        }
        int idx = side.ordinal();
        if (redstoneOutputsThisTick[idx] < value) {
            redstoneOutputsThisTick[idx] = value;
            return true;
        }
        return false;
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
