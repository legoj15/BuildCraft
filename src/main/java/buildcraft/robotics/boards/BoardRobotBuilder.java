/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.boards;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.core.IZone;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.builders.BCBuildersEventDist;
import buildcraft.builders.snapshot.BlueprintBuilder;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.lib.misc.StackUtil;
import buildcraft.robotics.ai.AIRobotDisposeItems;
import buildcraft.robotics.ai.AIRobotGotoBlock;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStation;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoad;
import buildcraft.robotics.ai.AIRobotRecharge;

/**
 * REWRITTEN for main's snapshot system — 7.1.x's {@code BoardRobotBuilder} fed construction markers
 * ({@code TileConstructionMarker.currentMarkers}) slot-by-slot and built each reserved
 * {@code BuildingSlot} with the robot's own battery. None of that API exists on main: the snapshot
 * rewrite replaced markers with the Builder machine, which builds autonomously from its own battery
 * and resource inventory — the one thing it cannot do for itself is fetch materials. This board is
 * that supply line:
 *
 * <ol>
 *   <li>find the closest Builder machine inside the delivery flight's reach (zone-filtered) whose blueprint
 *       still reports missing item requirements ({@link BlueprintBuilder#remainingRequiredItems}); with cargo
 *       aboard, one that still requires something the robot carries,</li>
 *   <li>fetch the first missing stack from a station the delivery flight can leave for that machine,</li>
 *   <li>fly to the machine (energy-gated, 8-block arrival radius like 7.1.x's slot flights) and insert what it
 *       requires into its resource inventory,</li>
 *   <li>repeat; sleep when no Builder needs anything, dispose of stale cargo when the need vanished.</li>
 * </ol>
 *
 * <p>Kept from 7.1.x: the zone filter, the one-stack-at-a-time fetch, the 40-tick
 * launching delay between fruitless passes, the {@code SAFETY_POWER} energy gate before the flight
 * leg, and the sleep/dispose fallbacks. Dropped with the API it fed: the slot reservation, the
 * {@code >4 requirements} slot skip (7.1.x robots refused slots too complex for their 4-slot cargo —
 * the modern fetch delivers one stack per trip, so the constraint never binds), and the robot paying
 * the build energy (the machine's battery pays now), and 7.1.x's search radius ({@code MAX_RANGE_SQ = 3*64*64},
 * about 111 blocks): it was wider than its own flight's reach ({@link AIRobotGotoBlock#FLIGHT_REACH}, 96), which
 * 7.1.x masked by building the slot whether or not the flight arrived. Now that a failed flight keeps the cargo,
 * a candidate between 97 and 111 blocks would fail every flight forever, so the search uses the flight's reach
 * from the robot's block. Template-builder machines are not served: only
 * {@code BlueprintBuilder} computes a missing-items list, and 7.1.x's markers were blueprint-only too.
 *
 * <p>Failure paths the port added (7.1.x's slot flights had none): a delivery flight that FAILS
 * keeps the cargo aboard and benches that machine for {@link #UNREACHABLE_RETRY_TICKS} (while every needy
 * machine is benched the robot sleeps with the cargo rather than disposing of it), until
 * {@link #MAX_FAILED_DELIVERIES} failures to the same machine with the same cargo: then the cargo is returned
 * and the machine benched for {@link #GIVE_UP_RETRY_TICKS}; cargo that no machine requires any more is returned
 * too (never emptied into a machine that wants something else); a fetch that fails (no station in reach of the
 * machine stocks the item) benches that machine for {@link #UNREACHABLE_RETRY_TICKS} too, so another needy machine
 * gets the next pass instead of the same doomed fetch; a recharge that fails (no free power
 * station) sends the robot home to sleep instead of re-running the search every cycle.
 */
public class BoardRobotBuilder extends RedstoneBoardRobot {

    /** How long a Builder the robot could not fly to stays off its candidate list (30 s). Package-private so
     *  the board's game test can pin the window. Not saved: a reloaded robot simply retries. */
    static final int UNREACHABLE_RETRY_TICKS = 600;

    /** How many delivery flights with the same cargo may fail to the same Builder before the robot gives up on
     *  it (see {@link #GIVE_UP_RETRY_TICKS}). Package-private so the board's game test can pin it. */
    static final int MAX_FAILED_DELIVERIES = 3;

    /** How long a Builder the robot gave up on stays off its candidate list (5 min): a sealed room or a detour
     *  past the flight's reach does not open up by itself, and each doomed attempt costs about 1000 ticks of
     *  pathfinding and 300 MJ. Package-private so the board's game test can pin the window. */
    static final int GIVE_UP_RETRY_TICKS = 6000;

    /** Builders whose delivery flight failed, keyed by position, mapped to the game time they come back. */
    private final Map<BlockPos, Long> unreachableUntil = new HashMap<>();

    /** Failed delivery flights per Builder for the cargo now aboard; cleared once the hold is empty. Not saved. */
    private final Map<BlockPos, Integer> failedDeliveries = new HashMap<>();

    /** The cargo has run out of Builders it can be taken to (one was given up on): the next pass returns it. */
    private boolean returnCargo;

    private TileBuilder builderToSupply;
    private ItemStack requiredStack = ItemStack.EMPTY;
    private int launchingDelay = 0;

    public BoardRobotBuilder(IRobotAccess iRobot) {
        super(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotBuilderNBT.INSTANCE;
    }

    @Override
    public void update() {
        if (launchingDelay > 0) {
            launchingDelay--;
            return;
        }

        boolean carrying = robot.containsItems();
        if (!carrying) {
            // Failed deliveries are counted per cargo: an empty hold starts the count afresh.
            failedDeliveries.clear();
            returnCargo = false;
        } else if (returnCargo) {
            // A Builder was given up on after repeated failed deliveries: take the cargo back to a station (or
            // drop it) rather than carry it round for ever.
            returnCargo = false;
            builderToSupply = null;
            requiredStack = ItemStack.EMPTY;
            startDelegateAI(new AIRobotDisposeItems(robot));
            return;
        }

        if (builderToSupply == null || !wantsATrip(builderToSupply)) {
            builderToSupply = findClosestBuilder(false);

            if (builderToSupply == null) {
                if (carrying && findClosestBuilder(true) != null) {
                    // The only machines that still want this cargo are benched after a failed flight: the
                    // need has not vanished, so keep the cargo aboard and sleep out the bench window
                    // (disposing would drop it on the ground whenever no station accepts it).
                    startDelegateAI(new AIRobotGotoSleep(robot));
                } else if (carrying) {
                    // No machine requires anything the robot carries any more (it was loaded for one that has
                    // since finished or lost its blueprint): never hand it to a machine that wants something else.
                    startDelegateAI(new AIRobotDisposeItems(robot));
                } else {
                    startDelegateAI(new AIRobotGotoSleep(robot));
                }
                return;
            }
        }

        if (carrying) {
            // Deliver what is aboard before fetching more (7.1.x prepared its slot with everything in
            // hand before flying; here the machine consumes from its inventory, so delivery IS the work).
            if (robot.getPower() <= IRobotAccess.SAFETY_POWER) {
                startDelegateAI(new AIRobotRecharge(robot));
            } else {
                BlockPos pos = builderToSupply.getBuilderPos();
                startDelegateAI(new AIRobotGotoBlock(robot, pos.getX(), pos.getY(), pos.getZ(), 8));
            }
            return;
        }

        requiredStack = firstMissing(builderToSupply);
        if (requiredStack.isEmpty()) {
            // The check sweep has not re-counted the requirements yet (or only power is missing) —
            // wait out 7.1.x's delay and look again.
            launchingDelay = 40;
            return;
        }

        // The delivery flight leaves from the station the cargo is loaded at, so the machine's reach is checked
        // from there too: a stocked station past the flight's reach of the machine is no use.
        BlockPos builderPos = builderToSupply.getBuilderPos();
        startDelegateAI(new AIRobotGotoStationAndLoad(robot, matchesRequired(), requiredStack.getCount(),
                station -> flightLeavesStationInReach(station, builderPos)));
    }

    /** Whether a delivery flight from the block a robot docks at on {@code station} is inside the flight's reach
     *  of {@code builderPos}. */
    static boolean flightLeavesStationInReach(DockingStation station, BlockPos builderPos) {
        BlockPos from = station.side() == null ? station.getPos()
                : AIRobotGotoStation.dockBlock(station.getPos(), station.side());
        return AIRobotGotoBlock.withinFlightReach(from, builderPos);
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotGotoStationAndLoad) {
            if (!ai.success()) {
                // No station could stock this machine's delivery (none holds the item, or none the delivery
                // flight could leave for it). Keeping the machine chosen would repeat the same fetch on every
                // pass, since it still needs the item, and starve every other needy machine (one whose supply
                // station IS in reach). Bench it like a failed delivery and forget it, so the next pass re-picks.
                if (builderToSupply != null) {
                    unreachableUntil.put(builderToSupply.getBuilderPos(), gameTime() + UNREACHABLE_RETRY_TICKS);
                }
                builderToSupply = null;
                requiredStack = ItemStack.EMPTY;
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotRecharge) {
            if (!ai.success()) {
                // No free power station: AIRobotRecharge reports the failure and ends at once, which would
                // leave this board idle and start the same doomed search again on the very next cycle,
                // forever. Go home and sleep like every other failure path — AIRobotMain's own recharge
                // preempt (below SAFETY_POWER) keeps retrying on its 120-tick cooldown meanwhile.
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotGotoBlock) {
            if (!ai.success()) {
                // The flight never arrived (no path, or too far): the cargo is still aboard, and inserting
                // it into a machine the robot never reached would teleport it there. Keep it, and keep
                // that machine off the candidate list for a while so the next pass looks elsewhere (or
                // disposes of the cargo) instead of sending the same doomed flight again. A machine the
                // flight keeps failing to (a sealed room, a detour past the flight's reach) is no better for
                // waiting: after MAX_FAILED_DELIVERIES the cargo goes back and the machine stays benched much
                // longer, instead of costing ~1000 ticks and ~300 MJ of pathfinding on every retry.
                if (builderToSupply != null) {
                    BlockPos pos = builderToSupply.getBuilderPos();
                    if (failedDeliveries.merge(pos, 1, Integer::sum) >= MAX_FAILED_DELIVERIES) {
                        failedDeliveries.remove(pos);
                        unreachableUntil.put(pos, gameTime() + GIVE_UP_RETRY_TICKS);
                        returnCargo = true;
                    } else {
                        unreachableUntil.put(pos, gameTime() + UNREACHABLE_RETRY_TICKS);
                    }
                }
                builderToSupply = null;
                requiredStack = ItemStack.EMPTY;
                return;
            }
            if (builderToSupply == null || builderToSupply.isRemoved()
                    || !acceptsCargo(builderToSupply)) {
                // Defensive: a wrong NBT load or a machine that finished/changed while we flew. The
                // cargo stays aboard — the next pass re-searches, delivers to a machine that requires it,
                // or disposes.
                builderToSupply = null;
                return;
            }

            failedDeliveries.remove(builderToSupply.getBuilderPos());
            boolean leftover = false;
            for (int i = 0; i < robot.getInventorySize(); i++) {
                ItemStack stack = robot.getInventoryStack(i);
                if (stack.isEmpty() || !requires(builderToSupply, stack)) {
                    // Only what the machine still requires goes in; the rest stays aboard for the next pass.
                    continue;
                }
                ItemStack remainder = builderToSupply.getInvResources().insert(stack, false, false);
                if (!remainder.isEmpty()) {
                    leftover = true;
                }
                robot.setInventoryStack(i, remainder);
            }

            builderToSupply = null;
            requiredStack = ItemStack.EMPTY;
            launchingDelay = 20;
            if (leftover) {
                // The machine's grid is full — nothing more to insert this pass. Sleep out the rest
                // of the cycle rather than hammering a full inventory (7.1.x slept on load failures).
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }

    /** The game time the bench windows run on. Package-private so the board's game test can let a window pass
     *  without waiting it out. */
    long gameTime() {
        return robot.level().getGameTime();
    }

    /** Whether {@code builder} is currently off the candidate list because a delivery flight to it failed. */
    boolean isSkipped(TileBuilder builder, long gameTime) {
        Long until = unreachableUntil.get(builder.getBuilderPos());
        return until != null && gameTime < until;
    }

    private IStackFilter matchesRequired() {
        ItemStack target = requiredStack;
        return stack -> StackUtil.canMerge(target, stack);
    }

    /** Package-private so the board's game test can pin the demand predicate against a live machine
     *  without depending on which other builders happen to be loaded in the shared test level. */
    static boolean needsItems(TileBuilder builder) {
        return builder.getBuilder() instanceof BlueprintBuilder blueprintBuilder
                && !blueprintBuilder.remainingRequiredItems.isEmpty();
    }

    /** Whether {@code builder}'s blueprint still misses an item {@code stack} would fill (same item, same
     *  components). Package-private so the board's game test can pin it. */
    static boolean requires(TileBuilder builder, ItemStack stack) {
        if (!(builder.getBuilder() instanceof BlueprintBuilder blueprintBuilder)) {
            return false;
        }
        for (ItemStack missing : blueprintBuilder.remainingRequiredItems) {
            if (StackUtil.canMerge(missing, stack)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code builder} still requires something the robot carries. */
    private boolean acceptsCargo(TileBuilder builder) {
        for (int i = 0; i < robot.getInventorySize(); i++) {
            ItemStack stack = robot.getInventoryStack(i);
            if (!stack.isEmpty() && requires(builder, stack)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code builder} is worth a trip right now. With cargo aboard that means it still requires
     *  something the robot carries (a trip with stone for a machine that wants sand would empty the stone into its
     *  grid); with an empty hold, that it is missing anything at all. */
    private boolean wantsATrip(TileBuilder builder) {
        return robot.containsItems() ? acceptsCargo(builder) : needsItems(builder);
    }

    private static ItemStack firstMissing(TileBuilder builder) {
        if (!(builder.getBuilder() instanceof BlueprintBuilder blueprintBuilder)) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> missing = blueprintBuilder.remainingRequiredItems;
        return missing.isEmpty() ? ItemStack.EMPTY : missing.get(0);
    }

    /** The closest Builder worth a trip ({@link #wantsATrip}: needy with an empty hold, requiring something the
     *  robot carries with cargo aboard) in zone and inside the delivery flight's reach from the robot's block.
     *  {@code includeBenched} also counts machines benched after a failed flight — used only to tell "the need
     *  vanished" from "the need is out of reach". Package-private so the board's game test can pin the reach. */
    TileBuilder findClosestBuilder(boolean includeBenched) {
        double minDistance = Double.MAX_VALUE;
        TileBuilder minBuilder = null;

        IZone zone = robot.getZoneToWork();
        BlockPos robotBlock = robot.blockPosition();

        long now = gameTime();
        unreachableUntil.values().removeIf(until -> now >= until);

        for (TileBuilder builder : BCBuildersEventDist.INSTANCE.getLoadedBuilders(robot.level())) {
            if (builder.getLevel() != robot.level()) {
                continue;
            }
            if (!wantsATrip(builder) || (!includeBenched && isSkipped(builder, now))) {
                continue;
            }
            BlockPos pos = builder.getBuilderPos();
            if (zone != null && !zone.contains(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))) {
                continue;
            }
            if (!AIRobotGotoBlock.withinFlightReach(robotBlock, pos)) {
                continue;
            }

            double dx = robot.position().x - pos.getX();
            double dy = robot.position().y - pos.getY();
            double dz = robot.position().z - pos.getZ();
            double distance = dx * dx + dy * dy + dz * dz;

            if (distance < minDistance) {
                minBuilder = builder;
                minDistance = distance;
            }
        }

        return minBuilder;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        nbt.putInt("launchingDelay", launchingDelay);
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        launchingDelay = NbtApiUtil.getInt(nbt, "launchingDelay", 0);
    }
}
