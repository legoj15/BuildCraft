/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
//? if >=1.21.10 {
import net.minecraft.util.ProblemReporter;
//?}
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.IZone;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementSlot;
import buildcraft.api.transport.pipe.IPipeHolder;

import buildcraft.core.BCCoreItems;
import buildcraft.core.BCCoreStatements;

import buildcraft.lib.misc.CapUtil;
import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;
import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.boards.BoardRobotLumberjackNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.boards.BoardRobotPlanterNBT;
import buildcraft.robotics.ai.AIRobotSearchStation;
import buildcraft.robotics.ai.AIRobotUnload;
import buildcraft.robotics.ai.MockRobotAccess;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.robotics.statements.StatementParameterMapLocation;
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
 * The in-client robots smoke rig ({@code scripts/smoke/scenarios/robots-live.json}) rebuilt as game tests: a
 * Lumberjack homed on a wooden-item-pipe Docking Station over a chest of axes, whose gate holds Always On + Provide
 * Items, must equip an axe and cut a log; a Picker homed on a station whose gate holds Always On + Accept Items must
 * unload its cargo there; a Planter on a Provide Items station over a chest of seeds must plant a row of farmland, one
 * seed per trip, on the API rig. The lumberjack and the picker each run against three rigs:
 * <ul>
 * <li>{@link Rig#API}: pipe, station and gate built through the Java API.</li>
 * <li>{@link Rig#NBT}: the pipe holder loaded from the smoke rig's exact {@code data merge} NBT, the way the command
 *     loads it, which pins that the rig's NBT (gate variant, statement kinds, wood pipe {@code currentDir}) is
 *     valid.</li>
 * <li>{@link Rig#ORPHANED}: the NBT rig built where a previous pipe and station were removed WITHOUT the removal
 *     side effects. That is what {@code /fill} and replace-mode {@code /setblock} do on 1.21.10+ (both place with
 *     {@code UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS}, so {@code preRemoveSideEffects} never runs and the station's
 *     pluggable is never told it is gone), and what the smoke scenario's "clear and floor" step did to the previous
 *     run's rig. On 1.21.1 those commands do run {@code onRemove}; the test removes the block entity first so the
 *     orphan arises on every node. The orphaned {@link DockingStationPipe} stays registered, the new station
 *     pluggable adopts it, and it used to keep reading the dead block entity it had cached: neither gate had any
 *     effect, so both robots sat idle at home.</li>
 * </ul>
 * Robots are placed the way {@code ItemRobot.useOn} places them (registry id, taken as main, docked) but zone-pinned
 * to the test's cell.
 */
public class RobotStationRigTester {

    /** Charge above {@code SAFETY_POWER} so the robot neither shuts down nor goes recharging mid-task. */
    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    /** Fetch (a 40-cycle wait at the station), then the search, the flight and the cut. */
    private static final int LUMBERJACK_BUDGET = 600;
    /** An empty fetch scan, then the 40-cycle unload at the home station. */
    private static final int PICKER_BUDGET = 400;
    /** Six plantings, each a seed fetch (a 40-cycle wait at the station), a search, a flight and the 40-cycle plant:
     *  about 150 cycles apiece by 7.1.x's code, so this is room for twice that. */
    private static final int PLANTER_BUDGET = 1800;

    /** The gate the smoke rig writes: {@code variant:{logic:0b,material:0b,modifier:0b}}. */
    private static final GateVariant RIG_GATE =
            new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER);
    /** A gate whose action takes a parameter (Work in Area needs its map). */
    private static final GateVariant PARAM_GATE =
            new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.QUARTZ);

    /** The smoke rig's {@code data merge block} payload, verbatim but for the action kind. */
    private static final String RIG_NBT = "{pipe:{def:\"buildcraftunofficial:wood_item\",beh:{currentDir:\"DOWN\"}},"
            + "plugs:{up:{id:\"buildcraftunofficial:robot_station\",data:{}},"
            + "north:{id:\"buildcraftunofficial:gate\",data:{data:{variant:{logic:0b,material:0b,modifier:0b},"
            + "\"trigger[0]\":{s:{kind:\"buildcraftunofficial:trigger.true\",side:6b}},"
            + "\"action[0]\":{s:{kind:\"%s\",side:6b}}}}}}}";

    private static final Direction STATION_FACE = Direction.UP;
    private static final Direction GATE_FACE = Direction.NORTH;

    private enum Rig { API, NBT, ORPHANED }

    // ---------- registered tests ----------

    public static void lumberjackApiRig(GameTestHelper helper) {
        lumberjack(helper, Rig.API);
    }

    public static void lumberjackNbtRig(GameTestHelper helper) {
        lumberjack(helper, Rig.NBT);
    }

    public static void lumberjackOnOrphanedStation(GameTestHelper helper) {
        lumberjack(helper, Rig.ORPHANED);
    }

    public static void pickerApiRig(GameTestHelper helper) {
        picker(helper, Rig.API);
    }

    public static void pickerNbtRig(GameTestHelper helper) {
        picker(helper, Rig.NBT);
    }

    public static void pickerOnOrphanedStation(GameTestHelper helper) {
        picker(helper, Rig.ORPHANED);
    }

    // ---------- stations in unloaded chunks, and stations whose pipe is gone ----------

    /** Far enough from the arena grid that its chunk is never loaded during the run. */
    private static final int UNLOADED_OFFSET = 4096;
    private static final int UNLOADED_HOME_BUDGET = 240;
    private static final int FILLED_AWAY_BUDGET = 200;

    /** A robot working in its cell while its home station's chunk is unloaded, which is ordinary on a server: the
     *  unload {@code setRemoved}s the home's cached pipe tile.
     *  <ul>
     *  <li>The home's gates still count: a robot linked to it takes its work zone from the home's Work in Area
     *      action, read through the cached tile (dropping the cache would leave the robot zoneless).</li>
     *  <li>The robot keeps running its board (the station is not "uninitialized" just because its chunk is away).</li>
     *  <li>Nothing about the station may load that chunk: not the station, not its pipe's neighbour lookups.</li>
     *  </ul>
     *  A second, never-resolved station (the state of every station read back from the save) is checked the same
     *  way. */
    public static void unloadedHomeKeepsTheRobotWorking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(2, 3, 2);
        BlockPos itemRel = new BlockPos(4, 3, 4);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);
        BlockPos farA = helper.absolutePos(robotRel).offset(UNLOADED_OFFSET, 0, 0);
        BlockPos farB = farA.offset(0, 0, 64);
        helper.assertFalse(level.isLoaded(farA) || level.isLoaded(farB),
                "precondition: the far stations' chunks are not loaded");

        // A home that had resolved its pipe before the chunk went away, its gate naming this cell as the work area.
        DockingStationPipe home = unloadedStation(level, farA, BCRoboticsStatements.ACTION_ROBOT_WORK_IN_AREA,
                new StatementParameterMapLocation(areaMap(helper, BlockPos.ZERO, new BlockPos(5, 6, 6))));
        // A station read back from the save, never resolved.
        DockingStationPipe neverResolved = new DockingStationPipe();
        CompoundTag record = new CompoundTag();
        record.putIntArray("pos", new int[] { farB.getX(), farB.getY(), farB.getZ() });
        record.putByte("side", (byte) STATION_FACE.ordinal());
        neverResolved.readFromNbt(record);
        neverResolved.world = level;
        RobotManager.registryProvider.getRegistry(level).registerStation(home);
        RobotManager.registryProvider.getRegistry(level).registerStation(neverResolved);

        // The work zone a robot homed there gets, read the way EntityRobot reads it (a board-less body, never added).
        EntityRobot probe = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        probe.setMainStation(home);
        IZone zone = probe.getZoneToWork();
        helper.assertTrue(zone != null && zone.contains(Vec3.atCenterOf(helper.absolutePos(itemRel))),
                "a robot homed on an unloaded station keeps the work zone its home gate names, got " + zone);
        helper.assertFalse(zone.contains(Vec3.atCenterOf(farA)), "...and that zone is the cell, not everywhere");
        IPipeHolder cached = home.getHolder();
        for (Direction dir : Direction.values()) {
            helper.assertTrue(cached.getNeighbourTile(dir) == null,
                    "a pipe's neighbour lookup in an unloaded chunk answers nothing rather than loading it");
        }
        helper.assertFalse(level.isLoaded(farA), "the pipe's neighbour lookups loaded the unloaded chunk");
        Object plainToken = new Object();
        for (Direction dir : Direction.values()) {
            // Both lookup kinds: a NeoForge block capability, and a plain token resolved through the neighbour tile.
            cached.getCapabilityFromPipe(dir, CapUtil.CAP_FLUIDS);
            cached.getCapabilityFromPipe(dir, plainToken);
        }
        helper.assertFalse(level.isLoaded(farA), "the pipe's neighbour capability lookups loaded the unloaded chunk");

        EntityRobot[] robot = { null };
        ItemEntity[] diamond = { null };
        EntityArenaUtil.tickUntil(helper, UNLOADED_HOME_BUDGET,
                () -> {
                    if (robot[0] == null) {
                        if (!EntityArenaUtil.isEntityTicking(helper, robotRel)) {
                            return false;
                        }
                        helper.assertTrue(neverResolved.isInitialized(),
                                "a station in an unloaded chunk must not read as uninitialized");
                        helper.assertTrue(neverResolved.getHolder() == null,
                                "a never-resolved station in an unloaded chunk has no pipe to answer with");
                        ZonePinnedRobot spawned = new ZonePinnedRobot(helper, BoardRobotPickerNBT.INSTANCE);
                        spawned.getBattery().addPower(SEEDED_CHARGE, false);
                        spawned.setUniqueRobotId(RobotManager.registryProvider.getRegistry(level).getNextRobotId());
                        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
                        spawned.setPos(pos.x, pos.y, pos.z);
                        robot[0] = spawned;
                        helper.assertTrue(home.takeAsMain(spawned),
                                "a station whose chunk is merely unloaded is not gone: the robot takes it as home");
                        level.addFreshEntity(spawned);
                        Vec3 at = Vec3.atCenterOf(helper.absolutePos(itemRel));
                        diamond[0] = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.DIAMOND));
                        diamond[0].setNoGravity(true);
                        level.addFreshEntity(diamond[0]);
                        return false;
                    }
                    return robot[0].containsItems();
                },
                () -> {
                    helper.assertTrue(robot[0].getLinkedStation() == home, "the robot keeps its unloaded home");
                    helper.assertTrue(stationAt(level, farA) == home && stationAt(level, farB) == neverResolved,
                            "stations in unloaded chunks stay registered");
                    helper.assertFalse(level.isLoaded(farA) || level.isLoaded(farB),
                            "nothing about a station may load its chunk");
                    helper.succeed();
                },
                "the robot froze while its home station's chunk was unloaded (it never fetched the diamond)",
                () -> {
                    EntityArenaUtil.discard(robot[0], diamond[0]);
                    RobotManager.registryProvider.getRegistry(level).removeStation(home);
                    RobotManager.registryProvider.getRegistry(level).removeStation(neverResolved);
                });
    }

    /** How long the low robot below must hold out: more than twice the 120-cycle recharge retry, and several times what
     *  its work would take to drain it. */
    private static final int WAIT_FOR_HOME_TICKS = 300;
    private static final int WAIT_FOR_HOME_BUDGET = 360;
    /** Below the safety reserve and small enough that any work drains it within one recharge back-off. */
    private static final long LOW_CHARGE = MjAPI.MJ / 2;

    /** A robot low on charge whose home's chunk is unloaded, and with no other charger in reach. Before a robot could
     *  outlive its home's chunk (a per-tick lookup used to load it), this never arose. Every recharge search skips the
     *  unloaded home, and the robot used to work on between the failed attempts until it was flat, then shut down and
     *  fell where it was, cargo and all. It waits instead, hovering in place at no cost, so it still holds its charge
     *  and its cargo when the home comes back. The robot reads an isolated registry holding only its home, so no
     *  charger elsewhere in the run can serve it. */
    public static void lowRobotWaitsForAnUnloadedHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(2, 3, 2);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);
        BlockPos far = helper.absolutePos(robotRel).offset(UNLOADED_OFFSET, 0, 192);
        helper.assertFalse(level.isLoaded(far), "precondition: the home's chunk is not loaded");

        DockingStationPipe home = unloadedStation(level, far, BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS, null);
        RobotManager.registryProvider.getRegistry(level).registerStation(home);
        MockRobotAccess.InertRobotRegistry onlyHome = new MockRobotAccess.InertRobotRegistry() {
            @Override
            public Collection<DockingStation> getStations() {
                return List.of(home);
            }

            @Override
            public DockingStation getStation(BlockPos pos, Direction side) {
                return home.getPos().equals(pos) && home.side() == side ? home : null;
            }
        };

        EntityRobot[] robot = { null };
        int[] ticks = { 0 };
        EntityArenaUtil.tickUntil(helper, WAIT_FOR_HOME_BUDGET,
                () -> {
                    if (robot[0] == null) {
                        if (!EntityArenaUtil.isEntityTicking(helper, robotRel)) {
                            return false;
                        }
                        ZonePinnedRobot spawned = new ZonePinnedRobot(helper, BoardRobotPickerNBT.INSTANCE)
                                .registry(onlyHome);
                        spawned.getBattery().addPower(LOW_CHARGE, false);
                        spawned.setUniqueRobotId(RobotManager.registryProvider.getRegistry(level).getNextRobotId());
                        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
                        spawned.setPos(pos.x, pos.y, pos.z);
                        spawned.setInventoryStack(0, new ItemStack(Items.COBBLESTONE, 8));
                        helper.assertTrue(home.takeAsMain(spawned), "precondition: the robot is homed there");
                        level.addFreshEntity(spawned);
                        robot[0] = spawned;
                        return false;
                    }
                    helper.assertFalse(robot[0].mainAI.getDelegateAI() instanceof buildcraft.robotics.ai.AIRobotShutdown,
                            "a low robot whose home is merely unloaded shut down in the field after " + ticks[0]
                                    + " ticks instead of waiting for it");
                    return ++ticks[0] >= WAIT_FOR_HOME_TICKS;
                },
                () -> {
                    helper.assertTrue(robot[0].getBattery().getStored() == LOW_CHARGE,
                            "waiting must cost nothing, so the robot still has its charge, has "
                                    + MjAPI.formatMj(robot[0].getBattery().getStored()) + " MJ");
                    helper.assertTrue(ItemStack.matches(robot[0].getInventoryStack(0),
                            new ItemStack(Items.COBBLESTONE, 8)), "...and its cargo");
                    helper.assertTrue(robot[0].getLinkedStation() == home, "...and its home");
                    helper.assertFalse(level.isLoaded(far), "nothing about the wait may load the home's chunk");
                    helper.succeed();
                },
                "the low robot never got through the wait",
                () -> {
                    EntityArenaUtil.discard(robot[0]);
                    RobotManager.registryProvider.getRegistry(level).removeStation(home);
                });
    }

    /** A station in an unloaded chunk is usable but not reachable: a robot cannot fly through unloaded cells, so a
     *  search must never pick one as a destination. When it did, the robot claimed it, failed the flight and picked it
     *  again on its next cycle, forever (a Delivery robot disposed of its cargo every time). Here a robot with cargo
     *  and no zone limit searches for somewhere to unload, and the only station that would take the cargo is in an
     *  unloaded chunk. */
    public static void searchSkipsAStationInAnUnloadedChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos robotRel = new BlockPos(2, 3, 2);
        BlockPos far = helper.absolutePos(robotRel).offset(UNLOADED_OFFSET, 0, 128);
        helper.assertFalse(level.isLoaded(far), "precondition: the far station's chunk is not loaded");

        DockingStationPipe accepting = unloadedStation(level, far, BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS,
                null);
        MockRobotAccess.InertRobotRegistry onlyFar = new MockRobotAccess.InertRobotRegistry() {
            @Override
            public Collection<DockingStation> getStations() {
                return List.of(accepting);
            }
        };
        ZonePinnedRobot robot = new ZonePinnedRobot(helper, null).loadUnloadZone(null).registry(onlyFar);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.setInventoryStack(0, new ItemStack(Items.COBBLESTONE, 8));

        helper.assertTrue(AIRobotUnload.unload(robot, accepting, false),
                "precondition: the unloaded station's cached Accept Items gate would take the cargo");
        AIRobotSearchStation search = new AIRobotSearchStation(robot,
                station -> AIRobotUnload.unload(robot, station, false), null);
        search.start();
        helper.assertTrue(search.targetStation == null,
                "a station in an unloaded chunk cannot be flown to, so the search must not pick it");
        helper.assertFalse(level.isLoaded(far), "the search must not load the station's chunk either");
        helper.succeed();
    }

    /** A station in a chunk that is not loaded, as a chunk unload leaves one that had resolved its pipe: a real wooden
     *  item pipe at {@code farAbs} with the station on top and a gate holding Always On + {@code action} (with
     *  {@code param}, if any) resolved, then {@code setRemoved}. Built without placing anything in the world, so the
     *  chunk stays unloaded. Not registered. */
    public static DockingStationPipe unloadedStation(ServerLevel level, BlockPos farAbs, IStatement action,
                                                      IStatementParameter param) {
        TilePipeHolder tile = new TilePipeHolder(farAbs, BCTransportBlocks.PIPE_HOLDER.get().defaultBlockState());
        tile.setLevel(level);
        CompoundTag gateData = new CompoundTag();
        gateData.put("variant", PARAM_GATE.writeToNBT());
        CompoundTag gatePlug = new CompoundTag();
        gatePlug.put("data", gateData);
        CompoundTag gateEntry = new CompoundTag();
        gateEntry.putString("id", "buildcraftunofficial:gate");
        gateEntry.put("data", gatePlug);
        CompoundTag plugs = new CompoundTag();
        plugs.put("up", parse("{id:\"buildcraftunofficial:robot_station\",data:{}}"));
        plugs.put("north", gateEntry);
        CompoundTag tag = parse("{pipe:{def:\"buildcraftunofficial:wood_item\",beh:{currentDir:\"DOWN\"}}}");
        tag.put("plugs", plugs);
        //? if >=1.21.10 {
        tile.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        //?} else {
        /*tile.loadCustomOnly(tag, level.registryAccess());*/
        //?}
        PluggableGate gate = (PluggableGate) tile.getPluggable(GATE_FACE);
        gate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        gate.logic.statements[0].action.set(ActionWrapper.wrap(action, null));
        if (param != null) {
            gate.logic.statements[0].action.set(0, param);
        }
        gate.logic.resolveActions();
        tile.setRemoved();
        return new DockingStationPipe(tile, STATION_FACE);
    }

    /** An AREA map-location stack covering the relative box {@code minRel..maxRel} (the keys
     *  {@code ItemMapLocation.getAreaBox} reads). */
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

    /** A station whose pipe was cleared to air without the removal side effects (what {@code /fill} and replace-mode
     *  {@code /setblock} do on 1.21.10+) and never rebuilt must not stay registered as a ghost: the first time it is
     *  asked for its pipe in a loaded chunk it deregisters, and the robot homed on it shuts down for want of a station
     *  instead of freezing silently. */
    public static void pipeFilledAwayDeregistersItsStation(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(1, 3, 1);
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(2, 3, 3));
        TilePipeHolder tile = placePipe(helper, pipeRel);
        tile.replacePluggable(STATION_FACE,
                new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, STATION_FACE));

        EntityRobot[] robot = { null };
        DockingStationPipe[] station = { null };
        EntityArenaUtil.tickUntil(helper, FILLED_AWAY_BUDGET,
                () -> {
                    if (robot[0] == null) {
                        station[0] = stationAt(helper, pipeRel);
                        if (station[0] == null) {
                            return false;
                        }
                        robot[0] = placeRobot(helper, station[0], BoardRobotPickerNBT.INSTANCE);
                        ServerLevel level = helper.getLevel();
                        BlockPos abs = helper.absolutePos(pipeRel);
                        level.removeBlockEntity(abs);
                        level.setBlock(abs, Blocks.AIR.defaultBlockState(), 2);
                        helper.assertTrue(stationAt(helper, pipeRel) == station[0],
                                "precondition: the pipe went without its side effects, so its station is still "
                                        + "registered");
                        return false;
                    }
                    return stationAt(helper, pipeRel) == null
                            && robot[0].mainAI.getDelegateAI() instanceof buildcraft.robotics.ai.AIRobotShutdown;
                },
                helper::succeed,
                "a station whose pipe is gone stayed registered, or its robot never shut down",
                () -> cleanUp(helper, robot[0]));
    }

    /** The {@code /fill} pipe-over-pipe case: the pipe under a station is replaced without the removal side effects by a
     *  pipe that carries NO station plug on that face. The new pipe is a pipe, but not this station's: adopting it kept
     *  a gateless ghost registered (searches still chose it, robots docked at it, its power answer came from whatever
     *  flow the new pipe had), where 1.21.1's {@code /fill}, which does run the removal hooks, deregisters it. It must
     *  deregister like a pipe that is gone, shutting its homed robot down. Placing a station plug on the new pipe
     *  afterwards registers a fresh station (a rebuild split across ticks heals). */
    public static void pipeReplacedWithoutAStationDeregistersIt(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(1, 3, 1);
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(2, 3, 3));
        TilePipeHolder tile = placePipe(helper, pipeRel);
        tile.replacePluggable(STATION_FACE,
                new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, STATION_FACE));

        EntityRobot[] robot = { null };
        DockingStationPipe[] station = { null };
        TilePipeHolder[] replacement = { null };
        EntityArenaUtil.tickUntil(helper, FILLED_AWAY_BUDGET,
                () -> {
                    if (robot[0] == null) {
                        station[0] = stationAt(helper, pipeRel);
                        if (station[0] == null) {
                            return false;
                        }
                        robot[0] = placeRobot(helper, station[0], BoardRobotPickerNBT.INSTANCE);
                        ServerLevel level = helper.getLevel();
                        BlockPos abs = helper.absolutePos(pipeRel);
                        level.removeBlockEntity(abs);
                        level.setBlock(abs, Blocks.AIR.defaultBlockState(), 2);
                        replacement[0] = placePipe(helper, pipeRel);
                        helper.assertTrue(stationAt(helper, pipeRel) == station[0],
                                "precondition: the pipe was replaced without its side effects, so its station is "
                                        + "still registered");
                        return false;
                    }
                    if (replacement[0].getPluggable(STATION_FACE) == null) {
                        if (stationAt(helper, pipeRel) != null
                                || !(robot[0].mainAI.getDelegateAI() instanceof buildcraft.robotics.ai.AIRobotShutdown)) {
                            return false;
                        }
                        helper.assertTrue(robot[0].getDockingStation() == null,
                                "the shut-down robot must not stay docked at the deregistered station");
                        replacement[0].replacePluggable(STATION_FACE, new RobotStationPluggable(
                                BCRoboticsPlugs.robotStation, replacement[0], STATION_FACE));
                        return false;
                    }
                    DockingStationPipe fresh = stationAt(helper, pipeRel);
                    return fresh != null && fresh != station[0];
                },
                helper::succeed,
                "a station whose pipe was replaced by a plugless pipe stayed registered (or its robot never shut "
                        + "down), or a station plug on the new pipe never registered a fresh station",
                () -> cleanUp(helper, robot[0]));
    }

    public static void planterApiRig(GameTestHelper helper) {
        planter(helper);
    }

    // ---------- scenarios ----------

    private static void lumberjack(GameTestHelper helper, Rig rig) {
        BlockPos chestRel = new BlockPos(1, 2, 1);
        BlockPos pipeRel = new BlockPos(1, 3, 1);
        BlockPos logRel = new BlockPos(4, 3, 5);
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(2, 3, 3));

        ChestBlockEntity chest = placeChest(helper, chestRel);
        chest.setItem(0, new ItemStack(Items.IRON_AXE));
        chest.setItem(1, new ItemStack(Items.IRON_AXE));
        helper.setBlock(logRel, Blocks.OAK_LOG);

        Rigging rigging = new Rigging(helper, pipeRel, rig, BCRoboticsStatements.ACTION_STATION_PROVIDE_ITEMS);
        EntityRobot[] robot = { null };
        EntityArenaUtil.tickUntil(helper, LUMBERJACK_BUDGET,
                () -> {
                    if (robot[0] == null) {
                        DockingStationPipe station = rigging.ready();
                        if (station == null) {
                            return false;
                        }
                        helper.assertTrue(station.getItemInput() == chest,
                                "the station's wooden pipe must supply the chest it points at, got "
                                        + station.getItemInput());
                        robot[0] = placeRobot(helper, station, BoardRobotLumberjackNBT.INSTANCE);
                        return false;
                    }
                    return !helper.getLevel().getBlockState(helper.absolutePos(logRel)).is(Blocks.OAK_LOG);
                },
                () -> {
                    helper.assertTrue(robot[0].getHeldItem().is(Items.IRON_AXE),
                            "the lumberjack cut the log with an axe from its station, holding "
                                    + robot[0].getHeldItem());
                    helper.assertTrue(chest.getItem(0).isEmpty() != chest.getItem(1).isEmpty(),
                            "exactly one axe left the chest");
                    helper.succeed();
                },
                "the lumberjack never equipped an axe from its gated station and cut the log",
                () -> cleanUp(helper, robot[0]));
    }

    private static void picker(GameTestHelper helper, Rig rig) {
        BlockPos chestRel = new BlockPos(1, 2, 1);
        BlockPos pipeRel = new BlockPos(1, 3, 1);
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(2, 3, 3));

        placeChest(helper, chestRel);
        Rigging rigging = new Rigging(helper, pipeRel, rig, BCRoboticsStatements.ACTION_STATION_ACCEPT_ITEMS);
        EntityRobot[] robot = { null };
        EntityArenaUtil.tickUntil(helper, PICKER_BUDGET,
                () -> {
                    if (robot[0] == null) {
                        DockingStationPipe station = rigging.ready();
                        if (station == null) {
                            return false;
                        }
                        // The cargo the smoke rig's picker came home with (nothing is left on the ground here, so
                        // its first fetch comes up empty and it heads straight for the unload).
                        robot[0] = placeRobot(helper, station, BoardRobotPickerNBT.INSTANCE);
                        robot[0].setInventoryStack(0, new ItemStack(Items.COBBLESTONE, 8));
                        robot[0].setInventoryStack(1, new ItemStack(Items.DIAMOND, 2));
                        return false;
                    }
                    return !robot[0].containsItems();
                },
                helper::succeed,
                "the picker never unloaded its cargo at its Accept Items station",
                () -> cleanUp(helper, robot[0]));
    }

    /** A Planter homed on a Provide Items station over a chest of seeds keeps planting trip after trip until every one
     *  of six farmland cells in its zone carries wheat, taking exactly one seed per trip (7.1.x's {@code takeSingle}).
     *  Measured on 26.2: about 170 ticks a seed, the two 40-tick waits (fetch, plant) plus two short flights. Guards
     *  the class of bug that lets a planter plant once and then starve: a claim not released, a successful planting
     *  remembered as a refusal, a fetch that stops after the first seed. */
    private static void planter(GameTestHelper helper) {
        BlockPos chestRel = new BlockPos(1, 2, 1);
        BlockPos pipeRel = new BlockPos(1, 3, 1);
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(2, 3, 3));

        ChestBlockEntity chest = placeChest(helper, chestRel);
        chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 32));
        List<BlockPos> crops = new ArrayList<>();
        for (int x = 0; x < 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
            // Light for the wheat whatever the arena's sky access.
            helper.setBlock(new BlockPos(x, 2, 6), Blocks.GLOWSTONE);
            crops.add(helper.absolutePos(new BlockPos(x, 2, 4)));
        }

        Rigging rigging = new Rigging(helper, pipeRel, Rig.API, BCRoboticsStatements.ACTION_STATION_PROVIDE_ITEMS);
        EntityRobot[] robot = { null };
        EntityArenaUtil.tickUntil(helper, PLANTER_BUDGET,
                () -> {
                    if (robot[0] == null) {
                        DockingStationPipe station = rigging.ready();
                        if (station == null) {
                            return false;
                        }
                        robot[0] = placeRobot(helper, station, BoardRobotPlanterNBT.INSTANCE);
                        return false;
                    }
                    return planted(helper, crops) == crops.size();
                },
                () -> {
                    helper.assertTrue(chest.getItem(0).getCount() == 32 - crops.size(),
                            "one seed left the chest per planting, chest holds " + chest.getItem(0));
                    helper.succeed();
                },
                "the planter did not plant all six farmland cells in its zone",
                () -> cleanUp(helper, robot[0]));
    }

    private static int planted(GameTestHelper helper, List<BlockPos> crops) {
        int n = 0;
        for (BlockPos crop : crops) {
            if (helper.getLevel().getBlockState(crop).is(Blocks.WHEAT)) {
                n++;
            }
        }
        return n;
    }

    // ---------- the rig ----------

    /** Builds the station rig at {@code pipeRel} and reports when it is ready: the station registered and the gate
     *  resolved, with the station seeing the gate's action. */
    private static final class Rigging {
        private final GameTestHelper helper;
        private final BlockPos pipeRel;
        private final Rig rig;
        private final IStatement action;
        private TilePipeHolder tile;
        private DockingStationPipe orphan;

        Rigging(GameTestHelper helper, BlockPos pipeRel, Rig rig, IStatement action) {
            this.helper = helper;
            this.pipeRel = pipeRel;
            this.rig = rig;
            this.action = action;
            switch (rig) {
                case API -> {
                    tile = placePipe(helper, pipeRel);
                    tile.replacePluggable(STATION_FACE,
                            new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, STATION_FACE));
                    PluggableGate gate = new PluggableGate(BCSiliconPlugs.gate, tile, GATE_FACE, RIG_GATE);
                    gate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
                    gate.logic.statements[0].action.set(ActionWrapper.wrap(action, null));
                    tile.replacePluggable(GATE_FACE, gate);
                }
                case NBT -> tile = loadRigNbt(helper, pipeRel, action);
                case ORPHANED -> {
                    // The previous run's rig: a gateless station on the same pipe.
                    TilePipeHolder previous = placePipe(helper, pipeRel);
                    previous.replacePluggable(STATION_FACE,
                            new RobotStationPluggable(BCRoboticsPlugs.robotStation, previous, STATION_FACE));
                }
            }
        }

        /** The registered, gate-resolved station, or null while the rig is still coming up. */
        DockingStationPipe ready() {
            if (tile == null) {
                // ORPHANED, first phase: wait for the previous station to register, then remove its pipe the way
                // /fill and /setblock do on the modern nodes and build the rig over it.
                orphan = stationAt(helper, pipeRel);
                if (orphan == null) {
                    return null;
                }
                ServerLevel level = helper.getLevel();
                BlockPos abs = helper.absolutePos(pipeRel);
                // Removing the block entity first keeps every node's removal hook (preRemoveSideEffects, or onRemove
                // on 1.21.1) from finding it, exactly as UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS skips it.
                level.removeBlockEntity(abs);
                level.setBlock(abs, Blocks.AIR.defaultBlockState(), 2);
                helper.assertTrue(stationAt(helper, pipeRel) == orphan,
                        "precondition: a pipe removed without its side effects leaves its station registered");
                tile = loadRigNbt(helper, pipeRel, action);
                return null;
            }
            DockingStationPipe station = stationAt(helper, pipeRel);
            if (station == null || !(tile.getPluggable(GATE_FACE) instanceof PluggableGate gate)
                    || gate.logic.getActiveActions().isEmpty()) {
                return null;
            }
            if (rig != Rig.API) {
                helper.assertTrue(gate.logic.variant.equals(RIG_GATE),
                        "the rig NBT's variant {0b,0b,0b} is the clay-brick AND gate, got " + gate.logic.variant);
            }
            helper.assertTrue(gate.logic.getActiveActions().get(0).statement == action,
                    "Always On must fire the rig gate's action " + action.getUniqueTag());
            if (rig == Rig.ORPHANED) {
                helper.assertTrue(station == orphan,
                        "precondition: the new station pluggable adopts the orphaned registration");
            }
            helper.assertTrue(hasActiveAction(station, action),
                    "the station must see the gate on the pipe it sits on (" + action.getUniqueTag()
                            + "), but sees " + station.getActiveActions()
                            + " - it is reading a pipe that is no longer in the world");
            return station;
        }
    }

    // ---------- fixtures ----------

    private static TilePipeHolder pipeTile(GameTestHelper helper, BlockPos relPos) {
        //? if >=1.21.10 {
        return helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*return helper.getBlockEntity(relPos);*/
        //?}
    }

    private static TilePipeHolder placePipe(GameTestHelper helper, BlockPos relPos) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder tile = pipeTile(helper, relPos);
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        return tile;
    }

    private static ChestBlockEntity placeChest(GameTestHelper helper, BlockPos relPos) {
        helper.setBlock(relPos, Blocks.CHEST);
        //? if >=1.21.10 {
        return helper.getBlockEntity(relPos, ChestBlockEntity.class);
        //?} else {
        /*return helper.getBlockEntity(relPos);*/
        //?}
    }

    /** {@code setblock <pos> buildcraftunofficial:pipe_holder} then {@code data merge block <pos> <rig>}: the
     *  command merges the payload into the block entity's full save and loads the result. */
    private static TilePipeHolder loadRigNbt(GameTestHelper helper, BlockPos relPos, IStatement action) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        TilePipeHolder tile = pipeTile(helper, relPos);
        ServerLevel level = helper.getLevel();
        CompoundTag merged = tile.saveWithFullMetadata(level.registryAccess());
        merged.merge(parse(String.format(RIG_NBT, action.getUniqueTag())));
        //? if >=1.21.10 {
        tile.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), merged));
        //?} else {
        /*tile.loadWithComponents(merged, level.registryAccess());*/
        //?}
        tile.setChanged();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        level.sendBlockUpdated(abs, state, state, 3);
        return tile;
    }

    private static CompoundTag parse(String snbt) {
        try {
            //? if >=1.21.10 {
            return TagParser.parseCompoundFully(snbt);
            //?} else {
            /*return TagParser.parseTag(snbt);*/
            //?}
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("the rig NBT does not parse: " + snbt, e);
        }
    }

    private static DockingStationPipe stationAt(GameTestHelper helper, BlockPos relPos) {
        return stationAt(helper.getLevel(), helper.absolutePos(relPos));
    }

    private static DockingStationPipe stationAt(ServerLevel level, BlockPos absPos) {
        DockingStation station = RobotManager.registryProvider.getRegistry(level).getStation(absPos, STATION_FACE);
        return station instanceof DockingStationPipe pipe ? pipe : null;
    }

    private static boolean hasActiveAction(DockingStation station, IStatement action) {
        for (StatementSlot slot : station.getActiveActions()) {
            if (slot.statement == action) {
                return true;
            }
        }
        return false;
    }

    /** Placed the way {@code ItemRobot.useOn} places a robot on a station (registry id first, taken as main, docked,
     *  then added), but zone-pinned to the cell so its searches stay out of the neighbouring arenas. */
    private static EntityRobot placeRobot(GameTestHelper helper, DockingStation station, RedstoneBoardRobotNBT board) {
        ZonePinnedRobot robot = new ZonePinnedRobot(helper, board);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        robot.setUniqueRobotId(RobotManager.registryProvider.getRegistry(helper.getLevel()).getNextRobotId());
        Vec3 pos = Vec3.atCenterOf(station.getPos().relative(station.side()));
        robot.setPos(pos.x, pos.y, pos.z);
        station.takeAsMain(robot);
        robot.dock(robot.getLinkedStation());
        helper.getLevel().addFreshEntity(robot);
        return robot;
    }

    /** The robot, and every drop in the cell (the cut log, cargo the pipe could not route). */
    private static void cleanUp(GameTestHelper helper, EntityRobot robot) {
        EntityArenaUtil.discard(robot);
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                EntityArenaUtil.cellBounds(helper))) {
            item.discard();
        }
    }
}
