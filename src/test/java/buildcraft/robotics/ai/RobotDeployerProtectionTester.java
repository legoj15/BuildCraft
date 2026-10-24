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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
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
import buildcraft.robotics.RobotProtection;
import buildcraft.robotics.entity.EntityRobot;

/**
 * A robot answers to two players: the one who deployed it and the owner of its home station. It acts as its deployer
 * (claim mods judge, and logs attribute to, the player who put it to work), and nothing it does may go through unless
 * protection mods permit BOTH: a robot whose deployer a claim refuses is refused on a station whose owner is trusted
 * there, and a robot whose station's owner is refused is refused although its deployer is trusted. Pinned on a break
 * (the shared break probe) and on a planting (an action that asks through the leased player's own events, guarded
 * for the station's owner by a placement probe), and on a stripes use reaching past its reserved cell. A robot saved before deployers were recorded has none and acts as
 * its station's owner alone, as before.
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

    // ── Doubles ─────────────────────────────────────────────────────────────

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
