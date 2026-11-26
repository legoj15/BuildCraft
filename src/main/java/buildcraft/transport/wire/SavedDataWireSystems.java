/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.wire;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.10 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?}
import net.minecraft.util.datafix.DataFixTypes;


import buildcraft.api.transport.EnumWirePart;
import buildcraft.api.transport.IWireEmitter;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.LegacySavedData;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.platform.Platform;

import org.jspecify.annotations.Nullable;

public class SavedDataWireSystems extends SavedData implements LegacySavedData.Recoverable {
    //? if >=26.1 {
    public static final SavedDataType<SavedDataWireSystems> TYPE = LegacySavedData.type(
        "buildcraft_wire_systems",
        SavedDataWireSystems::new,
        SavedDataWireSystems::makeCodec,
        DataFixTypes.SAVED_DATA_MAP_DATA
    );
    //?} elif >=1.21.10 {
    /*public static final SavedDataType<SavedDataWireSystems> TYPE = new SavedDataType<>(
        "buildcraft_wire_systems",
        () -> new SavedDataWireSystems(null),
        makeCodec(null),
        DataFixTypes.SAVED_DATA_MAP_DATA
    );*/
    //?} else {
    /*// 1.21.1: no SavedDataType — use SavedData.Factory (ctor, (tag,provider)->T deserializer, DataFixTypes).
    public static final SavedData.Factory<SavedDataWireSystems> TYPE = new SavedData.Factory<>(
        () -> new SavedDataWireSystems(null),
        (tag, provider) -> {
            SavedDataWireSystems data = new SavedDataWireSystems(null);
            data.readFromTag(tag);
            return data;
        },
        DataFixTypes.SAVED_DATA_MAP_DATA
    );*/
    //?}

    public Level world;
    public final Map<WireSystem, Boolean> wireSystems = new HashMap<>();
    public boolean gatesChanged = true;
    public boolean structureChanged = true;
    public final List<WireSystem> changedSystems = new ArrayList<>();
    /** Ticks a player waits between full wire resends triggered by chunk deliveries (one second). */
    public static final int RESEND_INTERVAL_TICKS = 20;
    /** Players whose client received a wire-holding chunk (fed by {@link #onChunkSent}) and the pacing of the full
     *  resend each is owed: every system it can see. Chunks are delivered over many ticks (login, a teleport, a walk
     *  along a wire run), so a player is sent at once and then at most once per {@link #RESEND_INTERVAL_TICKS}, with
     *  a trailing send for whatever landed in between: the state sent last always follows the last chunk. */
    private final ResendThrottle<ServerPlayer> resends = new ResendThrottle<>(RESEND_INTERVAL_TICKS);
    /** The throttle's clock: one per {@link #tick}. */
    private long resendClock;
    /** How many systems have an element in each chunk. Lets {@link #onChunkSent} ignore the (vast majority
     *  of) chunks with no wires in O(1), so terrain streaming never triggers a full wire resend. */
    private final Map<ChunkPos, Integer> systemsPerChunk = new HashMap<>();

    private record EmitterColour(WireSystem.WireElement emitter, DyeColor colour) {}

    /** What each emitter said, per colour, when it was last read in a chunk in memory. A gate in a chunk that is not
     *  in memory cannot change, so this is still what it says. Never saved, and an entry goes with the last system
     *  of its colour that holds the emitter ({@link #removeWireSystem}). */
    private final Map<EmitterColour, Boolean> lastEmitting = new HashMap<>();

    private final Map<WireSystem.WireElement, List<WireSystem>> elementsToWireSystemsIndex = new HashMap<>();
    /** Blocks whose wire systems are stale, drained by {@link #tick()}. Not saved: a pipe that loads rebuilds its
     *  own systems on its first tick. */
    private final Set<BlockPos> staleHolders = new LinkedHashSet<>();

    public SavedDataWireSystems(@Nullable ServerLevel level) {
        this.world = level;
    }

    @Override
    public boolean hasNoContent() {
        return wireSystems.isEmpty();
    }

    @Override
    public String describeContent() {
        return wireSystems.size() + " wire systems";
    }

    //? if <1.21.10 {
    /*// 1.21.1: SavedData.save(CompoundTag, Provider) is abstract (no Codec-driven SavedDataType).
    // Reuse the existing tag serializer; the returned tag is what DimensionDataStorage persists.
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return writeToTag();
    }*/
    //?}

    private static Codec<SavedDataWireSystems> makeCodec(@Nullable ServerLevel level) {
        return CompoundTag.CODEC.flatXmap(
            tag -> {
                SavedDataWireSystems data = new SavedDataWireSystems(level);
                data.readFromTag(tag);
                return DataResult.success(data);
            },
            data -> DataResult.success(data.writeToTag())
        );
    }

    public void markStructureChanged() {
        structureChanged = true;
        gatesChanged = true;
    }

    public List<WireSystem> getWireSystemsWithElement(WireSystem.WireElement element) {
        List<WireSystem> wireSystemsWithElement = this.elementsToWireSystemsIndex.get(element);
        return wireSystemsWithElement != null ? new ArrayList<>(wireSystemsWithElement) : Collections.emptyList();
    }

    public List<WireSystem> getWireSystemsWithElementAsReadOnlyList(WireSystem.WireElement element) {
        return this.elementsToWireSystemsIndex.getOrDefault(element, Collections.emptyList());
    }

    /** Called from the chunk-sent hook {@code BCTransport} registers ({@code Platform.gameHooks().onChunkSent}:
     *  NeoForge's {@code ChunkWatchEvent.Sent}, Fabric's chunk-send mixin) — after the chunk packet
     *  is queued on the player's connection, so the wire payloads that follow it are sent behind the chunk (1.12.2
     *  {@code BCTransportEventDist.onChunkWatch}). That send is paced: at the next {@link #tick} when the player
     *  has had none within {@link #RESEND_INTERVAL_TICKS}, otherwise when the interval is up (see
     *  {@link ResendThrottle}). Runs once per chunk delivered; keep it O(1). */
    public void onChunkSent(ServerPlayer player, ChunkPos pos) {
        if (systemsPerChunk.containsKey(pos)) {
            resends.request(player);
        }
    }

    /** Whether a chunk delivered to {@code player} has asked for a full wire resend that has not gone out yet. */
    public boolean isResendQueued(ServerPlayer player) {
        return resends.isPending(player);
    }

    /** Drop the queued resend and the pacing history of {@code player} (they left, or a test is cleaning up). */
    public void forgetResends(ServerPlayer player) {
        resends.forget(player);
    }

    private void indexChunks(WireSystem wireSystem, int delta) {
        wireSystem.getChunkPosesAsStream().forEach(pos ->
            systemsPerChunk.compute(pos, (unused, count) -> {
                int next = (count == null ? 0 : count) + delta;
                return next <= 0 ? null : next;
            }));
    }

    public void removeWireSystem(WireSystem wireSystem) {
        if (wireSystems.remove(wireSystem) != null) {
            indexChunks(wireSystem, -1);
        }
        wireSystem.elements.forEach(elementIn -> {
            elementsToWireSystemsIndex.computeIfPresent(elementIn, (element, systems) -> {
                systems.remove(wireSystem);
                return systems.isEmpty() ? null : systems;
            });
        });
        // What an emitter last said is kept only while a system of that colour still holds it.
        for (WireSystem.WireElement element : wireSystem.elements) {
            if (element.type == WireSystem.WireElement.Type.EMITTER_SIDE
                    && getWireSystemsWithElementAsReadOnlyList(element).stream()
                        .noneMatch(other -> other.color == wireSystem.color)) {
                lastEmitting.remove(new EmitterColour(element, wireSystem.color));
            }
        }
        markStructureChanged();
    }

    public void addWireSystem(WireSystem wireSystem, boolean powered) {
        if (this.wireSystems.put(wireSystem, powered) == null) {
            indexChunks(wireSystem, 1);
            wireSystem.elements.forEach(systemElement -> {
                List<WireSystem> wireSystemsWithElement = this.elementsToWireSystemsIndex.computeIfAbsent(systemElement, unused -> new ArrayList<>());
                if (wireSystemsWithElement.contains(wireSystem)) {
                    throw new IllegalStateException();
                }
                wireSystemsWithElement.add(wireSystem);
            });
        }
    }

    public void buildAndAddWireSystem(WireSystem.WireElement element) {
        rebuild(List.of(element));
    }

    /** The wire systems through the pipe at {@code pos} no longer match what its wires can link to (a pluggable, a
     *  colour or a connection changed there, or the pipe is gone); they are rebuilt at the next {@link #tick()}, once
     *  however many changes land in a tick. Only records the position, so it is safe to call from a block entity's
     *  removal. */
    public void markStale(BlockPos pos) {
        staleHolders.add(pos.immutable());
    }

    /** {@link #markStale} for a pipe that may have nothing to do with wires: it is marked when it has wires, or when
     *  a system still names a wire at its position (its wires went without the systems being told: NBT written into
     *  it before its first tick, or a save that already held such a system). A pipe with neither is in no system and
     *  links to nothing. */
    public void markStaleIfWired(BlockPos pos, boolean hasWires) {
        if (hasWires || namesWireAt(pos)) {
            markStale(pos);
        }
    }

    private boolean namesWireAt(BlockPos pos) {
        for (EnumWirePart part : EnumWirePart.VALUES) {
            if (elementsToWireSystemsIndex.containsKey(new WireSystem.WireElement(pos, part))) {
                return true;
            }
        }
        return false;
    }

    /** Builds the system of every seed that is a wire in a chunk in memory, replacing the same-colour systems it
     *  overlaps. Every wire part a replaced system held is re-seeded, so none of those that is still a wire in a
     *  chunk in memory is left without a system. A seed whose chunk is not in memory is skipped and stays in no
     *  system until a rebuild covers it again (its own pipe's first tick after loading, or a walk that crosses into
     *  its chunk): a rebuild cuts a system at the edge of the loaded world, and a gate beyond that edge stops
     *  counting. The walk itself crosses to a neighbour through
     *  {@code IPipeHolder.getNeighbourPipe}, whose loaded check is the chunk's ticket, so it can complete the load
     *  of a neighbouring chunk that is queued but not yet in memory; it never reaches one that has no ticket. */
    private void rebuild(Collection<WireSystem.WireElement> seeds) {
        Deque<WireSystem.WireElement> queue = new ArrayDeque<>(seeds);
        Set<WireSystem.WireElement> built = new HashSet<>();
        while (!queue.isEmpty()) {
            WireSystem.WireElement seed = queue.remove();
            if (seed.type != WireSystem.WireElement.Type.WIRE_PART || built.contains(seed)
                    || !BlockUtil.isChunkPresent(world, seed.blockPos)) {
                continue;
            }
            WireSystem wireSystem = new WireSystem(this, seed);
            if (wireSystem.isEmpty()) {
                continue;
            }
            for (WireSystem.WireElement element : wireSystem.elements) {
                if (element.type != WireSystem.WireElement.Type.WIRE_PART) {
                    continue;
                }
                for (WireSystem old : getWireSystemsWithElement(element)) {
                    if (old.color == wireSystem.color) {
                        removeWireSystem(old);
                        queue.addAll(old.elements);
                    }
                }
            }
            addWireSystem(wireSystem, false);
            wireSystems.put(wireSystem, wireSystem.update(this, false));
            built.addAll(wireSystem.elements);
        }
        markStructureChanged();
    }

    /** Drops every system through a stale block, whatever its colour (so a system naming a wire that is no longer
     *  there goes too), and rebuilds from what those systems held plus the block's own eight wire positions: a
     *  position without a wire, or a block without a pipe, builds nothing. */
    private void rebuildStale() {
        if (staleHolders.isEmpty()) {
            return;
        }
        Set<WireSystem.WireElement> seeds = new LinkedHashSet<>();
        for (BlockPos pos : staleHolders) {
            for (EnumWirePart part : EnumWirePart.VALUES) {
                WireSystem.WireElement element = new WireSystem.WireElement(pos, part);
                for (WireSystem wireSystem : getWireSystemsWithElement(element)) {
                    removeWireSystem(wireSystem);
                    seeds.addAll(wireSystem.elements);
                }
                seeds.add(element);
            }
        }
        staleHolders.clear();
        rebuild(seeds);
    }

    /** Whether the emitter on that pipe side is emitting {@code color}. Never loads a chunk: an emitter whose chunk
     *  is not in memory answers what it said when it was last read, or {@code unseen} when it never was (a system
     *  loaded from disk whose chunks have not loaded since). */
    public boolean isEmitterEmitting(WireSystem.WireElement element, DyeColor color, boolean unseen) {
        EmitterColour key = new EmitterColour(element, color);
        if (!BlockUtil.isChunkPresent(world, element.blockPos)) {
            Boolean last = lastEmitting.get(key);
            return last != null ? last : unseen;
        }
        boolean emitting = world.getBlockEntity(element.blockPos) instanceof IPipeHolder holder
            && holder.getPluggable(element.emitterSide) instanceof IWireEmitter emitter && emitter.isEmitting(color);
        lastEmitting.put(key, emitting);
        return emitting;
    }

    public void tick() {
        rebuildStale();
        // Wakeup matrix (all paths confirmed by audit 2026-05-19):
        //   • Wire add/remove        → WireManager.addPart/removePart → buildAndAddWireSystem
        //                              / removeWireSystem → markStructureChanged
        //   • Pipe BE load           → WireManager.tick (first call) → markStaleIfWired
        //   • Pipe removed (any cause but a chunk unload: player, /setblock, /fill, explosion,
        //     another mod) → TilePipeHolder.setRemoved → markWireSystemsStale → markStaleIfWired
        //   • Pluggable, colour or connection change → IPipeHolder.requestWireLinkRefresh
        //                              → TilePipeHolder.markWireSystemsStale → markStaleIfWired
        //   • NBT read into a pipe that has already ticked (/data, another mod)
        //                              → TilePipeHolder.readData → markStale
        //   • markStaleIfWired reaches markStale when the pipe has wires or a system still names
        //     one at its position. Every markStale drains through rebuildStale at the top of this
        //     method, which ends in markStructureChanged.
        //   • Gate emit state change → GateLogic.resolveActions sets gatesChanged = true directly
        //                              whenever the resolved wireBroadcasts set differs from
        //                              the previous one. (GateLogic is the sole IWireEmitter
        //                              implementation; PluggableGate just delegates.)
        // With every wakeup point in place we can correctly reset gatesChanged after the recalc.
        // An earlier port revision left this reset out as a workaround for a then-broken gate→wire
        // signal path (BCModules.TRANSPORT.isLoaded() always returned false, so the reflective
        // notification in GateLogic was dead code). That workaround caused every steady-state tick
        // to needlessly recompute every wire system on the level; with the direct signal restored,
        // we recompute only when something actually changed.
        if (gatesChanged) {
            wireSystems.replaceAll((wireSystem, oldPowered) -> {
                boolean newPowered = wireSystem.update(this, oldPowered);
                if (oldPowered != newPowered) {
                    changedSystems.add(wireSystem);
                }
                return newPowered;
            });
        }

        long now = ++resendClock;
        if (world instanceof ServerLevel serverLevel) {
            // Players whose chunk-delivery resend is due this tick (a flagged player still inside the interval of
            // its last send waits, and is returned by a later tick).
            Set<ServerPlayer> resendDue = resends.takeDue(now);
            // The send loops below only ever emit when structureChanged, a changed system, or a
            // due player exists — skip the whole player×system sweep on a quiet tick.
            if (structureChanged || !changedSystems.isEmpty() || !resendDue.isEmpty()) {
                for (ServerPlayer player : serverLevel.getPlayers(p -> true)) {
                    Map<Integer, WireSystem> changedWires = new HashMap<>();
                    Map<Integer, Boolean> hashesPowered = new HashMap<>();
                    if (structureChanged || resendDue.contains(player)) {
                        // This is the whole structure, so whatever chunk deliveries asked for is answered by it
                        // and the pacing interval restarts from here.
                        resends.markSent(player, now);
                        // Structure (and with it power) of every system this player watches.
                        for (Map.Entry<WireSystem, Boolean> entry : wireSystems.entrySet()) {
                            WireSystem ws = entry.getKey();
                            if (ws.isPlayerWatching(player)) {
                                changedWires.put(ws.getWiresHashCode(), ws);
                                hashesPowered.put(ws.getWiresHashCode(), entry.getValue());
                            }
                        }
                    } else {
                        // Power of the systems whose state flipped this tick, only.
                        for (WireSystem ws : changedSystems) {
                            Boolean powered = wireSystems.get(ws);
                            if (powered != null && ws.isPlayerWatching(player)) {
                                hashesPowered.put(ws.getWiresHashCode(), powered);
                            }
                        }
                    }
                    if (!changedWires.isEmpty()) {
                        Platform.network().sendToPlayer(player, new PayloadWireSystems(changedWires));
                    }
                    if (!hashesPowered.isEmpty()) {
                        Platform.network().sendToPlayer(player, new PayloadWireSystemsPowered(hashesPowered));
                    }
                }
            }
        }

        if (structureChanged || !changedSystems.isEmpty()) {
            setDirty();
        }
        gatesChanged = false;
        structureChanged = false;
        changedSystems.clear();
    }

    public CompoundTag writeToTag() {
        CompoundTag nbt = new CompoundTag();
        ListTag entriesList = new ListTag();
        wireSystems.forEach((wireSystem, powered) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("wireSystem", wireSystem.writeToNBT());
            entry.putBoolean("powered", powered);
            entriesList.add(entry);
        });
        nbt.put("entries", entriesList);
        return nbt;
    }

    public void readFromTag(CompoundTag nbt) {
        wireSystems.clear();
        this.elementsToWireSystemsIndex.clear();
        systemsPerChunk.clear();

        ListTag entriesList = NBTUtilBC.getList(nbt, "entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entriesList.size(); i++) {
            Tag tag = entriesList.get(i);
            if (tag instanceof CompoundTag entry) {
                CompoundTag wsTag = NBTUtilBC.getCompound(entry, "wireSystem");
                this.addWireSystem(new WireSystem(wsTag), NBTUtilBC.getBoolean(entry, "powered", false));
            }
        }
    }

    public static SavedDataWireSystems get(Level world) {
        if (world.isClientSide()) {
            throw new UnsupportedOperationException("Attempted to get SavedDataWireSystems on the client!");
        }
        if (world instanceof ServerLevel serverLevel) {
            //? if >=1.21.10 {
            SavedDataWireSystems instance = serverLevel.getDataStorage().computeIfAbsent(TYPE);
            //?} else {
            /*SavedDataWireSystems instance = serverLevel.getDataStorage().computeIfAbsent(TYPE, "buildcraft_wire_systems");*/
            //?}
            instance.world = world;
            return instance;
        }
        throw new IllegalArgumentException("World is not a ServerLevel!");
    }
}
