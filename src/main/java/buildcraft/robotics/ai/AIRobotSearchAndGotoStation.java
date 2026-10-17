/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import java.util.HashSet;
import java.util.Set;

import buildcraft.api.core.IZone;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.IStationFilter;
import buildcraft.robotics.StationIndex;

/** Composes {@link AIRobotSearchStation} with {@link AIRobotGotoStation}: find a matching station, fly to it,
 *  and report success only if both halves succeeded.
 *
 *  <p>With {@code tryEveryStation} (not 7.1.x, which tried the nearest match only) a flight that fails moves on to
 *  the next-nearest matching station, until one flight succeeds or none is left. That is what recharging needs: a
 *  goto is capped (the A* weight prune and its expansion budget keep a path within ~96 blocks), so a robot further
 *  than that from the nearest powered station would otherwise fail its recharge and shut down where it stood, with
 *  another powered station in reach. The stations tried are this AI's alone and not saved. */
public class AIRobotSearchAndGotoStation extends AIRobot {

    private IStationFilter filter;
    private IZone zone;
    private boolean tryEveryStation;
    private final Set<StationIndex> tried = new HashSet<>();
    private DockingStation target;

    public AIRobotSearchAndGotoStation(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotSearchAndGotoStation(IRobotAccess iRobot, IStationFilter iFilter, IZone iZone) {
        this(iRobot, iFilter, iZone, false);
    }

    /** @param tryEveryStation on a failed flight, try the next-nearest matching station instead of giving up */
    public AIRobotSearchAndGotoStation(IRobotAccess iRobot, IStationFilter iFilter, IZone iZone,
            boolean tryEveryStation) {
        this(iRobot);

        filter = iFilter;
        zone = iZone;
        this.tryEveryStation = tryEveryStation;
    }

    /** Leaves {@code station} out of the search, as if a flight to it had already failed. Call before starting. */
    void exclude(DockingStation station) {
        StationIndex key = keyOf(station);
        if (key != null) {
            tried.add(key);
        }
    }

    /** The station's registry key, or null for one with no position or side (which no flight can reach anyway, so
     *  a failure there ends the search rather than picking the same station again). */
    private static StationIndex keyOf(DockingStation station) {
        if (station == null || station.index() == null || station.side() == null) {
            return null;
        }
        return new StationIndex(station);
    }

    @Override
    public void start() {
        search();
    }

    private void search() {
        IStationFilter untried = station -> !tried.contains(keyOf(station)) && filter.matches(station);
        startDelegateAI(new AIRobotSearchStation(robot, tried.isEmpty() ? filter : untried, zone));
    }

    /** The flight to a found station; package-private so a test can stand in for it. */
    AIRobotGotoStation gotoStation(DockingStation station) {
        return new AIRobotGotoStation(robot, station);
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotSearchStation) {
            if (ai.success()) {
                target = ((AIRobotSearchStation) ai).targetStation;
                startDelegateAI(gotoStation(target));
            } else {
                setSuccess(false);
                terminate();
            }
        } else if (ai instanceof AIRobotGotoStation) {
            StationIndex failed = !ai.success() && tryEveryStation ? keyOf(target) : null;
            if (failed != null) {
                // The search's next-nearest pick, now that this one is ruled out. Each station is tried once, so
                // this ends when the matching stations run out.
                tried.add(failed);
                target = null;
                search();
                return;
            }
            setSuccess(ai.success());
            terminate();
        }
    }
}
