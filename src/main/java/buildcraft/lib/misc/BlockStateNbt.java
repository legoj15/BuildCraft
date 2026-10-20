/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

/** Reads block states out of BuildCraft's own NBT (blueprints, snapshots, facades) whichever way the keys are
 * spelled.
 *
 * <p>Minecraft 26.3 renamed the keys {@code NbtUtils.writeBlockState} produces from {@code Name}/{@code Properties}
 * to {@code id}/{@code properties}, and its {@code readBlockState} accepts only the new spelling. Vanilla upgrades
 * its own saves through the DataFixer, which never sees these tags, so a plain read turns every block state saved
 * on 26.2 or earlier into air. Reading through here accepts both spellings, so saved data also moves back to an
 * older node. Writing needs no helper: {@link NbtUtils#writeBlockState} is always the running node's own spelling. */
public class BlockStateNbt {
    //? if >=26.3 {
    /*private static final String ID = "id";
    private static final String PROPERTIES = "properties";
    private static final String FOREIGN_ID = "Name";
    private static final String FOREIGN_PROPERTIES = "Properties";*/
    //?} else {
    private static final String ID = "Name";
    private static final String PROPERTIES = "Properties";
    private static final String FOREIGN_ID = "id";
    private static final String FOREIGN_PROPERTIES = "properties";
    //?}

    public static BlockState read(CompoundTag nbt) {
        return NbtUtils.readBlockState(RegistryUtilBC.blockLookup(), withNativeKeys(nbt));
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
