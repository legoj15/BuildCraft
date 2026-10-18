/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.StatementSlot;

import buildcraft.core.BCCoreStatements;

import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;
import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.boards.BoardRobotLumberjackNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.boards.BoardRobotPlanterNBT;
import buildcraft.robotics.entity.EntityRobot;
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
 *     side effects. That is what {@code /fill} and {@code /setblock} do on the modern nodes (both place with
 *     {@code UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS}, so {@code preRemoveSideEffects} never runs and the station's
 *     pluggable is never told it is gone), and what the smoke scenario's "clear and floor" step did to the previous
 *     run's rig. The orphaned {@link DockingStationPipe} stays registered, the new station pluggable adopts it, and
 *     it used to keep reading the dead block entity it had cached: neither gate had any effect, so both robots sat
 *     idle at home.</li>
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
                            "the lumberjack cut the log with an axe from its station, holding " + robot[0].getHeldItem());
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
        DockingStation station = RobotManager.registryProvider.getRegistry(helper.getLevel())
                .getStation(helper.absolutePos(relPos), STATION_FACE);
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
