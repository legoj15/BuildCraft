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
 * it into "Sleeping: no tool" and the like. Synced as its ordinal (append, never reorder); the text lives in the lang
 * file, one full sentence per value ({@link #translationKey()}), so a translation can reorder the words.
 */
public enum RobotSleepReason {
    /** The tool, weapon or equipment the board works with is in no station. */
    NO_TOOL,
    /** The planter has no seeds to take from any station. */
    NO_SEEDS,
    /** The stripes board has no item it can use. */
    NO_ITEM,
    /** The search found nothing to do: no block, entity, item, order or builder that needs it. */
    NO_WORK,
    /** No station holds what the board loads. */
    NOTHING_TO_LOAD,
    /** No station accepts what the robot carries. */
    NOWHERE_TO_UNLOAD,
    /** A protection mod refused the work, over and over. */
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
    NO_PROGRAM;

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
