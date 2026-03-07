/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

public class NBTUtilBC {

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
            nbt.getIntOr("X", 0),
            nbt.getIntOr("Y", 0),
            nbt.getIntOr("Z", 0)
        );
    }

    // UUID serialization

    public static void putUUID(CompoundTag nbt, String key, UUID uuid) {
        nbt.putLong(key + "Most", uuid.getMostSignificantBits());
        nbt.putLong(key + "Least", uuid.getLeastSignificantBits());
    }

    public static UUID getUUID(CompoundTag nbt, String key) {
        return new UUID(
            nbt.getLongOr(key + "Most", 0L),
            nbt.getLongOr(key + "Least", 0L)
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
                return Enum.valueOf(clazz, stringTag.value());
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
                listTag.getDoubleOr(0, 0.0),
                listTag.getDoubleOr(1, 0.0),
                listTag.getDoubleOr(2, 0.0)
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
                        return element instanceof StringTag st ? st.value() : "";
                    });
        }
        return Stream.empty();
    }
}
