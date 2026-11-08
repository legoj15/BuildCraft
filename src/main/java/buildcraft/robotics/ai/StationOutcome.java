/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import buildcraft.api.robots.AIRobot;
import buildcraft.robotics.entity.RobotSleepReason;

/**
 * What a failed station trip can say about why it failed: whether no station for the job exists at all, or one does
 * but could not be used now (claimed by another robot, forbidding this one, in an unloaded chunk, past the flight's
 * reach, or emptied or filled between the search and the arrival). The search-and-goto, the load and unload trips
 * built on it and the tool fetch all answer it, so a board that sleeps on their failure can tell "no tool" from
 * "the tool's station is busy" ({@link #sleepReason}).
 */
public interface StationOutcome {

    /** After a failure: true when a station for this trip exists but could not be used now. Meaningless after a
     *  success. */
    boolean stationUnavailable();

    /** Whether {@code ai} is a trip that failed on a station that exists but could not be used now. */
    static boolean unavailable(AIRobot ai) {
        return ai instanceof StationOutcome outcome && outcome.stationUnavailable();
    }

    /** The reason to sleep after the station trip {@code failed} failed: {@code ifNoStation} when no station for it
     *  exists, {@link RobotSleepReason#STATION_UNAVAILABLE} when one exists but could not be used now. */
    static RobotSleepReason sleepReason(AIRobot failed, RobotSleepReason ifNoStation) {
        return unavailable(failed) ? RobotSleepReason.STATION_UNAVAILABLE : ifNoStation;
    }
}
