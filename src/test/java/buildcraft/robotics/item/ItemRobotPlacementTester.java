/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;

import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.events.RobotEvent;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.RobotManager;

import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.BCRoboticsPlugs;
import buildcraft.robotics.DockingStationPipe;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Ph3 game tests for {@link ItemRobot#useOn}: the one and only way a robot enters the world in survival.
 *
 * <p>The end-to-end contract is deliberately fussy, because every step of it is load-bearing — the clicked
 * FACE has to carry an untaken {@code RobotStationPluggable}, the cancellable {@code RobotEvent.Place} has to
 * be posted before anything is committed, the robot has to take the station as its MAIN station (so it is
 * linked, not merely reserved) and dock at the face centre, and the stack has to be consumed for a
 * non-creative player.
 *
 * <p>Deliberately NOT ported from 7.1.x: the empty-board placement rejection. Ph3 ships only the empty board,
 * so copying that guard would make the item unplaceable and nothing here could ever pass — see
 * {@link #emptyBoardRobotStillPlaces}.
 *
 * <p>Known limitation: {@code GameTestHelper.makeMockPlayer} returns a plain {@code Player}, not a
 * {@code ServerPlayer}, so anything the placement path gates on {@code instanceof ServerPlayer} (advancement
 * grants, stat increments) is invisible here. Everything asserted below is world state, which is observable.
 */
public class ItemRobotPlacementTester {

    /** Set while a test wants {@code RobotEvent.Place} vetoed. Belt and braces: even if the listener were
     *  somehow left registered after the test, it would be inert. */
    private static volatile boolean vetoPlacement = false;

    // ---------- fixtures ----------

    private static void installStation(GameTestHelper helper, BlockPos relPos, Direction side) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        tile.replacePluggable(side, new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, side));
    }

    private static DockingStationPipe stationAt(GameTestHelper helper, BlockPos relPos, Direction side) {
        DockingStation station = RobotManager.registryProvider.getRegistry(helper.getLevel())
                .getStation(helper.absolutePos(relPos), side);
        return station instanceof DockingStationPipe pipe ? pipe : null;
    }

    private static List<EntityRobot> robotsNear(GameTestHelper helper, BlockPos relPos, double radius) {
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(relPos));
        return helper.getLevel().getEntitiesOfClass(EntityRobot.class,
                AABB.ofSize(centre, radius * 2, radius * 2, radius * 2));
    }

    /** The face centre a placed robot must land on — block centre pushed half a block out along the clicked
     *  face, the same math the tick-time docking snap uses. */
    private static Vec3 faceCentre(GameTestHelper helper, BlockPos relPos, Direction side) {
        BlockPos abs = helper.absolutePos(relPos);
        return new Vec3(
                abs.getX() + 0.5 + side.getStepX() * 0.5,
                abs.getY() + 0.5 + side.getStepY() * 0.5,
                abs.getZ() + 0.5 + side.getStepZ() * 0.5);
    }

    /** Right-clicks {@code side} of the pipe at {@code relPos} with {@code stack} in the player's main hand. */
    private static Player clickStationWith(GameTestHelper helper, BlockPos relPos, Direction side,
                                           ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(relPos);
        Vec3 hit = faceCentre(helper, relPos, side);
        BlockHitResult hitResult = new BlockHitResult(hit, side, abs, false);
        BCRoboticsItems.ROBOT.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hitResult));
        return player;
    }

    // ---------- placement ----------

    /** The happy path: a charged robot stack used on a free station spawns exactly one robot, links AND docks
     *  it at the face centre with the stack's charge intact, and consumes the item. */
    public static void robotItemPlacesDockedRobotOnFreeStation(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(7, 2, 1);
        installStation(helper, pipeRel, Direction.UP);

        helper.runAfterDelay(5, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            helper.assertTrue(station != null, "precondition: the station registered");
            helper.assertFalse(station.isTaken(), "precondition: the station starts free");

            RedstoneBoardRobotNBT board = RedstoneBoardRegistry.instance.getEmptyRobotBoard();
            helper.assertTrue(board != null,
                    "Ph3 must register an empty robot board — the robot's skin, its creative-tab entry and "
                            + "every board-id round trip resolve through it");
            long charge = 3000L * MjAPI.MJ;
            ItemStack stack = ItemRobot.createRobotStack(board.getID(), charge);
            helper.assertFalse(stack.isEmpty(),
                    "createRobotStack must build a real robot stack carrying the board id and charge");

            Player player = clickStationWith(helper, pipeRel, Direction.UP, stack);

            List<EntityRobot> placed = robotsNear(helper, pipeRel, 2.0);
            helper.assertTrue(placed.size() == 1,
                    "clicking a free station with a robot item must spawn exactly one robot, found "
                            + placed.size());
            EntityRobot robot = placed.get(0);

            Vec3 expected = faceCentre(helper, pipeRel, Direction.UP);
            helper.assertTrue(robot.position().distanceToSqr(expected) < 1.0E-6,
                    "a placed robot must land on the clicked face's centre: expected " + expected
                            + " but was " + robot.position());
            helper.assertTrue(robot.getRobotId() != EntityRobotBase.NULL_ROBOT_ID,
                    "a placed robot must be handed an id from the registry before it is added to the world");
            helper.assertTrue(station.isTaken(), "placement must claim the station");
            helper.assertTrue(station.isMainStation(),
                    "placement must takeAsMain, not merely take — a plain reservation leaves the robot with "
                            + "no linked station and it shuts down on its next tick");
            helper.assertTrue(robot.getLinkedStation() == station,
                    "the placed robot's linked station must be the one clicked");
            helper.assertTrue(robot.getDockingStation() == station,
                    "the placed robot must arrive already docked, not merely linked");
            helper.assertTrue(robot.getBattery().getStored() == charge,
                    "the placed robot must carry the stack's charge: expected " + charge + " got "
                            + robot.getBattery().getStored());
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                    "a survival player must have the robot item consumed by a successful placement");
            helper.succeed();
        });
    }

    /** A station already claimed by another robot refuses the placement outright: no second robot, and the
     *  player keeps the item. */
    public static void robotItemRejectedWhenStationAlreadyTaken(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(7, 2, 3);
        installStation(helper, pipeRel, Direction.UP);

        EntityRobot squatter = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 squatterPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(7, 4, 3)));
        squatter.setPos(squatterPos.x, squatterPos.y, squatterPos.z);
        helper.getLevel().addFreshEntity(squatter);

        helper.runAfterDelay(5, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            helper.assertTrue(station != null, "precondition: the station registered");
            helper.assertTrue(squatter.getRobotId() != EntityRobotBase.NULL_ROBOT_ID,
                    "precondition: the squatting robot registered and holds a real id");
            helper.assertTrue(station.takeAsMain(squatter), "precondition: the squatter claims the station");
        });

        helper.runAfterDelay(9, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            ItemStack stack = new ItemStack(BCRoboticsItems.ROBOT.get());
            Player player = clickStationWith(helper, pipeRel, Direction.UP, stack);

            helper.assertTrue(robotsNear(helper, pipeRel, 3.0).size() == 1,
                    "a taken station must not accept a second robot — only the squatter may remain");
            helper.assertTrue(station.robotIdTaking() == squatter.getRobotId(),
                    "the original claimant must keep the station");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                    "a rejected placement must not consume the item");
            helper.succeed();
        });
    }

    /** {@code RobotEvent.Place} must be posted BEFORE anything is committed, and cancelling it must leave no
     *  trace: no robot, a free station and the item still in hand. Asserting the listener actually fired is
     *  what stops this passing vacuously against a {@code useOn} that does nothing at all. */
    public static void robotItemPlacementIsCancellableViaRobotEventPlace(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(7, 2, 5);
        installStation(helper, pipeRel, Direction.UP);

        helper.runAfterDelay(5, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            helper.assertTrue(station != null, "precondition: the station registered");

            boolean[] posted = { false };
            Consumer<RobotEvent.Place> veto = event -> {
                if (vetoPlacement) {
                    posted[0] = true;
                    event.setCanceled(true);
                }
            };
            ItemStack stack = new ItemStack(BCRoboticsItems.ROBOT.get());
            Player player;
            vetoPlacement = true;
            NeoForge.EVENT_BUS.addListener(RobotEvent.Place.class, veto);
            try {
                player = clickStationWith(helper, pipeRel, Direction.UP, stack);
            } finally {
                NeoForge.EVENT_BUS.unregister(veto);
                vetoPlacement = false;
            }

            helper.assertTrue(posted[0],
                    "ItemRobot.useOn must post the cancellable RobotEvent.Place before it commits anything — "
                            + "without it an addon has no way to veto a robot placement");
            helper.assertTrue(robotsNear(helper, pipeRel, 2.0).isEmpty(),
                    "a cancelled RobotEvent.Place must leave no robot in the world");
            helper.assertFalse(station.isTaken(),
                    "a cancelled placement must not leave the station reserved");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                    "a cancelled placement must not consume the item");
            helper.succeed();
        });
    }

    /** A bare robot stack — no CUSTOM_DATA blob at all, i.e. the empty board at zero charge — still places.
     *  7.1.x refused exactly this case; Ph3 has no other board, so keeping that guard would make the item
     *  permanently unplaceable. An empty-board robot places, docks and idles: that is the Ph3 MVP. */
    public static void emptyBoardRobotStillPlaces(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(7, 2, 7);
        installStation(helper, pipeRel, Direction.UP);

        helper.runAfterDelay(5, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            helper.assertTrue(station != null, "precondition: the station registered");

            ItemStack bare = new ItemStack(BCRoboticsItems.ROBOT.get());
            helper.assertTrue(ItemRobot.getEnergy(bare) == 0,
                    "a bare robot stack reads as zero charge, not as a malformed stack");
            Player player = clickStationWith(helper, pipeRel, Direction.UP, bare);

            List<EntityRobot> placed = robotsNear(helper, pipeRel, 2.0);
            helper.assertTrue(placed.size() == 1,
                    "an empty-board robot must PLACE — the 7.1.x empty-board rejection is deliberately "
                            + "dropped in Ph3, found " + placed.size() + " robots");
            helper.assertTrue(placed.get(0).getDockingStation() == station,
                    "an empty-board robot docks like any other");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                    "a successful empty-board placement still consumes the item");
            helper.succeed();
        });
    }
}
