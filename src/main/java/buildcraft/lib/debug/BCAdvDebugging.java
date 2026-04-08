/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.debug;

/**
 * Holds the current {@link IAdvDebugTarget}. In 1.12 this was ticked via a server-tick event; that hookup is not yet
 * wired in 1.21.11 but the core logic is ready for when tile entities start implementing IAdvDebugTarget.
 */
public enum BCAdvDebugging {
    INSTANCE;

    private IAdvDebugTarget target = null;

    public static boolean isBeingDebugged(IAdvDebugTarget target) {
        return INSTANCE.target == target;
    }

    public static void setCurrentDebugTarget(IAdvDebugTarget target) {
        if (INSTANCE.target != null) {
            INSTANCE.target.disableDebugging();
        }
        INSTANCE.target = target;
    }

    /** Called every server tick (not yet wired). */
    public void onServerPostTick() {
        if (target != null) {
            if (!target.doesExistInWorld()) {
                target.disableDebugging();
                target = null;
            } else {
                target.sendDebugState();
            }
        }
    }
}
