/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

/**
 * Which mouse buttons paint on the Zone Planner map. Pure (no Minecraft types) so the mapping can be unit-tested
 * without a screen: the GUI asks it what a click on the map with a coloured brush means.
 */
public final class ZonePaintInput {
    private ZonePaintInput() {}

    /** The button does not start a stroke. */
    public static final int NONE = -1;
    /** A stroke that sets zone cells (the left mouse button). */
    public static final int PAINT = 0;
    /** A stroke that clears zone cells (the right mouse button). */
    public static final int ERASE = 1;

    /** The stroke a click with {@code mouseButton} starts: {@link #PAINT} or {@link #ERASE}, else {@link #NONE}. */
    public static int strokeFor(int mouseButton) {
        return switch (mouseButton) {
            case 0 -> PAINT;
            case 1 -> ERASE;
            default -> NONE;
        };
    }
}
