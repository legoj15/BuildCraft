/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;

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
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
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
 * non-creative player. A blank (empty-board) robot is refused before any of that, exactly as 7.1.x refused it,
 * with an action-bar "Not programmed" message on top — see {@link #blankRobotRefusesPlacement}. Every other
 * test here therefore places a PROGRAMMED robot ({@link #programmedRobot}), or it would be exercising the
 * refusal instead of the path it names.
 *
 * <p><b>Arena discipline, same as {@code RobotStationPluggableTester} and {@code EntityRobotTester}.</b> The
 * framework spaces arenas 6 blocks apart in X and 8 in Z, so every relative position here stays inside that
 * cell (x 3, z 1..7). These tests used to build at x=7, i.e. one block into the NEXT test's arena -- which
 * both risked overwriting a neighbour's blocks outright and pushed the pipe and the robot into a chunk this
 * test never force-loads the way it force-loads its own. And because force-loading only makes a chunk tick
 * <em>eventually</em> ({@link EntityArenaUtil#forceLoadEntityArena}), every phase below is gated on the state
 * it needs -- the station being registered, the robot having ticked at least once -- never on a fixed tick.
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

    /** A robot stack carrying a real (non-empty) board — the only kind that places. The Picker is arbitrary:
     *  every placed robot is discarded in the tick it appears, before its board ever acts. */
    private static ItemStack programmedRobot(long charge) {
        return ItemRobot.createRobotStack(BoardRobotPickerNBT.ID, charge);
    }

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

    /** Failure-message detail for an unexpected robot count: each robot's position, registry id, board and
     *  docking state, so a stray leaked in from a neighbouring arena (failed tests skip the framework's
     *  entity cleanup, and the {@code minecraft:empty} arena bounds only cover the origin block) names
     *  itself instead of just inflating the count. */
    private static String describe(List<EntityRobot> robots) {
        StringBuilder sb = new StringBuilder(" [");
        for (int i = 0; i < robots.size(); i++) {
            EntityRobot robot = robots.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(robot.position())
                    .append(" id=").append(robot.getRobotId())
                    .append(" board=").append(robot.getBoard() == null ? "none"
                            : robot.getBoard().getNBTHandler().getID())
                    .append(robot.getDockingStation() != null ? " docked" : " undocked")
                    .append(robot.isRemoved() ? " removed" : "");
        }
        return sb.append(']').toString();
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

    /** Runs {@code body} on the first tick at which the pipe's UP face has a registered
     *  {@link DockingStationPipe}, rather than at a hard-coded tick -- the station is only ever registered
     *  from {@code RobotStationPluggable.onTick()}, and the arena's chunk starts block-ticking a variable
     *  number of ticks after {@link EntityArenaUtil#forceLoadEntityArena} asks it to. */
    private static void whenStationRegistered(GameTestHelper helper, BlockPos pipeRel, Runnable body) {
        EntityArenaUtil.tickUntil(helper, 40, () -> stationAt(helper, pipeRel, Direction.UP) != null, body,
                "RobotStationPluggable.onTick() never registered a DockingStation for the UP face of "
                        + pipeRel + " -- its pipe's chunk never started block-ticking");
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
        BlockPos pipeRel = new BlockPos(3, 2, 1);
        // Centre on the pipe: the station and the robot placed on it both sit in this chunk.
        EntityArenaUtil.forceLoadEntityArena(helper, pipeRel);
        installStation(helper, pipeRel, Direction.UP);

        whenStationRegistered(helper, pipeRel, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            helper.assertFalse(station.isTaken(), "precondition: the station starts free");

            RedstoneBoardRobotNBT board = RedstoneBoardRegistry.instance.getEmptyRobotBoard();
            helper.assertTrue(board != null,
                    "Ph3 must register an empty robot board — the robot's skin, its creative-tab entry and "
                            + "every board-id round trip resolve through it");
            long charge = 3000L * MjAPI.MJ;
            ItemStack stack = programmedRobot(charge);
            helper.assertFalse(stack.isEmpty(),
                    "createRobotStack must build a real robot stack carrying the board id and charge");
            // A programmed robot stacks to 1, so a pair is unreachable in play — it is here only so "the
            // placement consumes EXACTLY one" is observable rather than just "the hand ends up empty".
            stack.setCount(2);

            Player player = clickStationWith(helper, pipeRel, Direction.UP, stack);

            // Count only robots docked to THIS station, never a bare proximity count: the arena grid packs
            // concurrently-running tests 6 blocks apart in X and 7 in Z, and this pipe sits close enough to
            // the cell edge that another test's robot (or a leaked one) can fall inside a radius-2 box
            // without saying anything about this click. A robot THIS click spawned is always docked to the
            // clicked station, and nothing else ever is.
            List<EntityRobot> placed = robotsNear(helper, pipeRel, 2.0);
            placed.removeIf(r -> r.getDockingStation() != station);
            helper.assertTrue(placed.size() == 1,
                    "clicking a free station with a robot item must spawn exactly one robot, found "
                            + placed.size() + describe(placed));
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
            helper.assertTrue(robot.getBoard() != null
                            && BoardRobotPickerNBT.ID.equals(robot.getBoard().getNBTHandler().getID()),
                    "the placed robot must run the stack's board");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                    "a survival placement must consume exactly one robot, leaving "
                            + player.getItemInHand(InteractionHand.MAIN_HAND).getCount() + " of 2");
            // The placed robot is outside the framework's pass-time cleanup bounds (the empty structure
            // only covers the arena corner), so it survives into the NEXT batch's reuse of these very
            // coordinates and inflates that test's robot count — discard it before succeeding.
            robot.discard();
            helper.succeed();
        });
    }

    /** A station already claimed by another robot refuses the placement outright: no second robot, and the
     *  player keeps the item. */
    public static void robotItemRejectedWhenStationAlreadyTaken(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(3, 2, 3);
        installStation(helper, pipeRel, Direction.UP);

        BlockPos squatterRel = new BlockPos(3, 4, 3);
        // Centre on the squatter's chunk — it is the entity that must actually tick and get an id.
        EntityArenaUtil.forceLoadEntityArena(helper, squatterRel);
        EntityRobot squatter = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 squatterPos = Vec3.atCenterOf(helper.absolutePos(squatterRel));
        squatter.setPos(squatterPos.x, squatterPos.y, squatterPos.z);
        helper.getLevel().addFreshEntity(squatter);

        // The squatter has to have TICKED before it can claim anything: a robot only gets its id from the
        // registry on its first tick, and DockingStation#takeAsMain stores getRobotId() verbatim -- claiming
        // with the NULL_ROBOT_ID sentinel leaves isTaken() reading false and the test asserting nonsense.
        EntityArenaUtil.tickUntilThen(helper, 60,
                () -> stationAt(helper, pipeRel, Direction.UP) != null
                        && squatter.getRobotId() != EntityRobotBase.NULL_ROBOT_ID,
                () -> {
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    helper.assertTrue(station.takeAsMain(squatter),
                            "precondition: the squatter claims the station");
                },
                4,
                () -> {
                    DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
                    ItemStack stack = programmedRobot(0L);
                    Player player = clickStationWith(helper, pipeRel, Direction.UP, stack);

                    List<EntityRobot> dockedHere = robotsNear(helper, pipeRel, 3.0);
                    dockedHere.removeIf(r -> r.getDockingStation() != station);
                    helper.assertTrue(dockedHere.isEmpty(),
                            "a taken station must refuse the placement: the squatter claims it without "
                                    + "docking (no board, no AI), so anything docked to it came from this "
                                    + "click" + describe(dockedHere));
                    helper.assertTrue(station.robotIdTaking() == squatter.getRobotId(),
                            "the original claimant must keep the station");
                    helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                            "a rejected placement must not consume the item");
                    squatter.discard(); // outside the framework's cleanup bounds — see the happy path
                    helper.succeed();
                },
                "the station never registered, or the squatting robot never ticked and so never got an id "
                        + "from the RobotRegistry");
    }

    /** {@code RobotEvent.Place} must be posted BEFORE anything is committed, and cancelling it must leave no
     *  trace: no robot, a free station and the item still in hand. Asserting the listener actually fired is
     *  what stops this passing vacuously against a {@code useOn} that does nothing at all. */
    public static void robotItemPlacementIsCancellableViaRobotEventPlace(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(3, 2, 5);
        EntityArenaUtil.forceLoadEntityArena(helper, pipeRel);
        installStation(helper, pipeRel, Direction.UP);

        whenStationRegistered(helper, pipeRel, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);

            boolean[] posted = { false };
            Consumer<RobotEvent.Place> veto = event -> {
                if (vetoPlacement) {
                    posted[0] = true;
                    event.setCanceled(true);
                }
            };
            ItemStack stack = programmedRobot(0L);
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
            List<EntityRobot> dockedHere = robotsNear(helper, pipeRel, 2.0);
            dockedHere.removeIf(r -> r.getDockingStation() != station);
            helper.assertTrue(dockedHere.isEmpty(),
                    "a cancelled RobotEvent.Place must leave no robot docked to the station"
                            + describe(dockedHere));
            helper.assertFalse(station.isTaken(),
                    "a cancelled placement must not leave the station reserved");
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                    "a cancelled placement must not consume the item");
            helper.succeed();
        });
    }

    /** A {@link FakePlayer} (a real {@code ServerPlayer}, unlike {@code makeMockPlayer}'s bare {@code Player})
     *  that keeps every action-bar message it is sent, so the refusal's "Not programmed" line is observable.
     *  The overlay entry point is {@code displayClientMessage(msg, true)} below 26.1 and
     *  {@code sendOverlayMessage} from 26.1 on — the same split {@code MessageUtil.sendOverlayMessage} hides. */
    private static final class RecordingPlayer extends FakePlayer {
        final List<Component> overlay = new ArrayList<>();

        RecordingPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.fromString("5c7c0c9e-2b1f-4b8e-9a51-0b1d3c10e0aa"),
                    "[BC robot tester]"));
        }

        //? if >=26.1 {
        @Override
        public void sendOverlayMessage(Component message) {
            overlay.add(message);
        }
        //?} else {
        /*@Override
        public void displayClientMessage(Component message, boolean actionBar) {
            if (actionBar) {
                overlay.add(message);
            }
        }*/
        //?}
    }

    /** A blank robot — the bare no-blob stack, a stack naming the empty board outright, AND a stack naming a
     *  board id nothing registers (which the registry resolves to the empty board) — is refused, as 7.1.x
     *  refused it ({@code getRobotNBT(stack) == getEmptyRobotBoard()}, a comparison of the RESOLVED board,
     *  returned before any spawn): no robot, the station stays free, the whole stack stays in hand, and {@code useOn} answers
     *  {@code FAIL} so the hand does not swing a placement that never happened. The port's addition over
     *  7.1.x's silent refusal: one action-bar "Not programmed" message per click. */
    public static void blankRobotRefusesPlacement(GameTestHelper helper) {
        // z=6, not z=7: with the empty structure the grid rows are spaced 7 apart, so z=7 is already the
        // NEXT row's first block.
        BlockPos pipeRel = new BlockPos(4, 2, 6);
        EntityArenaUtil.forceLoadEntityArena(helper, pipeRel);
        installStation(helper, pipeRel, Direction.UP);

        whenStationRegistered(helper, pipeRel, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            BlockHitResult hit = new BlockHitResult(faceCentre(helper, pipeRel, Direction.UP), Direction.UP,
                    helper.absolutePos(pipeRel), false);

            ItemStack bare = new ItemStack(BCRoboticsItems.ROBOT.get(), 16);
            ItemStack namedEmpty = ItemRobot.createRobotStack(BoardRobotEmptyNBT.ID, 3000L * MjAPI.MJ);
            namedEmpty.setCount(16);
            helper.assertTrue(bare.getCount() == 16 && namedEmpty.getCount() == 16,
                    "precondition: blank robots stack to 16");
            // A board id nothing registers (a removed addon's board, a board renamed between releases,
            // hand-edited data) resolves to the empty board through the registry, so the robot it would
            // place is just as blank -- 7.1.x compared the RESOLVED board and refused it too. The id is not
            // the empty board's, so the stack carries MAX_STACK_SIZE 1: its expected count is 1, not 16.
            ItemStack unknownBoard = ItemRobot.createRobotStack("buildcraftunofficial:no_such_board",
                    3000L * MjAPI.MJ);

            for (ItemStack blank : new ItemStack[] { bare, namedEmpty, unknownBoard }) {
                int count = blank.getCount();
                RecordingPlayer player = new RecordingPlayer(helper.getLevel());
                player.setItemInHand(InteractionHand.MAIN_HAND, blank);
                InteractionResult result = BCRoboticsItems.ROBOT.get()
                        .useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

                helper.assertTrue(result == InteractionResult.FAIL,
                        "a blank robot's click must FAIL (handled, no arm swing), got " + result);
                List<EntityRobot> placed = robotsNear(helper, pipeRel, 2.0);
                placed.removeIf(r -> r.getDockingStation() != station);
                helper.assertTrue(placed.isEmpty(),
                        "a blank robot must not place — 7.1.x refused it" + describe(placed));
                helper.assertFalse(station.isTaken(), "a refused placement must leave the station free");
                helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == count,
                        "a refused placement must consume nothing, leaving "
                                + player.getItemInHand(InteractionHand.MAIN_HAND).getCount() + " of " + count);
                helper.assertTrue(player.overlay.size() == 1,
                        "exactly one action-bar message per refused click, got " + player.overlay);
                helper.assertTrue(player.overlay.get(0).getContents() instanceof TranslatableContents tc
                                && ItemRobot.NOT_PROGRAMMED_KEY.equals(tc.getKey()),
                        "the refusal must say 'Not programmed', got " + player.overlay.get(0));
            }
            helper.succeed();
        });
    }
}
