/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.List;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;*/
//?}

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.core.IZone;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.tile.TileEngineCreative;
import buildcraft.lib.mj.MjBatteryReceiver;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * GameTests for the Ph2 docking-station pluggable, covering exactly the scope todos.md asked for:
 * pluggable placement -> auto-register/remove, {@link RobotUtils#getStations} discovery through a real
 * pipe, the reservation lifecycle ({@code take}/{@code release}, standing in for "undock -> release" —
 * physical dock/undock movement is Ph3's {@code EntityRobot}, not tested here), the MJ-charge handoff
 * gated on actually-docked (not merely reserved), and an item-pipe handoff smoke test.
 *
 * <p>{@link RobotRegistry} is a per-level {@code SavedData} singleton shared by every concurrently
 * running arena in this batch — every assertion below is keyed to THIS test's own {@code (pos, side)},
 * never to {@code getStations().size()} or any other whole-registry state (see {@code MarkerTester} for
 * the same discipline applied via a private {@code test_environment} where a keyed lookup isn't enough).
 *
 * <p><b>Two rules about WHERE these tests build, both learned the hard way.</b> The game-test framework lays
 * arenas out on a grid: with the {@code minecraft:empty} structure they are 6 blocks apart in X and 8 in Z,
 * and the framework force-loads only the chunk(s) the (1x1x1) structure itself occupies. So:
 * <ul>
 * <li><b>Every test force-loads its own 3x3 chunk neighbourhood</b> ({@link EntityArenaUtil#forceLoadEntityArena}).
 *     A pipe placed even a few blocks from the arena origin can land in a neighbouring chunk, and a block
 *     entity in an unloaded chunk never ticks — which for this suite means {@code RobotStationPluggable.onTick}
 *     never runs, no {@code DockingStation} is ever registered, and the test NPEs on a null station. That was
 *     the exact failure mode of {@link #testRenderStateSurvivesNetworkRoundTrip} once a later batch of tests
 *     re-packed the grid: its pipe sat 17 blocks out, in a chunk that until then had happened to be kept
 *     loaded by a neighbouring arena.</li>
 * <li><b>...and then waits for its station instead of guessing a tick</b>
 *     ({@link #whenStationRegistered}). Force-loading gets the chunk ticking <em>eventually</em>, not
 *     immediately -- see {@link EntityArenaUtil#forceLoadEntityArena} for the measured spread. Every test
 *     below therefore polls for its own station rather than hard-coding {@code runAfterDelay(2)}.</li>
 * <li><b>Every relative position stays inside the 6x8 cell</b> (x in 1..2, z in 1..6 here). Anything further
 *     out lands in ANOTHER test's arena, where it is neither cleared between runs nor safe from being
 *     overwritten in the same tick.</li>
 * </ul>
 */
public class RobotStationPluggableTester {

    private static TilePipeHolder placeItemPipe(GameTestHelper helper, BlockPos relPos) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        return tile;
    }

    private static RobotStationPluggable install(TilePipeHolder tile, Direction side) {
        RobotStationPluggable plug = new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, side);
        tile.replacePluggable(side, plug);
        return plug;
    }

    /** Runs {@code body} on the first tick at which this pipe face's {@link DockingStationPipe} is actually in
     *  the {@link RobotRegistry}, instead of at a hard-coded tick.
     *
     * <p>{@link RobotStationPluggable#onTick()} is the ONLY place a station is ever registered (modern
     * {@code PipePluggable} has no {@code validate()} hook to piggyback on), and a block entity only ticks
     * once its chunk has been promoted to {@code BLOCK_TICKING} -- which happens a variable number of ticks
     * after {@link EntityArenaUtil#forceLoadEntityArena} asks for it, not synchronously. Waiting a fixed two
     * ticks was therefore a coin flip, and its losing side is not even a clean failure:
     * {@code getRenderState()} falls back to the never-synced {@code NONE}, and a station lookup hands the
     * next line a null to dereference. The production behaviour is correct, just not instantaneous. */
    private static void whenStationRegistered(GameTestHelper helper, BlockPos absPos, Direction side,
                                              Runnable body) {
        EntityArenaUtil.tickUntil(helper, 40,
                () -> RobotManager.registryProvider.getRegistry(helper.getLevel())
                        .getStation(absPos, side) != null,
                body,
                "RobotStationPluggable.onTick() never registered a DockingStation for the " + side
                        + " face of " + absPos + " -- its pipe's chunk never started block-ticking");
    }

    private static TilePipeHolder placePowerPipe(GameTestHelper helper, BlockPos relPos, net.minecraft.world.item.Item pipeItem) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(pipeItem));
        return tile;
    }

    // ---------- Placement auto-registers / removal auto-deregisters ----------

    public static void testPlacingRobotStationRegistersInRobotRegistry(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        install(tile, Direction.UP);

        BlockPos absPos = helper.absolutePos(relPos);
        whenStationRegistered(helper, absPos, Direction.UP, () -> {
            IRobotRegistry registry = RobotManager.registryProvider.getRegistry(helper.getLevel());
            DockingStation station = registry.getStation(absPos, Direction.UP);
            helper.assertTrue(station instanceof DockingStationPipe, "onTick() must lazily register a DockingStationPipe");
            helper.succeed();
        });
    }

    public static void testRemovingPipeDeregistersStation(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 2);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        install(tile, Direction.DOWN);

        BlockPos absPos = helper.absolutePos(relPos);
        ServerLevel level = helper.getLevel();
        whenStationRegistered(helper, absPos, Direction.DOWN, () -> {
            IRobotRegistry registry = RobotManager.registryProvider.getRegistry(level);
            helper.assertTrue(registry.getStation(absPos, Direction.DOWN) != null, "precondition: registered");

            // pluggable.onRemove() is only wired into the player-break path (TilePipeHolder.dropPipeItems,
            // called from BlockPipeHolder.playerWillDestroy) — a bare non-player removal (piston,
            // /setblock, level.removeBlock) leaves hardware cleanup to DockingStationPipe.getHolder()'s
            // lazy re-fetch-and-deregister instead (see PipeDropsTester's "non-player removal" tests for
            // the same split). Exercise the path that actually calls onRemove() synchronously.
            net.minecraft.world.entity.player.Player player =
                    helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(absPos);
            state.getBlock().playerWillDestroy(level, absPos, state, player);
            level.removeBlock(absPos, false);

            helper.assertTrue(registry.getStation(absPos, Direction.DOWN) == null,
                    "playerWillDestroy must deregister the station synchronously (onRemove -> removeStation)");
            helper.succeed();
        });
    }

    // ---------- RobotUtils discovery through a real IPipeHolder ----------

    public static void testRobotUtilsDiscoversStationThroughPipeHolder(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 3);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        install(tile, Direction.NORTH);
        BlockPos absPos = helper.absolutePos(relPos);

        whenStationRegistered(helper, absPos, Direction.NORTH, () -> {
            List<DockingStation> found = RobotUtils.getStations(tile);
            helper.assertTrue(found.size() == 1, "RobotUtils must discover exactly the one station on this pipe");
            helper.assertTrue(found.get(0) instanceof DockingStationPipe, "the discovered station must be a DockingStationPipe");
            helper.succeed();
        });
    }

    // ---------- Reservation lifecycle (take / takeAsMain / release) ----------

    public static void testReleaseFreesStationForReclaim(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 4);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        install(tile, Direction.SOUTH);
        BlockPos absPos = helper.absolutePos(relPos);

        whenStationRegistered(helper, absPos, Direction.SOUTH, () -> {
            DockingStationPipe station =
                    (DockingStationPipe) RobotManager.registryProvider.getRegistry(helper.getLevel())
                            .getStation(absPos, Direction.SOUTH);
            TestRobot robotA = new TestRobot(helper.getLevel());
            TestRobot robotB = new TestRobot(helper.getLevel());

            helper.assertTrue(station.take(robotA), "first reservation succeeds");
            helper.assertFalse(station.take(robotB), "a reserved station rejects a second claimant");

            station.release(robotA);

            helper.assertFalse(station.isTaken(), "release clears the reservation");
            helper.assertTrue(station.canRelease(), "a released, non-main station can release again harmlessly");
            helper.assertTrue(station.take(robotB), "freed station can be reclaimed by another robot");
            helper.succeed();
        });
    }

    public static void testRenderStateTransitionsAvailableReservedLinked(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 5);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        RobotStationPluggable plug = install(tile, Direction.EAST);
        BlockPos absPos = helper.absolutePos(relPos);

        whenStationRegistered(helper, absPos, Direction.EAST, () -> {
            helper.assertTrue(plug.getRenderState() == RobotStationPluggable.RobotStationState.AVAILABLE,
                    // Name the observed value and whether THIS pluggable instance is still the installed
                    // one: the two ways this can read wrong are a station that is somehow already taken,
                    // and an orphaned copy whose onTick() never runs (its station stays null, so
                    // getRenderState() silently falls back to the never-synced NONE).
                    "a freshly registered, untaken station renders as available; was "
                            + plug.getRenderState() + " (station=" + plug.getStation()
                            + ", still the installed pluggable=" + (tile.getPluggable(Direction.EAST) == plug)
                            + ")");

            DockingStationPipe station = (DockingStationPipe) RobotManager.registryProvider
                    .getRegistry(helper.getLevel()).getStation(absPos, Direction.EAST);
            TestRobot robot = new TestRobot(helper.getLevel());

            station.take(robot);
            helper.assertTrue(plug.getRenderState() == RobotStationPluggable.RobotStationState.RESERVED,
                    "take() (not as main) renders as reserved");

            station.release(robot);
            station.takeAsMain(robot);
            helper.assertTrue(plug.getRenderState() == RobotStationPluggable.RobotStationState.LINKED,
                    "takeAsMain() renders as linked");
            helper.succeed();
        });
    }

    // ---------- MJ charge handoff: docked only, lossless ----------

    public static void testDockedRobotChargesLosslesslyFromMjReceiver(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 6);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        RobotStationPluggable plug = install(tile, Direction.WEST);
        BlockPos absPos = helper.absolutePos(relPos);

        whenStationRegistered(helper, absPos, Direction.WEST, () -> {
            DockingStationPipe station = (DockingStationPipe) RobotManager.registryProvider
                    .getRegistry(helper.getLevel()).getStation(absPos, Direction.WEST);
            TestRobot robot = new TestRobot(helper.getLevel());
            station.takeAsMain(robot);
            robot.dock(station);

            IMjReceiver receiver = plug.getCapability(MjAPI.CAP_RECEIVER);
            helper.assertTrue(receiver != null, "a docked robot's station must expose an MJ receiver");

            long toSend = 1000L * MjAPI.MJ;
            long excess = receiver.receivePower(toSend, false);

            helper.assertTrue(excess == 0, "well under battery capacity: nothing should be rejected");
            helper.assertTrue(robot.getBattery().getStored() == toSend,
                    "the docked robot's battery must receive the FULL amount — pipes are lossless, and so is this handoff");
            helper.succeed();
        });
    }

    public static void testReservedButNotDockedRobotDoesNotCharge(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(2, 2, 1);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        RobotStationPluggable plug = install(tile, Direction.UP);
        BlockPos absPos = helper.absolutePos(relPos);

        whenStationRegistered(helper, absPos, Direction.UP, () -> {
            DockingStationPipe station = (DockingStationPipe) RobotManager.registryProvider
                    .getRegistry(helper.getLevel()).getStation(absPos, Direction.UP);
            TestRobot robot = new TestRobot(helper.getLevel());
            // Reserved as main (robotTaking() != null) but never actually docked (getDockingStation()
            // stays null) — 7.1.x's receiveEnergy() gate, and this port's dockedRobot(), require both.
            station.takeAsMain(robot);

            IMjReceiver receiver = plug.getCapability(MjAPI.CAP_RECEIVER);
            helper.assertTrue(receiver == null, "reserving without docking must NOT expose a charger");
            helper.succeed();
        });
    }

    /** The end-to-end bookend the two tests above deliberately do not cover: they call
     *  {@code plug.getCapability(...)} directly, which proves the battery hand-off but says nothing
     *  about whether a kinesis pipe can actually FIND the station. That delivery path is
     *  {@code PipeFlowPower.getReceiver} -> {@code TilePipeHolder.getCapabilityFromPipe} ->
     *  {@code plug.getInternalCapability}, and it is additionally gated on the station's face being a
     *  {@code ConnectedType.TILE} connection — which {@code Pipe.updateConnections} skips for any
     *  {@code isBlocking()} pluggable. Both halves have to be right or a docked robot never charges,
     *  and Ph4's {@code AIRobotRecharge} depends entirely on this working. */
    public static void testKinesisPipeChargesDockedRobot(GameTestHelper helper) {
        BlockPos redstonePos = new BlockPos(2, 1, 5);
        BlockPos enginePos = new BlockPos(2, 2, 5);
        BlockPos woodPipePos = new BlockPos(2, 3, 5);
        BlockPos pipePos = new BlockPos(2, 4, 5);

        if (BCCoreBlocks.ENGINE_CREATIVE == null) {
            throw new IllegalStateException(
                    "ENGINE_CREATIVE not registered — test JVM was launched without -Dbuildcraft.dev=true. "
                            + "Check build.gradle gameTestServer run config.");
        }

        // Engine -> WOOD kinesis -> stone kinesis -> station. The wood pipe is load-bearing, not
        // decoration: only wood/diaWood kinesis pipes accept power from an engine, so an engine
        // feeding a stone pipe directly transfers nothing at all.
        TilePipeHolder pipe = placePowerPipe(helper, pipePos, BCTransportItems.PIPE_STONE_POWER.get());
        TilePipeHolder woodPipe = placePowerPipe(helper, woodPipePos, BCTransportItems.PIPE_WOOD_POWER.get());
        RobotStationPluggable plug = install(pipe, Direction.WEST);

        BlockState engineState = BCCoreBlocks.ENGINE_CREATIVE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP);
        helper.setBlock(enginePos, engineState);
        helper.setBlock(redstonePos, Blocks.REDSTONE_BLOCK);

        //? if >=1.21.10 {
        TileEngineCreative engine = helper.getBlockEntity(enginePos, TileEngineCreative.class);
        //?} else {
        /*TileEngineCreative engine = helper.getBlockEntity(enginePos);*/
        //?}
        engine.currentOutputIndex = TileEngineCreative.OUTPUTS.length - 1; // 256 MJ/t

        // Same reason as PipeFlowPowerTester: setBlock + onPlacedBy skips the neighbour-changed
        // cascade a real placement triggers, so connections are never computed without this.
        woodPipe.getPipe().markForUpdate();
        pipe.getPipe().markForUpdate();

        BlockPos absPipePos = helper.absolutePos(pipePos);
        whenStationRegistered(helper, absPipePos, Direction.WEST, () -> {
            DockingStationPipe station = (DockingStationPipe) RobotManager.registryProvider
                    .getRegistry(helper.getLevel()).getStation(absPipePos, Direction.WEST);
            helper.assertTrue(station != null, "station must have registered by now");
            TestRobot robot = new TestRobot(helper.getLevel());
            station.takeAsMain(robot);
            robot.dock(station);
            helper.assertTrue(robot.getBattery().getStored() == 0, "fixture must start drained");

            // Poll rather than assert once: engine warm-up plus the power flow's 2-tick
            // request/transfer handshake means the first delivery lands a variable number of
            // ticks out, exactly as in PipeFlowPowerTester.
            helper.succeedWhen(() -> helper.assertTrue(robot.getBattery().getStored() > 0,
                    "a robot docked on a station attached to a powered kinesis pipe has received no MJ — "
                            + "the pipe cannot see the station as a power destination"));
        });
    }

    /** The client never runs {@link RobotStationPluggable#onTick()} (it early-returns off-server), so
     *  its copy of the pluggable has no {@code DockingStation} and can only learn the indicator colour
     *  from the network. This pins that the state actually rides the payload: a fresh pluggable rebuilt
     *  from the buffer — with no station attached, exactly like the client's — must still report LINKED.
     *  Without the sync it reports NONE and {@code PlugRobotStationRenderer} draws nothing at all. */
    public static void testRenderStateSurvivesNetworkRoundTrip(GameTestHelper helper) {
        // Distinct from testRenderStateTransitions...'s (1,2,5): the RobotRegistry is a per-level
        // SavedData keyed by (pos, side), and arena cells can be re-used between batches.
        BlockPos relPos = new BlockPos(2, 2, 3);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        RobotStationPluggable server = install(tile, Direction.NORTH);
        BlockPos absPos = helper.absolutePos(relPos);

        whenStationRegistered(helper, absPos, Direction.NORTH, () -> {
            DockingStationPipe station = (DockingStationPipe) RobotManager.registryProvider
                    .getRegistry(helper.getLevel()).getStation(absPos, Direction.NORTH);
            station.takeAsMain(new TestRobot(helper.getLevel()));
            helper.assertTrue(server.getRenderState() == RobotStationPluggable.RobotStationState.LINKED,
                    "server-side precondition: a main-station claim reads LINKED");

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            server.writeCreationPayload(buf);

            RobotStationPluggable client =
                    new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, Direction.NORTH, buf);
            helper.assertTrue(client.getStation() == null,
                    "fixture precondition: the rebuilt pluggable has no station, like the client's");
            helper.assertTrue(client.getRenderState() == RobotStationPluggable.RobotStationState.LINKED,
                    "render state must survive the network round-trip — otherwise the client always "
                            + "reads NONE and the available/reserved/linked indicator never renders");
            helper.succeed();
        });
    }

    // ---------- Item pipe handoff smoke ----------

    public static void testItemOutputInjectsIntoPipeNetwork(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(2, 2, 2);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        install(tile, Direction.DOWN);
        BlockPos absPos = helper.absolutePos(relPos);

        whenStationRegistered(helper, absPos, Direction.DOWN, () -> {
            DockingStationPipe station = (DockingStationPipe) RobotManager.registryProvider
                    .getRegistry(helper.getLevel()).getStation(absPos, Direction.DOWN);

            Object output = station.getItemOutput();
            helper.assertTrue(output instanceof PipeFlowItems, "an item pipe's flow must be exposed as the station's item output");

            // insertItemsForce (unlike injectItem) doesn't gate on pipe.isConnected(from) — this lone
            // pipe has no neighbours to connect to, so this is the right call to smoke-test the wiring
            // without also standing up a second connected pipe just to satisfy an unrelated gate.
            PipeFlowItems flow = (PipeFlowItems) output;
            flow.insertItemsForce(new ItemStack(Items.EMERALD, 4), Direction.DOWN.getOpposite(), null, 0.04);
            helper.succeed();
        });
    }

    /** Minimal concrete {@link EntityRobotBase} for tests — a real {@link MjBattery} and dock-state
     *  bookkeeping, everything else stubbed. Reusable fixture for Ph3's future entity tests too. */
    static class TestRobot extends EntityRobotBase {
        // Must NOT default to NULL_ROBOT_ID: DockingStation#take/takeAsMain store getRobotId() verbatim
        // as the station's robotTakingId, so a sentinel-ID robot would make isTaken() read false right
        // after taking. Real robots get their ID from RobotRegistry#registerRobot; these tests bypass
        // registration entirely (going straight through the station), so each instance self-assigns one.
        private static long nextTestId = 1;

        private final MjBattery battery = new MjBattery(1_000_000L * MjAPI.MJ);
        /** A plain battery receiver, not {@code EntityRobot}'s latching one: {@code getChargeReceiver} is
         *  abstract on {@code EntityRobotBase} (typing it against {@code IMjReceiver} is what keeps
         *  {@code buildcraft.api} free of any {@code buildcraft.lib} import), and this fixture has no sleep
         *  indicator to keep awake. It is still an {@code IMjReadable} by inheritance, which is what the
         *  {@code TriggerPower} path needs. */
        private final MjBatteryReceiver chargeReceiver = new MjBatteryReceiver(battery);
        private long robotId = nextTestId++;
        private DockingStation dockingStation;
        private DockingStation mainStation;

        TestRobot(Level level) {
            // EntityRobotBase sits on bare Entity now, so it needs a real registered EntityType rather than
            // the old hard-coded vanilla placeholder. Reusing the robot's own type keeps the fixture's
            // dimensions and tracking identical to the entity these tests stand in for.
            super(BCRoboticsEntities.ROBOT.get(), level);
        }

        @Override
        public ItemStack getHeldItem() {
            return ItemStack.EMPTY;
        }

        @Override
        public int getInventorySize() {
            return 0;
        }

        @Override
        public ItemStack getInventoryStack(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setInventoryStack(int slot, ItemStack stack) {
        }

        @Override
        public void setItemInUse(ItemStack stack) {
        }

        @Override
        public void setItemActive(boolean b) {
        }

        @Override
        public boolean isMoving() {
            return false;
        }

        @Override
        public DockingStation getLinkedStation() {
            return mainStation;
        }

        @Override
        public RedstoneBoardRobot getBoard() {
            return null;
        }

        @Override
        public void aimItemAt(float yaw, float pitch) {
        }

        @Override
        public void aimItemAt(BlockPos pos) {
        }

        @Override
        public float getAimYaw() {
            return 0;
        }

        @Override
        public float getAimPitch() {
            return 0;
        }

        @Override
        public MjBattery getBattery() {
            return battery;
        }

        @Override
        public IMjReceiver getChargeReceiver() {
            return chargeReceiver;
        }

        @Override
        public DockingStation getDockingStation() {
            return dockingStation;
        }

        @Override
        public void dock(DockingStation station) {
            this.dockingStation = station;
        }

        @Override
        public void undock() {
            this.dockingStation = null;
        }

        @Override
        public IZone getZoneToWork() {
            return null;
        }

        @Override
        public IZone getZoneToLoadUnload() {
            return null;
        }

        @Override
        public boolean containsItems() {
            return false;
        }

        @Override
        public boolean hasFreeSlot() {
            return true;
        }

        @Override
        public void unreachableEntityDetected(net.minecraft.world.entity.Entity entity) {
        }

        @Override
        public boolean isKnownUnreachable(net.minecraft.world.entity.Entity entity) {
            return false;
        }

        @Override
        public long getRobotId() {
            return robotId;
        }

        @Override
        public void setUniqueRobotId(long robotId) {
            this.robotId = robotId;
        }

        @Override
        public IRobotRegistry getRegistry() {
            return RobotManager.registryProvider.getRegistry(level());
        }

        @Override
        public void releaseResources() {
            getRegistry().releaseResources(this);
        }

        @Override
        public ItemStack receiveItem(net.minecraft.world.level.block.entity.BlockEntity tile, ItemStack stack) {
            return stack;
        }

        @Override
        public void setMainStation(DockingStation station) {
            this.mainStation = station;
        }

        // EntityRobotBase implements IFluidHandlerAdv — none of these tests touch fluid transfer, so
        // every method here is an inert stub. Transfer API (>=1.21.10) vs classic IFluidHandler (1.21.1).
        //? if >=1.21.10 {
        @Override
        public int size() {
            return 0;
        }

        @Override
        public net.neoforged.neoforge.transfer.fluid.FluidResource getResource(int index) {
            return net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0;
        }

        @Override
        public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            return 0;
        }

        @Override
        public boolean isValid(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            return false;
        }

        @Override
        public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                           net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                            net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(buildcraft.api.core.IFluidFilter filter, int maxDrain,
                            net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
            return 0;
        }
        //?} else {
        /*@Override
        public int getTanks() {
            return 0;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
            return false;
        }

        @Override
        public int fill(net.neoforged.neoforge.fluids.FluidStack resource,
                         net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
            return 0;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource,
                         net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain,
                         net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }

        @Override
        public int extract(buildcraft.api.core.IFluidFilter filter, int maxDrain, boolean simulate) {
            return 0;
        }*/
        //?}

        //? if >=1.21.10 {
        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
            return false;
        }
        //?}

        @Override
        protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
            // Bare Entity seeds its own synched-data entries before this hook runs and EntityRobotBase's
            // own body is empty, so this fixture defines nothing and calls nothing. (Under the old
            // LivingEntity base a super call was mandatory here — its fixed slots, health and flags among
            // them, were otherwise left undefined and the entity threw "has not defined synched data
            // value N" at spawn.)
        }

        // Bare Entity declares BOTH of these protected abstract on every node, so both branches are
        // required. (Under the old LivingEntity base the 1.21.1 branch was omitted, because LivingEntity
        // there already supplied concrete PUBLIC CompoundTag overrides that a protected re-declaration
        // would have illegally narrowed.)
        //? if >=1.21.10 {
        @Override
        protected void readAdditionalSaveData(ValueInput input) {
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
        }
        //?} else {
        /*@Override
        protected void readAdditionalSaveData(CompoundTag input) {
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag output) {
        }*/
        //?}
    }
}
