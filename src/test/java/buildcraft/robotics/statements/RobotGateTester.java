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
import net.minecraft.world.level.material.Fluids;
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
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileTank;

import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;
import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.BCRoboticsPlugs;
import buildcraft.robotics.BCRoboticsStatements;
import buildcraft.robotics.DockingStationPipe;
import buildcraft.robotics.RobotRegistry;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.ZonePinnedRobot;
import buildcraft.robotics.ai.AIRobotSleep;
import buildcraft.robotics.boards.BoardRobotCarrierNBT;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.boards.BoardRobotPumpNBT;
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

    /** A charged robot placed on {@code home} the way {@code ItemRobot.useOn} places one: the id comes from
     *  the registry before the entity reaches the world, the station is taken as MAIN (its linked station,
     *  whose gates name the robot's zones) and the robot docks there, so its board's first cycle already runs
     *  with its home and zones in place. Deliberately NOT a {@code ZonePinnedRobot}: the zone coming from the
     *  home station's gate is what the test uses (the one board robot {@code ZonePinnedRobotUseTester} allows
     *  without a pin). */
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

    private static ItemEntity dropItem(GameTestHelper helper, BlockPos relPos, ItemStack stack) {
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        ItemEntity item = new ItemEntity(helper.getLevel(), pos.x, pos.y, pos.z, stack);
        item.setNoGravity(true);
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    // ---------- sleep trigger + wakeup action ----------

    /** A picker docked at a station with the sleep trigger + wakeup action gate: the trigger fires while it
     *  sleeps, and the wakeup preempts the sleep so the picker re-scans and fetches the diamond dropped after it
     *  was asleep (its zone is this cell). The wake is observable ONLY through the fetch: a preempted sleep is re-entered within
     *  the same tick (the board's GotoSleep fallback), so {@code isSleeping()} polls would never see it. */
    public static void sleepTriggerAndWakeupPreemptsPicker(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(1, 3, 2);
        BlockPos robotRel = new BlockPos(3, 4, 2);
        BlockPos itemRel = new BlockPos(3, 4, 2);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        TilePipeHolder tile = installStation(helper, pipeRel, Direction.UP);

        EntityRobot[] robot = { null };
        ItemEntity[] diamond = { null };
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
                        // Zone-pinned: an unzoned picker's fetch searches the whole dimension and takes
                        // whatever neighbouring arenas drop, which is both this test's noise and their loss.
                        robot[0] = ZonePinnedRobot.spawn(helper, robotRel, BoardRobotPickerNBT.INSTANCE);
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
                        diamond[0] = dropItem(helper, itemRel, new ItemStack(Items.DIAMOND));
                        return false;
                    }
                    return robot[0].containsItems();
                },
                () -> {
                    // The robot was asleep (override) before the gate and the drop, and a sleeper never
                    // fetches, so a fetch is the proof. Its zone is this cell, so the diamond is the only thing
                    // it can have fetched (before the pin, a neighbour's nearer drop could be taken first:
                    // seen 2026-09-26 on 26.1.2).
                    helper.assertTrue(robot[0].getInventoryStack(0).is(Items.DIAMOND),
                            "the woken picker fetches the diamond dropped in its cell, holding "
                                    + robot[0].getInventoryStack(0));
                    helper.succeed();
                },
                "the picker never fell asleep at the station (or the wakeup never made it fetch)",
                () -> EntityArenaUtil.discard(robot[0], diamond[0]));
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
                    helper.assertFalse(dockedAtForbidden[0],
                            "the carrier must never dock at the gate-forbidden station — 7.1.x "
                                    + "search skips stations the forbid action names");
                    helper.assertTrue(ItemStack.matches(cargo, new ItemStack(Items.DIAMOND, 5)),
                            "the carrier must keep its cargo: the only accepting station in its zone is "
                                    + "forbidden to it - holding " + cargo);
                    helper.assertTrue(sleeping, "the carrier must settle into sleep at its home station");
                    helper.succeed();
                },
                "the carrier neither docked at the forbidden station nor settled into sleep",
                () -> EntityArenaUtil.discard(robot[0]));
    }

    // ---------- goto station action ----------

    /** A robot docked at station A while station A's gate carries the in-station trigger + goto-station
     *  action with a SPOT map-location parameter pointing at B: resolving the gate hands the docked robot an
     *  overriding {@code AIRobotGotoStation} redirect (takeAsMain), and the robot flies to B and takes it as
     *  its main station once it docks there (A stays its home for the whole flight). The map's SPOT keys are written directly onto the stack's CUSTOM_DATA — the same
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
                        robot[0] = ZonePinnedRobot.spawn(helper, robotRel, BoardRobotEmptyNBT.INSTANCE);
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
                    DockingStationPipe stationA = stationAt(helper, pipeARel, Direction.UP);
                    if (robot[0].getDockingStation() != stationB[0]) {
                        // In flight: B is only claimed, A stays the robot's home until it docks at B.
                        helper.assertTrue(robot[0].getLinkedStation() == stationA && stationA.isMainStation(),
                                "the redirected robot must keep its home A until it docks at B");
                        return false;
                    }
                    helper.assertTrue(robot[0].getLinkedStation() == stationB[0] && stationB[0].isMainStation(),
                            "docked at B, the redirect makes B the robot's home");
                    helper.assertFalse(stationA.isTaken(), "...and lets the old home A go");
                    return true;
                },
                helper::succeed,
                "the redirected robot never docked at station B",
                () -> EntityArenaUtil.discard(robot[0]));
    }

    /** The same action with its map-location parameter LEFT EMPTY. 7.1.x defaulted {@code newStation} to
     *  the gate's OWN station - {@code AIRobotGoAndLinkToDock(robot, station)} - so an unparameterised
     *  Goto Station still redirected the robot linked to that station, home. The port returned early on an
     *  empty parameter and did nothing at all.
     *
     *  <p>The robot here is LINKED to the station but not docked at it (the gate's trigger is plain TRUE, so
     *  it fires regardless): that is the one place the empty-parameter default still has something to do. A
     *  robot docked at the station is {@link #gotoStationActionAtTheDockedStationLeavesTheRobotAlone}'s case.
     *  Asserted on the redirect being handed out, which is the whole of the difference: the parameter is
     *  unset, so nothing else in the world changes. */
    public static void gotoStationActionWithNoParameterTargetsItsOwnStation(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(2, 3, 2);
        BlockPos robotRel = new BlockPos(3, 4, 3);

        TilePipeHolder tile = installStation(helper, pipeRel, Direction.UP);

        PluggableGate gate = addGate(tile, Direction.WEST, PARAM_GATE);
        gate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
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
                        robot[0] = ZonePinnedRobot.spawn(helper, robotRel, BoardRobotEmptyNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                        return false;
                    }
                    // The id is assigned on the robot's first tick; robotTaking() resolves null without it.
                    if (robot[0].getRobotId() == EntityRobotBase.NULL_ROBOT_ID) {
                        return false;
                    }
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    helper.assertTrue(station.takeAsMain(robot[0]),
                            "the free station must take the robot as its main (home) link");
                    helper.assertTrue(robot[0].getDockingStation() == null,
                            "the robot is linked to the station but not docked at it");

                    gate.logic.resolveActions();

                    buildcraft.api.robots.AIRobot override = robot[0].getOverridingAI();
                    helper.assertTrue(override != null,
                            "a Goto Station action with an EMPTY parameter must still send the robot linked "
                                    + "to the gate's station home - 7.1.x defaulted the destination to the "
                                    + "gate's own station");
                    helper.assertTrue(override instanceof buildcraft.robotics.ai.AIRobotGotoStation,
                            "the redirect must be a goto-station move, got " + override);
                    return true;
                },
                helper::succeed,
                "the station never registered, or the robot never received its id",
                () -> EntityArenaUtil.discard(robot[0]));
    }

    /** A robot already docked at the station the action names - an empty parameter defaults to the gate's own
     *  station, a map location may name it outright - must be left alone. The redirect would end in
     *  {@code AIRobotGotoStation.start()} the moment it began (the robot is already there), and the gate
     *  re-fires every tick while it is on: each tick installed a fresh override that aborted the board's AI and
     *  ended at once, so the robot sat frozen for as long as the gate held. */
    public static void gotoStationActionAtTheDockedStationLeavesTheRobotAlone(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(2, 3, 2);
        BlockPos robotRel = new BlockPos(3, 4, 3);

        TilePipeHolder tile = installStation(helper, pipeRel, Direction.UP);

        PluggableGate gate = addGate(tile, Direction.WEST, PARAM_GATE);
        gate.logic.statements[0].trigger.set(
                TriggerWrapper.wrap(BCRoboticsStatements.TRIGGER_ROBOT_IN_STATION, null));
        gate.logic.statements[0].action.set(
                ActionWrapper.wrap(BCRoboticsStatements.ACTION_ROBOT_GOTO_STATION, null));

        EntityRobot[] robot = { null };
        int[] phase = { 0 }; // 0: station, 1: id + dock + both checks, 2: let the live gate keep firing
        int[] settled = { 0 };
        EntityArenaUtil.tickUntil(helper, 300,
                () -> {
                    if (phase[0] == 0) {
                        if (stationAt(helper, pipeRel, Direction.UP) == null) {
                            return false;
                        }
                        phase[0] = 1;
                        robot[0] = ZonePinnedRobot.spawn(helper, robotRel, BoardRobotEmptyNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                        return false;
                    }
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    if (phase[0] == 1) {
                        if (robot[0].getRobotId() == EntityRobotBase.NULL_ROBOT_ID) {
                            return false;
                        }
                        helper.assertTrue(station.takeAsMain(robot[0]), "the free station takes the robot");
                        robot[0].dock(station);

                        // Empty parameter: the destination defaults to the gate's own station, the one the
                        // robot is docked at.
                        gate.logic.resolveActions();
                        helper.assertTrue(gate.logic.triggerOn[0],
                                "the in-station trigger must fire while the robot is docked here");
                        helper.assertTrue(robot[0].getOverridingAI() == null,
                                "an empty-parameter Goto Station must not redirect a robot already docked at "
                                        + "the gate's own station, got " + robot[0].getOverridingAI());

                        // A map location naming the very station the robot sits at is the same no-op.
                        gate.logic.statements[0].action.set(0,
                                new StatementParameterMapLocation(spotMap(helper, pipeRel, Direction.UP)));
                        gate.logic.resolveActions();
                        helper.assertTrue(gate.logic.triggerOn[0], "the trigger still fires");
                        helper.assertTrue(robot[0].getOverridingAI() == null,
                                "a Goto Station naming the station the robot is docked at must not redirect it, "
                                        + "got " + robot[0].getOverridingAI());
                        phase[0] = 2;
                        return false;
                    }
                    // The live gate now re-resolves every tick while the robot stays docked: it must never
                    // be pulled off its dock or caught holding a redirect.
                    helper.assertTrue(robot[0].getDockingStation() == station,
                            "the robot must stay docked at its station while the gate holds");
                    helper.assertTrue(robot[0].getOverridingAI() == null,
                            "no redirect may be installed while the gate holds, got "
                                    + robot[0].getOverridingAI());
                    return ++settled[0] >= 20;
                },
                helper::succeed,
                "the station never registered, or the robot never received its id",
                () -> EntityArenaUtil.discard(robot[0]));
    }

    /** A robot the registry knows about (id assigned) but that is not in the world: a stand-in holder for a
     *  station, which never ticks and so never wanders off to reserve a neighbouring test's stations. Killed
     *  through the registry when the test ends. */
    private static EntityRobot registeredOffWorldRobot(GameTestHelper helper) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        robot.setPos(pos.x, pos.y, pos.z);
        registry(helper).registerRobot(robot);
        return robot;
    }

    private static RobotRegistry registry(GameTestHelper helper) {
        return (RobotRegistry) RobotManager.registryProvider.getRegistry(helper.getLevel());
    }

    /** The cleanup of the two-robot tests: the live robot leaves the world (its removal path frees what it held)
     *  and the off-world holder is killed through the registry, whichever of the two the test got as far as. */
    private static void cleanUp(GameTestHelper helper, EntityRobot robot, EntityRobot holder) {
        EntityArenaUtil.discard(robot);
        if (holder != null) {
            registry(helper).killRobot(holder);
        }
    }

    /** A robot docked at its home A, whose gate sends it to station B while another robot holds B. The goto could
     *  only fail its claim at once, and a gate that holds re-fires every tick: each tick installed a fresh redirect
     *  that aborted the board's AI and failed on the spot, so the robot's board never got a cycle. The action must
     *  leave the robot alone: it settles into its sleep and keeps that same sleep AI for as long as the gate holds. */
    public static void gotoStationActionAtAStationAnotherRobotHoldsLeavesTheRobotAlone(GameTestHelper helper) {
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
        EntityRobot[] holder = { null };
        buildcraft.api.robots.AIRobot[] sleep = { null };
        int[] phase = { 0 }; // 0: stations, 1: id + home + B held + first resolve, 2: settled asleep under the gate
        int[] settled = { 0 };
        EntityArenaUtil.tickUntil(helper, 300,
                () -> {
                    if (phase[0] == 0) {
                        if (stationAt(helper, pipeARel, Direction.UP) == null
                                || stationAt(helper, pipeBRel, Direction.UP) == null) {
                            return false;
                        }
                        phase[0] = 1;
                        robot[0] = ZonePinnedRobot.spawn(helper, robotRel, BoardRobotEmptyNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                        return false;
                    }
                    DockingStationPipe stationA = stationAt(helper, pipeARel, Direction.UP);
                    DockingStationPipe stationB = stationAt(helper, pipeBRel, Direction.UP);
                    if (phase[0] == 1) {
                        if (robot[0].getRobotId() == EntityRobotBase.NULL_ROBOT_ID) {
                            return false;
                        }
                        // Advanced before any assertion: a failed assertion must fail the test, not re-run this
                        // one-shot setup on the next poll.
                        phase[0] = 2;
                        holder[0] = registeredOffWorldRobot(helper);
                        helper.assertTrue(stationB.takeAsMain(holder[0]), "precondition: the other robot holds B");
                        helper.assertTrue(stationA.takeAsMain(robot[0]), "the free station A takes the robot home");
                        robot[0].dock(stationA);

                        gate.logic.resolveActions();
                        helper.assertTrue(gate.logic.triggerOn[0],
                                "the in-station trigger must fire while the robot is docked at A");
                        helper.assertTrue(robot[0].getOverridingAI() == null,
                                "a Goto Station naming a station another robot holds must not redirect the robot, "
                                        + "got " + robot[0].getOverridingAI());
                        return false;
                    }
                    // The live gate keeps re-firing: the robot must stay home, unredirected, and once its board has
                    // put it to sleep that same sleep must survive tick after tick (no abort-and-restart churn).
                    helper.assertTrue(robot[0].getDockingStation() == stationA, "the robot must stay docked at A");
                    helper.assertTrue(robot[0].getOverridingAI() == null,
                            "no redirect may be installed while B is held, got " + robot[0].getOverridingAI());
                    helper.assertTrue(stationB.robotIdTaking() == holder[0].getRobotId(),
                            "the other robot's hold on B must be left alone");
                    buildcraft.api.robots.AIRobot active = robot[0].mainAI.getActiveAI();
                    if (sleep[0] == null) {
                        if (active instanceof AIRobotSleep) {
                            sleep[0] = active;
                        }
                        return false;
                    }
                    helper.assertTrue(active == sleep[0],
                            "the robot's board AI must run undisturbed while the gate holds, but its sleep was "
                                    + "replaced by " + active);
                    return ++settled[0] >= 20;
                },
                helper::succeed,
                "the robot never settled into its sleep at A under the gate",
                () -> cleanUp(helper, robot[0], holder[0]));
    }

    /** A redirect that fails (here: another robot takes B between the gate handing it out and the robot starting
     *  it) must not be re-issued on the next tick, or a gate that holds aims the robot at an unreachable station
     *  every tick and its board never gets a cycle. After the failure the robot's controller refuses new
     *  redirects for a while, even with B free again; once that back-off runs out, the gate's next pulse sends the
     *  robot to B after all. */
    public static void gotoStationActionBacksOffAfterAFailedGoto(GameTestHelper helper) {
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
        EntityRobot[] holder = { null };
        // 0: stations, 1: id + dock + redirect handed out + B taken under it, 2: the redirect failed -> free B,
        // 3: B free, the gate holding, the back-off refusing redirects, 4: the back-off ran out -> docked at B
        int[] phase = { 0 };
        int[] refused = { 0 };
        EntityArenaUtil.tickUntil(helper, 500,
                () -> {
                    if (phase[0] == 0) {
                        if (stationAt(helper, pipeARel, Direction.UP) == null
                                || stationAt(helper, pipeBRel, Direction.UP) == null) {
                            return false;
                        }
                        phase[0] = 1;
                        robot[0] = ZonePinnedRobot.spawn(helper, robotRel, BoardRobotEmptyNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                        return false;
                    }
                    DockingStationPipe stationA = stationAt(helper, pipeARel, Direction.UP);
                    DockingStationPipe stationB = stationAt(helper, pipeBRel, Direction.UP);
                    if (phase[0] == 1) {
                        if (robot[0].getRobotId() == EntityRobotBase.NULL_ROBOT_ID) {
                            return false;
                        }
                        phase[0] = 2; // before any assertion, so a failure cannot re-run this one-shot setup
                        helper.assertTrue(stationA.takeAsMain(robot[0]), "the free station A takes the robot home");
                        robot[0].dock(stationA);
                        gate.logic.resolveActions();
                        helper.assertTrue(robot[0].getOverridingAI() != null,
                                "precondition: with B free the gate hands the robot a redirect");
                        // Before the robot's next cycle starts that redirect, another robot takes B under it.
                        holder[0] = registeredOffWorldRobot(helper);
                        helper.assertTrue(stationB.take(holder[0]), "precondition: the other robot takes B");
                        return false;
                    }
                    if (phase[0] == 2) {
                        if (robot[0].getOverridingAI() != null) {
                            return false; // the robot has not cycled yet
                        }
                        helper.assertTrue(robot[0].getDockingStation() == stationA,
                                "the refused redirect must leave the robot docked at A");
                        stationB.release(holder[0]);
                        helper.assertFalse(stationB.isTaken(), "precondition: B is free again");
                        phase[0] = 3;
                        return false;
                    }
                    if (phase[0] == 3) {
                        // The gate re-fires every tick with B free: the back-off must still refuse it.
                        helper.assertTrue(robot[0].getOverridingAI() == null,
                                "a redirect that just failed must not be re-issued on the gate's next pulse, got "
                                        + robot[0].getOverridingAI());
                        helper.assertTrue(robot[0].getDockingStation() == stationA, "the robot stays home");
                        if (++refused[0] >= 20) {
                            phase[0] = 4;
                        }
                        return false;
                    }
                    // The back-off is temporary: the gate sends the robot to B once it runs out.
                    return robot[0].getDockingStation() == stationB;
                },
                helper::succeed,
                "the redirect never failed, or the robot never reached B once the back-off ran out",
                () -> cleanUp(helper, robot[0], holder[0]));
    }

    // ---------- pump: pumps inside its Work in Area zone, unloads at an Accept Fluids home ----------

    /** Poll budget for the pump test: two whole pump-and-unload cycles take a few hundred ticks; the 60 s
     *  ({@code AIRobotSleep}, 1200 ticks) sleep a failed unload search ends in does not fit inside it. */
    private static final int PUMP_BUDGET = 1100;

    /** The pump board's whole loop, which no other test drives end to end: a Pump robot homed on an Accept
     *  Fluids station (a stone fluid pipe over an empty BC tank) with a Work in Area zone over a 2x2 water pool
     *  pumps a bucket, flies back, unloads it into the pipe, which feeds the tank, and comes back for more. Two
     *  buckets in the tank prove the robot does not stop after the first (the pool regenerates, so the second
     *  pass finds a source again).
     *
     *  <p>The zone covers the station's column on purpose. As in 7.1.x ({@code EntityRobot.getZoneToLoadUnload}
     *  falls back to the work zone, and {@code AIRobotSearchStation} skips stations outside it), a robot with no
     *  Load/Unload in Area action looks for its unload station inside its WORK zone. With the home outside the
     *  zone (measured 2026-10-18 with the box at x 2..5, z 3..6) the first bucket finds no unload station, the
     *  robot sleeps at home holding it and unloads only when the 60 s sleep ends (the search's "already docked
     *  here" shortcut ignores the zone): one bucket a minute, which reads as a pump that stopped. */
    public static void pumpUnloadsIntoTankAtHomeStation(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(1, 3, 1);
        BlockPos tankRel = new BlockPos(1, 2, 1);
        EntityArenaUtil.forceLoadEntityArena(helper, pipeRel);

        // A 2x2 pool of water sources at y=1, walled in by stone at y=1 and floored at y=0 so nothing flows.
        for (int x = 2; x <= 5; x++) {
            for (int z = 3; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                boolean pool = (x == 3 || x == 4) && (z == 4 || z == 5);
                helper.setBlock(new BlockPos(x, 1, z), pool ? Blocks.WATER : Blocks.STONE);
            }
        }

        TilePipeHolder homeTile = installStation(helper, pipeRel, Direction.UP, BCTransportItems.PIPE_STONE_FLUID.get());
        helper.setBlock(tankRel, BCFactoryBlocks.TANK.get());
        //? if >=1.21.10 {
        TileTank tank = helper.getBlockEntity(tankRel, TileTank.class);
        //?} else {
        /*TileTank tank = helper.getBlockEntity(tankRel);*/
        //?}

        PluggableGate acceptGate = addGate(homeTile, Direction.WEST, BASIC_GATE);
        acceptGate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        acceptGate.logic.statements[0].action.set(
                ActionWrapper.wrap(BCRoboticsStatements.ACTION_STATION_ACCEPT_FLUIDS, null));
        PluggableGate zoneGate = addGate(homeTile, Direction.EAST, PARAM_GATE);
        zoneGate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        zoneGate.logic.statements[0].action.set(
                ActionWrapper.wrap(BCRoboticsStatements.ACTION_ROBOT_WORK_IN_AREA, null));
        zoneGate.logic.statements[0].action.set(0, new StatementParameterMapLocation(
                areaMap(helper, BlockPos.ZERO, new BlockPos(5, 3, 6))));

        EntityRobot[] robot = { null };
        EntityArenaUtil.tickUntil(helper, PUMP_BUDGET,
                () -> {
                    if (robot[0] == null) {
                        DockingStationPipe home = stationAt(helper, pipeRel, Direction.UP);
                        if (home == null
                                || !hasActiveAction(home, BCRoboticsStatements.ACTION_STATION_ACCEPT_FLUIDS)
                                || !hasActiveAction(home, BCRoboticsStatements.ACTION_ROBOT_WORK_IN_AREA)) {
                            return false;
                        }
                        robot[0] = addHomedRobot(helper, home, BoardRobotPumpNBT.INSTANCE);
                        return false;
                    }
                    return tank.tank.getAmountMb(0) >= 2000;
                },
                () -> {
                    helper.assertTrue(tank.tank.getFluidStack(0).getFluid() == Fluids.WATER,
                            "the tank must hold the pumped water, got " + tank.tank.getFluidStack(0));
                    helper.succeed();
                },
                "the pump robot never delivered two buckets of water to the tank under its home station",
                () -> EntityArenaUtil.discard(robot[0]));
    }
}
