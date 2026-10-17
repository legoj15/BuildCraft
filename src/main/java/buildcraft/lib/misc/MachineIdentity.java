/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

/**
 * Who a machine is when it asks protection mods for leave or acts in the world: its real, named owner, or else the
 * generic {@link FakePlayerUtil#BUILDCRAFT_PROFILE "[BuildCraft]"} identity. The one rule every caller resolves an
 * owner through - the break probe ({@link BlockUtil#canMachineBreak}), the leased fake players
 * ({@link FakePlayerUtil#resolveProfile}) and the robots ({@code RobotProtection}) - so the player a claim mod is
 * asked about and the player that then breaks, places or uses an item are always the same one. They used to apply
 * three different rules.
 *
 * <p>"Named" matters: an owner restored from a save that stored only its id comes back with an EMPTY name (authlib
 * rejects a null one, see {@code OwnerData}), and a fake player with a blank name is nobody to a claim mod's
 * trust list or log. Such a machine acts as "[BuildCraft]", like an unowned one.
 */
public final class MachineIdentity {
    private MachineIdentity() {}

    /** {@code owner} when it is a real profile with an id and a non-blank name, else {@code [BuildCraft]}. */
    public static GameProfile of(@Nullable GameProfile owner) {
        return isNamedOwner(owner) ? owner : FakePlayerUtil.BUILDCRAFT_PROFILE;
    }

    /** Whether {@code owner} is a real profile a machine can act as: it has an id and a non-blank name. */
    public static boolean isNamedOwner(@Nullable GameProfile owner) {
        return owner != null && GameProfileUtil.getId(owner) != null && GameProfileUtil.getDisplayName(owner) != null;
    }
}
