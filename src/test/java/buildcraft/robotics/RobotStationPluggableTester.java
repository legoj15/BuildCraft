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
import net.minecraft.world.entity.HumanoidArm;
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
 */
public class RobotStationPluggableTester {

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

    private static RobotStationPluggable install(TilePipeHolder tile, Direction side) {
        RobotStationPluggable plug = new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, side);
        tile.replacePluggable(side, plug);
        return plug;
    }

    private static TilePipeHolder placePowerPipe(GameTestHelper helper, BlockPos relPos, net.minecraft.world.item.Item pipeItem) {
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
        helper.runAfterDelay(2, () -> {
            IRobotRegistry registry = RobotManager.registryProvider.getRegistry(helper.getLevel());
            DockingStation station = registry.getStation(absPos, Direction.UP);
            helper.assertTrue(station instanceof DockingStationPipe, "onTick() must lazily register a DockingStationPipe");
            helper.succeed();
        });
    }

    public static void testRemovingPipeDeregistersStation(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 3);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        install(tile, Direction.DOWN);

        BlockPos absPos = helper.absolutePos(relPos);
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(2, () -> {
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
        BlockPos relPos = new BlockPos(1, 2, 5);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        install(tile, Direction.NORTH);

        helper.runAfterDelay(2, () -> {
            List<DockingStation> found = RobotUtils.getStations(tile);
            helper.assertTrue(found.size() == 1, "RobotUtils must discover exactly the one station on this pipe");
            helper.assertTrue(found.get(0) instanceof DockingStationPipe, "the discovered station must be a DockingStationPipe");
            helper.succeed();
        });
    }

    // ---------- Reservation lifecycle (take / takeAsMain / release) ----------

    public static void testReleaseFreesStationForReclaim(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 7);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        install(tile, Direction.SOUTH);
        BlockPos absPos = helper.absolutePos(relPos);

        helper.runAfterDelay(2, () -> {
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
        BlockPos relPos = new BlockPos(1, 2, 9);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        RobotStationPluggable plug = install(tile, Direction.EAST);
        BlockPos absPos = helper.absolutePos(relPos);

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(plug.getRenderState() == RobotStationPluggable.RobotStationState.AVAILABLE,
                    "a freshly registered, untaken station renders as available");

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
        BlockPos relPos = new BlockPos(1, 2, 11);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        RobotStationPluggable plug = install(tile, Direction.WEST);
        BlockPos absPos = helper.absolutePos(relPos);

        helper.runAfterDelay(2, () -> {
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
        BlockPos relPos = new BlockPos(1, 2, 13);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        RobotStationPluggable plug = install(tile, Direction.UP);
        BlockPos absPos = helper.absolutePos(relPos);

        helper.runAfterDelay(2, () -> {
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
        BlockPos redstonePos = new BlockPos(2, 1, 2);
        BlockPos enginePos = new BlockPos(2, 2, 2);
        BlockPos woodPipePos = new BlockPos(2, 3, 2);
        BlockPos pipePos = new BlockPos(2, 4, 2);

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
        helper.runAfterDelay(2, () -> {
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
        // Distinct from testRenderStateTransitions...'s (1,2,9): these tests share a
        // test_environment, and RobotRegistry is a per-level SavedData keyed by (pos, side).
        BlockPos relPos = new BlockPos(1, 2, 17);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        RobotStationPluggable server = install(tile, Direction.NORTH);
        BlockPos absPos = helper.absolutePos(relPos);

        helper.runAfterDelay(2, () -> {
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
        BlockPos relPos = new BlockPos(1, 2, 15);
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        install(tile, Direction.DOWN);
        BlockPos absPos = helper.absolutePos(relPos);

        helper.runAfterDelay(2, () -> {
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
        private long robotId = nextTestId++;
        private DockingStation dockingStation;
        private DockingStation mainStation;

        TestRobot(Level level) {
            super(level);
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
        public void onChunkUnload() {
        }

        @Override
        public ItemStack receiveItem(net.minecraft.world.level.block.entity.BlockEntity tile, ItemStack stack) {
            return stack;
        }

        @Override
        public void setMainStation(DockingStation station) {
            this.mainStation = station;
        }

        @Override
        public HumanoidArm getMainArm() {
            return HumanoidArm.RIGHT;
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
            // Overriding (not adding to) LivingEntity's own defineSynchedData left its fixed synced-data
            // slots (health, flags, ...) undefined -> "has not defined synched data value N" at spawn.
            super.defineSynchedData(builder);
        }

        //? if >=1.21.10 {
        @Override
        protected void readAdditionalSaveData(ValueInput input) {
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
        }
        //?}
        // On 1.21.1, LivingEntity itself already supplies concrete PUBLIC
        // readAdditionalSaveData/addAdditionalSaveData(CompoundTag) overrides — Entity's protected
        // abstract declaration is satisfied there already, and re-declaring them here at `protected`
        // would illegally narrow that visibility. Nothing to override on that node.

        //? if <1.21.10 {
        /*@Override
        public Iterable<ItemStack> getArmorSlots() {
            return java.util.Collections.emptyList();
        }

        @Override
        public ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
        }*/
        //?}
    }
}
