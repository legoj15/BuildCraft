/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.ai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.StatementSlot;
import buildcraft.core.BCCoreConfig;
import buildcraft.lib.misc.BreakEventCompat;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.DockingStationPipe;
import buildcraft.robotics.RobotProtection;
import buildcraft.robotics.boards.BoardRobotBomber;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * A robot answers to two players: the one who deployed it and the owner of its home station. It acts as its deployer
 * (claim mods judge, and logs attribute to, the player who put it to work), and nothing it does may go through unless
 * protection mods permit BOTH: a robot whose deployer a claim refuses is refused on a station whose owner is trusted
 * there, and a robot whose station's owner is refused is refused although its deployer is trusted. Pinned on a break
 * (the shared break probe) and on a planting (an action that asks through the leased player's own events, guarded
 * for the station's owner by a placement probe), on a stripes use reaching past its reserved cell, on the bomber's
 * drop (asked at the block the TNT lands on, which is then the deployer's) and on its blast (every block and entity
 * it takes, and the blast of any TNT it sets off), and on a melee swing (asked as a player's attack, landed naming the
 * deployer, and holding the weapon so a kill gets its Looting). A robot saved before deployers were recorded has none
 * and acts as its station's owner alone, as before. A station remembers its owner while its pipe cannot be resolved,
 * and one that cannot name its owner at all refuses everything.
 *
 * <p>Like {@link RobotPumpProtectionTester}, the AIs are cycled by hand on a robot that is never added to the level.
 */
public class RobotDeployerProtectionTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;
    /** Enough cycles for the plant AI's 40-cycle wind-up and its one attempt. */
    private static final int PLANT_CYCLES = 45;
    /** Enough cycles for a diamond pickaxe to break stone (6 are needed). */
    private static final int BREAK_CYCLES = 40;

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    private static void requireProtectionChecksOn() {
        check(!BCCoreConfig.minePlayerProtected.get(),
                "precondition: minePlayerProtected must be off (the default), or no machine asks at all");
    }

    private static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
    }

    /** A robot deployed by {@code deployer} (null: none recorded) on a home station owned by {@code owner}. */
    private static EntityRobot robot(GameTestHelper helper, BlockPos relPos, @Nullable GameProfile deployer,
            GameProfile owner) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        robot.setDeployer(deployer);
        robot.setMainStation(new OwnedStation(helper.absolutePos(relPos), owner));
        return robot;
    }

    // ── Breaking ────────────────────────────────────────────────────────────

    /** Swings a fresh break AI at {@code stone} until it breaks or the cycles run out; whether it broke. */
    private static boolean breakWith(GameTestHelper helper, EntityRobot robot, BlockPos stoneRel) {
        ServerLevel level = helper.getLevel();
        BlockPos stone = helper.absolutePos(stoneRel);
        robot.setItemInUse(new ItemStack(Items.DIAMOND_PICKAXE));
        AIRobotBreak breaker = new AIRobotBreak(robot, stone);
        for (int cycles = 0; cycles < BREAK_CYCLES && !level.getBlockState(stone).isAir(); cycles++) {
            breaker.cycle();
        }
        for (ItemEntity drop : EntityArenaUtil.droppedItems(helper, stoneRel, 0.75)) {
            drop.discard();
        }
        return level.getBlockState(stone).isAir();
    }

    /** The break probe has to clear the deployer AND the home station's owner; the swings themselves are the
     *  deployer's; and a robot deployed by its station's own owner is asked about once, not twice. */
    public static void breakNeedsDeployerAndStationOwner(GameTestHelper helper) {
        requireProtectionChecksOn();
        GameProfile deployer = profile("bc_deployer_break_deployer");
        GameProfile owner = profile("bc_deployer_break_owner");
        BlockPos stoneRel = new BlockPos(2, 1, 2);
        BlockPos stone = helper.absolutePos(stoneRel);
        BlockPos robotRel = stoneRel.above();

        helper.setBlock(stoneRel, Blocks.STONE);
        try (PlayerClaim claim = new PlayerClaim(stone, deployer)) {
            check(!breakWith(helper, robot(helper, robotRel, deployer, owner), stoneRel),
                    "a robot whose deployer the claim refuses must not break the block, although its station's "
                            + "owner is trusted there");
            check(claim.breakAsks.contains(GameProfileUtil.getId(deployer)), "the deployer was asked about");
        }

        helper.setBlock(stoneRel, Blocks.STONE);
        try (PlayerClaim claim = new PlayerClaim(stone, owner)) {
            check(!breakWith(helper, robot(helper, robotRel, deployer, owner), stoneRel),
                    "a robot whose station's owner the claim refuses must not break the block, although its "
                            + "deployer is trusted there");
            check(claim.breakAsks.contains(GameProfileUtil.getId(owner)), "the station's owner was asked about");
        }

        helper.setBlock(stoneRel, Blocks.STONE);
        List<UUID> swingers = new ArrayList<>();
        Consumer<PlayerEvent.BreakSpeed> swingObserver = event -> {
            if (event.getPosition().filter(stone::equals).isPresent()) {
                swingers.add(event.getEntity().getUUID());
            }
        };
        NeoForge.EVENT_BUS.addListener(PlayerEvent.BreakSpeed.class, swingObserver);
        int twoPlayerAsks;
        try (PlayerClaim claim = new PlayerClaim(stone, null)) {
            check(breakWith(helper, robot(helper, robotRel, deployer, owner), stoneRel),
                    "a robot whose deployer and station's owner are both trusted breaks the block");
            check(claim.breakAsks.contains(GameProfileUtil.getId(deployer))
                            && claim.breakAsks.contains(GameProfileUtil.getId(owner)),
                    "both players were asked about, got " + claim.breakAsks);
            twoPlayerAsks = claim.breakAsks.size();
        } finally {
            NeoForge.EVENT_BUS.unregister(swingObserver);
        }
        check(!swingers.isEmpty() && swingers.stream().allMatch(GameProfileUtil.getId(deployer)::equals),
                "the robot swings as its deployer, got " + swingers);

        helper.setBlock(stoneRel, Blocks.STONE);
        try (PlayerClaim claim = new PlayerClaim(stone, null)) {
            // A distinct profile object for the same player: the two are compared by id.
            check(breakWith(helper, robot(helper, robotRel, profile("bc_deployer_break_owner"), owner), stoneRel),
                    "a robot deployed by its station's owner breaks a block that player may break");
            // Relative to the two-player break, not an exact count: how many events the break itself posts is the
            // running version's business, and only the probe for a second player may go.
            check(claim.breakAsks.size() < twoPlayerAsks,
                    "a robot deployed by its station's owner is not asked about twice, got " + claim.breakAsks
                            + " against " + twoPlayerAsks + " for two players");
        }
        helper.succeed();
    }

    // ── Planting ────────────────────────────────────────────────────────────

    /** Runs a fresh plant AI with wheat seeds on the farmland under {@code cropRel}; whether wheat grew there. */
    private static boolean plantWith(GameTestHelper helper, EntityRobot robot, BlockPos cropRel) {
        ServerLevel level = helper.getLevel();
        BlockPos crop = helper.absolutePos(cropRel);
        robot.setItemInUse(new ItemStack(Items.WHEAT_SEEDS));
        AIRobotPlant planter = new AIRobotPlant(robot, crop.below());
        for (int cycles = 0; cycles < PLANT_CYCLES && !level.getBlockState(crop).is(Blocks.WHEAT); cycles++) {
            planter.cycle();
        }
        for (ItemEntity drop : EntityArenaUtil.droppedItems(helper, new BlockPos(3, 2, 2), 1.5)) {
            drop.discard();
        }
        return level.getBlockState(crop).is(Blocks.WHEAT);
    }

    /** The planting goes through the leased player's own events, so the station's owner is asked by a placement
     *  probe before it: both must be permitted, the seed goes in as the deployer, and a robot deployed by its
     *  station's own owner is not asked the probe on top of its own placement. */
    public static void plantNeedsDeployerAndStationOwner(GameTestHelper helper) {
        requireProtectionChecksOn();
        GameProfile deployer = profile("bc_deployer_plant_deployer");
        GameProfile owner = profile("bc_deployer_plant_owner");
        BlockPos cropRel = new BlockPos(2, 2, 2);
        BlockPos crop = helper.absolutePos(cropRel);
        BlockPos robotRel = new BlockPos(3, 2, 2);
        helper.setBlock(cropRel.below(), Blocks.FARMLAND);
        helper.setBlock(cropRel.above(), Blocks.GLOWSTONE);

        try (PlayerClaim claim = new PlayerClaim(crop, deployer)) {
            check(!plantWith(helper, robot(helper, robotRel, deployer, owner), cropRel),
                    "a robot whose deployer the claim refuses must not plant, although its station's owner is "
                            + "trusted there");
        }

        try (PlayerClaim claim = new PlayerClaim(crop, owner)) {
            check(!plantWith(helper, robot(helper, robotRel, deployer, owner), cropRel),
                    "a robot whose station's owner the claim refuses must not plant, although its deployer is "
                            + "trusted there");
            check(claim.placeAsks.contains(GameProfileUtil.getId(owner)), "the station's owner was asked about");
        }

        int twoPlayerAsks;
        try (PlayerClaim claim = new PlayerClaim(crop, null)) {
            check(plantWith(helper, robot(helper, robotRel, deployer, owner), cropRel),
                    "a robot whose deployer and station's owner are both trusted plants");
            check(claim.placeAsks.contains(GameProfileUtil.getId(owner)), "the station's owner was asked about");
            check(!claim.placeAsks.isEmpty()
                            && GameProfileUtil.getId(deployer).equals(claim.placeAsks.get(claim.placeAsks.size() - 1)),
                    "the seed goes in as the deployer, got " + claim.placeAsks);
            twoPlayerAsks = claim.placeAsks.size();
        }

        helper.setBlock(cropRel, Blocks.AIR);
        try (PlayerClaim claim = new PlayerClaim(crop, null)) {
            check(plantWith(helper, robot(helper, robotRel, profile("bc_deployer_plant_owner"), owner), cropRel),
                    "a robot deployed by its station's owner plants where that player may");
            check(claim.placeAsks.size() < twoPlayerAsks,
                    "a robot deployed by its station's owner is not asked the placement probe as well, got "
                            + claim.placeAsks + " against " + twoPlayerAsks + " for two players");
        }
        helper.setBlock(cropRel, Blocks.AIR);
        helper.succeed();
    }

    // ── Stripes ─────────────────────────────────────────────────────────────

    /** Enough cycles for the stripes AI's 60-cycle wind-up and its one use. */
    private static final int STRIPES_CYCLES = 62;

    /** Runs a fresh stripes AI with a hoe at {@code targetRel}; the AI, for its outcome. */
    private static AIRobotStripesHandler hoeWith(GameTestHelper helper, EntityRobot robot, BlockPos targetRel) {
        robot.setItemInUse(new ItemStack(Items.DIAMOND_HOE));
        AIRobotStripesHandler user = new AIRobotStripesHandler(robot, helper.absolutePos(targetRel));
        for (int cycles = 0; cycles < STRIPES_CYCLES; cycles++) {
            user.cycle();
        }
        for (ItemEntity drop : EntityArenaUtil.droppedItems(helper, targetRel, 1.5)) {
            drop.discard();
        }
        return user;
    }

    /** A stripes use acts on more than the reserved cell: when the hoe finds nothing to till in the cell or under
     *  it, the dispenser fallback clicks the cell behind it (the handlers' activator cell, toward the robot's
     *  south), and tills that as the deployer. The station's owner must be asked about that cell too. */
    public static void stripesNeedsStationOwnerAtTheActivatorCell(GameTestHelper helper) {
        requireProtectionChecksOn();
        GameProfile deployer = profile("bc_deployer_stripes_deployer");
        GameProfile owner = profile("bc_deployer_stripes_owner");
        BlockPos targetRel = new BlockPos(2, 2, 2);
        BlockPos activatorRel = targetRel.south();
        BlockPos activator = helper.absolutePos(activatorRel);
        BlockPos robotRel = targetRel.above();
        // The reserved cell is air over stone, which no hoe tills; the activator cell is dirt open to the sky.
        helper.setBlock(targetRel.below(), Blocks.STONE);
        helper.setBlock(activatorRel, Blocks.DIRT);

        try (PlayerClaim claim = new PlayerClaim(activator, owner)) {
            AIRobotStripesHandler user = hoeWith(helper, robot(helper, robotRel, deployer, owner), targetRel);
            check(helper.getLevel().getBlockState(activator).is(Blocks.DIRT),
                    "a robot whose station's owner the claim refuses at the activator cell must not till it, "
                            + "although its deployer is trusted there");
            check(user.refused(), "the use is reported as refused, not as an unusable item");
            check(claim.breakAsks.contains(GameProfileUtil.getId(owner)), "the station's owner was asked about");
        }

        try (PlayerClaim claim = new PlayerClaim(activator, null)) {
            hoeWith(helper, robot(helper, robotRel, deployer, owner), targetRel);
            check(helper.getLevel().getBlockState(activator).is(Blocks.FARMLAND),
                    "a robot whose deployer and station's owner are both trusted tills the activator cell (the "
                            + "refusal above is the claim's, not a use that never reached the cell)");
        }
        helper.setBlock(activatorRel, Blocks.AIR);
        helper.setBlock(targetRel.below(), Blocks.AIR);
        helper.succeed();
    }

    /** Runs a fresh stripes AI with a water bucket at {@code targetRel}; the AI, for its outcome. */
    private static AIRobotStripesHandler bucketWith(GameTestHelper helper, EntityRobot robot, BlockPos targetRel) {
        robot.setItemInUse(new ItemStack(Items.WATER_BUCKET));
        AIRobotStripesHandler user = new AIRobotStripesHandler(robot, helper.absolutePos(targetRel));
        for (int cycles = 0; cycles < STRIPES_CYCLES; cycles++) {
            user.cycle();
        }
        return user;
    }

    /** A bucket has no use on a block, so the dispenser fallback uses it in the air, where it lands wherever the
     *  posed player's eye ray (north, at head height over the reserved cell) meets a block: here, against a wall two
     *  cells out. The station's owner must be asked about that cell too, not only the deployer's own events. */
    public static void stripesInAirUseNeedsStationOwner(GameTestHelper helper) {
        requireProtectionChecksOn();
        GameProfile deployer = profile("bc_deployer_air_deployer");
        GameProfile owner = profile("bc_deployer_air_owner");
        BlockPos targetRel = new BlockPos(2, 2, 2);
        BlockPos wallRel = targetRel.above().north(2);
        BlockPos waterRel = wallRel.south();
        BlockPos water = helper.absolutePos(waterRel);
        helper.setBlock(targetRel.below(), Blocks.STONE);
        helper.setBlock(wallRel, Blocks.STONE);

        try (PlayerClaim claim = new PlayerClaim(water, owner)) {
            AIRobotStripesHandler user = bucketWith(helper, robot(helper, targetRel.above(), deployer, owner), targetRel);
            check(!helper.getLevel().getBlockState(water).is(Blocks.WATER),
                    "a robot whose station's owner the claim refuses where its bucket lands must not pour there, "
                            + "although its deployer is trusted there");
            check(user.refused(), "the use is reported as refused, not as an unusable item");
            check(claim.placeAsks.contains(GameProfileUtil.getId(owner)), "the station's owner was asked about");
        }

        try (PlayerClaim claim = new PlayerClaim(water, null)) {
            bucketWith(helper, robot(helper, targetRel.above(), deployer, owner), targetRel);
            check(helper.getLevel().getBlockState(water).is(Blocks.WATER),
                    "a robot whose deployer and station's owner are both trusted pours its bucket against the wall "
                            + "(the refusal above is the claim's, not a use that never reached the cell)");
        }
        helper.setBlock(waterRel, Blocks.AIR);
        helper.setBlock(wallRel, Blocks.AIR);
        helper.setBlock(targetRel.below(), Blocks.AIR);
        helper.succeed();
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    /** The deployer survives a save and load (and a re-homing); a robot saved without one acts as its station's
     *  owner, exactly as every robot did before deployers were recorded. */
    public static void deployerSurvivesSaveAndUnknownActsAsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile deployer = profile("bc_deployer_save_deployer");
        GameProfile owner = profile("bc_deployer_save_owner");
        GameProfile otherOwner = profile("bc_deployer_save_other_owner");
        BlockPos robotRel = new BlockPos(2, 2, 2);

        EntityRobot robot = robot(helper, robotRel, deployer, owner);
        CompoundTag tag = EntityArenaUtil.saveEntity(level, robot);

        EntityRobot loaded = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        EntityArenaUtil.loadEntity(level, loaded, tag);
        GameProfile read = loaded.getDeployer();
        check(read != null && GameProfileUtil.getId(deployer).equals(GameProfileUtil.getId(read))
                        && GameProfileUtil.getName(deployer).equals(GameProfileUtil.getName(read)),
                "the deployer's id and name survive a save, got " + read);
        loaded.setMainStation(new OwnedStation(helper.absolutePos(robotRel), otherOwner));
        check(GameProfileUtil.getId(deployer).equals(GameProfileUtil.getId(RobotProtection.identityOf(loaded))),
                "a reloaded, re-homed robot still acts as its deployer");

        CompoundTag legacy = tag.copy();
        legacy.remove("deployer");
        EntityRobot old = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        EntityArenaUtil.loadEntity(level, old, legacy);
        check(old.getDeployer() == null, "a robot saved without a deployer has none, got " + old.getDeployer());
        old.setMainStation(new OwnedStation(helper.absolutePos(robotRel), owner));
        check(GameProfileUtil.getId(owner).equals(GameProfileUtil.getId(RobotProtection.identityOf(old))),
                "a robot with no recorded deployer acts as its station's owner");
        robot.setMainStation(null);
        loaded.setMainStation(null);
        old.setMainStation(null);
        helper.succeed();
    }

    // ── The station's owner, remembered ─────────────────────────────────────

    /** A robot deployed by {@code deployer} (null: none recorded) homed on {@code home}. */
    private static EntityRobot robotOn(GameTestHelper helper, BlockPos relPos, @Nullable GameProfile deployer,
            DockingStation home) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        robot.setDeployer(deployer);
        robot.setMainStation(home);
        return robot;
    }

    /** A cobblestone pipe at {@code relPos} owned by {@code owner} (null: nobody). */
    private static TilePipeHolder ownedPipe(GameTestHelper helper, BlockPos relPos, @Nullable GameProfile owner) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));
        tile.setOwner(owner);
        return tile;
    }

    /**
     * A station whose pipe cannot be resolved (its chunk not loaded yet after a restart, here the pipe simply gone)
     * still answers with the owner it resolved last, also after a save and load; and one that never resolved it
     * (saved before stations remembered their owner) is "owner unknown" and refuses everything, rather than asking
     * as the generic "[BuildCraft]" identity that operators whitelist. A station whose pipe is there but has no owner
     * keeps asking as "[BuildCraft]".
     */
    public static void stationRemembersItsOwnerAndUnknownFailsClosed(GameTestHelper helper) {
        requireProtectionChecksOn();
        ServerLevel level = helper.getLevel();
        GameProfile deployer = profile("bc_deployer_known_deployer");
        GameProfile owner = profile("bc_deployer_known_owner");
        BlockPos pipeRel = new BlockPos(1, 1, 1);
        BlockPos robotRel = new BlockPos(1, 2, 1);
        BlockPos stoneRel = new BlockPos(3, 1, 3);
        BlockPos stone = helper.absolutePos(stoneRel);
        helper.setBlock(stoneRel, Blocks.STONE);

        DockingStationPipe station = new DockingStationPipe(ownedPipe(helper, pipeRel, owner), Direction.UP);
        GameProfile resolved = station.getOwner();
        check(resolved != null && GameProfileUtil.getId(owner).equals(GameProfileUtil.getId(resolved)),
                "a station on a loaded pipe answers with the pipe's owner, got " + resolved);
        helper.setBlock(pipeRel, Blocks.AIR);

        try (PlayerClaim claim = new PlayerClaim(stone, owner)) {
            check(!RobotProtection.canBreak(robotOn(helper, robotRel, deployer, station), level, stone),
                    "a station that resolved its owner once still asks that owner once its pipe cannot be "
                            + "resolved, so the owner's refusal holds");
            check(claim.breakAsks.contains(GameProfileUtil.getId(owner)), "the remembered owner was asked about");
        }

        CompoundTag saved = new CompoundTag();
        station.writeToNbt(saved);
        DockingStationPipe reloaded = new DockingStationPipe();
        reloaded.readFromNbt(saved);
        reloaded.world = level;
        try (PlayerClaim claim = new PlayerClaim(stone, owner)) {
            check(!RobotProtection.canBreak(robotOn(helper, robotRel, deployer, reloaded), level, stone),
                    "the remembered owner survives a save and load of the station");
        }
        try (PlayerClaim claim = new PlayerClaim(stone, null)) {
            check(RobotProtection.canBreak(robotOn(helper, robotRel, deployer, reloaded), level, stone),
                    "a reloaded station whose remembered owner is trusted lets its robot break");
            check(claim.breakAsks.contains(GameProfileUtil.getId(owner)), "and asked the remembered owner");
        }

        DockingStationPipe unknown = unknownStation(helper, pipeRel, owner);
        try (PlayerClaim claim = new PlayerClaim(stone, null)) {
            check(!RobotProtection.canBreak(robotOn(helper, robotRel, deployer, unknown), level, stone),
                    "a station whose owner is unknown refuses a break its robot's deployer is allowed");
            check(!RobotProtection.canBreak(robotOn(helper, robotRel, null, unknown), level, stone),
                    "and a robot with no recorded deployer does not act as \"[BuildCraft]\" on it");
            check(!RobotProtection.stationOwnerCanPlace(robotOn(helper, robotRel, deployer, unknown), level, stone),
                    "a station whose owner is unknown refuses placements too");
        }

        DockingStationPipe ownerless = new DockingStationPipe(ownedPipe(helper, pipeRel, null), Direction.UP);
        try (PlayerClaim claim = new PlayerClaim(stone, null)) {
            check(RobotProtection.canBreak(robotOn(helper, robotRel, deployer, ownerless), level, stone),
                    "a station on a pipe that has no owner is not unknown: it asks as \"[BuildCraft]\", as before");
        }
        helper.setBlock(pipeRel, Blocks.AIR);
        helper.setBlock(stoneRel, Blocks.AIR);
        helper.succeed();
    }

    /** A station read back from the record one wrote before stations remembered their owner (the base fields only),
     *  at {@code relPos}, where no pipe stands: its owner is unknown. */
    private static DockingStationPipe unknownStation(GameTestHelper helper, BlockPos relPos, GameProfile owner) {
        CompoundTag legacy = new CompoundTag();
        new OwnedStation(helper.absolutePos(relPos), owner).writeToNbt(legacy);
        DockingStationPipe unknown = new DockingStationPipe();
        unknown.readFromNbt(legacy);
        unknown.world = helper.getLevel();
        return unknown;
    }

    // ── Bomber ──────────────────────────────────────────────────────────────

    /** A bomber board on a robot holding TNT, told its flight ended over the drop point; the TNT it primed (kept out
     *  of the world, so nothing explodes), or null when it dropped none. */
    @Nullable
    private static PrimedTnt bombWith(GameTestHelper helper, EntityRobot robot) {
        return bombWith(helper, robot, new BoardRobotBomber(robot));
    }

    @Nullable
    private static PrimedTnt bombWith(GameTestHelper helper, EntityRobot robot, BoardRobotBomber board) {
        robot.getTransactor().insert(new ItemStack(Items.TNT, 4), false, false);
        BlockPos at = robot.blockPosition();
        AIRobotGotoBlock arrived = new AIRobotGotoBlock(robot, at.getX(), at.getY(), at.getZ()) {
            @Override
            public boolean success() {
                return true;
            }
        };
        PrimedTnt[] primed = new PrimedTnt[1];
        Consumer<EntityJoinLevelEvent> catcher = event -> {
            if (event.getEntity() instanceof PrimedTnt tnt && event.getLevel() == helper.getLevel()) {
                primed[0] = tnt;
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, catcher);
        try {
            board.delegateAIEnded(arrived);
        } finally {
            NeoForge.EVENT_BUS.unregister(catcher);
        }
        return primed[0];
    }

    /** A half-power explosion of {@code bomb} (which stays out of the world) centred in the arena cell {@code rel}, or
     *  at {@code center} when given, then its drops cleared away. */
    private static void blowUp(GameTestHelper helper, PrimedTnt bomb, BlockPos rel, @Nullable Vec3 center) {
        Vec3 at = center != null ? center : Vec3.atCenterOf(helper.absolutePos(rel));
        helper.getLevel().explode(bomb, at.x, at.y, at.z, 0.5F, Level.ExplosionInteraction.TNT);
        for (ItemEntity drop : EntityArenaUtil.droppedItems(helper, rel, 1.5)) {
            drop.discard();
        }
    }

    /** The bomber's TNT lands on the block under it: both the deployer and the station's owner must be allowed to
     *  break that block, or no TNT is dropped; a dropped one is the deployer's (its owner is the deployer's player),
     *  so claim mods and kill credit see who sent the robot. Its blast then spares every block and entity either
     *  player may not touch. */
    public static void bomberNeedsBothAndTntIsTheDeployers(GameTestHelper helper) {
        requireProtectionChecksOn();
        GameProfile deployer = profile("bc_deployer_bomb_deployer");
        GameProfile owner = profile("bc_deployer_bomb_owner");
        BlockPos groundRel = new BlockPos(2, 1, 2);
        BlockPos ground = helper.absolutePos(groundRel);
        BlockPos robotRel = new BlockPos(2, 4, 2);
        helper.setBlock(groundRel, Blocks.STONE);

        for (GameProfile refused : List.of(owner, deployer)) {
            try (PlayerClaim claim = new PlayerClaim(ground, refused)) {
                EntityRobot robot = robot(helper, robotRel, deployer, owner);
                PrimedTnt tnt = bombWith(helper, robot);
                check(tnt == null, "a bomber must drop no TNT over a block " + GameProfileUtil.getName(refused)
                        + " may not break");
                check(claim.breakAsks.contains(GameProfileUtil.getId(refused)), "the refused player was asked about");
                check(robot.getTransactor().extract(stack -> stack.is(Items.TNT), 1, 4, true).getCount() == 4,
                        "a refused drop keeps its TNT");
            }
        }

        PrimedTnt bomb;
        try (PlayerClaim claim = new PlayerClaim(ground, null)) {
            bomb = bombWith(helper, robot(helper, robotRel, deployer, owner));
            check(bomb != null, "a bomber over a block both its players may break drops TNT");
            check(bomb.getOwner() != null && GameProfileUtil.getId(deployer).equals(bomb.getOwner().getUUID()),
                    "the dropped TNT is the deployer's, got owner " + bomb.getOwner());
        }

        // The blast reaches past the landing block, so every block and entity it takes is asked about for both
        // players. A half-power blast centred in a slime block (no blast resistance) always takes that block.
        BlockPos blastRel = new BlockPos(4, 1, 5);
        for (GameProfile refused : List.of(owner, deployer)) {
            helper.setBlock(blastRel, Blocks.SLIME_BLOCK);
            try (PlayerClaim claim = new PlayerClaim(helper.absolutePos(blastRel), refused)) {
                blowUp(helper, bomb, blastRel, null);
                check(!helper.getLevel().getBlockState(helper.absolutePos(blastRel)).isAir(), "a robot's blast must "
                        + "spare a block " + GameProfileUtil.getName(refused) + " may not break");
                check(claim.breakAsks.contains(GameProfileUtil.getId(refused)), "the refused player was asked about");
            }
        }
        try (PlayerClaim claim = new PlayerClaim(helper.absolutePos(blastRel), null)) {
            blowUp(helper, bomb, blastRel, null);
            check(helper.getLevel().getBlockState(helper.absolutePos(blastRel)).isAir(),
                    "a robot's blast takes a block both its players may break");
        }
        // A TNT block the blast takes primes TNT of its own, owned by the deployer: it inherits the robot's mark, so
        // its blast spares the station owner's claim too. The primed TNT is caught last and kept out of the world.
        helper.setBlock(blastRel, Blocks.TNT);
        PrimedTnt[] chained = new PrimedTnt[1];
        Consumer<EntityJoinLevelEvent> chainCatcher = event -> {
            if (event.getEntity() instanceof PrimedTnt tnt && event.getLevel() == helper.getLevel()) {
                chained[0] = tnt;
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, chainCatcher);
        try (PlayerClaim claim = new PlayerClaim(helper.absolutePos(blastRel), null)) {
            blowUp(helper, bomb, blastRel, null);
        } finally {
            NeoForge.EVENT_BUS.unregister(chainCatcher);
        }
        check(chained[0] != null, "a robot's blast sets off the TNT block it takes");
        helper.setBlock(blastRel, Blocks.SLIME_BLOCK);
        try (PlayerClaim claim = new PlayerClaim(helper.absolutePos(blastRel), owner)) {
            blowUp(helper, chained[0], blastRel, null);
            check(!helper.getLevel().getBlockState(helper.absolutePos(blastRel)).isAir(), "TNT a robot's blast set "
                    + "off must spare a block the station's owner may not break");
            check(claim.breakAsks.contains(GameProfileUtil.getId(owner)), "the station's owner was asked about");
        }
        helper.setBlock(blastRel, Blocks.AIR);
        BlockPos cowRel = new BlockPos(1, 2, 5);
        //? if >=26.2 {
        Mob cow = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, cowRel);
        //?} else {
        /*Mob cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, cowRel);*/
        //?}
        cow.setNoAi(true);
        cow.setNoGravity(true);
        try {
            for (GameProfile refused : List.of(owner, deployer)) {
                try (AttackClaim claim = new AttackClaim(cow, refused)) {
                    blowUp(helper, bomb, cowRel, cow.position());
                    check(cow.getHealth() == cow.getMaxHealth(), "a robot's blast must not hurt a target "
                            + GameProfileUtil.getName(refused) + " may not attack, got health " + cow.getHealth());
                    check(claim.asks.contains(GameProfileUtil.getId(refused)), "the refused player was asked about");
                }
            }
            try (AttackClaim claim = new AttackClaim(cow, null)) {
                blowUp(helper, bomb, cowRel, cow.position());
                check(cow.getHealth() < cow.getMaxHealth(), "a robot's blast hurts a target both players may attack");
            }
        } finally {
            cow.discard();
        }

        // The robot hovers off the centre of the column its search picked, so its TNT falls on the next column's
        // block: a refusal there must also keep the search off the picked column, or it is picked again and again.
        BlockPos landingRel = groundRel.east();
        BlockPos landing = helper.absolutePos(landingRel);
        helper.setBlock(landingRel, Blocks.STONE);
        try (PlayerClaim claim = new PlayerClaim(landing, owner)) {
            EntityRobot robot = robot(helper, robotRel, deployer, owner);
            Vec3 hover = Vec3.atCenterOf(helper.absolutePos(robotRel));
            robot.setPos(hover.x + 0.3, hover.y, hover.z);
            BoardRobotBomber board = new BoardRobotBomber(robot);
            AIRobotSearchRandomGroundBlock search = new AIRobotSearchRandomGroundBlock(robot);
            search.blockFound = ground;
            board.delegateAIEnded(search);
            check(bombWith(helper, robot, board) == null, "a bomber must drop no TNT onto a refused block");
            check(claim.breakAsks.contains(GameProfileUtil.getId(owner)), "the block the TNT lands on was asked about");
            check(!board.isDropTarget(landing), "the refused landing block is not picked again");
            check(!board.isDropTarget(ground), "nor is the column the search picked, whose drop it refused");
        }
        helper.setBlock(landingRel, Blocks.AIR);
        helper.setBlock(groundRel, Blocks.AIR);
        helper.succeed();
    }

    // ── Melee ───────────────────────────────────────────────────────────────

    /** Enough cycles for the attack AI's first swing (its delay starts at 10 and swings past 20). */
    private static final int ATTACK_CYCLES = 12;

    /** Cycles a fresh attack AI at {@code target}; the AI, for its outcome. */
    private static AIRobotAttack attackWith(EntityRobot robot, Entity target) {
        return attackWith(robot, target, new ItemStack(Items.DIAMOND_SWORD));
    }

    /** {@link #attackWith(EntityRobot, Entity)} swinging {@code weapon}. */
    private static AIRobotAttack attackWith(EntityRobot robot, Entity target, ItemStack weapon) {
        robot.setItemInUse(weapon);
        AIRobotAttack attack = new AIRobotAttack(robot, target);
        for (int cycles = 0; cycles < ATTACK_CYCLES; cycles++) {
            attack.cycle();
        }
        return attack;
    }

    /** A robot's swing is asked of protection mods as a player's attack would be (the attack-entity event), for both
     *  the deployer and the station's owner; a refused swing does no damage and the target is left alone for a
     *  while, and a permitted one names the deployer's player as the attacker. */
    public static void meleeNeedsBothAndNamesTheDeployer(GameTestHelper helper) {
        requireProtectionChecksOn();
        GameProfile deployer = profile("bc_deployer_melee_deployer");
        GameProfile owner = profile("bc_deployer_melee_owner");
        BlockPos robotRel = new BlockPos(3, 2, 2);
        BlockPos stationRel = new BlockPos(4, 1, 4);
        //? if >=26.2 {
        Mob cow = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, 2, 2, 2);
        //?} else {
        /*Mob cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, 2, 2, 2);*/
        //?}
        // A second cow for the kill: the first is still inside its hurt cooldown when the kill swing comes
        //? if >=26.2 {
        Mob victim = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, 3, 2, 3);
        //?} else {
        /*Mob victim = helper.spawn(net.minecraft.world.entity.EntityType.COW, 3, 2, 3);*/
        //?}
        for (Mob mob : List.of(cow, victim)) {
            mob.setNoAi(true);
            mob.setNoGravity(true);
        }
        try {
            for (GameProfile refused : List.of(owner, deployer)) {
                try (AttackClaim claim = new AttackClaim(cow, refused)) {
                    EntityRobot robot = robot(helper, robotRel, deployer, owner);
                    attackWith(robot, cow);
                    check(cow.getHealth() == cow.getMaxHealth(), "a robot must not hurt a target "
                            + GameProfileUtil.getName(refused) + " may not attack, got health " + cow.getHealth());
                    check(claim.asks.contains(GameProfileUtil.getId(refused)), "the refused player was asked about");
                    check(robot.isKnownUnreachable(cow), "a refused target is left alone for a while");
                }
            }

            try (AttackClaim claim = new AttackClaim(cow, null)) {
                EntityRobot robot = robotOn(helper, robotRel, deployer, unknownStation(helper, stationRel, owner));
                AIRobotAttack attack = attackWith(robot, cow);
                check(attack.refused() && cow.getHealth() == cow.getMaxHealth(),
                        "a robot whose station's owner is unknown must not attack, got health " + cow.getHealth());
                check(!robot.isKnownUnreachable(cow), "a target refused only because the station's owner is unknown is "
                        + "not left alone: the robot asks again as soon as the owner is known");
            }

            try (AttackClaim claim = new AttackClaim(cow, null)) {
                EntityRobot robot = robot(helper, robotRel, deployer, owner);
                attackWith(robot, cow);
                check(cow.getHealth() < cow.getMaxHealth(), "a robot whose players may both attack hurts the target");
                DamageSource source = cow.getLastDamageSource();
                check(source != null && source.getEntity() != null
                                && GameProfileUtil.getId(deployer).equals(source.getEntity().getUUID()),
                        "the hit names the deployer's player as the attacker, got "
                                + (source == null ? null : source.getEntity()));
                check(source.getDirectEntity() == robot, "the robot itself still lands the blow");
                check(!isPlayer(cow.getLastHurtByMob(), deployer), "the stand-in player is not the target's attacker");

                // Looting is read off the killer's hand when the kill's loot is rolled (vanilla's loot functions
                // and equipment drop chances alike), so the stand-in player must hold the weapon through the hit.
                Holder<Enchantment> looting = helper.getLevel().registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                sword.enchant(looting, 3);
                int[] lootingAtDeath = {-1};
                Consumer<LivingDropsEvent> drops = event -> {
                    if (event.getEntity() == victim && event.getSource().getEntity() instanceof LivingEntity killer) {
                        lootingAtDeath[0] = EnchantmentHelper.getEnchantmentLevel(looting, killer);
                    }
                };
                NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class, drops);
                try {
                    victim.setHealth(1.0F);
                    attackWith(robot, victim, sword);
                } finally {
                    NeoForge.EVENT_BUS.unregister(drops);
                }
                check(victim.isDeadOrDying(), "a one-health cow dies to the robot's sword");
                check(lootingAtDeath[0] == 3, "a robot's kill rolls its loot with the weapon's Looting III, got "
                        + lootingAtDeath[0]);
            }
        } finally {
            cow.discard();
            victim.discard();
            for (ItemEntity drop : EntityArenaUtil.droppedItems(helper, new BlockPos(3, 2, 3), 2.0)) {
                drop.discard();
            }
            for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class,
                    new AABB(helper.absolutePos(new BlockPos(3, 2, 3))).inflate(2.0))) {
                orb.discard();
            }
        }

        // The stand-in player is not in the world, so a mob the hit would turn on it must not. A warden takes its
        // attacker as its target straight from the hurt hook, with no targeting check; a hoglin does so only when the
        // attacker can be targeted at all, which a fake player cannot be.
        //? if >=26.2 {
        Mob warden = helper.spawn(net.minecraft.world.entity.EntityTypes.WARDEN, 4, 2, 2);
        Mob hoglin = helper.spawn(net.minecraft.world.entity.EntityTypes.HOGLIN, 3, 2, 3);
        //?} else {
        /*Mob warden = helper.spawn(net.minecraft.world.entity.EntityType.WARDEN, 4, 2, 2);
        Mob hoglin = helper.spawn(net.minecraft.world.entity.EntityType.HOGLIN, 3, 2, 3);*/
        //?}
        try {
            for (Mob mob : List.of(warden, hoglin)) {
                mob.setNoGravity(true);
                EntityRobot robot = robot(helper, robotRel, deployer, owner);
                attackWith(robot, mob);
                check(mob.getHealth() < mob.getMaxHealth(), "the robot hits the " + mob.getType());
                Object remembered = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                check(!isPlayer(remembered, deployer) && !isPlayer(mob.getTarget(), deployer),
                        "a " + mob.getType() + " hit by a robot must not turn on the stand-in player, got " + remembered);
            }
        } finally {
            warden.discard();
            hoglin.discard();
        }
        helper.succeed();
    }

    /** Whether {@code entity} is the player standing in for {@code profile}. */
    private static boolean isPlayer(@Nullable Object entity, GameProfile profile) {
        return entity instanceof Player player && GameProfileUtil.getId(profile).equals(player.getUUID());
    }

    // ── Doubles ─────────────────────────────────────────────────────────────

    /** A claim around one entity that refuses one player's attacks on it: records who every attack-entity event on
     *  it was posted for and cancels those of {@code refused} (none when null). */
    private static final class AttackClaim implements AutoCloseable {
        final List<UUID> asks = new ArrayList<>();
        private final Consumer<AttackEntityEvent> listener;

        AttackClaim(Entity target, @Nullable GameProfile refused) {
            UUID refusedId = refused == null ? null : GameProfileUtil.getId(refused);
            listener = event -> {
                if (event.getTarget() == target) {
                    UUID id = event.getEntity().getUUID();
                    asks.add(id);
                    if (id.equals(refusedId)) {
                        event.setCanceled(true);
                    }
                }
            };
            NeoForge.EVENT_BUS.addListener(AttackEntityEvent.class, listener);
        }

        @Override
        public void close() {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    /**
     * A claim at one position that refuses one player: records who every break and placement event there was posted
     * for and cancels those of {@code refused} (none when null). {@code ClaimProtectionMod} refuses everyone alike,
     * which cannot tell the deployer from the station's owner.
     */
    private static final class PlayerClaim implements AutoCloseable {
        final List<UUID> breakAsks = new ArrayList<>();
        final List<UUID> placeAsks = new ArrayList<>();
        private final Object breakToken;
        private final Consumer<BlockEvent.EntityPlaceEvent> placeListener;

        PlayerClaim(BlockPos pos, @Nullable GameProfile refused) {
            UUID refusedId = refused == null ? null : GameProfileUtil.getId(refused);
            breakToken = BreakEventCompat.onBreak(event -> {
                if (event.getPos().equals(pos)) {
                    Player asker = BreakEventCompat.playerOf(event);
                    UUID id = asker == null ? null : asker.getUUID();
                    breakAsks.add(id);
                    if (id != null && id.equals(refusedId)) {
                        BreakEventCompat.cancel(event);
                    }
                }
            });
            placeListener = event -> {
                if (event.getPos().equals(pos)) {
                    Entity placer = event.getEntity();
                    UUID id = placer == null ? null : placer.getUUID();
                    placeAsks.add(id);
                    if (id != null && id.equals(refusedId)) {
                        event.setCanceled(true);
                    }
                }
            };
            NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, placeListener);
        }

        @Override
        public void close() {
            BreakEventCompat.removeListener(breakToken);
            NeoForge.EVENT_BUS.unregister(placeListener);
        }
    }

    /** A bare home station owned by {@code owner}: just enough for {@link RobotProtection}. */
    private static final class OwnedStation extends DockingStation {
        private final GameProfile owner;

        OwnedStation(BlockPos pos, GameProfile owner) {
            super(pos, Direction.UP);
            this.owner = owner;
        }

        @Override
        public GameProfile getOwner() {
            return owner;
        }

        @Override
        public Iterable<StatementSlot> getActiveActions() {
            return Collections.emptyList();
        }
    }
}
