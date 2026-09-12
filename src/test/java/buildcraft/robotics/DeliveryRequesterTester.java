/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.IRequestProvider;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.ResourceIdRequest;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;

import buildcraft.core.BCCoreStatements;
import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;
import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.boards.BoardRobotDeliveryNBT;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.robotics.statements.StatementParameterItemStackExact;
import buildcraft.robotics.tile.TileRequester;
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
 * Ph8 game tests for the request network: the Requester block's discovery and arithmetic against live
 * tiles, the gate-driven virtual provider, and the full delivery loop a mock cannot prove — a delivery
 * robot autonomously taking an order, loading at a supply station, and handing the goods to the Requester.
 *
 * <p>Position discipline per {@code EntityRobotTester} (6x7 arena cell, x≤5/z≤6, no fixed-tick asserts),
 * and the E2E runs in its own private environment because the order search is global.
 */
public class DeliveryRequesterTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    /** The 1-slot, param-less gate. */
    private static final GateVariant BASIC_GATE =
            new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER);

    /** The 1-slot gate with action parameters — what the request-items action needs to say WHAT it wants. */
    private static final GateVariant PARAM_GATE =
            new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.QUARTZ);

    // ---------- fixtures ----------

    private static TilePipeHolder placeItemPipe(GameTestHelper helper, BlockPos relPos, net.minecraft.world.item.Item pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    private static TilePipeHolder installStation(GameTestHelper helper, BlockPos relPos, Direction side,
            net.minecraft.world.item.Item pipeItem) {
        TilePipeHolder tile = placeItemPipe(helper, relPos, pipeItem);
        tile.replacePluggable(side, new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, side));
        return tile;
    }

    private static void addAlwaysOnGate(TilePipeHolder tile, Direction side, IStatement action,
            IStatementParameter[] params) {
        PluggableGate gate = new PluggableGate(BCSiliconPlugs.gate, tile, side,
                params == null ? BASIC_GATE : PARAM_GATE);
        gate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        gate.logic.statements[0].action.set(ActionWrapper.wrap(action, null));
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                gate.logic.statements[0].action.set(i, params[i]);
            }
        }
        tile.replacePluggable(side, gate);
        gate.logic.resolveActions();
    }

    private static DockingStationPipe stationAt(GameTestHelper helper, BlockPos relPos, Direction side) {
        IRobotRegistry registry = RobotManager.registryProvider.getRegistry(helper.getLevel());
        return registry.getStation(helper.absolutePos(relPos), side) instanceof DockingStationPipe pipe
                ? pipe : null;
    }

    private static TileRequester getRequester(GameTestHelper helper, BlockPos relPos) {
        //? if >=1.21.10 {
        return helper.getBlockEntity(relPos, TileRequester.class);
        //?} else {
        /*return helper.getBlockEntity(relPos);*/
        //?}
    }

    private static EntityRobot addDeliveryRobot(GameTestHelper helper, BlockPos relPos) {
        EntityRobot robot = new EntityRobot(helper.getLevel(), BoardRobotDeliveryNBT.INSTANCE);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        helper.getLevel().addFreshEntity(robot);
        return robot;
    }

    // ---------- discovery ----------

    /** The six-neighbour scan: a station's provider is the Requester block beside its HOST PIPE, wherever
     *  the station's own face points. A station with no Requester neighbour falls back to itself — the
     *  virtual provider (7.1.x returned {@code this}, never null). */
    public static void stationFindsRequesterThroughNeighbourScan(GameTestHelper helper) {
        BlockPos scanPipeRel = new BlockPos(4, 3, 4);
        BlockPos requesterRel = new BlockPos(5, 3, 4);
        BlockPos plainPipeRel = new BlockPos(1, 3, 4);

        helper.setBlock(requesterRel, BCRoboticsBlocks.REQUESTER.get());
        TileRequester requester = getRequester(helper, requesterRel);
        requester.setRequest(0, new ItemStack(Items.DIAMOND, 5));

        installStation(helper, scanPipeRel, Direction.UP, BCTransportItems.PIPE_COBBLE_ITEM.get());
        installStation(helper, plainPipeRel, Direction.UP, BCTransportItems.PIPE_COBBLE_ITEM.get());

        EntityArenaUtil.tickUntil(helper, 120,
                () -> stationAt(helper, scanPipeRel, Direction.UP) != null
                        && stationAt(helper, plainPipeRel, Direction.UP) != null,
                () -> {
                    DockingStationPipe scanning = stationAt(helper, scanPipeRel, Direction.UP);
                    IRequestProvider provider = scanning.getRequestProvider();
                    helper.assertTrue(provider == requester,
                            "the station's provider must be the Requester block beside its pipe, not " + provider);
                    helper.assertTrue(
                            ItemStack.matches(new ItemStack(Items.DIAMOND, 5), provider.getRequest(0)),
                            "the provider surfaces the requester's open request");

                    DockingStationPipe plain = stationAt(helper, plainPipeRel, Direction.UP);
                    helper.assertTrue(plain.getRequestProvider() == plain,
                            "with no Requester neighbour the station is its own (virtual) provider — 7.1.x"
                                    + " returned this, never null");
                    for (int slot = 0; slot < plain.getRequestProvider().getRequestsCount(); slot++) {
                        helper.assertTrue(plain.getRequestProvider().getRequest(slot).isEmpty(),
                                "an ungated station's virtual provider requests nothing");
                    }
                    helper.succeed();
                },
                "the stations never registered");
    }

    // ---------- virtual provider (gate Request Items actions) ----------

    /** A gate holding {@code Request Needed Items} with an item parameter turns its station into a virtual
     *  requester for exactly that stack — the Ph6 statement's one real effect, read back through the same
     *  encoding 7.1.x used (side/action/param bits over 127 slots). */
    public static void gateRequestItemsActionMakesTheStationAProvider(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 3, 4);
        TilePipeHolder tile = installStation(helper, pipeRel, Direction.UP, BCTransportItems.PIPE_COBBLE_ITEM.get());

        addAlwaysOnGate(tile, Direction.WEST, BCRoboticsStatements.ACTION_STATION_REQUEST_ITEMS,
                new IStatementParameter[] {
                        new StatementParameterItemStackExact(new ItemStack(Items.DIAMOND, 3), 4)
                });

        EntityArenaUtil.tickUntil(helper, 120,
                () -> stationAt(helper, pipeRel, Direction.UP) != null,
                () -> {
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    IRequestProvider provider = station.getRequestProvider();
                    helper.assertTrue(provider == station,
                            "no Requester block neighbours this pipe — the provider is the station itself");

                    int found = -1;
                    for (int slot = 0; slot < provider.getRequestsCount(); slot++) {
                        ItemStack request = provider.getRequest(slot);
                        if (!request.isEmpty()) {
                            helper.assertTrue(found == -1,
                                    "exactly one virtual request should exist, found another at slot " + slot);
                            helper.assertTrue(
                                    ItemStack.matches(new ItemStack(Items.DIAMOND, 3), request),
                                    "the virtual request is the gate action's parameter stack, got " + request);
                            found = slot;
                        }
                    }
                    helper.assertTrue(found != -1,
                            "the Request Items gate action must surface a request through the provider");
                    helper.succeed();
                },
                "the station never registered");
    }

    // ---------- insertion policy ----------

    /** The delivery slots only accept the item their template asks for — 7.1.x {@code isItemValidForSlot},
     *  now the handler's insertion checker (pipes, hoppers and players all go through it). */
    public static void requesterAcceptsOnlyTemplatedItems(GameTestHelper helper) {
        BlockPos requesterRel = new BlockPos(2, 3, 4);
        helper.setBlock(requesterRel, BCRoboticsBlocks.REQUESTER.get());
        TileRequester requester = getRequester(helper, requesterRel);
        requester.setRequest(0, new ItemStack(Items.DIAMOND, 5));

        helper.assertTrue(requester.invDeliveries.canSet(0, new ItemStack(Items.DIAMOND, 1)),
                "the templated item is insertable");
        helper.assertFalse(requester.invDeliveries.canSet(0, new ItemStack(Items.DIRT, 1)),
                "a different item is refused even into an open request slot");
        helper.assertFalse(requester.invDeliveries.canSet(1, new ItemStack(Items.DIAMOND, 1)),
                "a slot with no template accepts nothing");

        // And the pair arithmetic the robots drive:
        requester.invDeliveries.setStackInSlot(0, new ItemStack(Items.DIAMOND, 2));
        ItemStack leftover = requester.offerItem(0, new ItemStack(Items.DIAMOND, 4));
        helper.assertTrue(leftover.getCount() == 1, "2 + 4 against a request of 5 returns 1");
        helper.assertTrue(requester.invDeliveries.getStackInSlot(0).getCount() == 5,
                "the slot tops out at the requested count");
        helper.assertTrue(requester.isFulfilled(0), "5 of 5 delivered is fulfilled");
        helper.succeed();
    }

    // ---------- delivery E2E ----------

    /** The full Ph8 loop against a live {@link EntityRobot}: the Requester beside a pipe-station asks for
     *  5 diamonds; the delivery robot finds the order, loads at a wooden-pipe supply chest (gate-gated,
     *  D1), flies to the ordering station and hands the goods over. The order is released afterwards. */
    public static void deliveryRobotFulfilsARequesterOrder(GameTestHelper helper) {
        BlockPos supplyChestRel = new BlockPos(1, 2, 4);
        BlockPos supplyPipeRel = new BlockPos(1, 3, 4);
        BlockPos orderPipeRel = new BlockPos(4, 3, 4);
        BlockPos requesterRel = new BlockPos(5, 3, 4);
        BlockPos robotRel = new BlockPos(2, 4, 4);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        // The supply side: wooden pipe over a chest, provide-items gate so the load policy passes (D1).
        TilePipeHolder supplyPipe = installStation(helper, supplyPipeRel, Direction.UP,
                BCTransportItems.PIPE_WOOD_ITEM.get());
        helper.setBlock(supplyChestRel, Blocks.CHEST);
        //? if >=1.21.10 {
        ChestBlockEntity chest = helper.getBlockEntity(supplyChestRel, ChestBlockEntity.class);
        //?} else {
        /*ChestBlockEntity chest = helper.getBlockEntity(supplyChestRel);*/
        //?}
        chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
        addAlwaysOnGate(supplyPipe, Direction.WEST, BCRoboticsStatements.ACTION_STATION_PROVIDE_ITEMS, null);

        // The order side: ANY pipe works (the provider is found by block scan, not flow) + the Requester.
        installStation(helper, orderPipeRel, Direction.UP, BCTransportItems.PIPE_COBBLE_ITEM.get());
        helper.setBlock(requesterRel, BCRoboticsBlocks.REQUESTER.get());
        TileRequester requester = getRequester(helper, requesterRel);
        requester.setRequest(0, new ItemStack(Items.DIAMOND, 5));

        EntityRobot[] robot = { null };
        EntityArenaUtil.tickUntil(helper, 360,
                () -> {
                    if (stationAt(helper, orderPipeRel, Direction.UP) == null
                            || stationAt(helper, supplyPipeRel, Direction.UP) == null) {
                        return false;
                    }
                    if (robot[0] == null) {
                        robot[0] = addDeliveryRobot(helper, robotRel);
                        return false;
                    }
                    return requester.invDeliveries.getStackInSlot(0).getCount() == 5;
                },
                () -> {
                    helper.assertTrue(
                            ItemStack.matches(new ItemStack(Items.DIAMOND, 5),
                                    requester.invDeliveries.getStackInSlot(0)),
                            "the requester must hold exactly its requested 5 diamonds");
                    helper.assertTrue(requester.isFulfilled(0), "the order is fulfilled");
                    helper.assertTrue(robot[0].getInventoryStack(0).isEmpty()
                            && robot[0].getInventoryStack(1).isEmpty()
                            && robot[0].getInventoryStack(2).isEmpty()
                            && robot[0].getInventoryStack(3).isEmpty(),
                            "the robot handed over everything it loaded");

                    // The reservation was released after delivery: the order slot is free for another robot.
                    DockingStationPipe orderStation = stationAt(helper, orderPipeRel, Direction.UP);
                    IRobotRegistry registry = RobotManager.registryProvider.getRegistry(helper.getLevel());
                    helper.assertFalse(
                            registry.isTaken(new ResourceIdRequest(orderStation, 0)),
                            "a delivered request must release its reservation");
                    // The board itself must survive the whole loop with its NBT identity intact.
                    helper.assertTrue(
                            robot[0].getBoard() instanceof buildcraft.robotics.boards.BoardRobotDelivery,
                            "the delivery board still drives the robot after the loop");
                    robot[0].discard();
                    helper.succeed();
                },
                "the delivery robot never fulfilled the requester's order");
    }

    /** The saved request survives the robot's save/load path — the by-value station link resolving through
     *  the LIVE registry. Writes the deliver AI's NBT with a real station, reloads it, and checks the
     *  re-resolved station and requester. */
    public static void stackRequestNbtRoundTripsAgainstALiveRegistry(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(4, 3, 4);
        BlockPos requesterRel = new BlockPos(5, 3, 4);

        installStation(helper, pipeRel, Direction.UP, BCTransportItems.PIPE_COBBLE_ITEM.get());
        helper.setBlock(requesterRel, BCRoboticsBlocks.REQUESTER.get());
        TileRequester requester = getRequester(helper, requesterRel);
        requester.setRequest(2, new ItemStack(Items.IRON_INGOT, 17));

        EntityArenaUtil.tickUntil(helper, 120,
                () -> stationAt(helper, pipeRel, Direction.UP) != null,
                () -> {
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    StackRequest original = new StackRequest(requester, 2, new ItemStack(Items.IRON_INGOT, 17));
                    original.setStation(station);

                    net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
                    original.writeToNBT(nbt);
                    StackRequest loaded = StackRequest.loadFromNBT(nbt);

                    helper.assertTrue(loaded != null, "the request must reload");
                    helper.assertTrue(loaded.getSlot() == 2, "the provider slot round-trips");
                    helper.assertTrue(ItemStack.matches(new ItemStack(Items.IRON_INGOT, 17), loaded.getStack()),
                            "the owed stack round-trips");
                    helper.assertTrue(loaded.getStation(helper.getLevel()) == station,
                            "the station re-resolves from the live registry");
                    helper.assertTrue(loaded.getRequester(helper.getLevel()) == requester,
                            "the requester re-resolves through the re-resolved station");
                    helper.assertTrue(
                            new ResourceIdRequest(station, 2).equals(loaded.getResourceId(helper.getLevel())),
                            "the reservation id round-trips");
                    helper.succeed();
                },
                "the station never registered");
    }
}
