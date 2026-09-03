/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;

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
        /*Mob zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
        Mob slime = helper.spawn(net.minecraft.world.entity.EntityTypes.SLIME, new BlockPos(2, 2, 3));
        Mob cow = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, new BlockPos(4, 2, 1));
        Mob villager = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(4, 2, 4));*/
        //?} else {
        Mob zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        Mob slime = helper.spawn(net.minecraft.world.entity.EntityType.SLIME, new BlockPos(2, 2, 3));
        Mob cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(4, 2, 1));
        Mob villager = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(4, 2, 4));
        //?}

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
}
