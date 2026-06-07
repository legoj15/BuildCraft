/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.UUID;

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

    /** @return the profile's name, or {@code null} if it has none. */
    public static String getName(GameProfile profile) {
        //? if >=1.21.10 {
        return profile.name();
        //?} else {
        /*return profile.getName();*/
        //?}
    }
}
