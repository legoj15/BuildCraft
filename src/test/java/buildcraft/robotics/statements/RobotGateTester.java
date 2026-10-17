/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.statements;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.StatementSlot;

import buildcraft.core.BCCoreItems;
import buildcraft.core.BCCoreStatements;

import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;
import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.BCRoboticsPlugs;
import buildcraft.robotics.BCRoboticsStatements;
import buildcraft.robotics.DockingStationPipe;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.ai.AIRobotSleep;
import buildcraft.robotics.boards.BoardRobotCarrierNBT;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Ph6 game tests for the robotics gate statements, driven against a LIVE pipe holder + {@link PluggableGate}
 * with a real robot: the sleep trigger + wakeup action preempting a sleeping picker (observable through the
 * picker fetching a dropped item the wake made it re-scan for), the forbid-robot action keeping a carrier's
 * station search off an otherwise accepting station (the D1 pin — the forbid is visible through the LIVE
 * station's {@code getActiveActions()} aggregation, which the JUnit predicate tests never reach), and the
 * goto-station action redirecting a docked robot to a second station via a SPOT map-location parameter.
 *
 * <p>The gate fixture follows {@code GateRedstoneSyncTester}; the robot fixtures follow
 * {@code PickerCarrierTester}: every relative position stays inside the 6x7 arena cell, the arena chunks are
 * force-loaded, and nothing asserts on a fixed tick — each phase is gated on observed state.
 */
public class RobotGateTester {

    /** Charge above {@code SAFETY_POWER} so the {@code AIRobotMain} ladder neither shuts the robot down nor
     *  sends it recharging mid-task. */
    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    /** The 1-slot, param-less gate (sleep test). */
    private static final GateVariant BASIC_GATE =
            new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER);
    /** The 1-slot gate with one trigger and one action parameter (forbid + goto tests). */
    private static final GateVariant PARAM_GATE =
            new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.QUARTZ);

    // ---------- fixtures ----------

    private static TilePipeHolder placePipe(GameTestHelper helper, BlockPos relPos, Item pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    /** A wooden-pipe supply station on {@code side}, returning the tile the station sits on. */
    private static TilePipeHolder installStation(GameTestHelper helper, BlockPos relPos, Direction side) {
        return installStation(helper, relPos, side, BCTransportItems.PIPE_WOOD_ITEM.get());
    }

    /** A station on {@code side} of a pipe of the given kind, returning the tile the station sits on. */
    private static TilePipeHolder installStation(GameTestHelper helper, BlockPos relPos, Direction side,
                                                 Item pipeItem) {
        TilePipeHolder tile = placePipe(helper, relPos, pipeItem);
        tile.replacePluggable(side, new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, side));
        return tile;
    }

    /** Whether any gate on the station's pipe currently holds {@code action} active. */
    private static boolean hasActiveAction(DockingStation station, IStatement action) {
        for (StatementSlot slot : station.getActiveActions()) {
            if (slot.statement == action) {
                return true;
            }
        }
        return false;
    }

    private static PluggableGate addGate(TilePipeHolder tile, Direction side, GateVariant variant) {
        PluggableGate gate = new PluggableGate(BCSiliconPlugs.gate, tile, side, variant);
        tile.replacePluggable(side, gate);
        return gate;
    }

    /** The registered station for a pipe face, or null while {@code RobotStationPluggable.onTick()} has not
     *  yet lazily registered it. */
    private static DockingStationPipe stationAt(GameTestHelper helper, BlockPos relPos, Direction side) {
        DockingStation station = RobotManager.registryProvider.getRegistry(helper.getLevel())
                .getStation(helper.absolutePos(relPos), side);
        return station instanceof DockingStationPipe pipe ? pipe : null;
    }

    private static EntityRobot addBoardRobot(GameTestHelper helper, BlockPos relPos, RedstoneBoardRobotNBT boardNBT) {
        EntityRobot robot = new EntityRobot(helper.getLevel(), boardNBT);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        helper.getLevel().addFreshEntity(robot);
        return robot;
    }

    /** A charged robot placed on {@code home} the way {@code ItemRobot.useOn} places one: the id comes from
     *  the registry before the entity reaches the world, the station is taken as MAIN (its linked station,
     *  whose gates name the robot's zones) and the robot docks there, so its board's first cycle already runs
     *  with its home and zones in place. */
    private static EntityRobot addHomedRobot(GameTestHelper helper, DockingStation home,
                                             RedstoneBoardRobotNBT boardNBT) {
        EntityRobot robot = new EntityRobot(helper.getLevel(), boardNBT);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        robot.setUniqueRobotId(RobotManager.registryProvider.getRegistry(helper.getLevel()).getNextRobotId());
        Vec3 pos = Vec3.atCenterOf(home.getPos().relative(home.side()));
        robot.setPos(pos.x, pos.y, pos.z);
        home.takeAsMain(robot);
        robot.dock(robot.getLinkedStation());
        helper.getLevel().addFreshEntity(robot);
        return robot;
    }

    /** An AREA map-location stack covering the relative box {@code minRel..maxRel}, written with the keys
     *  {@code ItemMapLocation.getAreaBox} reads and {@code mapType} = {@code AREA}. */
    private static ItemStack areaMap(GameTestHelper helper, BlockPos minRel, BlockPos maxRel) {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        BlockPos a = helper.absolutePos(minRel);
        BlockPos b = helper.absolutePos(maxRel);
        CompoundTag tag = new CompoundTag();
        tag.putInt("xMin", Math.min(a.getX(), b.getX()));
        tag.putInt("yMin", Math.min(a.getY(), b.getY()));
        tag.putInt("zMin", Math.min(a.getZ(), b.getZ()));
        tag.putInt("xMax", Math.max(a.getX(), b.getX()));
        tag.putInt("yMax", Math.max(a.getY(), b.getY()));
        tag.putInt("zMax", Math.max(a.getZ(), b.getZ()));
        tag.putString("mapType", "AREA");
        map.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return map;
    }

    /** A SPOT map-location stack pointing at {@code relPos} with the {@code side} byte, written the same way
     *  {@code ItemMapLocation.useOn} stamps a spot — the {@code x}/{@code y}/{@code z} ints, a {@code side}
     *  byte and {@code mapType} = {@code SPOT} on the CUSTOM_DATA component. */
    private static ItemStack spotMap(GameTestHelper helper, BlockPos relPos, Direction side) {
        ItemStack map = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        BlockPos abs = helper.absolutePos(relPos);
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", abs.getX());
        tag.putInt("y", abs.getY());
        tag.putInt("z", abs.getZ());
        tag.putByte("side", (byte) side.ordinal());
        tag.putString("mapType", "SPOT");
        map.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return map;
    }

    private static void dropItem(GameTestHelper helper, BlockPos relPos, ItemStack stack) {
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        ItemEntity item = new ItemEntity(helper.getLevel(), pos.x, pos.y, pos.z, stack);
        item.setNoGravity(true);
        helper.getLevel().addFreshEntity(item);
    }

    // ---------- sleep trigger + wakeup action ----------

    /** A picker docked at a station with the sleep trigger + wakeup action gate: the trigger fires while it
     *  sleeps, and the wakeup preempts the sleep so the picker re-scans and fetches an item (the diamond dropped
     *  after it was asleep, or a nearer neighbour-cell item — either proves the wake). The wake is observable ONLY through the fetch: a preempted sleep is re-entered within
     *  the same tick (the board's GotoSleep fallback), so {@code isSleeping()} polls would never see it. */
    public static void sleepTriggerAndWakeupPreemptsPicker(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(1, 3, 2);
        BlockPos robotRel = new BlockPos(3, 4, 2);
        BlockPos itemRel = new BlockPos(3, 4, 2);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        TilePipeHolder tile = installStation(helper, pipeRel, Direction.UP);

        EntityRobot[] robot = { null };
        boolean[] docked = { false };
        int[] phase = { 0 }; // 0: station registered, 1: dock + sleep, 2: gate + drop, then fetch
        // ONE flat tickUntil scheduled from the test body. Nested tickUntils are forbidden: runAfterDelay
        // inserts into GameTestInfo's runAtTickTimeMap immediately, and inserting from inside a scheduled
        // runnable can rehash the map mid-iteration (GameTestInfo.tickInternal iterates it) and NPE the
        // iterator — the framework javadoc itself warns against scheduling from inside a runnable. Each
        // phase transition happens on the tick its guard first passes, and returns false so `then` never
        // fires before the side effects land.
        EntityArenaUtil.tickUntil(helper, 400,
                () -> {
                    if (phase[0] == 0) {
                        if (stationAt(helper, pipeRel, Direction.UP) == null) {
                            return false;
                        }
                        phase[0] = 1;
                        robot[0] = addBoardRobot(helper, robotRel, BoardRobotPickerNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                        return false;
                    }
                    if (phase[0] == 1) {
                        if (!docked[0]) {
                            // Hand-dock and force the sleep: deterministic, and the override keeps the board
                            // from scanning before the gate exists.
                            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                            station.takeAsMain(robot[0]);
                            robot[0].dock(station);
                            robot[0].overrideAI(new AIRobotSleep(robot[0]));
                            docked[0] = true;
                            return false;
                        }
                        if (!robot[0].isSleeping()) {
                            return false;
                        }
                        // Asleep: resolve the gate and drop the diamond only NOW, so the sleeping robot
                        // never saw it before the wake.
                        phase[0] = 2;
                        DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                        helper.assertTrue(robot[0].getDockingStation() == station,
                                "the picker must be docked at the station before the gate resolves");
                        PluggableGate gate = addGate(tile, Direction.WEST, BASIC_GATE);
                        gate.logic.statements[0].trigger.set(
                                TriggerWrapper.wrap(BCRoboticsStatements.TRIGGER_ROBOT_SLEEP, null));
                        gate.logic.statements[0].action.set(
                                ActionWrapper.wrap(BCRoboticsStatements.ACTION_ROBOT_WAKE_UP, null));
                        // Resolve synchronously, AFTER the robot is asleep — no wake/awake race.
                        gate.logic.resolveActions();
                        helper.assertTrue(gate.logic.triggerOn[0],
                                "the sleep trigger must fire while a robot sleeps at the station");
                        dropItem(helper, itemRel, new ItemStack(Items.DIAMOND));
                        return false;
                    }
                    return robot[0].containsItems();
                },
                () -> {
                    // Holding ANYTHING is the proof: the robot was asleep (override) before the gate and the
                    // drop, and a sleeper never fetches. Do NOT pin the diamond itself — game tests run side
                    // by side and the picker's search roams past this cell, so a neighbour's dropped item can
                    // be nearer and legitimately fetched first (seen 2026-09-26 on 26.1.2). The diamond only
                    // guarantees there is something to fetch; a missed wakeup still times out above.
                    robot[0].discard();
                    helper.succeed();
                },
                "the picker never fell asleep at the station (or the wakeup never made it fetch)");
    }

    // ---------- forbid robot (the D1 pin) ----------

    /** A carrier with cargo, homed in the same cell as a station it would otherwise unload into, where that
     *  station's gate carries the forbid-robot action naming the carrier's board: the D1 pin. 7.1.x's
     *  {@code AIRobotSearchStation} SKIPS forbidden stations, so the carrier never flies to it — it finds no
     *  unload target, goes back to sleep at home and keeps its cargo.
     *
     *  <p>Two things make this an honest pin rather than a vacuous one:
     *  <ul>
     *  <li><b>The forbidden station is otherwise a valid target.</b> A second gate on its pipe holds Accept
     *      Items. Without it the station refuses every unload anyway ({@code canRobotAcceptItem} needs an
     *      accept action since D1), so the search would skip it whether or not the forbid worked. The old
     *      version of this test also asserted that {@code AIRobotUnload.unload} refused at the forbidden
     *      station, which was that same missing action talking: unload never consults the forbid (7.1.x
     *      neither), only the search does.</li>
     *  <li><b>The search is pinned to this cell.</b> The carrier's home station carries a Load/Unload Area
     *      action whose AREA map is this arena cell, so the search cannot reach other tests' stations. The
     *      station search covers the whole dimension; without the zone the carrier flew off to a free,
     *      accepting station in some other arena (more of them free up since stations are released by robot
     *      id), never settled, and timed out on 26.2 and 1.21.11.</li>
     *  </ul>
     *  The home station sits on a fluid pipe, so it has no item output and is not an unload target itself.
     *  The robot is placed exactly as {@code ItemRobot.useOn} places one (registry id, take as main, dock,
     *  then add), so the board's very first search already sees its zone. */
    public static void forbidRobotActionRefusesUnloadAtStation(GameTestHelper helper) {
        BlockPos forbiddenRel = new BlockPos(1, 3, 3);
        BlockPos chestRel = new BlockPos(1, 2, 3);
        BlockPos homeRel = new BlockPos(4, 3, 3);
        EntityArenaUtil.forceLoadEntityArena(helper, homeRel);

        TilePipeHolder forbiddenTile = installStation(helper, forbiddenRel, Direction.UP);
        helper.setBlock(chestRel, Blocks.CHEST);

        PluggableGate forbidGate = addGate(forbiddenTile, Direction.WEST, PARAM_GATE);
        forbidGate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        forbidGate.logic.statements[0].action.set(
                ActionWrapper.wrap(BCRoboticsStatements.ACTION_STATION_FORBID_ROBOT, null));
        forbidGate.logic.statements[0].action.set(0, new StatementParameterRobot(
                ItemRobot.createRobotStack(BoardRobotCarrierNBT.INSTANCE.getID(), 0)));
        // Accept Items with no filter: accepts everything, so only the forbid can keep the carrier away.
        PluggableGate acceptGate = addGate(forbiddenTile, Direction.EAST, PARAM_GATE);
        acceptGate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        acceptGate.logic.statements[0].action.set(
                ActionWrapper.wrap(BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS, null));

        TilePipeHolder homeTile = installStation(helper, homeRel, Direction.UP, BCTransportItems.PIPE_STONE_FLUID.get());
        PluggableGate zoneGate = addGate(homeTile, Direction.WEST, PARAM_GATE);
        zoneGate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        zoneGate.logic.statements[0].action.set(
                ActionWrapper.wrap(BCRoboticsStatements.ACTION_ROBOT_LOAD_UNLOAD_AREA, null));
        zoneGate.logic.statements[0].action.set(0, new StatementParameterMapLocation(
                areaMap(helper, BlockPos.ZERO, new BlockPos(5, 6, 6))));

        EntityRobot[] robot = { null };
        DockingStationPipe[] forbidden = { null };
        boolean[] dockedAtForbidden = { false };
        // One flat tickUntil (see sleepTriggerAndWakeupPreemptsPicker for the no-nested-scheduling rule).
        EntityArenaUtil.tickUntil(helper, 300,
                () -> {
                    if (robot[0] == null) {
                        // Both stations registered AND every gate resolved: the forbid, the accept and the zone
                        // all reach the robot through getActiveActions(), which is empty until a gate has ticked.
                        DockingStationPipe home = stationAt(helper, homeRel, Direction.UP);
                        forbidden[0] = stationAt(helper, forbiddenRel, Direction.UP);
                        if (home == null || forbidden[0] == null
                                || !hasActiveAction(forbidden[0], BCRoboticsStatements.ACTION_STATION_FORBID_ROBOT)
                                || !hasActiveAction(forbidden[0], BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS)
                                || !hasActiveAction(home, BCRoboticsStatements.ACTION_ROBOT_LOAD_UNLOAD_AREA)) {
                            return false;
                        }
                        robot[0] = addHomedRobot(helper, home, BoardRobotCarrierNBT.INSTANCE);
                        robot[0].setInventoryStack(0, new ItemStack(Items.DIAMOND, 5));
                        return false;
                    }
                    DockingStation dockedAt = robot[0].getDockingStation();
                    if (dockedAt != null && dockedAt.getPos().equals(forbidden[0].getPos())
                            && dockedAt.side() == forbidden[0].side()) {
                        dockedAtForbidden[0] = true;
                    }
                    // Settled: asleep at home (the forbid working), or the regression showing itself.
                    return robot[0].isSleeping() || dockedAtForbidden[0]
                            || !ItemStack.matches(robot[0].getInventoryStack(0), new ItemStack(Items.DIAMOND, 5));
                },
                () -> {
                    boolean sleeping = robot[0].isSleeping();
                    ItemStack cargo = robot[0].getInventoryStack(0).copy();
                    robot[0].discard();
                    helper.assertFalse(dockedAtForbidden[0],
                            "the carrier must never dock at the gate-forbidden station — 7.1.x "
                                    + "search skips stations the forbid action names");
                    helper.assertTrue(ItemStack.matches(cargo, new ItemStack(Items.DIAMOND, 5)),
                            "the carrier must keep its cargo: the only accepting station in its zone is "
                                    + "forbidden to it - holding " + cargo);
                    helper.assertTrue(sleeping, "the carrier must settle into sleep at its home station");
                    helper.succeed();
                },
                "the carrier neither docked at the forbidden station nor settled into sleep");
    }

    // ---------- goto station action ----------

    /** A robot docked at station A while station A's gate carries the in-station trigger + goto-station
     *  action with a SPOT map-location parameter pointing at B: resolving the gate hands the docked robot an
     *  overriding {@code AIRobotGotoStation} redirect (takeAsMain), and the robot flies to B and takes it as
     *  its main station. The map's SPOT keys are written directly onto the stack's CUSTOM_DATA — the same
     *  shape {@code ItemMapLocation.useOn} stamps. */
    public static void gotoStationActionRedirectsDockedRobot(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeARel = new BlockPos(1, 3, 2);
        BlockPos pipeBRel = new BlockPos(4, 3, 2);
        BlockPos robotRel = new BlockPos(2, 4, 3);

        TilePipeHolder tileA = installStation(helper, pipeARel, Direction.UP);
        installStation(helper, pipeBRel, Direction.UP);

        PluggableGate gate = addGate(tileA, Direction.WEST, PARAM_GATE);
        gate.logic.statements[0].trigger.set(
                TriggerWrapper.wrap(BCRoboticsStatements.TRIGGER_ROBOT_IN_STATION, null));
        gate.logic.statements[0].action.set(
                ActionWrapper.wrap(BCRoboticsStatements.ACTION_ROBOT_GOTO_STATION, null));
        gate.logic.statements[0].action.set(0,
                new StatementParameterMapLocation(spotMap(helper, pipeBRel, Direction.UP)));

        EntityRobot[] robot = { null };
        DockingStationPipe[] stationB = { null };
        int[] phase = { 0 }; // 0: both stations, 1: robot id + dock + resolve, then docked at B
        // One flat tickUntil (see sleepTriggerAndWakeupPreemptsPicker for the no-nested-scheduling rule).
        EntityArenaUtil.tickUntil(helper, 420,
                () -> {
                    if (phase[0] == 0) {
                        if (stationAt(helper, pipeARel, Direction.UP) == null
                                || stationAt(helper, pipeBRel, Direction.UP) == null) {
                            return false;
                        }
                        phase[0] = 1;
                        robot[0] = addBoardRobot(helper, robotRel, BoardRobotEmptyNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                        return false;
                    }
                    if (phase[0] == 1) {
                        // Wait for the FIRST tick: registerRobot assigns the robot's id then, and takeAsMain
                        // must record a real id or robotTaking() resolves null (NULL_ROBOT_ID) and the
                        // in-station trigger cannot fire. The empty board's load search finds no gateless
                        // station anyway, so the redirect still beats it.
                        if (robot[0].getRobotId() == EntityRobotBase.NULL_ROBOT_ID) {
                            return false;
                        }
                        phase[0] = 2;
                        DockingStationPipe stationA = stationAt(helper, pipeARel, Direction.UP);
                        stationB[0] = stationAt(helper, pipeBRel, Direction.UP);
                        stationA.takeAsMain(robot[0]);
                        robot[0].dock(stationA);

                        gate.logic.resolveActions();
                        helper.assertTrue(gate.logic.triggerOn[0],
                                "the in-station trigger must fire while the robot is docked at "
                                        + "station A");
                        helper.assertTrue(robot[0].getOverridingAI() != null,
                                "the goto action must hand the docked robot a redirect to station B");
                        return false;
                    }
                    return robot[0].getDockingStation() == stationB[0];
                },
                () -> {
                    robot[0].discard();
                    helper.succeed();
                },
                "the redirected robot never docked at station B");
    }

    /** The same action with its map-location parameter LEFT EMPTY. 7.1.x defaulted {@code newStation} to
     *  the gate's OWN station — {@code AIRobotGoAndLinkToDock(robot, station)} — so an unparameterised
     *  Goto Station still redirected the docked robot, at its own station. The port returned early on an
     *  empty parameter and did nothing at all.
     *
     *  <p>Asserted on the redirect being handed out, which is the whole of the difference: the parameter
     *  is unset, so nothing else in the world changes. */
    public static void gotoStationActionWithNoParameterTargetsItsOwnStation(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(2, 3, 2);
        BlockPos robotRel = new BlockPos(3, 4, 3);

        TilePipeHolder tile = installStation(helper, pipeRel, Direction.UP);

        PluggableGate gate = addGate(tile, Direction.WEST, PARAM_GATE);
        gate.logic.statements[0].trigger.set(
                TriggerWrapper.wrap(BCRoboticsStatements.TRIGGER_ROBOT_IN_STATION, null));
        gate.logic.statements[0].action.set(
                ActionWrapper.wrap(BCRoboticsStatements.ACTION_ROBOT_GOTO_STATION, null));
        // Deliberately NO action.set(0, ...): the slot holds the empty StatementParameterItemStack the
        // action's createParameter builds, which is exactly what an untouched GUI slot looks like.

        EntityRobot[] robot = { null };
        int[] phase = { 0 };
        EntityArenaUtil.tickUntil(helper, 300,
                () -> {
                    if (phase[0] == 0) {
                        if (stationAt(helper, pipeRel, Direction.UP) == null) {
                            return false;
                        }
                        phase[0] = 1;
                        robot[0] = addBoardRobot(helper, robotRel, BoardRobotEmptyNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                        return false;
                    }
                    // The id is assigned on the robot's first tick; robotTaking() resolves null without it.
                    if (robot[0].getRobotId() == EntityRobotBase.NULL_ROBOT_ID) {
                        return false;
                    }
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    station.take(robot[0]);
                    robot[0].dock(station);

                    gate.logic.resolveActions();
                    helper.assertTrue(gate.logic.triggerOn[0],
                            "the in-station trigger must fire while the robot is docked here");

                    buildcraft.api.robots.AIRobot override = robot[0].getOverridingAI();
                    helper.assertTrue(override != null,
                            "a Goto Station action with an EMPTY parameter must still redirect the docked "
                                    + "robot — 7.1.x defaulted the destination to the gate's own station");
                    helper.assertTrue(override instanceof buildcraft.robotics.ai.AIRobotGotoStation,
                            "the redirect must be a goto-station move, got " + override);
                    return true;
                },
                () -> {
                    robot[0].discard();
                    helper.succeed();
                },
                "the station never registered, or the robot never received its id");
    }
}
