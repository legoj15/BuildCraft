/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.util.Locale;

/**
 * What a robot is doing right now, in the words the Robot Goggles label shows. Derived on the server from the robot's
 * AI chain every tick ({@link RobotStatus#derive}) and synced as its ordinal, so the order is a wire format within one
 * version: append, never reorder. Common code only - the label text lives in the lang file, one key per value
 * ({@link #translationKey()}), and is resolved on the client.
 *
 * <p>{@link #SLEEPING} and {@link #RETURNING_HOME} carry a second answer: why. That is a {@link RobotSleepReason}, shown
 * through the activity's {@code .reason} format key ("Sleeping: no tool") when the robot knows it.
 */
public enum RobotActivity {
    /** No AI at all, between two cycles of one, or a board between two passes (nothing under it). */
    IDLE,
    /** Fallback for a chain no case below names. */
    WORKING,
    CHARGING,
    /** Shut down with an empty battery. */
    OUT_OF_POWER,
    /** Shut down with charge left: orphaned from, or mis-assigned to, its docking station. */
    SHUT_DOWN,
    /** Holding in place because its home is unloaded or forbids it. */
    WAITING_FOR_HOME,
    /** Running a redirect a gate issued (Goto Station). */
    FOLLOWING_GATE,
    /** Asleep; the reason says why. The flight home before it is {@link #RETURNING_HOME}. */
    SLEEPING,
    /** Fetching what the board works with: a tool, a weapon, seeds or an item to use. */
    FETCHING_GEAR,
    SEARCHING,
    TRAVELLING,
    LOADING,
    UNLOADING,
    BREAKING,
    HARVESTING,
    PLANTING,
    PUMPING,
    ATTACKING,
    DELIVERING,
    /** Getting rid of cargo no board job wants. */
    DISPOSING,
    PICKING_UP,
    USING_TOOL,
    USING_ITEM,
    /** Flying home (or to the nearest powered station it can reach) to sleep there; the reason says why. The robot is
     *  still awake, as its synced sleeping flag says, so the label does not yet say "Sleeping". */
    RETURNING_HOME;

    private static final RobotActivity[] VALUES = values();

    private final String translationKey = "buildcraft.robot.activity." + name().toLowerCase(Locale.ROOT);

    public String translationKey() {
        return translationKey;
    }

    /** The value synced as {@code id}; an out-of-range id (a client talking to a newer server) reads as {@link #IDLE}. */
    public static RobotActivity byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
    }
}
