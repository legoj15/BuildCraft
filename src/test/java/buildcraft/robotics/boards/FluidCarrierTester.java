/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?}

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.IStatement;

import buildcraft.core.BCCoreStatements;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileTank;
import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;
import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.BCRoboticsPlugs;
import buildcraft.robotics.BCRoboticsStatements;
import buildcraft.robotics.DockingStationPipe;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.ai.AIRobotUnloadFluids;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.transport.pipe.flow.PipeFlowFluids;
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
 * The fluid carrier board's game tests — the fluid twin of {@link PickerCarrierTester}'s carrier pins.
 * The whole loop a mock cannot prove: a fluid carrier placed into the world autonomously finds the supply
 * station (a station on a WOODEN FLUID pipe pointed at a tank), pulls the tank's water bucket by bucket
 * into its own 4000 mB tank, and does it with no hand-holding beyond a seeded charge.
 *
 * <p>Position discipline is the same as {@code PickerCarrierTester}: every relative position stays inside
 * the 6x7 arena cell, the arena chunks are force-loaded around the robot, and nothing asserts on a fixed
 * tick — each phase is gated on observed state via {@link EntityArenaUtil#tickUntil}.
 */
public class FluidCarrierTester {

    /** Charge above {@code SAFETY_POWER} so the {@code AIRobotMain} ladder neither shuts the robot down nor
     *  sends it recharging mid-task. */
    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    // ---------- fixtures ----------

    private static TilePipeHolder placeFluidPipe(GameTestHelper helper, BlockPos relPos, net.minecraft.world.item.Item pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    private static TilePipeHolder installFluidStation(GameTestHelper helper, BlockPos relPos,
                                                      net.minecraft.world.item.Item pipeItem, Direction side) {
        TilePipeHolder tile = placeFluidPipe(helper, relPos, pipeItem);
        tile.replacePluggable(side, new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, side));
        return tile;
    }

    /** The 1-slot, param-less gate. */
    private static final GateVariant BASIC_GATE =
            new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER);

    /** An always-on gate whose single slot carries {@code action} with no parameters — the D1 way to make
     *  a station's provide policy pass EVERY fluid (7.1.x: an action with an empty filter set matches
     *  anything). Resolved synchronously, before the robot's first search. */
    private static void addAlwaysOnGate(TilePipeHolder tile, Direction side, IStatement action) {
        PluggableGate gate = new PluggableGate(BCSiliconPlugs.gate, tile, side, BASIC_GATE);
        gate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        gate.logic.statements[0].action.set(ActionWrapper.wrap(action, null));
        tile.replacePluggable(side, gate);
        gate.logic.resolveActions();
    }

    /** Spawns a robot carrying {@code boardNBT} at {@code relPos}. The {@code (Level, RedstoneBoardRobotNBT)}
     *  constructor calls {@code setBoard}, which creates and starts the {@code AIRobotMain} — so the robot
     *  begins cycling the moment its first tick registers it with the registry. */
    private static EntityRobot addBoardRobot(GameTestHelper helper, BlockPos relPos, RedstoneBoardRobotNBT boardNBT) {
        EntityRobot robot = new EntityRobot(helper.getLevel(), boardNBT);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        helper.getLevel().addFreshEntity(robot);
        return robot;
    }

    /** The registered station for a pipe face, or null while {@code RobotStationPluggable.onTick()} has not
     *  yet lazily registered it. */
    private static DockingStationPipe stationAt(GameTestHelper helper, BlockPos relPos, Direction side) {
        DockingStation station = RobotManager.registryProvider.getRegistry(helper.getLevel())
                .getStation(helper.absolutePos(relPos), side);
        return station instanceof DockingStationPipe pipe ? pipe : null;
    }

    /** The mB the robot is currently carrying (0 when its tank is empty). */
    private static int carriedBy(EntityRobot robot) {
        //? if >=1.21.10 {
        return robot.getFluidHandler().getAmountAsInt(0);
        //?} else {
        /*net.neoforged.neoforge.fluids.FluidStack carried = robot.getFluidHandler().getFluidInTank(0);
        return carried == null ? 0 : carried.getAmount();*/
        //?}
    }

    private static void fillTank(TileTank tank, FluidStack stack) {
        //? if >=1.21.10 {
        try (Transaction tx = Transaction.open(null)) {
            tank.tank.insert(0, FluidResource.of(stack), stack.getAmount(), tx);
            tx.commit();
        }
        //?} else {
        /*// 1.21.1 has no Transfer API; BCFluidTank exposes a version-neutral fill(slot, stack, simulate).
        tank.tank.fill(0, stack, false);*/
        //?}
    }

    /** Seeds the robot's own 4000 mB tank directly (the E2E fills it through the load path instead). */
    private static void fillRobotTank(EntityRobot robot, FluidStack stack) {
        //? if >=1.21.10 {
        try (Transaction tx = Transaction.open(null)) {
            robot.getFluidHandler().insert(0, FluidResource.of(stack), stack.getAmount(), tx);
            tx.commit();
        }
        //?} else {
        /*robot.getFluidHandler().fill(stack, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);*/
        //?}
    }

    // ---------- supply-station discovery (the fluid twin of the D6 rule) ----------

    /** A station is only a FLUID supply station when its pipe is a wooden FLUID (extraction) pipe, and the
     *  tank it supplies is the one that pipe is pointed at. This is 7.1.x's {@code getFluidInput}, and it
     *  is the load half of the fluid carrier's loop — without it the board could never fill its tank.
     *
     *  <p>Deliberately asserts on {@code getFluidInput()} directly rather than driving a live robot: the
     *  negative case ("the carrier never loads") is unprovable by waiting, since a passing run and a
     *  merely slow one look identical — the same reasoning as the item twin's pin. */
    public static void fluidSupplyStationNeedsAWoodenFluidPipe(GameTestHelper helper) {
        BlockPos plainRel = new BlockPos(1, 3, 1);
        BlockPos plainTankRel = new BlockPos(1, 2, 1);
        BlockPos woodRel = new BlockPos(4, 3, 1);
        BlockPos woodTankRel = new BlockPos(4, 2, 1);

        helper.setBlock(plainTankRel, BCFactoryBlocks.TANK.get());
        installFluidStation(helper, plainRel, BCTransportItems.PIPE_COBBLE_FLUID.get(), Direction.UP);
        helper.setBlock(woodTankRel, BCFactoryBlocks.TANK.get());
        installFluidStation(helper, woodRel, BCTransportItems.PIPE_WOOD_FLUID.get(), Direction.UP);

        // The wooden pipe aims itself on its first server tick (PipeBehaviourDirectional.onTick advances to
        // the first face carrying a TILE connection), so gate on the stations existing AND the wooden one
        // having resolved its tank — never on a fixed tick.
        EntityArenaUtil.tickUntil(helper, 140,
                () -> {
                    DockingStationPipe wood = stationAt(helper, woodRel, Direction.UP);
                    return wood != null && wood.getFluidInput() != null
                            && stationAt(helper, plainRel, Direction.UP) != null;
                },
                () -> {
                    DockingStationPipe plain = stationAt(helper, plainRel, Direction.UP);
                    helper.assertTrue(plain.getFluidInput() == null,
                            "a station on a plain cobblestone FLUID pipe must NOT supply the tank beneath "
                                    + "it — fluid extraction takes a wooden pipe, or every robot station is "
                                    + "a free unpowered pump");

                    DockingStationPipe wood = stationAt(helper, woodRel, Direction.UP);
                    helper.assertTrue(wood.getFluidInput() != null,
                            "a station on a wooden FLUID pipe pointed at a tank must supply that tank");
                    // The input side is the face of the TANK the pipe touches, i.e. the opposite of the
                    // direction searched. The pipe faces DOWN at the tank, so the tank is entered from UP.
                    helper.assertTrue(wood.getFluidInputSide().face == Direction.UP,
                            "the fluid input side must be the tank's own face (UP for a pipe facing DOWN), "
                                    + "not the search direction: got " + wood.getFluidInputSide());
                    helper.succeed();
                },
                "the wooden fluid supply station never resolved the tank below it");
    }

    // ---------- fluid carrier E2E ----------

    /** A charged fluid carrier with no home station must autonomously find a loadable supply station and
     *  pull the tank's contents into its own tank. Exercises search -> goto (fly + dock) ->
     *  {@code AIRobotLoadFluids} through {@code DockingStationPipe.getFluidInput} (the wooden fluid pipe's
     *  discovery) with the D1 work filter ({@code getRobotFluidFilter}) in the loop.
     *
     *  <p>Ph6 (D1): the station's gate must hold an unfiltered provide-fluids action or the policy refuses
     *  the load (gateless stations refuse, as in 7.1.x) — so the rig carries an always-on, unfiltered
     *  {@code ActionStationProvideFluids} gate, which passes everything. The tank holds exactly one bucket,
     *  so the load is a single 40-tick AI pass — the test stays inside its tick budget. */
    public static void fluidCarrierLoadsFromSupplyTank(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 3, 4);
        // One cell below the pipe: the station's input is on the pipe's extraction face = DOWN for the
        // aimed wooden fluid pipe.
        BlockPos tankRel = new BlockPos(2, 2, 4);
        BlockPos robotRel = new BlockPos(4, 3, 4);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        TilePipeHolder tile = installFluidStation(helper, pipeRel, BCTransportItems.PIPE_WOOD_FLUID.get(),
                Direction.UP);
        addAlwaysOnGate(tile, Direction.WEST, BCRoboticsStatements.ACTION_STATION_PROVIDE_FLUIDS);
        helper.setBlock(tankRel, BCFactoryBlocks.TANK.get());
        //? if >=1.21.10 {
        TileTank tank = helper.getBlockEntity(tankRel, TileTank.class);
        //?} else {
        /*TileTank tank = helper.getBlockEntity(tankRel);*/
        //?}
        fillTank(tank, new FluidStack(Fluids.WATER, 1000));

        // Lazy-spawn on the first tick the supply station has registered: the carrier must not search
        // before there is anything to find, or its first search comes up empty and it goes to sleep. The
        // fluid supply discovery makes this test naturally hermetic — no other test's station sits on a
        // wooden FLUID pipe, so this robot's station search can only end at this rig.
        EntityRobot[] robot = { null };
        EntityArenaUtil.tickUntil(helper, 340,
                () -> {
                    if (stationAt(helper, pipeRel, Direction.UP) == null) {
                        return false;
                    }
                    if (robot[0] == null) {
                        robot[0] = addBoardRobot(helper, robotRel, BoardRobotFluidCarrierNBT.INSTANCE);
                        robot[0].getBattery().addPower(SEEDED_CHARGE, false);
                    }
                    return carriedBy(robot[0]) > 0;
                },
                () -> {
                    helper.assertTrue(carriedBy(robot[0]) == 1000,
                            "the fluid carrier must load the tank's whole bucket into its own tank, got "
                                    + carriedBy(robot[0]) + " mB");
                    // Same false-positive guard as the item carrier test: the load reads the tank from the
                    // STATION's position, so a regression that docks the robot without flying would load
                    // fine while the robot sat at its spawn. A genuinely docked robot is snapped to the
                    // station's face centre every tick.
                    net.minecraft.world.phys.Vec3 faceCentre =
                            net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(pipeRel)).add(0, 0.5, 0);
                    helper.assertTrue(robot[0].position().distanceToSqr(faceCentre) < 2.25,
                            "the fluid carrier must FLY to and dock at the supply station, not load from "
                                    + "its spawn: robot at " + robot[0].position() + " but station face at "
                                    + faceCentre);
                    robot[0].discard();
                    helper.succeed();
                },
                "the fluid carrier robot never loaded the water from the supply tank");
    }

    // ---------- dead-end unload (the fluid twin of the item unload pin) ----------

    /** The fluid unload must NOT require the pipe to have a connection on the face opposite the station —
     *  a lone dead-end fluid pipe is a perfectly good unload target, exactly as the item side already
     *  guarantees ({@code PickerCarrierTester.unloadStationDoesNotNeedAnOppositeFaceConnection}).
     *
     *  <p>The item side needed a synthetic station-side {@code IInjectable} to get there, because the raw
     *  {@code PipeFlowItems.canInjectItems} is {@code pipe.isConnected(from)}. The fluid side reaches the
     *  pipe's per-face {@code Section} instead, which has no such gate — this test is the pin that says so
     *  and keeps it that way.
     *
     *  <p>Ph6 (D1): a gateless station refuses, so the rig carries an always-on, unfiltered
     *  {@code ActionStationAcceptFluids} gate. */
    public static void fluidUnloadStationDoesNotNeedAnOppositeFaceConnection(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 3, 4);
        BlockPos robotRel = new BlockPos(2, 4, 4);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        // A plain cobblestone FLUID pipe with nothing at all around it: no tank, no neighbouring pipe, and
        // in particular nothing on the DOWN face opposite the station.
        TilePipeHolder tile = installFluidStation(helper, pipeRel, BCTransportItems.PIPE_COBBLE_FLUID.get(),
                Direction.UP);
        addAlwaysOnGate(tile, Direction.WEST, BCRoboticsStatements.ACTION_STATION_ACCEPT_FLUIDS);

        EntityArenaUtil.tickUntil(helper, 140,
                () -> stationAt(helper, pipeRel, Direction.UP) != null,
                () -> {
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    EntityRobot robot = addBoardRobot(helper, robotRel, BoardRobotEmptyNBT.INSTANCE);
                    station.takeAsMain(robot);
                    robot.dock(station);
                    fillRobotTank(robot, new FluidStack(Fluids.WATER, 1000));
                    helper.assertTrue(carriedBy(robot) == 1000, "precondition: the robot carries a bucket");

                    helper.assertTrue(station.getFluidOutput() != null,
                            "a station on a fluid pipe must expose a fluid output at a dead end — with no "
                                    + "output there is nowhere for a pump or tank robot to unload");

                    int dryRun = AIRobotUnloadFluids.unload(robot, station, false);
                    helper.assertTrue(dryRun > 0,
                            "the unload dry-run must accept a dead-end fluid pipe (the station search "
                                    + "uses it to decide whether a station is worth flying to)");
                    helper.assertTrue(carriedBy(robot) == 1000,
                            "a dry run must move nothing");

                    int moved = AIRobotUnloadFluids.unload(robot, station, true);
                    helper.assertTrue(moved > 0,
                            "a docked robot must be able to unload fluid into a dead-end pipe");
                    helper.assertTrue(carriedBy(robot) == 1000 - moved,
                            "the unload must actually drain the robot's tank, not just report success: "
                                    + "moved " + moved + " but the robot still holds " + carriedBy(robot));

                    PipeFlowFluids flow = (PipeFlowFluids) tile.getPipe().getFlow();
                    helper.assertTrue(flow.doesContainFluid(),
                            "the fluid must have landed in the pipe");

                    robot.discard();
                    helper.succeed();
                },
                "the station never registered");
    }
}
