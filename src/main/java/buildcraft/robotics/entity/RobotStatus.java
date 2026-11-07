/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.robots.AIRobot;

import buildcraft.robotics.ai.AIRobotAttack;
import buildcraft.robotics.ai.AIRobotBreak;
import buildcraft.robotics.ai.AIRobotDeliverRequested;
import buildcraft.robotics.ai.AIRobotDisposeItems;
import buildcraft.robotics.ai.AIRobotFetchAndEquipItemStack;
import buildcraft.robotics.ai.AIRobotFetchItem;
import buildcraft.robotics.ai.AIRobotGoto;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStation;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoad;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoadFluids;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnload;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnloadFluids;
import buildcraft.robotics.ai.AIRobotGotoStationToLoad;
import buildcraft.robotics.ai.AIRobotGotoStationToLoadFluids;
import buildcraft.robotics.ai.AIRobotGotoStationToUnload;
import buildcraft.robotics.ai.AIRobotGotoStationToUnloadFluids;
import buildcraft.robotics.ai.AIRobotHarvest;
import buildcraft.robotics.ai.AIRobotLoad;
import buildcraft.robotics.ai.AIRobotLoadFluids;
import buildcraft.robotics.ai.AIRobotMain;
import buildcraft.robotics.ai.AIRobotPlant;
import buildcraft.robotics.ai.AIRobotPumpBlock;
import buildcraft.robotics.ai.AIRobotRecharge;
import buildcraft.robotics.ai.AIRobotSearchAndGotoBlock;
import buildcraft.robotics.ai.AIRobotSearchAndGotoStation;
import buildcraft.robotics.ai.AIRobotSearchBlock;
import buildcraft.robotics.ai.AIRobotSearchEntity;
import buildcraft.robotics.ai.AIRobotSearchRandomGroundBlock;
import buildcraft.robotics.ai.AIRobotSearchStackRequest;
import buildcraft.robotics.ai.AIRobotSearchStation;
import buildcraft.robotics.ai.AIRobotShutdown;
import buildcraft.robotics.ai.AIRobotSleep;
import buildcraft.robotics.ai.AIRobotStripesHandler;
import buildcraft.robotics.ai.AIRobotUnload;
import buildcraft.robotics.ai.AIRobotUnloadFluids;
import buildcraft.robotics.ai.AIRobotUseToolOnBlock;
import buildcraft.robotics.ai.AIRobotWaitForHome;

/**
 * What a robot is doing and, when it is asleep, why: the answer the Robot Goggles label gives. The server works it out
 * from the robot's AI chain every tick ({@link #derive}) and syncs the two parts as small ids; the client turns them
 * back into text ({@link #label}). Common code, no client types.
 *
 * <p>The chain runs {@code AIRobotMain} - one delegate at a time - down to the AI doing the work, and what the delegate
 * under the controller is decides most of it: the energy rungs (shutdown, wait for home, recharge) and a gate's
 * redirect each take the whole robot over. Below the board, a sleep names the reason it was started with; otherwise
 * the outermost AI that names a whole job wins ({@code Load} also covers the flight to the station that has the cargo),
 * and with none, the innermost search or flight. Eviction by a Forbid Robot gate has no state of its own to show - the
 * controller undocks the robot and the board starts over - so the label simply moves on to whatever the robot does
 * next.
 */
public record RobotStatus(RobotActivity activity, @Nullable RobotSleepReason sleepReason) {

    /** The synced reason id for "none". */
    public static final int NO_SLEEP_REASON = -1;

    private static final RobotStatus IDLE = new RobotStatus(RobotActivity.IDLE, null);

    public RobotStatus {
        // A reason belongs to a sleep and nothing else; a stray one would only mislead the label.
        if (activity != RobotActivity.SLEEPING) {
            sleepReason = null;
        }
    }

    /** The reason as the synced id: its ordinal, or {@link #NO_SLEEP_REASON}. */
    public int sleepReasonId() {
        return sleepReason == null ? NO_SLEEP_REASON : sleepReason.ordinal();
    }

    /** Rebuilds a status from the two synced ids. */
    public static RobotStatus ofIds(int activityId, int sleepReasonId) {
        return new RobotStatus(RobotActivity.byId(activityId), RobotSleepReason.byId(sleepReasonId));
    }

    /** The label text: "Sleeping: no tool" for a sleep with a reason, the activity's own text otherwise. */
    public Component label() {
        return Component.translatable(sleepReason != null ? sleepReason.translationKey() : activity.translationKey());
    }

    /**
     * Works out the status of a robot whose controller is {@code mainAI}. Pure, so the whole mapping can be tested
     * without a world.
     *
     * @param mainAI the robot's top-level AI ({@code EntityRobot.mainAI}); null for a robot with no AI
     * @param powerDepleted whether the battery is at or under the shutdown line, which tells an empty robot from one
     *                      shut down for another reason
     */
    public static RobotStatus derive(@Nullable AIRobot mainAI, boolean powerDepleted) {
        AIRobot top = mainAI == null ? null : mainAI.getDelegateAI();
        if (top == null) {
            return IDLE;
        }

        // The controller's rungs take the whole robot over, so the AI under it settles all of these.
        if (top instanceof AIRobotShutdown) {
            return of(powerDepleted ? RobotActivity.OUT_OF_POWER : RobotActivity.SHUT_DOWN);
        }
        if (top instanceof AIRobotWaitForHome) {
            return of(RobotActivity.WAITING_FOR_HOME);
        }
        if (top instanceof AIRobotRecharge) {
            return of(RobotActivity.CHARGING);
        }
        if (mainAI instanceof AIRobotMain main && top == main.getOverridingAI()) {
            return of(RobotActivity.FOLLOWING_GATE);
        }

        // Below the board. A sleep anywhere in the chain wins: its reason is the answer, and the flight home it starts
        // with would otherwise read as "Travelling".
        for (AIRobot ai = top; ai != null; ai = ai.getDelegateAI()) {
            if (ai instanceof AIRobotGotoSleep goingToSleep) {
                return new RobotStatus(RobotActivity.SLEEPING, goingToSleep.getReason());
            }
            if (ai instanceof AIRobotSleep) {
                return of(RobotActivity.SLEEPING);
            }
        }

        // A board with nothing under it is between two passes (the Builder waits out a launch delay this way).
        if (top instanceof RedstoneBoardRobot && top.getDelegateAI() == null) {
            return IDLE;
        }

        // The outermost job wins; searches and flights only fill in when no job names the whole stretch, and the
        // innermost of those is the truest (the flight, not the search that started it).
        RobotActivity leg = null;
        for (AIRobot ai = top; ai != null; ai = ai.getDelegateAI()) {
            RobotActivity activity = activityOf(ai);
            if (activity == null) {
                continue;
            }
            if (activity != RobotActivity.SEARCHING && activity != RobotActivity.TRAVELLING) {
                return of(activity);
            }
            leg = activity;
        }
        return of(leg != null ? leg : RobotActivity.WORKING);
    }

    private static RobotStatus of(RobotActivity activity) {
        return new RobotStatus(activity, null);
    }

    /**
     * The one place an AI class becomes an activity. Null for the board itself and for AIs that only wrap others
     * without being a job of their own. A new AI that is not listed here reads as {@link RobotActivity#WORKING} until
     * it is.
     */
    @Nullable
    private static RobotActivity activityOf(AIRobot ai) {
        // Jobs. The station-and-load/unload family is listed whole: the outer AI wins, but a board may start any of the
        // layers directly. A recharge is the controller's own rung (answered above) and also one a board starts itself.
        if (ai instanceof AIRobotRecharge) {
            return RobotActivity.CHARGING;
        }
        if (ai instanceof AIRobotFetchAndEquipItemStack) {
            return RobotActivity.FETCHING_GEAR;
        }
        if (ai instanceof AIRobotGotoStationAndLoad || ai instanceof AIRobotGotoStationToLoad
                || ai instanceof AIRobotLoad || ai instanceof AIRobotGotoStationAndLoadFluids
                || ai instanceof AIRobotGotoStationToLoadFluids || ai instanceof AIRobotLoadFluids) {
            return RobotActivity.LOADING;
        }
        if (ai instanceof AIRobotGotoStationAndUnload || ai instanceof AIRobotGotoStationToUnload
                || ai instanceof AIRobotUnload || ai instanceof AIRobotGotoStationAndUnloadFluids
                || ai instanceof AIRobotGotoStationToUnloadFluids || ai instanceof AIRobotUnloadFluids) {
            return RobotActivity.UNLOADING;
        }
        if (ai instanceof AIRobotDisposeItems) {
            return RobotActivity.DISPOSING;
        }
        if (ai instanceof AIRobotDeliverRequested) {
            return RobotActivity.DELIVERING;
        }
        if (ai instanceof AIRobotBreak) {
            return RobotActivity.BREAKING;
        }
        if (ai instanceof AIRobotHarvest) {
            return RobotActivity.HARVESTING;
        }
        if (ai instanceof AIRobotPlant) {
            return RobotActivity.PLANTING;
        }
        if (ai instanceof AIRobotPumpBlock) {
            return RobotActivity.PUMPING;
        }
        if (ai instanceof AIRobotAttack) {
            return RobotActivity.ATTACKING;
        }
        if (ai instanceof AIRobotFetchItem) {
            return RobotActivity.PICKING_UP;
        }
        if (ai instanceof AIRobotUseToolOnBlock) {
            return RobotActivity.USING_TOOL;
        }
        if (ai instanceof AIRobotStripesHandler) {
            return RobotActivity.USING_ITEM;
        }

        // Legs: what the robot is doing when no job above names the stretch. The search-and-go wrappers say
        // "searching" so a tick between their two halves still reads sensibly; their flight overrides it.
        if (ai instanceof AIRobotSearchBlock || ai instanceof AIRobotSearchEntity
                || ai instanceof AIRobotSearchRandomGroundBlock || ai instanceof AIRobotSearchStackRequest
                || ai instanceof AIRobotSearchStation || ai instanceof AIRobotSearchAndGotoBlock
                || ai instanceof AIRobotSearchAndGotoStation) {
            return RobotActivity.SEARCHING;
        }
        if (ai instanceof AIRobotGoto || ai instanceof AIRobotGotoStation) {
            return RobotActivity.TRAVELLING;
        }
        return null;
    }
}
