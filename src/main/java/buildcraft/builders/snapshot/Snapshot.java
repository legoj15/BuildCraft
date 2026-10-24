/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.enums.EnumSnapshotType;

import net.minecraft.network.FriendlyByteBuf;

import buildcraft.lib.misc.HashUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.RotationUtil;
import buildcraft.lib.misc.VecUtil;
import buildcraft.lib.misc.data.Box;

public abstract class Snapshot {
    public Key key = new Key();
    public BlockPos size;
    public Direction facing;
    public BlockPos offset;

    public static Snapshot create(EnumSnapshotType type) {
        switch (type) {
            case TEMPLATE:
                return new Template();
            case BLUEPRINT:
                return new Blueprint();
        }
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static int posToIndex(int sizeX, int sizeY, int sizeZ, int x, int y, int z) {
        return ((z * sizeY) + y) * sizeX + x;
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static int posToIndex(BlockPos size, int x, int y, int z) {
        return posToIndex(size.getX(), size.getY(), size.getZ(), x, y, z);
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static int posToIndex(int sizeX, int sizeY, int sizeZ, BlockPos pos) {
        return posToIndex(sizeX, sizeY, sizeZ, pos.getX(), pos.getY(), pos.getZ());
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static int posToIndex(BlockPos size, BlockPos pos) {
        return posToIndex(size.getX(), size.getY(), size.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public int posToIndex(int x, int y, int z) {
        return posToIndex(size, x, y, z);
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public int posToIndex(BlockPos pos) {
        return posToIndex(size, pos);
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static BlockPos indexToPos(int sizeX, int sizeY, int sizeZ, int i) {
        return new BlockPos(
            i % sizeX,
            (i / sizeX) % sizeY,
            i / (sizeY * sizeX)
        );
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static BlockPos indexToPos(BlockPos size, int i) {
        return indexToPos(size.getX(), size.getY(), size.getZ(), i);
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public BlockPos indexToPos(int i) {
        return indexToPos(size, i);
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static int getDataSize(int x, int y, int z) {
        return x * y * z;
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static int getDataSize(BlockPos size) {
        return getDataSize(size.getX(), size.getY(), size.getZ());
    }

    /**
     * The cell count of a size read from a file or an upload, where it is untrusted: refused when a side is negative
     * or the count does not fit an int (it would wrap, and {@link #getDataSize} would then disagree with the cells
     * actually indexed).
     */
    public static int checkedDataSize(BlockPos size) throws InvalidInputDataException {
        if (size.getX() < 0 || size.getY() < 0 || size.getZ() < 0) {
            throw new InvalidInputDataException("Snapshot size " + size + " has a negative side");
        }
        long xy = (long) size.getX() * size.getY(); // at most 2^62: cannot wrap
        // xy is checked first so the second product stays inside a long too
        if (xy > Integer.MAX_VALUE || xy * size.getZ() > Integer.MAX_VALUE) {
            throw new InvalidInputDataException("Snapshot size " + size + " holds more cells than an int can count");
        }
        return (int) (xy * size.getZ());
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public int getDataSize() {
        return getDataSize(size);
    }

    public static CompoundTag writeToNBT(Snapshot snapshot) {
        CompoundTag nbt = snapshot.serializeNBT();
        nbt.put("type", NBTUtilBC.writeEnum(snapshot.getType()));
        return nbt;
    }

    public static Snapshot readFromNBT(CompoundTag nbt) throws InvalidInputDataException {
        return readFromNBT(nbt, Integer.MAX_VALUE);
    }

    /**
     * Read a snapshot of untrusted origin (an upload). A size that claims more than {@code maxCells} cells is
     * refused before anything else is read, for every snapshot type: how much data a snapshot carries says nothing
     * about its claimed size (a template drops trailing zero bits, so a header claiming two billion cells can come
     * with no data at all), yet every builder that loads it sizes its working arrays from the claim.
     */
    public static Snapshot readFromNBT(CompoundTag nbt, int maxCells) throws InvalidInputDataException {
        BlockPos claimed = NBTUtilBC.readBlockPos(NBTUtilBC.getCompound(nbt, "size"));
        int cells = checkedDataSize(claimed);
        if (cells > maxCells) {
            throw new InvalidInputDataException(
                "Snapshot size " + claimed + " claims " + cells + " cells, more than the " + maxCells + " allowed");
        }
        Tag tag = nbt.get("type");
        EnumSnapshotType type = NBTUtilBC.readEnum(tag, EnumSnapshotType.class);
        if (type == null) {
            throw new InvalidInputDataException("Unknown snapshot type " + tag);
        }
        Snapshot snapshot = Snapshot.create(type);
        snapshot.deserializeNBT(nbt);
        return snapshot;
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("key", key.serializeNBT());
        nbt.put("size", NBTUtilBC.writeBlockPos(size));
        nbt.put("facing", NBTUtilBC.writeEnum(facing));
        nbt.put("offset", NBTUtilBC.writeBlockPos(offset));
        return nbt;
    }

    public void deserializeNBT(CompoundTag nbt) throws InvalidInputDataException {
        key = new Key(NBTUtilBC.getCompound(nbt, "key"));
        size = NBTUtilBC.readBlockPos(NBTUtilBC.getCompound(nbt, "size"));
        checkedDataSize(size);
        facing = NBTUtilBC.readEnum(nbt.get("facing"), Direction.class);
        offset = NBTUtilBC.readBlockPos(NBTUtilBC.getCompound(nbt, "offset"));
    }

    abstract public Snapshot copy();

    abstract public EnumSnapshotType getType();

    /**
     * Count of cells in this snapshot that represent something other than empty air.
     * Templates count every fillable cell (a Template doesn't know what gets placed —
     * the Builder pulls from inventory); Blueprints count cells whose palette entry is not
     * the {@link SchematicBlockAir} placeholder. Fluid sources go through
     * {@link SchematicBlockFluid} which doesn't override {@code isAir()}, so they count.
     * <p>
     * Drives the {@code start_of_something_big} advancement threshold in TileBuilder, but
     * kept generic in case anything else wants a "non-trivial structure" gate.
     */
    abstract public int countNonAirCells();

    /** Whether everything this snapshot was read from resolved in this game, so writing it back loses nothing that
     *  was saved (load-time migrations aside). False when it names a block, property value or schematic content this
     *  game lacks, or holds a schematic that cannot vouch for its own read. */
    abstract boolean readFully();

    /**
     * {@code value} made fit for one log line: every control character (a newline would let it forge a line of its
     * own) becomes {@code ?}, and it is cut to {@code maxLength}. For text read from untrusted snapshot bytes, or a
     * loader's exception message, which may echo them.
     */
    static String logSafe(@Nullable Object value, int maxLength) {
        String text = String.valueOf(value).replaceAll("\\p{Cntrl}", "?");
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    public void computeKey() {
        computeKeyAndSerialize();
    }

    /**
     * {@link #computeKey} that also hands back what it serialized: the snapshot's NBT as {@link #writeToNBT} writes it
     * once the new key is set, built once. For a caller that goes on to measure or store that form of a large snapshot
     * (an upload checking it against its size limit), which should not pay to build it a second time.
     */
    public CompoundTag computeKeyAndSerialize() {
        CompoundTag nbt = writeToNBT(this);
        // The hash covers the content, not the key it replaces.
        nbt.remove("key");
        key = new Key(key, HashUtil.computeHash(nbt));
        nbt.put("key", key.serializeNBT());
        return nbt;
    }

    @Override
    public String toString() {
        return "Snapshot{" +
            "key=" + key +
            ", size=" + (size != null ? size.getX() + "x" + size.getY() + "x" + size.getZ() : "null") +
            ", facing=" + facing +
            ", offset=" + offset +
            "}";
    }

    public static class Key {
        public final byte[] hash;
        @Nullable // for client storage
        public final Header header;

        @SuppressWarnings("WeakerAccess")
        public Key() {
            this.hash = new byte[0];
            this.header = null;
        }

        @SuppressWarnings("WeakerAccess")
        public Key(Key oldKey, byte[] hash) {
            this.hash = hash;
            this.header = oldKey.header;
        }

        @SuppressWarnings("WeakerAccess")
        public Key(Key oldKey, @Nullable Header header) {
            this.hash = oldKey.hash;
            this.header = header;
        }

        @SuppressWarnings("WeakerAccess")
        public Key(CompoundTag nbt) {
            hash = NBTUtilBC.getByteArray(nbt, "hash", new byte[0]);
            header = nbt.contains("header") ? new Header(NBTUtilBC.getCompound(nbt, "header")) : null;
        }

        public Key(FriendlyByteBuf buf) {
            this.hash = buf.readByteArray();
            this.header = (buf.readByte() != 0) ? new Header(buf) : null;
        }

        public CompoundTag serializeNBT() {
            CompoundTag nbt = new CompoundTag();
            nbt.putByteArray("hash", hash);
            if (header != null) {
                nbt.put("header", header.serializeNBT());
            }
            return nbt;
        }

        public void writeToByteBuf(FriendlyByteBuf buf) {
            buf.writeByteArray(hash);
            buf.writeByte(header != null ? 1 : 0);
            if (header != null) {
                header.writeToByteBuf(buf);
            }
        }

        @Override
        public boolean equals(Object o) {
            return this == o ||
                o != null &&
                    getClass() == o.getClass() &&
                    Arrays.equals(hash, ((Key) o).hash) &&
                    (header != null ? header.equals(((Key) o).header) : ((Key) o).header == null);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hash);
        }

        @Override
        public String toString() {
            return HashUtil.convertHashToString(hash);
        }
    }

    public static class Header {
        public final Key key;
        public final UUID owner;
        public final Date created;
        public final String name;

        @SuppressWarnings("WeakerAccess")
        public Header(Key key, UUID owner, Date created, String name) {
            this.key = key;
            this.owner = owner;
            this.created = created;
            this.name = name;
        }

        @SuppressWarnings("WeakerAccess")
        public Header(CompoundTag nbt) {
            key = new Key(NBTUtilBC.getCompound(nbt, "key"));
            owner = NBTUtilBC.getUUID(nbt, "owner");
            created = new Date(NBTUtilBC.getLong(nbt, "created", 0L));
            name = NBTUtilBC.getString(nbt, "name", "");
        }

        public Header(FriendlyByteBuf buf) {
            key = new Key(buf);
            owner = buf.readUUID();
            created = new Date(buf.readLong());
            name = buf.readUtf();
        }

        public CompoundTag serializeNBT() {
            CompoundTag nbt = new CompoundTag();
            nbt.put("key", key.serializeNBT());
            NBTUtilBC.putUUID(nbt, "owner", owner);
            nbt.putLong("created", created.getTime());
            nbt.putString("name", name);
            return nbt;
        }

        public void writeToByteBuf(FriendlyByteBuf buf) {
            key.writeToByteBuf(buf);
            buf.writeUUID(owner);
            buf.writeLong(created.getTime());
            buf.writeUtf(name);
        }

        public Player getOwnerPlayer(Level level) {
            return level.getPlayerByUUID(owner);
        }

        @Override
        public boolean equals(Object o) {
            return this == o ||
                o != null &&
                    getClass() == o.getClass() &&
                    key.equals(((Header) o).key) &&
                    owner.equals(((Header) o).owner) &&
                    created.equals(((Header) o).created) &&
                    name.equals(((Header) o).name);
        }

        @Override
        public int hashCode() {
            int result = key.hashCode();
            result = 31 * result + owner.hashCode();
            result = 31 * result + created.hashCode();
            result = 31 * result + name.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public abstract class BuildingInfo {
        public final BlockPos basePos;
        public final BlockPos offsetPos;
        public final Rotation rotation;
        public final Box box = new Box();

        @SuppressWarnings("this-escape")
        protected BuildingInfo(BlockPos basePos, Rotation rotation) {
            this.basePos = basePos;
            this.offsetPos = basePos.offset(offset.rotate(rotation));
            this.rotation = rotation;
            this.box.extendToEncompass(toWorld(BlockPos.ZERO));
            this.box.extendToEncompass(toWorld(size.subtract(VecUtil.POS_ONE)));
        }

        public BlockPos toWorld(BlockPos blockPos) {
            return blockPos
                .rotate(rotation)
                .offset(offsetPos);
        }

        public BlockPos fromWorld(BlockPos blockPos) {
            return blockPos
                .subtract(offsetPos)
                .rotate(RotationUtil.invert(rotation));
        }

        public abstract Snapshot getSnapshot();
    }
}
