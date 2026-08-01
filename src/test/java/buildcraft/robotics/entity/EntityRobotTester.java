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
 *     non-force-loaded chunk never ticks, and roughly every assertion here needs the robot to tick.</li>
 * <li>All phases are scheduled from the top of the test, never from inside another scheduled runnable —
 *     {@code GameTestInfo} runs its callbacks while iterating the very map {@code runAfterDelay} writes to.</li>
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
        EntityArenaUtil.forceLoadEntityArena(helper);
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
                    helper.succeed();
                },
                "a robot added to the world must register itself with the RobotRegistry on its first tick "
                        + "and be assigned a unique id");
    }

    /** A robot is world-persistent: it saves with its chunk and, unlike a {@code Mob}, has no despawn path at
     *  all — it is still alive, still registered and still exactly where it was left after a long idle. */
    public static void robotPersistsAndDoesNotDespawn(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos relPos = new BlockPos(5, 4, 1);
        EntityRobot robot = addRobot(helper, relPos);
        Vec3 spawnedAt = Vec3.atCenterOf(helper.absolutePos(relPos));

        helper.assertTrue(robot.shouldBeSaved(), "a robot must ride its chunk's entity save");
        helper.assertTrue(robot.isNoGravity(), "a robot flies — gravity would drag it off its station");

        helper.runAfterDelay(60, () -> {
            IRobotRegistry registry = RobotManager.registryProvider.getRegistry(helper.getLevel());
            helper.assertTrue(robot.isAlive(), "a robot must not despawn or expire while idling");
            helper.assertTrue(registry.getLoadedRobot(robot.getRobotId()) == robot,
                    "an idling robot must stay registered — dropping out of the registry silently frees "
                            + "every station and resource it holds");
            helper.assertTrue(robot.position().distanceToSqr(spawnedAt) < 1.0E-6,
                    "an idle robot with no AI must not drift or fall: expected " + spawnedAt
                            + " but was " + robot.position());
            helper.succeed();
        });
    }

    // ---------- persistence ----------

    /** A full save/load cycle round-trips everything the robot owns: the battery's single {@code stored}
     *  long, the four transfer slots, the tank, the held item and both station records (7.1.x key names,
     *  modern {@code pos} int[3] + side-byte bodies). */
    public static void robotNbtRoundTripPreservesState(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        ServerLevel level = helper.getLevel();
        BlockPos mainRel = new BlockPos(5, 2, 3);
        BlockPos dockRel = new BlockPos(5, 2, 5);
        installStation(helper, mainRel, Direction.UP);
        installStation(helper, dockRel, Direction.DOWN);
        EntityRobot robot = addRobot(helper, new BlockPos(5, 3, 4));

        helper.runAfterDelay(5, () -> {
            DockingStationPipe main = stationAt(helper, mainRel, Direction.UP);
            DockingStationPipe dock = stationAt(helper, dockRel, Direction.DOWN);
            helper.assertTrue(main != null && dock != null, "precondition: both stations registered");
            helper.assertTrue(robot.getRobotId() != EntityRobotBase.NULL_ROBOT_ID,
                    "precondition: the robot registered and holds a real id");

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
            helper.succeed();
        });
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
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(5, 2, 7);
        installStation(helper, pipeRel, Direction.UP);
        EntityRobot robot = addRobot(helper, new BlockPos(7, 4, 7));

        helper.runAfterDelay(5, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            helper.assertTrue(station != null, "precondition: the station registered");
            station.takeAsMain(robot);
            robot.dock(station);
            // Deliberately give it somewhere else to be: the snap has to WIN against live motion, not merely
            // happen to coincide with a stationary robot.
            robot.setDeltaMovement(new Vec3(0.4, 0.4, 0.4));
        });

        helper.runAfterDelay(9, () -> {
            Vec3 expected = faceCentre(helper, pipeRel, Direction.UP);
            helper.assertTrue(robot.position().distanceToSqr(expected) < 1.0E-6,
                    "a docked robot must snap to its station's face centre: expected " + expected
                            + " but was " + robot.position());
            helper.assertTrue(robot.getDeltaMovement().lengthSqr() < 1.0E-9,
                    "a docked robot's motion must be zeroed, or it fights the snap every tick");
            helper.succeed();
        });
    }

    // ---------- damage / death ----------

    /** A hit an undocked robot can pay for debits its battery by {@code 260 MJ} per damage point and raises
     *  the synched hurt flash, which then decays. */
    public static void damageDebitsBatteryAndSetsHurtTime(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        ServerLevel level = helper.getLevel();
        EntityRobot robot = addRobot(helper, new BlockPos(5, 2, 9));

        helper.runAfterDelay(5, () -> {
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
        });

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(robot.getHurtTime() == 0,
                    "the hurt flash must decay back to 0 (one per tick from 10), or the robot stays red "
                            + "forever");
            helper.succeed();
        });
    }

    /** A hit the battery cannot cover converts the robot into items: the charged robot item plus its cargo
     *  drop, the station reservation is released and the registry forgets it. Also pins the boundary — 7.1.x
     *  used {@code stored - debit > 0}, so a robot holding EXACTLY the debit is destroyed, not left at zero. */
    public static void batteryExhaustedHitConvertsToItems(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pipeRel = new BlockPos(5, 2, 11);
        BlockPos robotRel = new BlockPos(5, 3, 11);
        installStation(helper, pipeRel, Direction.UP);
        EntityRobot robot = addRobot(helper, robotRel);
        long[] robotId = { EntityRobotBase.NULL_ROBOT_ID };

        helper.runAfterDelay(5, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            helper.assertTrue(station != null, "precondition: the station registered");
            // Linked but NOT docked: a docked robot is invulnerable by design, so this test needs the
            // reservation (to prove death frees it) without the immunity.
            station.takeAsMain(robot);
            robotId[0] = robot.getRobotId();
            helper.assertTrue(robotId[0] != EntityRobotBase.NULL_ROBOT_ID,
                    "precondition: the robot registered and holds a real id");

            robot.setInventoryStack(0, new ItemStack(Items.DIAMOND, 2));
            // Exactly the debit for a single damage point: 'stored - debit > 0' is strictly greater, so
            // landing on precisely zero still destroys the robot. Preserved from 7.1.x deliberately.
            robot.getBattery().addPower(DAMAGE_DEBIT_PER_POINT, false);

            Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
            EntityArenaUtil.hurt(level, robot, level.damageSources().playerAttack(attacker), 1.0F);
        });

        helper.runAfterDelay(9, () -> {
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
            helper.assertTrue(ItemRobot.getEnergy(robotDrop) == DAMAGE_DEBIT_PER_POINT,
                    "the dropped robot item must carry the charge the robot died with (the fatal hit is "
                            + "never debited): expected " + DAMAGE_DEBIT_PER_POINT + " got "
                            + ItemRobot.getEnergy(robotDrop));
            helper.assertTrue(cargoDropped,
                    "the four transfer slots must spill when the robot converts to items");
            helper.succeed();
        });
    }

    /** A docked robot is invulnerable, and mob / falling-block damage never touches a robot at all — a robot
     *  parked on a station is machinery, not prey. */
    public static void dockedAndFilteredDamageDoesNothing(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pipeRel = new BlockPos(5, 2, 13);
        installStation(helper, pipeRel, Direction.UP);
        EntityRobot docked = addRobot(helper, new BlockPos(5, 3, 13));
        EntityRobot airborne = addRobot(helper, new BlockPos(7, 3, 13));

        // The mob only ever supplies a DamageSource; keep it inert so it cannot wander, burn or path.
        BlockPos mobRel = new BlockPos(7, 3, 15);
        //? if >=26.2 {
        /*net.minecraft.world.entity.Mob mob =
                helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, mobRel);*/
        //?} else {
        net.minecraft.world.entity.Mob mob =
                helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, mobRel);
        //?}
        mob.setNoAi(true);
        mob.setNoGravity(true);

        helper.runAfterDelay(5, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            helper.assertTrue(station != null, "precondition: the station registered");
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
                    helper.absolutePos(new BlockPos(7, 6, 13)), Blocks.SAND.defaultBlockState());
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
            helper.succeed();
        });
    }

    // ---------- synchronisation ----------

    /** The {@code ItemStack} identity trap. {@code ItemStack} does not override {@code equals()} and
     *  {@code SynchedEntityData.set} gates dirtiness on value inequality, so a slot mutated IN PLACE and
     *  re-pushed is a silent no-op — the client keeps rendering the stale stack. The correct fix (a
     *  server-side shadow copy plus an {@code ItemStack.matches} guard) has to satisfy BOTH halves of this
     *  test: a real change must go dirty, and an unchanged re-push must NOT (or the robot re-sends five
     *  stacks at 20 Hz forever). */
    public static void inPlaceInventoryMutationPropagatesToSynchedData(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        EntityRobot robot = addRobot(helper, new BlockPos(5, 4, 3));

        // One scheduled block, no ticks in between: a tick could dirty an unrelated accessor (ENERGY_MJ,
        // SLEEPING, …) and make the dirty flag meaningless.
        helper.runAfterDelay(5, () -> {
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
            helper.succeed();
        });
    }

    // ---------- charging ----------

    /** The charge hand-off a docking station exposes for a docked robot. Three things have to hold at once:
     *  the receiver is still an {@link IMjReadable} (the "Energy Stored" gate trigger reads a docked robot's
     *  charge through it), a simulated receive commits nothing, and a real one shows up on the robot's
     *  synched whole-MJ energy so the renderer's charge bar can track it.
     *
     *  <p>Not asserted here: the {@code ticksCharging} latch (Decision 6) that must NOT move on a simulated
     *  transfer. It has no public accessor on the Ph3 skeleton and reflecting at it would pin an
     *  implementation detail; the honest fix is to expose a read-only accessor on
     *  {@code RobotChargeReceiver}/{@code EntityRobot} when the latch lands and extend this test then. What
     *  IS pinned below is the observable half — simulate must leave the battery, and everything downstream
     *  of it, completely untouched. */
    public static void dockedRobotChargeIsReadableAndSimulateIsInert(GameTestHelper helper) {
        EntityArenaUtil.forceLoadEntityArena(helper);
        BlockPos pipeRel = new BlockPos(5, 4, 5);
        installStation(helper, pipeRel, Direction.UP);
        EntityRobot robot = addRobot(helper, new BlockPos(5, 5, 5));
        long delivered = 1200L * MjAPI.MJ;

        helper.runAfterDelay(5, () -> {
            DockingStationPipe station = stationAt(helper, pipeRel, Direction.UP);
            helper.assertTrue(station != null, "precondition: the station registered");
            station.takeAsMain(robot);
            robot.dock(station);

            IMjReceiver receiver = robot.getChargeReceiver();
            helper.assertTrue(receiver != null, "a docked robot must expose an MJ receiver");
            helper.assertTrue(receiver instanceof IMjReadable,
                    "the robot's charge receiver must stay an IMjReadable — TriggerPower instanceof-checks "
                            + "the station's receiver to read a docked robot's charge, and a receiver-only "
                            + "wrapper kills that gate trigger with no other symptom");

            long excess = receiver.receivePower(delivered, true);
            helper.assertTrue(excess == 0, "well under capacity: a simulated receive rejects nothing");
            helper.assertTrue(robot.getBattery().getStored() == 0,
                    "a SIMULATED receive must commit nothing — the obsidian pipe and the pulsar both probe "
                            + "with simulate == true every tick");

            helper.assertTrue(receiver.receivePower(delivered, false) == 0,
                    "a real receive well under capacity rejects nothing");
            helper.assertTrue(robot.getBattery().getStored() == delivered,
                    "the charge hand-off is lossless");
            helper.assertTrue(((IMjReadable) receiver).getStored() == delivered,
                    "the readable view must report the robot's live charge");
        });

        helper.runAfterDelay(9, () -> {
            int expectedMj = (int) (delivered / MjAPI.MJ);
            helper.assertTrue(robot.getEnergyMj() == expectedMj,
                    "the synched energy accessor carries WHOLE MJ (0.." + (EntityRobotBase.MAX_POWER / MjAPI.MJ)
                            + ") and must track the battery, or the renderer's charge overlay never fills: "
                            + "expected " + expectedMj + " got " + robot.getEnergyMj());
            helper.succeed();
        });
    }
}
