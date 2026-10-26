/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.DynamicOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.phys.Vec3;

public class NBTUtilBC {

    /** Sentinel value representing "no NBT value found". Use EndTag.INSTANCE. */
    public static final Tag NBT_NULL = EndTag.INSTANCE;

    /** Resolve a registry-aware {@link DynamicOps} for NBT codecs that need to look up dynamic-
     * registry entries (enchantments, banner patterns, painting variants, jukebox songs, etc.).
     *
     * <p>Without a {@link RegistryOps} wrapper around {@link NbtOps#INSTANCE}, codecs that
     * reference dynamic registries fail silently — {@code DataResult} returns an error and
     * {@code resultOrPartial()} yields {@link Optional#empty()}, so any
     * {@code .ifPresent(payload -> tag.put(...))} call ends up writing nothing. This was the
     * exact mode of failure behind the "Fortune III pickaxe placed in a list slot disappears
     * on close/reopen" report.
     *
     * <p>Source priority: integrated/dedicated server's registry access (works in either) →
     * client level's registry access → plain {@link NbtOps#INSTANCE} as a last-resort fallback
     * (lossy, but prevents NPE if invoked before any world is loaded). */
    public static DynamicOps<Tag> registryAwareOps() {
        net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return RegistryOps.create(NbtOps.INSTANCE, (HolderLookup.Provider) server.registryAccess());
        }
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            HolderLookup.Provider clientProvider = clientLevelRegistryAccess();
            if (clientProvider != null) {
                return RegistryOps.create(NbtOps.INSTANCE, clientProvider);
            }
        }
        return NbtOps.INSTANCE;
    }

    /** Wrapped in its own method so the client-only class reference doesn't get loaded on
     * dedicated servers (which don't have {@code Minecraft} on the classpath at all). */
    private static HolderLookup.Provider clientLevelRegistryAccess() {
        try {
            net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
            return level == null ? null : level.registryAccess();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Returns Optional.empty() if tag is null or is NBT_NULL, otherwise Optional.of(tag). */
    @SuppressWarnings("unchecked")
    public static <T extends Tag> Optional<T> toOptional(@Nullable T tag) {
        if (tag == null || tag == NBT_NULL || tag.getId() == Tag.TAG_END) {
            return Optional.empty();
        }
        return Optional.of(tag);
    }

    /**
     * Merges {@code source} over {@code destination}, as 1.12.2's blueprint rule directive {@code replaceNbt} did.
     * Compounds merge recursively over the union of their keys; a {@code source} key whose value is
     * {@link #NBT_NULL} (JSON {@code null} in a rule file) removes that key; any other {@code source} value replaces
     * the destination's wholesale (lists and scalars, and a compound over a non-compound).
     * <p>
     * Neither argument is mutated and the result shares no tag with either, so a rule's tag (shared by every
     * schematic it matches) can be merged into freshly loaded data that is then edited. Unlike 1.12.2, a
     * {@link #NBT_NULL} inside a compound that has nothing to merge into is dropped too, rather than copied over as a
     * stray end tag that the loader would choke on; and a compound holding nothing but removals, for a key the
     * destination lacks, adds no empty compound in its place.
     *
     * @return null if {@code source} is null; {@link #NBT_NULL} if it is {@link #NBT_NULL} itself; the merged compound
     *         if it is a compound; else a copy of {@code source}. */
    @Nullable
    public static Tag merge(@Nullable Tag destination, @Nullable Tag source) {
        if (source == null) {
            return null;
        }
        if (source == NBT_NULL) {
            return NBT_NULL;
        }
        if (!(source instanceof CompoundTag sourceCompound)) {
            return source.copy();
        }
        CompoundTag destinationCompound = destination instanceof CompoundTag compound ? compound : null;
        CompoundTag result = new CompoundTag();
        if (destinationCompound != null) {
            for (String key : keysOf(destinationCompound)) {
                if (!sourceCompound.contains(key)) {
                    result.put(key, destinationCompound.get(key).copy());
                }
            }
        }
        for (String key : keysOf(sourceCompound)) {
            Tag value = sourceCompound.get(key);
            if (value == NBT_NULL) {
                continue;
            }
            Tag existing = destinationCompound == null ? null : destinationCompound.get(key);
            Tag merged = merge(existing, value);
            // A compound of nothing but removals, aimed at a key the destination does not have, removes nothing and
            // must not leave an empty compound behind (a rule for one kind of pipe is aimed at every pipe).
            if (existing == null && merged instanceof CompoundTag mergedCompound && mergedCompound.isEmpty()
                && !((CompoundTag) value).isEmpty()) {
                continue;
            }
            result.put(key, merged);
        }
        return result;
    }

    /** A snapshot of {@code tag}'s keys, safe to iterate while editing the tag. */
    public static List<String> keysOf(CompoundTag tag) {
        //? if >=1.21.10 {
        return new ArrayList<>(tag.keySet());
        //?} else {
        /*return new ArrayList<>(tag.getAllKeys());*/
        //?}
    }

    // Item NBT data (replaces 1.12.2 stack.getTagCompound() pattern)

    /** Returns a copy of the item's custom data compound tag, or a new empty tag if none exists.
     * Note: Unlike 1.12.2, this returns a COPY. Use {@link #setItemData} to write changes back. */
    @Nonnull
    public static CompoundTag getItemData(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag();
    }

    /** Writes the given compound tag back to the item's custom data component.
     * If the tag is empty, the custom data component is removed entirely. */
    public static void setItemData(@Nonnull ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    // BlockPos serialization

    public static CompoundTag writeBlockPos(BlockPos pos) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("X", pos.getX());
        nbt.putInt("Y", pos.getY());
        nbt.putInt("Z", pos.getZ());
        return nbt;
    }

    public static BlockPos readBlockPos(CompoundTag nbt) {
        return new BlockPos(
            getInt(nbt, "X", 0),
            getInt(nbt, "Y", 0),
            getInt(nbt, "Z", 0)
        );
    }

    // UUID serialization

    public static void putUUID(CompoundTag nbt, String key, UUID uuid) {
        nbt.putLong(key + "Most", uuid.getMostSignificantBits());
        nbt.putLong(key + "Least", uuid.getLeastSignificantBits());
    }

    public static UUID getUUID(CompoundTag nbt, String key) {
        return new UUID(
            getLong(nbt, key + "Most", 0L),
            getLong(nbt, key + "Least", 0L)
        );
    }

    // Enum serialization

    public static StringTag writeEnum(Enum<?> value) {
        return StringTag.valueOf(value.name());
    }

    @Nullable
    public static <E extends Enum<E>> E readEnum(Tag tag, Class<E> clazz) {
        if (tag instanceof StringTag stringTag) {
            try {
                //? if >=1.21.10 {
                return Enum.valueOf(clazz, stringTag.value());
                //?} else {
                /*return Enum.valueOf(clazz, stringTag.getAsString());*/
                //?}
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    // CompoundTag list serialization

    public static ListTag writeCompoundList(Stream<CompoundTag> stream) {
        ListTag list = new ListTag();
        stream.forEach(list::add);
        return list;
    }

    public static Stream<CompoundTag> readCompoundList(Tag tag) {
        if (tag instanceof ListTag listTag) {
            return IntStream.range(0, listTag.size())
                    .mapToObj(i -> {
                        Tag element = listTag.get(i);
                        return element instanceof CompoundTag ct ? ct : new CompoundTag();
                    });
        }
        return Stream.empty();
    }

    // Vec3 serialization

    public static ListTag writeVec3(Vec3 vec) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(vec.x));
        list.add(DoubleTag.valueOf(vec.y));
        list.add(DoubleTag.valueOf(vec.z));
        return list;
    }

    @Nullable
    public static Vec3 readVec3(@Nullable Tag tag) {
        if (tag instanceof ListTag listTag && listTag.size() >= 3) {
            return new Vec3(
                getDouble(listTag, 0, 0.0),
                getDouble(listTag, 1, 0.0),
                getDouble(listTag, 2, 0.0)
            );
        }
        return null;
    }

    // String list serialization

    public static ListTag writeStringList(Stream<String> stream) {
        ListTag list = new ListTag();
        stream.map(StringTag::valueOf).forEach(list::add);
        return list;
    }

    public static Stream<String> readStringList(@Nullable Tag tag) {
        if (tag instanceof ListTag listTag) {
            return IntStream.range(0, listTag.size())
                    .mapToObj(i -> {
                        Tag element = listTag.get(i);
                        //? if >=1.21.10 {
                        return element instanceof StringTag st ? st.value() : "";
                        //?} else {
                        /*return element instanceof StringTag st ? st.getAsString() : "";*/
                        //?}
                    });
        }
        return Stream.empty();
    }

    // ItemStack serialization: there is deliberately NO id+count helper here. One existed (itemStackToNBT /
    // itemStackFromNBT) and silently dropped every data component, which broke the Assembly Table JEI "+"
    // transfer for any component-carrying ingredient. For disk/NBT use ItemStack.CODEC through
    // registryAwareOps(); for the wire use ItemStack.OPTIONAL_STREAM_CODEC on a RegistryFriendlyByteBuf
    // (see BCContainerSupport.registryBuf, used by the ghost-slot drop and AssemblyTableTransfer).

    // ───────────────────────────────────────────────────────────────────────────
    // Version-neutral NBT accessors. On 1.21.5+ the vanilla CompoundTag/ListTag getX
    // accessors return Optional<X> (with getXOr / getXOrEmpty convenience variants); on
    // 1.21.1 they return the value (or a type default) directly and getList takes a
    // tag-type argument. Shared BuildCraft serialization routes through these so it reads
    // NBT identically on every node. The >=1.21.10 branch is exactly the call BuildCraft
    // already makes today, so runtime behaviour on the released nodes is unchanged.
    // ───────────────────────────────────────────────────────────────────────────

    public static byte getByte(CompoundTag nbt, String key, byte def) {
        //? if >=1.21.10 {
        return nbt.getByteOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getByte(key) : def;*/
        //?}
    }

    /** The {@link Direction} whose ordinal is {@code ordinal}, or {@code null} when it is not one. The byte is read
     *  unsigned: 7.1.x's {@code ForgeDirection.UNKNOWN} was 6, and a corrupt or hand-edited save holds anything, so
     *  indexing {@code Direction.values()} with it blind threw out of the load. */
    @Nullable
    public static Direction directionOrNull(byte ordinal) {
        int index = ordinal & 0xFF;
        Direction[] sides = Direction.values();
        return index < sides.length ? sides[index] : null;
    }

    /** Reads a {@link Direction} saved as its ordinal in a byte ({@code def} when the key is missing), or {@code null}
     *  when the byte is not one (see {@link #directionOrNull}). What {@code null} means is the caller's policy: the
     *  map location and direction parameter readers take it as "no direction", while the robot readers that share this
     *  check each decide for themselves (a saved order is dropped, a goto loads stationless, a station record is
     *  ignored). */
    @Nullable
    public static Direction getDirectionOrNull(CompoundTag nbt, String key, byte def) {
        return directionOrNull(getByte(nbt, key, def));
    }

    public static short getShort(CompoundTag nbt, String key, short def) {
        //? if >=1.21.10 {
        return nbt.getShortOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getShort(key) : def;*/
        //?}
    }

    public static int getInt(CompoundTag nbt, String key, int def) {
        //? if >=1.21.10 {
        return nbt.getIntOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getInt(key) : def;*/
        //?}
    }

    public static long getLong(CompoundTag nbt, String key, long def) {
        //? if >=1.21.10 {
        return nbt.getLongOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getLong(key) : def;*/
        //?}
    }

    public static float getFloat(CompoundTag nbt, String key, float def) {
        //? if >=1.21.10 {
        return nbt.getFloatOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getFloat(key) : def;*/
        //?}
    }

    public static double getDouble(CompoundTag nbt, String key, double def) {
        //? if >=1.21.10 {
        return nbt.getDoubleOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getDouble(key) : def;*/
        //?}
    }

    public static boolean getBoolean(CompoundTag nbt, String key, boolean def) {
        //? if >=1.21.10 {
        return nbt.getBooleanOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getBoolean(key) : def;*/
        //?}
    }

    public static String getString(CompoundTag nbt, String key, String def) {
        //? if >=1.21.10 {
        return nbt.getStringOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getString(key) : def;*/
        //?}
    }

    public static int[] getIntArray(CompoundTag nbt, String key, int[] def) {
        //? if >=1.21.10 {
        return nbt.getIntArray(key).orElse(def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getIntArray(key) : def;*/
        //?}
    }

    public static byte[] getByteArray(CompoundTag nbt, String key, byte[] def) {
        //? if >=1.21.10 {
        return nbt.getByteArray(key).orElse(def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getByteArray(key) : def;*/
        //?}
    }

    /** CompoundTag child, empty (new) when absent. */
    public static CompoundTag getCompound(CompoundTag nbt, String key) {
        //? if >=1.21.10 {
        return nbt.getCompoundOrEmpty(key);
        //?} else {
        /*return nbt.getCompound(key);*/
        //?}
    }

    /** CompoundTag child, null when absent (for callers that branch on null). */
    @Nullable
    public static CompoundTag getCompoundOrNull(CompoundTag nbt, String key) {
        //? if >=1.21.10 {
        return nbt.getCompound(key).orElse(null);
        //?} else {
        /*return nbt.contains(key, Tag.TAG_COMPOUND) ? nbt.getCompound(key) : null;*/
        //?}
    }

    /** ListTag child, empty (new) when absent. tagType is a {@code Tag.TAG_*} constant (ignored on modern nodes). */
    public static ListTag getList(CompoundTag nbt, String key, int tagType) {
        //? if >=1.21.10 {
        return nbt.getListOrEmpty(key);
        //?} else {
        /*return nbt.getList(key, tagType);*/
        //?}
    }

    /** ListTag child, null when absent. tagType is a {@code Tag.TAG_*} constant (ignored on modern nodes). */
    @Nullable
    public static ListTag getListOrNull(CompoundTag nbt, String key, int tagType) {
        //? if >=1.21.10 {
        return nbt.getList(key).orElse(null);
        //?} else {
        /*return nbt.contains(key, Tag.TAG_LIST) ? nbt.getList(key, tagType) : null;*/
        //?}
    }

    // ── ListTag indexed access ──

    /** CompoundTag element at index i, empty (new) when absent/wrong-type. */
    public static CompoundTag getCompound(ListTag list, int i) {
        //? if >=1.21.10 {
        return list.getCompoundOrEmpty(i);
        //?} else {
        /*return list.getCompound(i);*/
        //?}
    }

    /** CompoundTag element at index i, null when absent/wrong-type. */
    @Nullable
    public static CompoundTag getCompoundOrNull(ListTag list, int i) {
        //? if >=1.21.10 {
        return list.getCompound(i).orElse(null);
        //?} else {
        /*return i >= 0 && i < list.size() && list.get(i) instanceof CompoundTag ct ? ct : null;*/
        //?}
    }

    public static double getDouble(ListTag list, int i, double def) {
        //? if >=1.21.10 {
        return list.getDoubleOr(i, def);
        //?} else {
        /*return i >= 0 && i < list.size() ? list.getDouble(i) : def;*/
        //?}
    }
}
