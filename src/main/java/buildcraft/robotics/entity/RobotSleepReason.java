/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.util.Locale;

import javax.annotation.Nullable;

/**
 * Why a robot is going home to sleep, named by whichever board decided it. {@code AIRobotGotoSleep} takes one as a
 * required constructor argument, so a new sleep site cannot be written without saying why; the Robot Goggles label turns
 * it into "Returning home: no tool" on the way and "Sleeping: no tool" once there. Synced as its ordinal (append, never
 * reorder); the text lives in the lang file, one short phrase per value ({@link #translationKey()}) that the activity's
 * own format key ({@code <activity key>.reason}) places, so a translation can reorder the words.
 */
public enum RobotSleepReason {
    /** The tool, weapon or equipment the board works with is in no station. */
    NO_TOOL,
    /** The planter has no seeds to take from any station. */
    NO_SEEDS,
    /** The stripes board has no item it can use. */
    NO_ITEM,
    /** The search found nothing to do: no block, entity, item, order or builder that needs it. A search that found
     *  only blocks that refused this robot says {@link #PROTECTED} or {@link #WORK_FAILING} instead. */
    NO_WORK,
    /** No station holds what the board loads. One that does but cannot be used now is {@link #STATION_UNAVAILABLE}. */
    NOTHING_TO_LOAD,
    /** No station accepts what the robot carries. */
    NOWHERE_TO_UNLOAD,
    /** A protection mod refused the work, over and over, or every block the search could have worked had refused this
     *  robot lately. */
    PROTECTED,
    /** The work is there but cannot be reached for now; the board looks again after the sleep. */
    RETRYING,
    /** The machine the cargo was for has no room for it. */
    TARGET_FULL,
    /** The delivery's requester is in an unloaded chunk; the robot keeps the order and waits for it. */
    AWAITING_REQUESTER,
    /** The battery needs a charge and no powered station is free. */
    NO_CHARGER,
    /** The robot carries the empty board. */
    NO_PROGRAM,
    /** Several attempts in a row failed for any cause (a protection refusal, a block that changed under the robot,
     *  one it cannot break): the boards whose give-up counts every failure cannot say which. Also a search that found
     *  only blocks it had failed on lately. */
    WORK_FAILING,
    /** A station that has the tool, the cargo or room for it exists, but could not be used now: another robot holds
     *  it, it forbids this robot, its chunk is not loaded, it is past the flight's reach (of the robot, or of the
     *  machine a delivery is for), or it was emptied or filled between the search and the arrival. Distinct from
     *  {@link #NO_TOOL}, {@link #NOTHING_TO_LOAD} and the like, which mean no such station exists at all. */
    STATION_UNAVAILABLE;

    private static final RobotSleepReason[] VALUES = values();

    private final String translationKey = "buildcraft.robot.sleep_reason." + name().toLowerCase(Locale.ROOT);

    public String translationKey() {
        return translationKey;
    }

    /** The value synced as {@code id}: null for {@link RobotStatus#NO_SLEEP_REASON} and for an id out of range. */
    @Nullable
    public static RobotSleepReason byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
