/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.GameProfileUtil;

/**
 * Reads/writes a tile owner's {@link GameProfile} to the standard {@code ownerUUID}/{@code ownerName}
 * NBT keys. Extracted from the copies that {@link TileBC_Neptune}, the engine base, the flood gate and
 * the distiller each carried verbatim. The owner field + placement entry point live on
 * {@link AbstractBCBlockEntity}; this class owns only the (de)serialization.
 */
public final class OwnerData {

    private OwnerData() {}

    /** Writes {@code owner} under {@code ownerUUID}/{@code ownerName}. No-op when {@code owner} is null
     *  or carries no id — matching the previous per-tile guards, so the saved bytes are unchanged. */
    public static void writeOwner(BCValueOutput out, @Nullable GameProfile owner) {
        if (owner != null && GameProfileUtil.getId(owner) != null) {
            out.putString("ownerUUID", GameProfileUtil.getId(owner).toString());
            if (GameProfileUtil.getName(owner) != null) {
                out.putString("ownerName", GameProfileUtil.getName(owner));
            }
        }
    }

    /** @return the owner stored under {@code ownerUUID}/{@code ownerName}, or null when the key is
     *  absent or the stored UUID is unparseable. The name defaults to {@code "Unknown"} when the id is
     *  present but the name is not (matching the previous per-tile reads). */
    @Nullable
    public static GameProfile readOwner(BCValueInput in) {
        String uuidStr = in.getStringOr("ownerUUID", "");
        if (uuidStr.isEmpty()) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(uuidStr);
            String name = in.getStringOr("ownerName", "Unknown");
            return new GameProfile(uuid, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
