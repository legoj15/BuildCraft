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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.RobotManager;

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
import buildcraft.robotics.ai.AIRobotUnload;
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
 * picker fetching a dropped item the wake made it re-scan for), the forbid-robot action refusing a carrier's
 * unload at a gate-forbidden station (the D1 pin — the refusal is visible through the LIVE station's
 * {@code getActiveActions()} aggregation, which the JUnit predicate tests never reach), and the
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

    private static TilePipeHolder placeItemPipe(GameTestHelper helper, BlockPos relPos) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        return tile;
    }

    /** A wooden-pipe supply station on {@code side}, returning the tile the station sits on. */
    private static TilePipeHolder installStation(GameTestHelper helper, BlockPos relPos, Direction side) {
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        tile.replacePluggable(side, new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, side));
        return tile;
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
     *  sleeps, and the wakeup preempts the sleep so the picker re-scans and fetches a diamond dropped after
     *  it was asleep. The wake is observable ONLY through the fetch: a preempted sleep is re-entered within
     *  the same tick (the board's GotoSleep fallback), so {@code isSleeping()} polls would never see it. */
    public static void sleepTriggerAndWakeupPreemptsPicker(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(1, 3, 2);
        BlockPos robotRel = new BlockPos(3, 4, 2);
        BlockPos itemRel = new BlockPos(3, 4, 2);

        TilePipeHolder tile = installStation(helper, pipeRel, Direction.UP);

        EntityRobot[] robot = { null };
        boolean[] docked = { false };
        EntityArenaUtil.tickUntil(helper, 200,
                () -> {
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    if (station == null) {
                        return false;
                    }
                    if (robot[0] == null) {
                        robot[0] = addBoardRobot(helper, robotRel, BoardRobotPickerNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                        return false;
                    }
                    if (!docked[0]) {
                        // Hand-dock and force the sleep: deterministic, and the override keeps the board
                        // from scanning before the gate exists.
                        station.takeAsMain(robot[0]);
                        robot[0].dock(station);
                        robot[0].overrideAI(new AIRobotSleep(robot[0]));
                        docked[0] = true;
                        return false;
                    }
                    return robot[0].isSleeping();
                },
                () -> {
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

                    // A diamond the woken picker can find: dropped only now, so a sleeping robot never saw it.
                    dropItem(helper, itemRel, new ItemStack(Items.DIAMOND));

                    EntityArenaUtil.tickUntil(helper, 200,
                            robot[0]::containsItems,
                            () -> {
                                helper.assertTrue(ItemStack.matches(robot[0].getInventoryStack(0),
                                        new ItemStack(Items.DIAMOND, 1)),
                                        "the woken picker must fetch the dropped diamond — the wakeup action "
                                                + "preempted the sleep and the board re-scanned");
                                robot[0].discard();
                                helper.succeed();
                            },
                            "the wakeup action never woke the sleeping picker (or it woke and re-slept without "
                                    + "re-scanning)");
                },
                "the picker never fell asleep at the station");
    }

    // ---------- forbid robot (the D1 pin) ----------

    /** A carrier at a supply station whose gate carries the forbid-robot action naming the carrier's board:
     *  the D1 pin. The unload is refused at the forbidden station — visible through the LIVE station's
     *  {@code getActiveActions()} aggregation, the seam the JUnit predicate tests never reach. This is what
     *  kills the Ph4 permissive loop ({@code carrierUnloadsAtTheStationItLoadedFrom}): once
     *  {@code DockingStationPipe.getActiveActions()} exposes the gate's forbid action, the carrier loads,
     *  refuses to unload, and sleeps with its cargo instead of cycling load -> unload -> load. */
    public static void forbidRobotActionRefusesUnloadAtStation(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(1, 3, 3);
        BlockPos chestRel = new BlockPos(1, 2, 3);
        BlockPos robotRel = new BlockPos(3, 4, 3);

        TilePipeHolder tile = installStation(helper, pipeRel, Direction.UP);
        helper.setBlock(chestRel, Blocks.CHEST);
        //? if >=1.21.10 {
        ChestBlockEntity chest = helper.getBlockEntity(chestRel, ChestBlockEntity.class);
        //?} else {
        /*ChestBlockEntity chest = helper.getBlockEntity(chestRel);*/
        //?}
        chest.setItem(0, new ItemStack(Items.DIAMOND, 5));

        PluggableGate gate = addGate(tile, Direction.WEST, PARAM_GATE);
        gate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        gate.logic.statements[0].action.set(
                ActionWrapper.wrap(BCRoboticsStatements.ACTION_STATION_FORBID_ROBOT, null));
        gate.logic.statements[0].action.set(0, new StatementParameterRobot(
                ItemRobot.createRobotStack(BoardRobotCarrierNBT.INSTANCE.getID(), 0)));

        EntityRobot[] robot = { null };
        EntityArenaUtil.tickUntil(helper, 300,
                () -> {
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    if (station == null) {
                        return false;
                    }
                    if (robot[0] == null) {
                        robot[0] = addBoardRobot(helper, robotRel, BoardRobotCarrierNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                        return false;
                    }
                    return robot[0].containsItems();
                },
                () -> {
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    helper.assertTrue(robot[0].getDockingStation() == station,
                            "the carrier must be docked at the supply station it loaded from");
                    helper.assertFalse(AIRobotUnload.unload(robot[0], station, false),
                            "the gate-forbidden station must refuse the carrier's unload — the forbid action "
                                    + "names this robot's board, and the refusal flows through the station's "
                                    + "live getActiveActions()");
                    helper.assertTrue(ItemStack.matches(robot[0].getInventoryStack(0),
                            new ItemStack(Items.DIAMOND, 5)),
                            "the refused unload must leave the cargo in the robot");
                    robot[0].discard();
                    helper.succeed();
                },
                "the carrier never loaded the chest's diamonds at the forbidden station");
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
        EntityArenaUtil.tickUntil(helper, 200,
                () -> stationAt(helper, pipeARel, Direction.UP) != null
                        && stationAt(helper, pipeBRel, Direction.UP) != null,
                () -> {
                    DockingStationPipe stationA = stationAt(helper, pipeARel, Direction.UP);
                    DockingStationPipe stationB = stationAt(helper, pipeBRel, Direction.UP);

                    // Spawn, dock and resolve synchronously, before the robot's first tick: the action must
                    // hand it the redirect before the empty board parks it.
                    robot[0] = addBoardRobot(helper, robotRel, BoardRobotEmptyNBT.INSTANCE);
                    robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                    stationA.takeAsMain(robot[0]);
                    robot[0].dock(stationA);

                    gate.logic.resolveActions();
                    helper.assertTrue(gate.logic.triggerOn[0],
                            "the in-station trigger must fire while the robot is docked at station A");
                    helper.assertTrue(robot[0].getOverridingAI() != null,
                            "the goto action must hand the docked robot a redirect to station B");

                    EntityArenaUtil.tickUntil(helper, 200,
                            () -> robot[0].getDockingStation() == stationB,
                            () -> {
                                robot[0].discard();
                                helper.succeed();
                            },
                            "the redirected robot never docked at station B");
                },
                "the two stations never registered");
    }
}
