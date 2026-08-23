/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.RobotManager;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.BCRoboticsPlugs;
import buildcraft.robotics.DockingStationPipe;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.ai.AIRobotShutdown;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Ph3 game tests for the live {@link EntityRobot}: registry lifecycle, persistence, docking, damage/death and
 * the two synchronisation traps the design record calls out. Written FIRST against the Ph3 skeleton, so every
 * assertion here describes the behaviour the implementation still owes.
 *
 * <p>Three pieces of discipline this file inherits from the Ph2 station tests and the entity-arena harness:
 * <ul>
 * <li>{@code RobotRegistry} is a per-level {@code SavedData} shared by every arena running in this batch, so
 *     nothing below is keyed to a whole-registry count — only to this test's own robot id and its own
 *     {@code (pos, side)}. Relative positions are also kept distinct from
 *     {@code RobotStationPluggableTester}'s, because a re-used arena origin would otherwise collide on
 *     {@code registerStation}.</li>
 * <li>Every test force-loads its arena's chunks ({@link EntityArenaUtil#forceLoadEntityArena}): an entity in a
 *     non-force-loaded chunk never ticks, and roughly every assertion here needs the robot to tick. The same
 *     applies to the docking-station pipes — a {@code RobotStationPluggable} only registers its station from
 *     {@code onTick}, so an unloaded chunk means no station at all.</li>
 * <li>Force-loading is where the wait STARTS, not where it ends: the chunk is promoted to entity/block
 *     ticking a variable number of ticks later (measured 0-2+, unbounded under load — see
 *     {@link EntityArenaUtil#forceLoadEntityArena}). So no phase below fires on a hard-coded tick. Each one
 *     is gated on the state it actually needs, via {@link EntityArenaUtil#tickUntil} for single-phase tests
 *     and {@link EntityArenaUtil#tickUntilThen} where a second phase has to follow the first by a fixed
 *     number of ticks.</li>
 * <li>Every relative position stays inside the arena's grid cell — with the {@code minecraft:empty} structure
 *     the framework spaces arenas 6 blocks apart in X and 7 in Z, so anything beyond that lands in a
 *     neighbouring test's arena, which is neither cleared between runs nor safe from being written over in the
 *     same tick.</li>
 * <li>All phases are scheduled from the top of the test, never from inside another scheduled runnable —
 *     {@code GameTestInfo} runs its callbacks while iterating the very map {@code runAfterDelay} writes to.</li>
 * <li>Everything a test ADDS to the world it also DISCARDS before succeeding. The framework's pass-time
 *     cleanup only discards entities inside the structure's own bounds +1, which for {@code minecraft:empty}
 *     is a 3x3x3 box at the arena corner — every robot here sits further out and so SURVIVES its own test's
 *     success. {@code clearOnBatch} then hands the same coordinates to the next batch's tests, where the
 *     leaked robot (empty board, flat battery, still docked to a long-cleared station) lands inside another
 *     test's entity query and fails it. This is not hypothetical: it is exactly how
 *     {@code robot_item_places_docked_robot} kept finding "2 robots" on tick 1.</li>
 * </ul>
 */
public class EntityRobotTester {

    /** One damage point costs this much charge. Chosen, not derived — 7.1.x debited 2600 RF per point at
     *  BuildCraft's canonical 10 RF/MJ bridge. */
    private static final long DAMAGE_DEBIT_PER_POINT = 260L * MjAPI.MJ;

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

    private static void installStation(GameTestHelper helper, BlockPos relPos, Direction side) {
        TilePipeHolder tile = placeItemPipe(helper, relPos);
        tile.replacePluggable(side, new RobotStationPluggable(BCRoboticsPlugs.robotStation, tile, side));
    }

    /** The registered station for a pipe face, or null while {@code RobotStationPluggable.onTick()} has not
     *  yet lazily registered it (two ticks after placement). */
    private static DockingStationPipe stationAt(GameTestHelper helper, BlockPos relPos, Direction side) {
        DockingStation station = RobotManager.registryProvider.getRegistry(helper.getLevel())
                .getStation(helper.absolutePos(relPos), side);
        return station instanceof DockingStationPipe pipe ? pipe : null;
    }

    private static EntityRobot addRobot(GameTestHelper helper, BlockPos relPos) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        // addFreshEntity, deliberately — the same uniform call ItemRobot.useOn must use. EntityType.spawn
        // forks three ways across the nodes and buys nothing here.
        helper.getLevel().addFreshEntity(robot);
        return robot;
    }

    /** Whether {@code robot} has ticked at least once, which is the only thing that gets it an id from the
     *  {@link IRobotRegistry}. Every phase that claims a station has to wait for this: {@code takeAsMain}
     *  stores {@code getRobotId()} verbatim, so a claim made with the {@code NULL_ROBOT_ID} sentinel leaves
     *  {@code isTaken()} reading false and every assertion after it meaningless. */
    private static boolean hasTicked(EntityRobot robot) {
        return robot.getRobotId() != EntityRobotBase.NULL_ROBOT_ID;
    }

    /** The face-centre a docked robot must sit at: block centre pushed half a block out along the mounting
     *  face. Both the tick snap and {@code ItemRobot.useOn}'s initial placement use this. */
    private static Vec3 faceCentre(GameTestHelper helper, BlockPos relPos, Direction side) {
        BlockPos abs = helper.absolutePos(relPos);
        return new Vec3(
                abs.getX() + 0.5 + side.getStepX() * 0.5,
                abs.getY() + 0.5 + side.getStepY() * 0.5,
                abs.getZ() + 0.5 + side.getStepZ() * 0.5);
    }

    // ---------- registry lifecycle ----------

    /** A robot added to the world registers itself with the per-level {@link IRobotRegistry} on its first
     *  tick, is handed a unique non-sentinel id, and can find its own registry again. */
    public static void robotSpawnRegistersWithUniqueId(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(5, 2, 1));
        ServerLevel level = helper.getLevel();
        IRobotRegistry registry = RobotManager.registryProvider.getRegistry(level);

        EntityRobot a = addRobot(helper, new BlockPos(5, 2, 1));
        EntityRobot b = addRobot(helper, new BlockPos(5, 2, 2));

        helper.assertTrue(a.getRobotId() == EntityRobotBase.NULL_ROBOT_ID,
                "precondition: a robot that has not ticked yet still carries the NULL_ROBOT_ID sentinel");

        EntityArenaUtil.tickUntil(helper, 40,
                () -> a.getRobotId() != EntityRobotBase.NULL_ROBOT_ID
                        && b.getRobotId() != EntityRobotBase.NULL_ROBOT_ID,
                () -> {
                    helper.assertTrue(a.getRobotId() != b.getRobotId(),
                            "each robot must get its own id from RobotRegistry.getNextRobotId()");
                    helper.assertTrue(registry.getLoadedRobot(a.getRobotId()) == a,
                            "the registry must hand back the very entity it registered for robot A's id");
                    helper.assertTrue(registry.getLoadedRobot(b.getRobotId()) == b,
                            "the registry must hand back the very entity it registered for robot B's id");
                    helper.assertTrue(a.getRegistry() == registry,
                            "getRegistry() must resolve this level's RobotRegistry — every AI, station claim "
                                    + "and resource reservation goes through it");
                    a.discard();
                    b.discard();
                    helper.succeed();
                },
                "a robot added to the world must register itself with the RobotRegistry on its first tick "
                        + "and be assigned a unique id");
    }

    /** A robot is world-persistent: it saves with its chunk and, unlike a {@code Mob}, has no despawn path at
     *  all — it is still alive, still registered and still exactly where it was left after a long idle. */
    public static void robotPersistsAndDoesNotDespawn(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(5, 4, 1);
        EntityArenaUtil.forceLoadEntityArena(helper, relPos);
        EntityRobot robot = addRobot(helper, relPos);
        Vec3 spawnedAt = Vec3.atCenterOf(helper.absolutePos(relPos));

        helper.assertTrue(robot.shouldBeSaved(), "a robot must ride its chunk's entity save");
        helper.assertTrue(robot.isNoGravity(), "a robot flies — gravity would drag it off its station");

        // Empty first phase on purpose: all it does is pin the start of the idle window to the robot's own
        // first tick, so "60 ticks of idling" really is 60 ticks of TICKING rather than 60 ticks that may
        // have begun before the arena's chunk started ticking at all.
        EntityArenaUtil.tickUntilThen(helper, 120, () -> hasTicked(robot), () -> { }, 60, () -> {
            IRobotRegistry registry = RobotManager.registryProvider.getRegistry(helper.getLevel());
            helper.assertTrue(robot.isAlive(), "a robot must not despawn or expire while idling");
            helper.assertTrue(registry.getLoadedRobot(robot.getRobotId()) == robot,
                    "an idling robot must stay registered — dropping out of the registry silently frees "
                            + "every station and resource it holds");
            helper.assertTrue(robot.position().distanceToSqr(spawnedAt) < 1.0E-6,
                    "an idle robot with no AI must not drift or fall: expected " + spawnedAt
                            + " but was " + robot.position());
            robot.discard();
            helper.succeed();
        }, "the robot never ticked, so the 60-tick idle window never even started");
    }

    // ---------- persistence ----------

    /** A full save/load cycle round-trips everything the robot owns: the battery's single {@code stored}
     *  long, the four transfer slots, the tank, the held item and both station records (7.1.x key names,
     *  modern {@code pos} int[3] + side-byte bodies). */
    public static void robotNbtRoundTripPreservesState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos mainRel = new BlockPos(5, 2, 3);
        BlockPos dockRel = new BlockPos(5, 2, 5);
        installStation(helper, mainRel, Direction.UP);
        installStation(helper, dockRel, Direction.DOWN);
        // Centre on the robot's chunk (5, 3, 4), not the arena origin — the robot only ticks once its
        // whole 5x5 neighbourhood is FULL, and it can land in the arena's second chunk column.
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(5, 3, 4));
        EntityRobot robot = addRobot(helper, new BlockPos(5, 3, 4));

        EntityArenaUtil.tickUntil(helper, 40,
                () -> stationAt(helper, mainRel, Direction.UP) != null
                        && stationAt(helper, dockRel, Direction.DOWN) != null
                        && hasTicked(robot),
                () -> {
            DockingStationPipe main = stationAt(helper, mainRel, Direction.UP);
            DockingStationPipe dock = stationAt(helper, dockRel, Direction.DOWN);

            main.takeAsMain(robot);
            dock.take(robot);
            robot.dock(dock);

            long charge = 4321L * MjAPI.MJ;
            robot.getBattery().addPower(charge, false);
            robot.setInventoryStack(0, new ItemStack(Items.DIAMOND, 3));
            robot.setInventoryStack(3, new ItemStack(Items.REDSTONE, 7));
            robot.setItemInUse(new ItemStack(Items.IRON_PICKAXE));
            int filled = EntityArenaUtil.fillTank(robot, Fluids.WATER, 1500);
            helper.assertTrue(filled == 1500,
                    "precondition: the robot's " + EntityRobot.TANK_CAPACITY + " mB tank must accept 1500 mB "
                            + "of water, but accepted " + filled);

            CompoundTag tag = EntityArenaUtil.saveEntity(level, robot);

            // Exactly what a chunk load does: the registry constructor, then load(). A fresh instance rather
            // than reloading into the same one, so nothing can pass by simply never having been cleared.
            EntityRobot loaded = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
            EntityArenaUtil.loadEntity(level, loaded, tag);

            helper.assertTrue(loaded.getBattery().getStored() == charge,
                    "the battery's stored long must survive the save: expected " + charge + " got "
                            + loaded.getBattery().getStored());
            helper.assertTrue(loaded.getRobotId() == robot.getRobotId(),
                    "the robot id must survive the save, or the reloaded robot orphans its reservations");
            helper.assertTrue(ItemStack.matches(loaded.getInventoryStack(0), new ItemStack(Items.DIAMOND, 3)),
                    "transfer slot 0 must round-trip with its count intact");
            helper.assertTrue(ItemStack.matches(loaded.getInventoryStack(3), new ItemStack(Items.REDSTONE, 7)),
                    "transfer slot 3 must round-trip — the inv list is fixed-size and zero-filled, so a "
                            + "trailing populated slot is the one a naive list write drops");
            helper.assertTrue(loaded.getInventoryStack(1).isEmpty() && loaded.getInventoryStack(2).isEmpty(),
                    "empty transfer slots must load back empty, not shifted down from the populated ones");
            helper.assertTrue(ItemStack.matches(loaded.getHeldItem(), new ItemStack(Items.IRON_PICKAXE)),
                    "the held item (7.1.x itemInUse) must round-trip");
            helper.assertTrue(EntityArenaUtil.tankAmount(loaded) == 1500,
                    "the 4000 mB tank must round-trip: expected 1500 mB got "
                            + EntityArenaUtil.tankAmount(loaded));

            // Station records: 7.1.x key names, modern pos/side bodies (Decision 5). Asserted on the raw tag
            // because a freshly loaded robot re-resolves its stations from the registry during its first
            // tick — the SAVE is what has to be right here.
            CompoundTag linked = NBTUtilBC.getCompound(tag, "linkedStation");
            int[] linkedPos = NBTUtilBC.getIntArray(linked, "pos", new int[0]);
            helper.assertTrue(linkedPos.length == 3
                            && new BlockPos(linkedPos[0], linkedPos[1], linkedPos[2])
                                    .equals(helper.absolutePos(mainRel)),
                    "the main station must be saved under 'linkedStation' as a pos int[3]");
            helper.assertTrue(NBTUtilBC.getByte(linked, "side", (byte) -1) == (byte) Direction.UP.ordinal(),
                    "the main station's mounting face must be saved as a side byte");

            CompoundTag current = NBTUtilBC.getCompound(tag, "currentStation");
            int[] currentPos = NBTUtilBC.getIntArray(current, "pos", new int[0]);
            helper.assertTrue(currentPos.length == 3
                            && new BlockPos(currentPos[0], currentPos[1], currentPos[2])
                                    .equals(helper.absolutePos(dockRel)),
                    "the station the robot is docked at must be saved under 'currentStation'");
            helper.assertTrue(NBTUtilBC.getByte(current, "side", (byte) -1) == (byte) Direction.DOWN.ordinal(),
                    "the docked station's mounting face must be saved as a side byte");
            robot.discard();
            helper.succeed();
        }, "both stations must register and the robot must tick before the save can be set up");
    }

    /** A corrupt (or legacy {@code UNKNOWN} = 6) station side byte must read as "no station", never as an
     *  {@code ArrayIndexOutOfBoundsException} during chunk load — that would be an unloadable world. The
     *  battery assertion is the control: it proves the read actually ran and simply skipped the bad station,
     *  rather than bailing out (or never having read anything in the first place). */
    public static void robotNbtCorruptStationSideIsIgnored(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        ServerLevel level = helper.getLevel();
        EntityRobot source = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 4, 7)));
        source.setPos(pos.x, pos.y, pos.z);
        long charge = 777L * MjAPI.MJ;
        source.getBattery().addPower(charge, false);

        CompoundTag tag = EntityArenaUtil.saveEntity(level, source);
        CompoundTag bad = new CompoundTag();
        bad.putIntArray("pos", new int[] { 0, 64, 0 });
        bad.putByte("side", (byte) 6); // legacy EnumFacing.UNKNOWN — out of range for Direction.values()
        tag.put("linkedStation", bad);
        tag.put("currentStation", bad.copy());

        EntityRobot loaded = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        EntityArenaUtil.loadEntity(level, loaded, tag);

        helper.assertTrue(loaded.getBattery().getStored() == charge,
                "control: the rest of the save must still load past a corrupt station record — expected "
                        + charge + " got " + loaded.getBattery().getStored());
        helper.assertTrue(loaded.getLinkedStation() == null,
                "an out-of-range station side byte must load as 'no station', not crash the chunk load");
        helper.assertTrue(loaded.getDockingStation() == null,
                "an out-of-range docked-station side byte must load as 'not docked'");
        helper.succeed();
    }

    // ---------- docking ----------

    /** A docked robot is pinned to its station's face centre and has its motion zeroed every tick — that snap
     *  is what makes a robot visually sit on its station instead of drifting off it. */
    public static void dockedRobotSnapsToStationFaceCentre(GameTestHelper helper) {
        // z=6, not the old z=7: grid rows are spaced 7 apart for the empty structure, so z=7 is already
        // the next row's first block — the pipe and robot sat inside a neighbouring test's arena.
        BlockPos pipeRel = new BlockPos(5, 2, 6);
        installStation(helper, pipeRel, Direction.UP);
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(3, 4, 6));
        EntityRobot robot = addRobot(helper, new BlockPos(3, 4, 6));

        EntityArenaUtil.tickUntilThen(helper, 60,
                () -> stationAt(helper, pipeRel, Direction.UP) != null && hasTicked(robot),
                () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            station.takeAsMain(robot);
            robot.dock(station);
            // Deliberately give it somewhere else to be: the snap has to WIN against live motion, not merely
            // happen to coincide with a stationary robot.
            robot.setDeltaMovement(new Vec3(0.4, 0.4, 0.4));
        }, 4, () -> {
            Vec3 expected = faceCentre(helper, pipeRel, Direction.UP);
            helper.assertTrue(robot.position().distanceToSqr(expected) < 1.0E-6,
                    "a docked robot must snap to its station's face centre: expected " + expected
                            + " but was " + robot.position());
            helper.assertTrue(robot.getDeltaMovement().lengthSqr() < 1.0E-9,
                    "a docked robot's motion must be zeroed, or it fights the snap every tick");
            robot.discard();
            helper.succeed();
        }, "the station never registered, or the robot never ticked");
    }

    // ---------- damage / death ----------

    /** A hit an undocked robot can pay for debits its battery by {@code 260 MJ} per damage point and raises
     *  the synched hurt flash, which then decays. */
    public static void damageDebitsBatteryAndSetsHurtTime(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(3, 2, 6));
        ServerLevel level = helper.getLevel();
        EntityRobot robot = addRobot(helper, new BlockPos(3, 2, 6));

        EntityArenaUtil.tickUntilThen(helper, 80, () -> hasTicked(robot), () -> {
            long stored = 5000L * MjAPI.MJ;
            robot.getBattery().addPower(stored, false);
            Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
            DamageSource source = level.damageSources().playerAttack(attacker);

            boolean hurt = EntityArenaUtil.hurt(level, robot, source, 2.0F);

            helper.assertTrue(hurt, "an undocked robot with charge to spare must accept the hit");
            long expected = stored - 2L * DAMAGE_DEBIT_PER_POINT;
            helper.assertTrue(robot.getBattery().getStored() == expected,
                    "two damage points must cost exactly 2 x 260 MJ: expected " + expected + " got "
                            + robot.getBattery().getStored());
            helper.assertTrue(robot.isAlive(),
                    "a robot that could pay for the hit survives it (stored - debit > 0)");
            helper.assertTrue(robot.getHurtTime() == 10,
                    "the hurt flash must be written to the synched accessor at damage time, not deferred to "
                            + "the next tick push — a 10-tick flash pushed a tick late is a frame of lie and "
                            + "can be missed entirely");
        }, 25, () -> {
            helper.assertTrue(robot.getHurtTime() == 0,
                    "the hurt flash must decay back to 0 (one per tick from 10), or the robot stays red "
                            + "forever");
            robot.discard();
            helper.succeed();
        }, "the robot never ticked, so it could never have been hit in the first place");
    }

    /** A hit the battery cannot cover converts the robot into items: the robot item drops carrying its
     *  charge AND ITS BOARD (a dead picker must drop a picker robot), the cargo spills, the station
     *  reservation is released and the registry forgets it. Also pins the boundary — 7.1.x used
     *  {@code stored - debit > 0}, so a robot holding EXACTLY the debit is destroyed, not left at zero. */
    public static void batteryExhaustedHitConvertsToItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pipeRel = new BlockPos(5, 2, 2);
        BlockPos robotRel = new BlockPos(5, 3, 2);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);
        installStation(helper, pipeRel, Direction.UP);
        // Boarded, like every robot that exists through real gameplay — a bare entity could never pin
        // that the dropped item carries the board. Seeding the battery BEFORE the first tick keeps the
        // AIRobotMain ladder off the AIRobotShutdown branch (power > 0); at this charge it lands on
        // AIRobotRecharge instead, whose search finds no station it can use here and fails into its
        // 120-tick cooldown while the robot hovers at spawn. The exact-death boundary itself is pinned
        // in the next phase, at the moment of the hit.
        EntityRobot robot = new EntityRobot(helper.getLevel(), BoardRobotPickerNBT.INSTANCE);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(DAMAGE_DEBIT_PER_POINT, false);
        helper.getLevel().addFreshEntity(robot);
        long[] robotId = { EntityRobotBase.NULL_ROBOT_ID };

        EntityArenaUtil.tickUntilThen(helper, 60,
                () -> stationAt(helper, pipeRel, Direction.UP) != null && hasTicked(robot),
                () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            // Linked but NOT docked: a docked robot is invulnerable by design, so this test needs the
            // reservation (to prove death frees it) without the immunity.
            station.takeAsMain(robot);
            robotId[0] = robot.getRobotId();

            robot.setInventoryStack(0, new ItemStack(Items.DIAMOND, 2));
            // Re-pin EXACTLY the debit at the moment of the hit: the boarded robot's AI burns a trickle
            // between spawn and this phase (measured: 0.3 MJ over the registration wait), so the
            // construction-time seed alone no longer lands on the boundary. 'stored - debit > 0' is
            // strictly greater, so landing on precisely zero still destroys the robot — preserved from
            // 7.1.x deliberately.
            robot.getBattery().setStored(DAMAGE_DEBIT_PER_POINT);

            Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
            EntityArenaUtil.hurt(level, robot, level.damageSources().playerAttack(attacker), 1.0F);
        }, 4, () -> {
            IRobotRegistry registry = RobotManager.registryProvider.getRegistry(level);
            helper.assertTrue(robot.isRemoved(),
                    "a robot that cannot pay for a hit converts to items and leaves the world");
            helper.assertTrue(registry.getLoadedRobot(robotId[0]) == null,
                    "a dead robot must be removed from the registry — a leaked entry keeps its station "
                            + "reservations and resource locks alive forever");
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            helper.assertTrue(station != null && !station.isTaken(),
                    "the dead robot's main-station reservation must be released, so the station can be "
                            + "claimed again");

            List<ItemEntity> drops = EntityArenaUtil.droppedItems(helper, robotRel, 2.0);
            ItemStack robotDrop = ItemStack.EMPTY;
            boolean cargoDropped = false;
            for (ItemEntity entity : drops) {
                ItemStack stack = entity.getItem();
                if (stack.getItem() == BCRoboticsItems.ROBOT.get()) {
                    robotDrop = stack;
                } else if (stack.getItem() == Items.DIAMOND && stack.getCount() == 2) {
                    cargoDropped = true;
                }
            }
            helper.assertTrue(!robotDrop.isEmpty(),
                    "converting to items must drop the robot item itself, carrying its board and charge");
            helper.assertTrue(BoardRobotPickerNBT.ID.equals(ItemRobot.getBoardId(robotDrop)),
                    "the dropped robot item must carry the robot's board — a picker that dies must drop "
                            + "a PICKER robot, not an unboarded one: got " + ItemRobot.getBoardId(robotDrop));
            helper.assertTrue(ItemRobot.getEnergy(robotDrop) == DAMAGE_DEBIT_PER_POINT,
                    "the dropped robot item must carry the charge the robot died with (the fatal hit is "
                            + "never debited): expected " + DAMAGE_DEBIT_PER_POINT + " got "
                            + ItemRobot.getEnergy(robotDrop));
            helper.assertTrue(cargoDropped,
                    "the four transfer slots must spill when the robot converts to items");
            // The drops are outside the framework's cleanup bounds — discard exactly what this test made
            // (filtered, because the query radius can reach into the neighbouring arena's cell).
            for (ItemEntity entity : drops) {
                ItemStack stack = entity.getItem();
                if (stack.getItem() == BCRoboticsItems.ROBOT.get() || stack.getItem() == Items.DIAMOND) {
                    entity.discard();
                }
            }
            helper.succeed();
        }, "the station never registered, or the robot never ticked");
    }

    /** A docked robot is invulnerable, and mob / falling-block damage never touches a robot at all — a robot
     *  parked on a station is machinery, not prey. */
    public static void dockedAndFilteredDamageDoesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pipeRel = new BlockPos(5, 2, 4);
        installStation(helper, pipeRel, Direction.UP);
        // Both robots sit in the arena's second chunk column; centre on their chunk.
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(5, 3, 4));
        EntityRobot docked = addRobot(helper, new BlockPos(5, 3, 4));
        EntityRobot airborne = addRobot(helper, new BlockPos(3, 3, 4));

        // The mob only ever supplies a DamageSource; keep it inert so it cannot wander, burn or path.
        BlockPos mobRel = new BlockPos(3, 3, 6);
        //? if >=26.2 {
        /*net.minecraft.world.entity.Mob mob =
                helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, mobRel);*/
        //?} else {
        net.minecraft.world.entity.Mob mob =
                helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, mobRel);
        //?}
        mob.setNoAi(true);
        mob.setNoGravity(true);

        EntityArenaUtil.tickUntil(helper, 40,
                () -> stationAt(helper, pipeRel, Direction.UP) != null
                        && hasTicked(docked) && hasTicked(airborne),
                () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            station.takeAsMain(docked);
            docked.dock(station);

            long charge = 5000L * MjAPI.MJ;
            docked.getBattery().addPower(charge, false);
            airborne.getBattery().addPower(charge, false);

            Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
            boolean hitDocked =
                    EntityArenaUtil.hurt(level, docked, level.damageSources().playerAttack(attacker), 4.0F);
            helper.assertFalse(hitDocked, "a docked robot must reject damage outright");
            helper.assertTrue(docked.getBattery().getStored() == charge,
                    "a docked robot's battery must be untouched by a hit");
            helper.assertTrue(docked.getHurtTime() == 0, "a docked robot must not even flash");
            helper.assertTrue(docked.isAlive(), "a docked robot must survive being hit");

            boolean hitByMob =
                    EntityArenaUtil.hurt(level, airborne, level.damageSources().mobAttack(mob), 4.0F);
            helper.assertFalse(hitByMob, "mob damage must never reach a robot");
            helper.assertTrue(airborne.getBattery().getStored() == charge,
                    "a mob hit must not drain a robot's battery");

            FallingBlockEntity falling = FallingBlockEntity.fall(level,
                    helper.absolutePos(new BlockPos(3, 6, 4)), Blocks.SAND.defaultBlockState());
            boolean hitByBlock = EntityArenaUtil.hurt(level, airborne,
                    level.damageSources().fallingBlock(falling), 4.0F);
            helper.assertFalse(hitByBlock, "falling-block damage must never reach a robot");
            helper.assertTrue(airborne.getBattery().getStored() == charge,
                    "a falling block must not drain a robot's battery");
            helper.assertTrue(airborne.getHurtTime() == 0,
                    "neither filtered damage source may raise the hurt flash");

            // Positive control. Without it every assertion above is satisfied by a robot that simply ignores
            // ALL damage — the point is that the filter is selective, not blanket.
            boolean hitAirborne =
                    EntityArenaUtil.hurt(level, airborne, level.damageSources().playerAttack(attacker), 4.0F);
            helper.assertTrue(hitAirborne,
                    "control: the very same undocked robot MUST take a player hit — otherwise the "
                            + "docked/mob/falling-block exemptions above prove nothing");
            helper.assertTrue(airborne.getBattery().getStored() == charge - 4L * DAMAGE_DEBIT_PER_POINT,
                    "control: the player hit must debit 4 x 260 MJ");
            docked.discard();
            airborne.discard();
            mob.discard();
            helper.succeed();
        }, "the station never registered, or one of the two robots never ticked");
    }

    // ---------- synchronisation ----------

    /** The {@code ItemStack} identity trap. {@code ItemStack} does not override {@code equals()} and
     *  {@code SynchedEntityData.set} gates dirtiness on value inequality, so a slot mutated IN PLACE and
     *  re-pushed is a silent no-op — the client keeps rendering the stale stack. The correct fix (a
     *  server-side shadow copy plus an {@code ItemStack.matches} guard) has to satisfy BOTH halves of this
     *  test: a real change must go dirty, and an unchanged re-push must NOT (or the robot re-sends five
     *  stacks at 20 Hz forever). */
    public static void inPlaceInventoryMutationPropagatesToSynchedData(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(5, 4, 3));
        EntityRobot robot = addRobot(helper, new BlockPos(5, 4, 3));

        // One scheduled block, no ticks in between: a tick could dirty an unrelated accessor (ENERGY_MJ,
        // SLEEPING, …) and make the dirty flag meaningless.
        EntityArenaUtil.tickUntil(helper, 40, () -> hasTicked(robot), () -> {
            ItemStack stack = new ItemStack(Items.DIAMOND, 1);
            robot.setInventoryStack(0, stack);
            helper.assertTrue(ItemStack.matches(robot.getInventoryStack(0), new ItemStack(Items.DIAMOND, 1)),
                    "precondition: the slot took the stack");

            robot.getEntityData().packDirty();
            helper.assertFalse(robot.getEntityData().isDirty(),
                    "precondition: synched data is clean after packing");

            // Mutate the SAME instance the robot was handed, exactly as an AI moving items would.
            stack.grow(3);
            robot.setInventoryStack(0, stack);

            helper.assertTrue(robot.getEntityData().isDirty(),
                    "an in-place stack mutation must still mark the slot's synched accessor dirty — "
                            + "ItemStack has no equals(), so re-setting the same instance is a no-op and the "
                            + "client never sees the change");
            helper.assertTrue(robot.getInventoryStack(0).getCount() == 4,
                    "the slot must read back the mutated count");

            robot.getEntityData().packDirty();
            robot.setInventoryStack(0, stack);
            helper.assertFalse(robot.getEntityData().isDirty(),
                    "re-pushing an UNCHANGED stack must not dirty the accessor — an unconditional copy() "
                            + "every tick re-sends every slot at 20 Hz");
            robot.discard();
            helper.succeed();
        }, "the robot never ticked");
    }

    // ---------- charging ----------

    /** The charge hand-off a docking station exposes for a docked robot. Three things have to hold at once:
     *  the receiver is still an {@link IMjReadable} (the "Energy Stored" gate trigger reads a docked robot's
     *  charge through it), a simulated receive commits nothing, and a real one shows up on the robot's
     *  synched whole-MJ energy so the renderer's charge bar can track it.
     *
     *  <p>The {@code ticksCharging} latch (Decision 6) is pinned here too, now that
     *  {@code EntityRobot.getTicksCharging()} exposes it read-only — the skeleton had no accessor and this
     *  test asked for one. It matters because the latch is what keeps a recharging robot LOOKING awake, and
     *  the two in-repo callers that probe a receiver with {@code simulate == true} every tick (the obsidian
     *  pipe behaviour and the pulsar pluggable) would otherwise pin every robot next to a pipe as "charging"
     *  forever, whether or not a single MJ ever arrived. */
    public static void dockedRobotChargeIsReadableAndSimulateIsInert(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(5, 4, 5);
        installStation(helper, pipeRel, Direction.UP);
        EntityArenaUtil.forceLoadEntityArena(helper, new BlockPos(5, 5, 5));
        EntityRobot robot = addRobot(helper, new BlockPos(5, 5, 5));
        long delivered = 1200L * MjAPI.MJ;

        EntityArenaUtil.tickUntilThen(helper, 60,
                () -> stationAt(helper, pipeRel, Direction.UP) != null && hasTicked(robot),
                () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            station.takeAsMain(robot);
            robot.dock(station);

            IMjReceiver receiver = robot.getChargeReceiver();
            helper.assertTrue(receiver != null, "a docked robot must expose an MJ receiver");
            helper.assertTrue(receiver instanceof IMjReadable,
                    "the robot's charge receiver must stay an IMjReadable — TriggerPower instanceof-checks "
                            + "the station's receiver to read a docked robot's charge, and a receiver-only "
                            + "wrapper kills that gate trigger with no other symptom");

            int latchBefore = robot.getTicksCharging();

            long excess = receiver.receivePower(delivered, true);
            helper.assertTrue(excess == 0, "well under capacity: a simulated receive rejects nothing");
            helper.assertTrue(robot.getBattery().getStored() == 0,
                    "a SIMULATED receive must commit nothing — the obsidian pipe and the pulsar both probe "
                            + "with simulate == true every tick");
            helper.assertTrue(robot.getTicksCharging() == latchBefore,
                    "a SIMULATED receive must not move the ticksCharging latch either: the two in-repo "
                            + "simulating callers probe every tick, so a latch that moved on a probe would "
                            + "report every robot beside a pipe as permanently charging");

            helper.assertTrue(receiver.receivePower(delivered, false) == 0,
                    "a real receive well under capacity rejects nothing");
            helper.assertTrue(robot.getBattery().getStored() == delivered,
                    "the charge hand-off is lossless");
            helper.assertTrue(((IMjReadable) receiver).getStored() == delivered,
                    "the readable view must report the robot's live charge");
            helper.assertTrue(robot.getTicksCharging() > latchBefore,
                    "control: a REAL receive above the detection threshold must move the latch — otherwise "
                            + "the simulate assertion above is satisfied by a latch that never moves at all");
        }, 4, () -> {
            int expectedMj = (int) (delivered / MjAPI.MJ);
            helper.assertTrue(robot.getEnergyMj() == expectedMj,
                    "the synched energy accessor carries WHOLE MJ (0.." + (EntityRobotBase.MAX_POWER / MjAPI.MJ)
                            + ") and must track the battery, or the renderer's charge overlay never fills: "
                            + "expected " + expectedMj + " got " + robot.getEnergyMj());
            robot.discard();
            helper.succeed();
        }, "the station never registered, or the robot never ticked");
    }

    /** {@code RobotTransactor.insert} serves the load/unload AIs' dry-run-then-commit flow, so two halves
     *  of the {@link buildcraft.lib.inventory.AbstractInvItemTransactor} contract are load-bearing: a
     *  simulated insert must not mutate the caller's stack OR the inventory, and a commit must deliver
     *  exactly what the simulate pass previewed. The first cut failed both — {@code stack.split(...)} and
     *  {@code stack.shrink(...)} ran before {@code simulate} was consulted, so an all-or-none insert that
     *  previewed "fully accepted" then committed the ALREADY-SHRUNK stack and silently deleted the items.
     *  Driven synchronously on an unadded robot: the transactor is a plain adapter over the slot array and
     *  needs no ticking. */
    public static void transactorInsertConservesItemsAcrossSimulateAndCommit(GameTestHelper helper) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 4, 1)));
        robot.setPos(pos.x, pos.y, pos.z);

        // -- empty-slot path: simulate is inert --
        ItemStack offered = new ItemStack(Items.DIAMOND, 10);
        ItemStack leftover = robot.getTransactor().insert(offered, false, true);
        helper.assertTrue(offered.getCount() == 10,
                "a SIMULATED insert must not mutate the caller's stack — the unload AI dry-runs with the "
                        + "very stack it then inserts for real, so a mutating dry-run loses the probed items");
        helper.assertTrue(leftover.isEmpty(), "10 diamonds into an empty robot: nothing left over");
        helper.assertTrue(robot.getInventoryStack(0).isEmpty(),
                "a SIMULATED insert must not change the inventory");

        // -- merge path, spanning a partial merge into a fresh slot: simulate is inert --
        robot.setInventoryStack(0, new ItemStack(Items.DIAMOND, 60));
        ItemStack offered2 = new ItemStack(Items.DIAMOND, 10);
        ItemStack leftover2 = robot.getTransactor().insert(offered2, false, true);
        helper.assertTrue(offered2.getCount() == 10,
                "a SIMULATED merge must not mutate the caller's stack either");
        helper.assertTrue(leftover2.isEmpty(),
                "4 merge into slot 0 and 6 into the next empty slot: nothing left over");
        helper.assertTrue(robot.getInventoryStack(0).getCount() == 60
                        && robot.getInventoryStack(1).isEmpty(),
                "a SIMULATED merge must not change the inventory");

        // -- the real commit delivers exactly what the dry-run previewed --
        ItemStack leftover3 = robot.getTransactor().insert(new ItemStack(Items.DIAMOND, 10), false, false);
        helper.assertTrue(leftover3.isEmpty(), "the commit accepts what the dry-run accepted");
        helper.assertTrue(robot.getInventoryStack(0).getCount() == 64
                        && robot.getInventoryStack(1).getCount() == 6,
                "commit: slot 0 tops up to 64 and the remaining 6 land in slot 1");

        // -- all-or-none: the internal simulate-then-commit chain must conserve the items --
        ItemStack leftover4 = robot.getTransactor().insert(new ItemStack(Items.DIAMOND, 20), true, false);
        helper.assertTrue(leftover4.isEmpty(), "20 diamonds fit (slot 1 has 58 free): all-or-none accepts");
        helper.assertTrue(robot.getInventoryStack(0).getCount() == 64
                        && robot.getInventoryStack(1).getCount() == 26,
                "all-or-none must deliver the previewed items — a simulate pass that mutates the original "
                        + "stack leaves the commit pass nothing to insert, deleting the cargo while "
                        + "reporting full acceptance");
        helper.succeed();
    }

    // ---------- shutdown ----------

    /** {@code AIRobotShutdown} re-imposes only the FALL each tick, never the horizontal motion it captured
     *  at construction — 7.1.x pinned {@code motionY} alone and let physics spend the drift. The first port
     *  re-applied the captured X/Z every unblocked tick, so a robot whose drift had already died (it clipped
     *  a wall mid-fall) started sliding again. Driven synchronously on an unadded robot: the AI reads motion
     *  and the collision set, neither of which needs a ticking entity, and the void arena below guarantees
     *  the unblocked branch. */
    public static void shutdownFallKeepsCurrentHorizontalMotion(GameTestHelper helper) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 5, 3)));
        robot.setPos(pos.x, pos.y, pos.z);

        robot.setDeltaMovement(new Vec3(0.5, 0, 0));
        AIRobotShutdown ai = new AIRobotShutdown(robot); // captures the pre-shutdown drift, as 7.1.x did
        ai.start(); // now falling with (0.5, -0.075, 0)

        // Mid-fall the drift dies — a wall, a collision, anything that kills X without stopping the fall.
        robot.setDeltaMovement(new Vec3(0, -0.075, 0));
        ai.update();

        helper.assertTrue(robot.getDeltaMovement().x == 0,
                "a shut-down robot must fall with its CURRENT horizontal motion — re-imposing the velocity "
                        + "captured at construction resurrects drift that physics already spent");
        helper.assertTrue(robot.getDeltaMovement().y < 0, "the fall itself must continue");
        helper.succeed();
    }
}
