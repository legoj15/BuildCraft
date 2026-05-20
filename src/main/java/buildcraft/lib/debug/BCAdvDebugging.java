/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.debug;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;

/**
 * Client-side holder for the current advanced-debug target. The whole overlay is client-authoritative:
 * everything the overlay needs is already client-synced or client-computable, so there is no packet
 * traffic — {@link buildcraft.lib.item.ItemDebugger} records the clicked position here on the client
 * and {@link AdvDebugRenderer} reads it back during world rendering.
 */
public enum BCAdvDebugging {
    INSTANCE;

    @Nullable
    private BlockPos clientTarget = null;

    /** Records (client-side) the position of the tile the player just debugged with the Debugger. */
    public void setClientTarget(BlockPos pos) {
        clientTarget = pos == null ? null : pos.immutable();
    }

    /** The position of the current debug target, or {@code null} if nothing is being debugged. */
    @Nullable
    public BlockPos getClientTarget() {
        return clientTarget;
    }

    /** Clears the current debug target (e.g. once the tile no longer exists). */
    public void clear() {
        clientTarget = null;
    }
}
