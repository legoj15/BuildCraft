/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.wire;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ChunkPos;

import buildcraft.api.transport.EnumWirePart;
import buildcraft.api.transport.IWireEmitter;
import buildcraft.api.transport.WireNode;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.misc.MathUtil;
import buildcraft.lib.misc.NBTUtilBC;

public final class WireSystem {
    public final ImmutableList<WireElement> elements;
    public final DyeColor color;

    /** Hands out {@link #clientKey}s: one per system instance for as long as the JVM runs. */
    private static final AtomicInteger NEXT_CLIENT_KEY = new AtomicInteger();

    private final transient int cachedHashCode;
    /** The number clients know this system by. It belongs to the instance, so two systems never share one (a
     *  hash of the wires could collide, and one of the two would then never reach the client), and a system
     *  rebuilt as a new instance is a new system to the clients: {@link SavedDataWireSystems}'s rebuild puts an
     *  unchanged system's old instance back for that reason. */
    private final int clientKey = NEXT_CLIENT_KEY.getAndIncrement();
    /** Distinct chunks of {@link #elements} — several elements share a pipe's chunk. Cached because
     *  {@link #isPlayerWatching} walks it for player×system pairs in {@link SavedDataWireSystems#tick()}. */
    private final transient Set<ChunkPos> cachedChunkPoses;

    public boolean hasElement(WireElement element) {
        return elements.contains(element);
    }

    /** Whether a wire may link from the pipe at {@code holder} into the neighbouring pipe across {@code side}.
     *  <p>A blocking pluggable on either facing side cuts the link, whatever the pipes say about their connection.
     *  That is tested first because a kinesis pipe beside a Power Adaptor on a pipe that takes power (or beside a
     *  Docking Station holding a robot) has a connection across that face (a tile connection, which is how the power
     *  gets in) that the pluggable's own pipe does not have. Otherwise a pipe connection links the wires only when
     *  BOTH pipes report it, and a structure pipe links across an unconnected face when the two pipes' colours allow
     *  it. Every clause reads the same from either pipe, so the answer does too: the wire-system walk
     *  ({@link #getConnectedElementsOfElement}) relies on that to build the same system wherever it starts, and
     *  {@link SavedDataWireSystems}'s rebuild to leave no wire without one (#251).
     *  <p>A pipe marked for a connection update recomputes on its next tick, so while one of the two lags (a tick at
     *  most, or for as long as its chunk is loaded but not ticking) a new connection links nothing yet; the lagging
     *  pipe's own recompute then asks for the refresh that forms the link. No pair disagrees for longer than that,
     *  because a ticking pipe always finds its face neighbours loaded and so never skips one when it recomputes.
     *  The links depend on that holding; {@code TilePipeHolder.getNeighbourTile} has the argument. */
    public static boolean canWireConnect(IPipeHolder holder, Direction side) {
        IPipe pipe = holder.getPipe();
        if (pipe == null) {
            return false;
        }
        IPipe oPipe = holder.getNeighbourPipe(side);
        if (oPipe == null) {
            return false;
        }
        if (isBlockingPluggable(holder, side) || isBlockingPluggable(oPipe.getHolder(), side.getOpposite())) {
            return false;
        }
        if (pipe.isConnected(side) && oPipe.isConnected(side.getOpposite())) {
            return true;
        }
        if (pipe.getDefinition().flowType == PipeApi.flowStructure || oPipe.getDefinition().flowType == PipeApi.flowStructure) {
            return pipe.getColour() == null || oPipe.getColour() == null || pipe.getColour() == oPipe.getColour();
        }
        return false;
    }

    /** Whether the pipe holds a pluggable on that face whose {@code isBlocking()} is true: of BuildCraft's own, every
     *  one except a hollow facade and a lens. */
    private static boolean isBlockingPluggable(IPipeHolder holder, Direction side) {
        PipePluggable pluggable = holder.getPluggable(side);
        return pluggable != null && pluggable.isBlocking();
    }

    public static List<WireElement> getConnectedElementsOfElement(IPipeHolder holder, WireElement element) {
        assert element.wirePart != null;
        WireNode node = new WireNode(element.blockPos, element.wirePart);

        List<WireElement> list = new ArrayList<>();
        for (Direction face : Direction.values()) {
            WireNode oNode = node.offset(face);
            // equality check is fine here -- WireNode.offset returns the same blockpos (identity wise) if its the same
            if (oNode.pos == node.pos || canWireConnect(holder, face)) {
                list.add(new WireElement(oNode.pos, oNode.part));
            }
        }
        return list;
    }

    public static List<WireElement> getConnectedElementsOfElement(Level world, WireElement element) {
        if (element.type == WireElement.Type.WIRE_PART) {
            BlockEntity tile = world.getBlockEntity(element.blockPos);
            if (tile instanceof IPipeHolder holder) {
                return getConnectedElementsOfElement(holder, element);
            }
        }
        return Collections.emptyList();
    }

    public WireSystem(ImmutableList<WireElement> elements, DyeColor color) {
        this.elements = Objects.requireNonNull(elements, "elements");
        this.color = color;

        this.cachedHashCode = this.computeHashCode();
        this.cachedChunkPoses = computeChunkPoses();
    }

    /** Walks the wires linked to {@code startElement}, reading only the world: registering the result, and replacing
     *  the same-colour systems it overlaps, is {@link SavedDataWireSystems}'s job. */
    public WireSystem(SavedDataWireSystems wireSystems, WireElement startElement) {
        Map<BlockPos, IPipeHolder> holdersCache = new HashMap<>();
        Set<WireElement> walked = new HashSet<>();

        Queue<WireElement> queue = new ArrayDeque<>();
        queue.add(startElement);

        DyeColor tempColor = null;
        ImmutableList.Builder<WireElement> elementBuilder = ImmutableList.builder();

        while (!queue.isEmpty()) {
            WireElement element = queue.remove();

            if (!walked.contains(element)) {
                if (!holdersCache.containsKey(element.blockPos)) {
                    BlockEntity tile = wireSystems.world.getBlockEntity(element.blockPos);
                    IPipeHolder holder = null;
                    if (tile instanceof IPipeHolder) {
                        holder = (IPipeHolder) tile;
                    }
                    holdersCache.put(element.blockPos, holder);
                }
                IPipeHolder holder = holdersCache.get(element.blockPos);
                if (holder != null) {
                    if (element.type == WireElement.Type.WIRE_PART) {
                        DyeColor colorOfPart = holder.getWireManager().getColorOfPart(element.wirePart);
                        if (tempColor == null) {
                            if (colorOfPart != null) {
                                tempColor = colorOfPart;
                            }
                        }
                        if (tempColor != null && colorOfPart == tempColor) {
                            elementBuilder.add(element);
                            queue.addAll(getConnectedElementsOfElement(wireSystems.world, element));
                            Arrays.stream(Direction.values()).forEach(side -> queue.add(new WireElement(element.blockPos, side)));
                        }
                    } else if (element.type == WireElement.Type.EMITTER_SIDE) {
                        if (holder.getPluggable(element.emitterSide) instanceof IWireEmitter) {
                            elementBuilder.add(new WireElement(element.blockPos, element.emitterSide));
                        }
                    }
                }
                walked.add(element);
            }
        }

        this.elements = elementBuilder.build();
        this.color = tempColor;

        this.cachedHashCode = this.computeHashCode();
        this.cachedChunkPoses = computeChunkPoses();
    }

    public boolean isEmpty() {
        return elements.stream().noneMatch(element -> element.type == WireElement.Type.WIRE_PART);
    }

    /** Whether any emitter of this system is emitting its colour. Never loads a chunk: an emitter whose chunk is out
     *  of memory answers what it said when it was last read, and one that was never read is taken to agree with
     *  {@code stored} — the system's current power state — so a system saved as powered stays so until its chunks
     *  load. Every emitter in memory is read (the loop does not stop at the first hit) so each one's answer is
     *  remembered for the next time its chunk is not. */
    public boolean update(SavedDataWireSystems wireSystems, boolean stored) {
        boolean powered = false;
        for (WireElement element : elements) {
            if (element.type == WireElement.Type.EMITTER_SIDE
                    && wireSystems.isEmitterEmitting(element, color, stored)) {
                powered = true;
            }
        }
        return powered;
    }

    public Stream<ChunkPos> getChunkPosesAsStream() {
        return this.cachedChunkPoses.stream();
    }

    private Set<ChunkPos> computeChunkPoses() {
        return elements.stream()
            .map(element -> buildcraft.lib.misc.PositionUtil.chunkContaining(element.blockPos))
            .collect(Collectors.toUnmodifiableSet());
    }

    /** True when any of this system's chunks is inside THIS player's chunk tracking view — the modern
     *  equivalent of 1.12.2's per-player {@code PlayerChunkMapEntry.containsPlayer} check. The caller passes
     *  only players of the system's own level. */
    public boolean isPlayerWatching(ServerPlayer player) {
        ChunkTrackingView view = player.getChunkTrackingView();
        for (ChunkPos chunkPos : this.cachedChunkPoses) {
            if (view.contains(chunkPos)) {
                return true;
            }
        }
        return false;
    }

    public int getClientKey() {
        return this.clientKey;
    }

    public CompoundTag writeToNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag elementsList = new ListTag();
        elements.stream().map(WireElement::writeToNBT).forEach(elementsList::add);
        nbt.put("elements", elementsList);
        nbt.putInt("color", color.getId());
        return nbt;
    }

    public WireSystem(CompoundTag nbt) {
        ListTag elementsList = NBTUtilBC.getList(nbt, "elements", Tag.TAG_COMPOUND);
        //noinspection UnstableApiUsage
        elements = IntStream.range(0, elementsList.size())
            .mapToObj(i -> {
                Tag tag = elementsList.get(i);
                return tag instanceof CompoundTag ct ? ct : new CompoundTag();
            })
            .map(WireElement::new)
            .collect(ImmutableList.toImmutableList());
        color = DyeColor.byId(NBTUtilBC.getInt(nbt, "color", 0));

        this.cachedHashCode = this.computeHashCode();
        this.cachedChunkPoses = computeChunkPoses();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        WireSystem that = (WireSystem) o;

        if (this.cachedHashCode != that.cachedHashCode) {
            return false;
        }

        if (!elements.equals(that.elements)) {
            return false;
        }
        return color == that.color;
    }

    @Override
    public int hashCode() {
        return this.cachedHashCode;
    }

    private int computeHashCode() {
        int result = elements.hashCode();
        result = 31 * result + (color != null ? color.hashCode() : 0);
        return result;
    }

    public static class WireElement {
        public final Type type;
        public final BlockPos blockPos;
        public final EnumWirePart wirePart;
        public final Direction emitterSide;

        public WireElement(BlockPos blockPos, EnumWirePart wirePart) {
            this.type = Type.WIRE_PART;
            this.blockPos = blockPos;
            this.wirePart = wirePart;
            this.emitterSide = null;
        }

        public WireElement(BlockPos blockPos, Direction emitterSide) {
            this.type = Type.EMITTER_SIDE;
            this.blockPos = blockPos;
            this.wirePart = null;
            this.emitterSide = emitterSide;
        }

        public WireElement(FriendlyByteBuf buf) {
            // Saved ordinals are clamped to the array range: a corrupt or malicious count (negative or
            // oversized) must degrade to a known element instead of throwing on array indexing.
            type = Type.values()[MathUtil.clamp(buf.readInt(), 0, Type.values().length - 1)];
            blockPos = buf.readBlockPos();
            if (type == Type.WIRE_PART) {
                wirePart = EnumWirePart.VALUES[MathUtil.clamp(buf.readInt(), 0, EnumWirePart.VALUES.length - 1)];
                this.emitterSide = null;
            } else if (type == Type.EMITTER_SIDE) {
                this.wirePart = null;
                emitterSide = Direction.from3DDataValue(buf.readInt());
            } else {
                this.wirePart = null;
                this.emitterSide = null;
            }
        }

        public WireElement(CompoundTag nbt) {
            type = Type.values()[MathUtil.clamp(NBTUtilBC.getInt(nbt, "type", 0), 0, Type.values().length - 1)];
            int bpX = NBTUtilBC.getInt(nbt, "bpX", 0);
            int bpY = NBTUtilBC.getInt(nbt, "bpY", 0);
            int bpZ = NBTUtilBC.getInt(nbt, "bpZ", 0);
            blockPos = new BlockPos(bpX, bpY, bpZ);
            if (type == Type.WIRE_PART) {
                wirePart = EnumWirePart.VALUES[MathUtil.clamp(NBTUtilBC.getInt(nbt, "wirePart", 0), 0, EnumWirePart.VALUES.length - 1)];
                this.emitterSide = null;
            } else if (type == Type.EMITTER_SIDE) {
                this.wirePart = null;
                emitterSide = Direction.from3DDataValue(NBTUtilBC.getInt(nbt, "emitterSide", 0));
            } else {
                this.wirePart = null;
                this.emitterSide = null;
            }
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(type.ordinal());
            buf.writeBlockPos(blockPos);
            if (type == Type.WIRE_PART) {
                assert wirePart != null;
                buf.writeInt(wirePart.ordinal());
            } else if (type == Type.EMITTER_SIDE) {
                assert emitterSide != null;
                buf.writeInt(emitterSide.get3DDataValue());
            }
        }

        public CompoundTag writeToNBT() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("type", type.ordinal());
            nbt.putInt("bpX", blockPos.getX());
            nbt.putInt("bpY", blockPos.getY());
            nbt.putInt("bpZ", blockPos.getZ());
            if (type == Type.WIRE_PART) {
                assert wirePart != null;
                nbt.putInt("wirePart", wirePart.ordinal());
            } else if (type == Type.EMITTER_SIDE) {
                assert emitterSide != null;
                nbt.putInt("emitterSide", emitterSide.get3DDataValue());
            }
            return nbt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            WireElement element = (WireElement) o;

            if (type != element.type) {
                return false;
            }
            if (!blockPos.equals(element.blockPos)) {
                return false;
            }
            if (wirePart != element.wirePart) {
                return false;
            }
            return emitterSide == element.emitterSide;
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + blockPos.hashCode();
            result = 31 * result + (wirePart != null ? wirePart.hashCode() : 0);
            result = 31 * result + (emitterSide != null ? emitterSide.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Element{" + "type=" + type + ", blockPos=" + blockPos + ", wirePart=" + wirePart + ", emitterSide=" + emitterSide + '}';
        }

        public enum Type {
            WIRE_PART,
            EMITTER_SIDE
        }
    }
}
