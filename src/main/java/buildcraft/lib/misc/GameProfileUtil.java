/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

/** Version-neutral accessors for {@link GameProfile} id/name.
 *
 * <p>On 1.21.10+ (authlib 7.x) {@code GameProfile} is a record exposing {@code id()} / {@code name()}.
 * On 1.21.1 (authlib 6.x) it is the classic bean exposing {@code getId()} / {@code getName()}. The two
 * accessor families are mutually exclusive, and {@code .id()} / {@code .name()} are far too overloaded for a
 * safe build-time text replacement — so this helper hides the split and call sites stay version-neutral. */
public class GameProfileUtil {
    /** @return the profile's UUID, or {@code null} if it has none. */
    public static UUID getId(GameProfile profile) {
        //? if >=1.21.10 {
        return profile.id();
        //?} else {
        /*return profile.getId();*/
        //?}
    }

    /** The name to show a player for {@code profile}, or {@code null} when there is nothing to show — no profile, or
     *  one without a usable name. authlib rejects a null name, so a nameless profile is spelled {@code ""}: tiles
     *  restore a stored owner whose name was never saved as exactly that (see {@code OwnerData}). Callers substitute
     *  their own localised "unknown" text for the null; the English word never lives in the profile itself. */
    @Nullable
    public static String getDisplayName(@Nullable GameProfile profile) {
        if (profile == null) {
            return null;
        }
        String name = getName(profile);
        return name == null || name.isBlank() ? null : name;
    }

    /** @return the profile's name. authlib rejects a null name, so this is never null for a real profile, but it
     *  may be empty (an owner restored from a save that stored an id without a name) — see {@link #getDisplayName}
     *  for the show-this-to-a-player form. */
    public static String getName(GameProfile profile) {
        //? if >=1.21.10 {
        return profile.name();
        //?} else {
        /*return profile.getName();*/
        //?}
    }
}
