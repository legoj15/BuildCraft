/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.nbt.CompoundTag;

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * Reads/writes a tile owner's {@link GameProfile} to the standard {@code ownerUUID}/{@code ownerName}
 * NBT keys. Extracted from the copies that {@link TileBC_Neptune}, the engine base, the flood gate and
 * the distiller each carried verbatim. The owner field + placement entry point live on
 * {@link AbstractBCBlockEntity}; this class owns only the (de)serialization. The {@link CompoundTag} overloads are
 * the same keys and rules for records that are not block-entity or entity data (a robot docking station's).
 */
public final class OwnerData {

    private OwnerData() {}

    /** Writes {@code owner} under {@code ownerUUID}/{@code ownerName}. No-op when {@code owner} is null
     *  or carries no id — matching the previous per-tile guards, so the saved bytes are unchanged. */
    public static void writeOwner(BCValueOutput out, @Nullable GameProfile owner) {
        write(out::putString, owner);
    }

    /** @return the owner stored under {@code ownerUUID}/{@code ownerName}, or null when the key is
     *  absent or the stored UUID is unparseable. The name is the empty string when the id is present
     *  but the name is not: authlib rejects a null name, and the English placeholder this used to
     *  substitute was persisted into the profile and shown untranslated. Display code turns the blank
     *  into localised text via {@link buildcraft.lib.misc.GameProfileUtil#getDisplayName}. */
    @Nullable
    public static GameProfile readOwner(BCValueInput in) {
        return profileOf(in.getStringOr("ownerUUID", ""), in.getStringOr("ownerName", ""));
    }

    /** {@link #writeOwner(BCValueOutput, GameProfile)} into a plain tag. */
    public static void writeOwner(CompoundTag out, @Nullable GameProfile owner) {
        write(out::putString, owner);
    }

    /** {@link #readOwner(BCValueInput)} from a plain tag. */
    @Nullable
    public static GameProfile readOwner(CompoundTag in) {
        return profileOf(NBTUtilBC.getString(in, "ownerUUID", ""), NBTUtilBC.getString(in, "ownerName", ""));
    }

    private static void write(BiConsumer<String, String> put, @Nullable GameProfile owner) {
        if (owner != null && GameProfileUtil.getId(owner) != null) {
            put.accept("ownerUUID", GameProfileUtil.getId(owner).toString());
            if (GameProfileUtil.getName(owner) != null) {
                put.accept("ownerName", GameProfileUtil.getName(owner));
            }
        }
    }

    @Nullable
    private static GameProfile profileOf(String uuidStr, String name) {
        if (uuidStr.isEmpty()) {
            return null;
        }
        try {
            return new GameProfile(UUID.fromString(uuidStr), name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
