/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
//? if >=1.21.11 {
import net.minecraft.world.entity.animal.equine.AbstractHorse;
//?} else {
/*import net.minecraft.world.entity.animal.horse.AbstractHorse;*/
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.api.statements.StatementSlot;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.test.ClaimProtectionMod;
import buildcraft.robotics.ai.MockRobotAccess;
import buildcraft.robotics.statements.ActionRobotFilter;
import buildcraft.robotics.statements.TestStation;

/** The two combat boards' entity predicates, pinned against REAL entities — which a unit test cannot
 *  build, since every entity constructor wants a live {@code Level} — and the lumberjack's tag-backed block
 *  predicate, which the unit JVM cannot resolve (it loads no tags).
 *
 *  <p>The knight's target set is the load-bearing half: 7.1.x asked {@code entity instanceof IMob}, an
 *  INTERFACE that a good number of hostiles implement without sharing a base class. The modern
 *  equivalent is {@code net.minecraft.world.entity.monster.Enemy} (which is what {@code EntityRobot}'s
 *  own damage filter already maps IMob onto) — NOT {@code Monster}, which a slime, magma cube, ghast,
 *  phantom, shulker and the ender dragon all fail to extend. A slime is therefore the whole test. */
public class BoardTargetPredicateTester {

    public static void knightTargetsEveryHostile(GameTestHelper helper) {
        BoardRobotKnight knight = new BoardRobotKnight(new MockRobotAccess());
        BoardRobotButcher butcher = new BoardRobotButcher(new MockRobotAccess());

        //? if >=26.2 {
        Mob zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
        Mob slime = helper.spawn(net.minecraft.world.entity.EntityTypes.SLIME, new BlockPos(2, 2, 3));
        Mob cow = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, new BlockPos(4, 2, 1));
        Mob villager = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(4, 2, 4));
        //?} else {
        /*Mob zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        Mob slime = helper.spawn(net.minecraft.world.entity.EntityType.SLIME, new BlockPos(2, 2, 3));
        Mob cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(4, 2, 1));
        Mob villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(4, 2, 4));
        *///?}

        Mob[] spawned = { zombie, slime, cow, villager };
        for (Mob mob : spawned) {
            // Inert: the predicates are pure, and a wandering mob could leave its own arena cell.
            mob.setNoAi(true);
            mob.setNoGravity(true);
        }

        try {
            helper.assertTrue(knight.isExpectedTarget(zombie),
                    "a zombie is a knight's target");
            helper.assertTrue(knight.isExpectedTarget(slime),
                    "a slime is hostile and 7.1.x's IMob check caught it — but a slime does NOT extend "
                            + "Monster, so a Monster-based predicate silently ignores slimes, magma cubes, "
                            + "ghasts, phantoms, shulkers and the ender dragon");
            helper.assertFalse(knight.isExpectedTarget(cow),
                    "a cow is not hostile — the knight must leave livestock alone");
            helper.assertFalse(knight.isExpectedTarget(villager),
                    "a villager is not hostile — the knight must leave villagers alone");

            // The butcher is the mirror image, and pins that widening the knight did not widen the butcher.
            helper.assertTrue(butcher.isExpectedTarget(cow),
                    "a cow is the butcher's target");
            helper.assertFalse(butcher.isExpectedTarget(zombie),
                    "a zombie is not livestock — the butcher must ignore it");
        } finally {
            // Discard exactly what this test made: the framework's own cleanup does not reach entities.
            for (Mob mob : spawned) {
                mob.discard();
            }
        }
        helper.succeed();
    }

    // ---------- the butcher spares what a player kept ----------

    private static Mob cow(GameTestHelper helper, BlockPos pos) {
        //? if >=26.2 {
        return helper.spawn(net.minecraft.world.entity.EntityTypes.COW, pos);
        //?} else {
        /*return helper.spawn(net.minecraft.world.entity.EntityType.COW, pos);
        *///?}
    }

    private static TamableAnimal wolf(GameTestHelper helper, BlockPos pos) {
        //? if >=26.2 {
        return helper.spawn(net.minecraft.world.entity.EntityTypes.WOLF, pos);
        //?} else {
        /*return helper.spawn(net.minecraft.world.entity.EntityType.WOLF, pos);
        *///?}
    }

    private static AbstractHorse horse(GameTestHelper helper, BlockPos pos) {
        //? if >=26.2 {
        return helper.spawn(net.minecraft.world.entity.EntityTypes.HORSE, pos);
        //?} else {
        /*return helper.spawn(net.minecraft.world.entity.EntityType.HORSE, pos);
        *///?}
    }

    /** 7.1.x's butcher killed any {@code EntityAnimal}: a player's tamed wolf, cat or parrot, a tamed horse, donkey,
     *  mule, llama or camel, and any animal they had name-tagged to keep, all went the way of the cattle. The port
     *  spares them (a deliberate divergence; see docs/robotics-resurrection.md): an owned or tamed animal, a tamed
     *  horse (owner or not), and anything with a custom name. Wild and untamed animals stay targets.
     *
     *  <p>"Owned" is the owner REFERENCE being set, not the owner being found: the owner is usually offline, and
     *  {@code getOwner()} resolves to null for an offline player, which would make their pet look ownerless. The
     *  mock owner here is not in the level for that reason. */
    public static void butcherSparesPetsHorsesAndNamedAnimals(GameTestHelper helper) {
        BoardRobotButcher butcher = new BoardRobotButcher(new buildcraft.robotics.ai.MockRobotAccess());
        Player owner = helper.makeMockPlayer(GameType.CREATIVE);

        Mob cow = cow(helper, new BlockPos(1, 2, 1));
        Mob namedCow = cow(helper, new BlockPos(2, 2, 1));
        namedCow.setCustomName(Component.literal("Bessie"));
        TamableAnimal wildWolf = wolf(helper, new BlockPos(3, 2, 1));
        TamableAnimal ownedWolf = wolf(helper, new BlockPos(4, 2, 1));
        ownedWolf.tame(owner);
        TamableAnimal tamedOwnerlessWolf = wolf(helper, new BlockPos(1, 2, 3));
        tamedOwnerlessWolf.setTame(true, false);
        AbstractHorse wildHorse = horse(helper, new BlockPos(2, 2, 3));
        AbstractHorse ownedHorse = horse(helper, new BlockPos(3, 2, 3));
        ownedHorse.tameWithName(owner);
        AbstractHorse tamedOwnerlessHorse = horse(helper, new BlockPos(4, 2, 3));
        tamedOwnerlessHorse.setTamed(true);

        Mob[] spawned = { cow, namedCow, wildWolf, ownedWolf, tamedOwnerlessWolf, wildHorse, ownedHorse,
                tamedOwnerlessHorse };
        for (Mob mob : spawned) {
            // Inert: the predicate is pure, and a wandering mob could leave its own arena cell.
            mob.setNoAi(true);
            mob.setNoGravity(true);
        }

        try {
            helper.assertTrue(butcher.isExpectedTarget(cow), "a plain cow is still the butcher's target");
            helper.assertTrue(butcher.isExpectedTarget(wildWolf), "a wild wolf is livestock to the butcher");
            helper.assertTrue(butcher.isExpectedTarget(wildHorse), "a wild horse is livestock to the butcher");
            helper.assertFalse(butcher.isExpectedTarget(namedCow),
                    "a name-tagged animal is one the player wants kept");
            helper.assertFalse(butcher.isExpectedTarget(ownedWolf),
                    "a wolf with an owner is a pet, even though its owner is not in the level");
            helper.assertFalse(butcher.isExpectedTarget(tamedOwnerlessWolf), "a tamed wolf is a pet whoever owns it");
            helper.assertFalse(butcher.isExpectedTarget(ownedHorse), "a horse with an owner is spared");
            helper.assertFalse(butcher.isExpectedTarget(tamedOwnerlessHorse),
                    "a tamed horse is spared whether or not it has an owner");
        } finally {
            // Discard exactly what this test made: the framework's own cleanup does not reach entities.
            for (Mob mob : spawned) {
                mob.discard();
            }
        }
        helper.succeed();
    }

    // ---------- the lumberjack cuts a trunk from its base ----------

    private static DockingStation filterNaming(ItemStack... stacks) {
        StatementSlot slot = new StatementSlot();
        slot.statement = new ActionRobotFilter();
        List<IStatementParameter> params = new ArrayList<>();
        for (ItemStack stack : stacks) {
            params.add(new StatementParameterItemStack(stack));
        }
        slot.parameters = params.toArray(new IStatementParameter[0]);
        return new TestStation(List.of(slot));
    }

    /** Three logs stacked at (x, 2..4, z) on a stone floor: {@code bottom}, {@code middle} and {@code top} of them. */
    private static void stackLogs(GameTestHelper helper, int x, int z, net.minecraft.world.level.block.Block bottom,
            net.minecraft.world.level.block.Block middle, net.minecraft.world.level.block.Block top) {
        helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        helper.setBlock(new BlockPos(x, 2, z), bottom);
        helper.setBlock(new BlockPos(x, 3, z), middle);
        helper.setBlock(new BlockPos(x, 4, z), top);
    }

    /** Tree-felling mods (FallingTree and its kin) bring the whole tree down when its BOTTOM log is cut, and ignore a
     *  cut from the middle. So the lumberjack hunts only a wood block with no wood directly below it: a log standing on
     *  stone, dirt or air, never one standing on another log (of any species). Without such a mod it just chops each
     *  column bottom-up, since the log above a cut one then has air below it. The state-only predicate stays "is
     *  wood"; it is the neighbour-aware seam the search and {@code finishAttempt} ask that knows the column. */
    public static void lumberjackHuntsTrunkBases(GameTestHelper helper) {
        BoardRobotLumberjack lumberjack = new BoardRobotLumberjack(new MockRobotAccess());
        Level level = helper.getLevel();

        // A trunk of three, two species, standing on stone; and a log floating by itself.
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        BlockPos base = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos middle = base.above();
        BlockPos top = middle.above();
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(2, 4, 2), Blocks.SPRUCE_LOG);
        helper.setBlock(new BlockPos(4, 3, 2), Blocks.OAK_LOG);
        BlockPos floating = helper.absolutePos(new BlockPos(4, 3, 2));
        BlockPos stone = helper.absolutePos(new BlockPos(2, 1, 2));

        helper.assertTrue(lumberjack.isExpectedBlock(level, base), "the log standing on stone is a trunk base");
        helper.assertFalse(lumberjack.isExpectedBlock(level, middle),
                "a log standing on a log is mid-trunk, not a candidate");
        helper.assertFalse(lumberjack.isExpectedBlock(level, top),
                "a log of another species standing on a log is mid-trunk too");
        helper.assertTrue(lumberjack.isExpectedBlock(level, floating),
                "a floating log (air below) has nothing under it, so it is a base");
        helper.assertFalse(lumberjack.isExpectedBlock(level, stone), "stone is not wood");
        helper.assertTrue(lumberjack.isExpectedBlock(level.getBlockState(middle)),
                "the state-only predicate still says what the block is: wood");

        // Cut the base: the log above it has air below it now, and is the new base.
        level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(lumberjack.isExpectedBlock(level, middle),
                "with the base cut the next log is the bottom of what is left");
        helper.assertFalse(lumberjack.isExpectedBlock(level, top), "and the one above it is still mid-trunk");

        strandedColumnsStayReachable(helper, level);
        helper.succeed();
    }

    /** The base is only worth waiting for when this robot's search can take it. Whatever keeps it from taking the
     *  base - the gate's Filter does not name it, protection refused it, the work zone leaves it out, a search gave up
     *  on reaching it - makes the log above it the one to cut, or the column is never cut at all: the logs above would
     *  wait for a base nobody may take. Each column is three logs on stone; the log above the untakeable base is a
     *  candidate and the one above THAT is not (its base-to-be passes everything). */
    private static void strandedColumnsStayReachable(GameTestHelper helper, Level level) {
        // The gate's Filter names spruce: the oak base is not a block this robot may take.
        stackLogs(helper, 2, 4, Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LOG);
        MockRobotAccess filtered = new MockRobotAccess();
        filtered.setLinkedStation(filterNaming(new ItemStack(Blocks.SPRUCE_LOG)));
        BoardRobotLumberjack filterBoard = new BoardRobotLumberjack(filtered);
        filterBoard.updateFilter();
        BlockPos oak = helper.absolutePos(new BlockPos(2, 2, 4));
        helper.assertTrue(filterBoard.isExpectedBlock(level, oak.above()),
                "the Filter names spruce, so an oak base is not worth waiting for: the spruce on it is cut now");
        helper.assertFalse(filterBoard.isExpectedBlock(level, oak.above(2)),
                "the spruce above that one stands on spruce the Filter names, so its base-to-be is takeable");

        // Protection refuses the base (the search remembers a refused block, as it does once it has settled on one).
        stackLogs(helper, 4, 4, Blocks.OAK_LOG, Blocks.OAK_LOG, Blocks.OAK_LOG);
        BlockPos claimed = helper.absolutePos(new BlockPos(4, 2, 4));
        BoardRobotLumberjack refusedBoard = new BoardRobotLumberjack(new MockRobotAccess());
        helper.assertFalse(refusedBoard.isExpectedBlock(level, claimed.above()),
                "precondition: before anyone is refused the second log waits for the base");
        try (ClaimProtectionMod claim = new ClaimProtectionMod(claimed, true)) {
            helper.assertFalse(refusedBoard.approveSearchTarget(level, claimed), "precondition: the claim refuses the base");
        }
        helper.assertTrue(refusedBoard.isExpectedBlock(level, claimed.above()),
                "a base protection refused is not coming down, so the log on it is cut instead");
        helper.assertFalse(refusedBoard.isExpectedBlock(level, claimed.above(2)),
                "and the one above that waits for it");

        // A search gave up on reaching the base from where the robot stands.
        stackLogs(helper, 2, 6, Blocks.OAK_LOG, Blocks.OAK_LOG, Blocks.OAK_LOG);
        BlockPos stuck = helper.absolutePos(new BlockPos(2, 2, 6));
        MockRobotAccess unreachingRobot = new MockRobotAccess();
        BoardRobotLumberjack unreachingBoard = new BoardRobotLumberjack(unreachingRobot);
        helper.assertFalse(unreachingBoard.isExpectedBlock(level, stuck.above()),
                "precondition: before the base is given up on the second log waits for it");
        unreachingRobot.unreachableBlockDetected(stuck);
        helper.assertTrue(unreachingRobot.isKnownUnreachableBlock(stuck), "precondition: the base is remembered");
        helper.assertTrue(unreachingBoard.isExpectedBlock(level, stuck.above()),
                "a base the robot cannot get to is not worth waiting for");
        helper.assertFalse(unreachingBoard.isExpectedBlock(level, stuck.above(2)), "and the one above that waits for it");

        // The work zone leaves the base out (its own cell, y 2, is below the zone).
        stackLogs(helper, 4, 6, Blocks.OAK_LOG, Blocks.OAK_LOG, Blocks.OAK_LOG);
        BlockPos outside = helper.absolutePos(new BlockPos(4, 2, 6));
        MockRobotAccess zoned = new MockRobotAccess();
        zoned.setZoneToWork(new Box(helper.absolutePos(new BlockPos(0, 3, 0)), helper.absolutePos(new BlockPos(5, 6, 6))));
        BoardRobotLumberjack zonedBoard = new BoardRobotLumberjack(zoned);
        zonedBoard.updateFilter();
        helper.assertTrue(zonedBoard.isExpectedBlock(level, outside.above()),
                "the base is outside the work zone, so the log on it is cut instead");
        helper.assertFalse(zonedBoard.isExpectedBlock(level, outside.above(2)),
                "and the one above that, whose base-to-be is inside the zone, waits for it");
    }
}
