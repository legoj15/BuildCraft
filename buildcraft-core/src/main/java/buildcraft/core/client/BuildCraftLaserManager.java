/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.core.client;

import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;

/**
 * Defines the visual style (color, width) for different laser types used
 * by the marker system.
 * <p>
 * In 1.12 these were textured quad definitions using SpriteHolder UVs.
 * In the current 1.21 port they are simplified to colored lines.
 */
public class BuildCraftLaserManager {
    // Volume marker lasers (blue)
    /** Thick red lines for connected volume markers */
    public static final LaserType MARKER_VOLUME_CONNECTED = new LaserType(0.8f, 0.2f, 0.2f, 1.0f, 3.0f);
    /** Thin blue lines for possible volume connections */
    public static final LaserType MARKER_VOLUME_POSSIBLE = new LaserType(0.3f, 0.3f, 1.0f, 0.8f, 1.5f);
    /** Blue signal lines for volume marker range display */
    public static final LaserType MARKER_VOLUME_SIGNAL = new LaserType(0.2f, 0.2f, 0.9f, 0.6f, 1.0f);

    // Path marker lasers (green)
    /** Thick red lines for connected path markers */
    public static final LaserType MARKER_PATH_CONNECTED = new LaserType(0.8f, 0.2f, 0.2f, 1.0f, 3.0f);
    /** Thin green lines for possible path connections */
    public static final LaserType MARKER_PATH_POSSIBLE = new LaserType(0.3f, 1.0f, 0.3f, 0.8f, 1.5f);

    // Default possible connection
    public static final LaserType MARKER_DEFAULT_POSSIBLE = new LaserType(0.8f, 0.8f, 0.8f, 0.6f, 1.0f);

    // Power lasers (for engines, etc.)
    public static final LaserType POWER_LOW = new LaserType(1.0f, 0.2f, 0.2f, 0.8f, 2.0f);
    public static final LaserType POWER_MED = new LaserType(1.0f, 1.0f, 0.2f, 0.8f, 2.0f);
    public static final LaserType POWER_HIGH = new LaserType(0.2f, 1.0f, 0.2f, 0.8f, 2.0f);
    public static final LaserType POWER_FULL = new LaserType(0.2f, 0.2f, 1.0f, 0.8f, 2.0f);
    public static final LaserType[] POWERS = {POWER_LOW, POWER_MED, POWER_HIGH, POWER_FULL};

    // Stripes
    public static final LaserType STRIPES_READ = new LaserType(0.9f, 0.9f, 0.2f, 0.8f, 2.0f);
    public static final LaserType STRIPES_WRITE = new LaserType(0.2f, 0.9f, 0.9f, 0.8f, 2.0f);
    public static final LaserType STRIPES_WRITE_DIRECTION = new LaserType(0.9f, 0.2f, 0.9f, 0.8f, 2.0f);
}
