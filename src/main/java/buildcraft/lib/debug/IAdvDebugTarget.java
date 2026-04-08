/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.debug;

/**
 * Something that can be put into an "advanced debug" state — every tick {@link #sendDebugState()} will be called on
 * the server, allowing the client to render details normally hidden on the server.
 */
public interface IAdvDebugTarget {
    /** Called when the current debug target changes from this to something else (or to nothing). */
    void disableDebugging();

    /**
     * Called every tick on the server to see if this still exists in the world.
     * If this returns false then {@link #disableDebugging()} will be called and the current debug target will be
     * removed.
     */
    boolean doesExistInWorld();

    /** Called every tick on the server to send all the debug information to the client. */
    void sendDebugState();
}
