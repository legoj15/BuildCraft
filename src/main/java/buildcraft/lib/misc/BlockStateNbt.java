/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Reads block states out of BuildCraft's own NBT (blueprints, snapshots, facades) whichever way the keys are
 * spelled.
 *
 * <p>Minecraft 26.3 renamed the keys {@code NbtUtils.writeBlockState} produces from {@code Name}/{@code Properties}
 * to {@code id}/{@code properties}, and its {@code readBlockState} accepts only the new spelling. Vanilla upgrades
 * its own saves through the DataFixer, which never sees these tags, so a plain read turns every block state saved
 * on 26.2 or earlier into air. Reading through here accepts both spellings, so saved data also moves back to an
 * older node.
 *
 * <p>NBT that is hashed (snapshots: the Electronic Library re-keys every upload and compares the hash with the key it
 * asked for) is written through {@link #write}, which uses the pre-26.3 spelling on every node, so a blueprint keeps
 * one hash across Minecraft lines. Other NBT may use {@link NbtUtils#writeBlockState} directly. */
public class BlockStateNbt {
    //? if >=26.3 {
    private static final String ID = "id";
    private static final String PROPERTIES = "properties";
    private static final String FOREIGN_ID = "Name";
    private static final String FOREIGN_PROPERTIES = "Properties";
    //?} else {
    /*private static final String ID = "Name";
    private static final String PROPERTIES = "Properties";
    private static final String FOREIGN_ID = "id";
    private static final String FOREIGN_PROPERTIES = "properties";
    *///?}

    /** The pre-26.3 key spelling for a block state on every node: {@code Name}/{@code Properties}. */
    private static final String LEGACY_ID = "Name";
    private static final String LEGACY_PROPERTIES = "Properties";

    /** {@code state} as NBT spelled the same on every node (the pre-26.3 {@code Name}/{@code Properties}), for NBT
     *  whose hash must not depend on the Minecraft line. {@link #read} accepts it everywhere. */
    public static CompoundTag write(BlockState state) {
        CompoundTag nbt = NbtUtils.writeBlockState(state);
        // Before 26.3 the native keys already are the legacy ones, and the move is a no-op.
        if (nbt.contains(ID)) {
            move(nbt, ID, LEGACY_ID);
        }
        if (nbt.contains(PROPERTIES)) {
            move(nbt, PROPERTIES, LEGACY_PROPERTIES);
        }
        return nbt;
    }

    public static BlockState read(CompoundTag nbt) {
        return NbtUtils.readBlockState(RegistryUtilBC.blockLookup(), withNativeKeys(nbt));
    }

    /** Whether {@code read}, what {@link #read} made of {@code nbt}, holds everything {@code nbt} names: the same
     *  block (a block this game lacks reads as air), and each of its properties that {@code nbt} sets at the value
     *  {@code nbt} sets (a value the block does not have reads as the default). A property {@code nbt} leaves out
     *  takes its default without losing anything; a property the block no longer has is not checked. */
    public static boolean readsBack(CompoundTag nbt, BlockState read) {
        CompoundTag nativeNbt = withNativeKeys(nbt);
        Identifier id = Identifier.tryParse(NBTUtilBC.getString(nativeNbt, ID, ""));
        if (id == null || !id.equals(BuiltInRegistries.BLOCK.getKey(read.getBlock()))) {
            return false;
        }
        CompoundTag properties = NBTUtilBC.getCompound(nativeNbt, PROPERTIES);
        for (Property<?> property : read.getProperties()) {
            String name = property.getName();
            if (properties.contains(name)
                    && !NBTUtilBC.getString(properties, name, "").equals(valueName(read, property))) {
                return false;
            }
        }
        return true;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** {@code nbt} itself when it already uses the running node's keys; otherwise a copy that does. */
    private static CompoundTag withNativeKeys(CompoundTag nbt) {
        boolean renameId = !nbt.contains(ID) && nbt.contains(FOREIGN_ID);
        boolean renameProperties = !nbt.contains(PROPERTIES) && nbt.contains(FOREIGN_PROPERTIES);
        if (!renameId && !renameProperties) {
            return nbt;
        }
        CompoundTag copy = nbt.copy();
        if (renameId) {
            move(copy, FOREIGN_ID, ID);
        }
        if (renameProperties) {
            move(copy, FOREIGN_PROPERTIES, PROPERTIES);
        }
        return copy;
    }

    private static void move(CompoundTag nbt, String from, String to) {
        Tag value = nbt.get(from);
        nbt.remove(from);
        nbt.put(to, value);
    }
}
