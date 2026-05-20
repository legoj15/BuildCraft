/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.debug;

import net.minecraft.network.chat.Component;

/**
 * A block entity that can be inspected with the Debugger item. Right-clicking such a tile with the
 * Debugger records it (client-side) as the current advanced-debug target and renders an in-world
 * overlay of its internal state; the server side shows the player {@link #getAdvDebugMessage()} in
 * the action bar so they know what is being highlighted.
 */
public interface IAdvDebugTarget {
    /** The action-bar popup explaining what the debug overlay is showing for this tile. */
    Component getAdvDebugMessage();
}
