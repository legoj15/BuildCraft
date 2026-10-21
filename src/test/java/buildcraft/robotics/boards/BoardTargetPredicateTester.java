/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

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
import net.minecraft.world.level.GameType;

import buildcraft.robotics.ai.MockRobotAccess;

/** The two combat boards' entity predicates, pinned against REAL entities — which a unit test cannot
 *  build, since every entity constructor wants a live {@code Level}.
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
}
