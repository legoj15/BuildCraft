/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.crops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.crops.CropManager;
import buildcraft.api.mj.MjAPI;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.ai.AIRobotPlant;
import buildcraft.robotics.entity.EntityRobot;

/** Sugar cane through the crop API, end to end: {@link CropHandlerReeds}' soil rule (7.1.x's
 *  {@code canSustainPlant(reeds) && block != reeds && isAirBlock(above)}, which modern MC spells as the cane
 *  block's own {@code canSurvive} — sand or dirt beside water) and a Planter robot's {@link AIRobotPlant}
 *  actually setting the cane down through {@link CropManager}. Before the reeds handler was ported the
 *  cane item was nobody's seed, so the Planter never fetched it and this plant never happened. */
public class CropHandlerReedsTester {

    /** A wet sand cell (water beside it in a stone cup, so nothing flows into a neighbouring arena), a dry
     *  sand cell, and stone beside the same water. Only the wet sand takes cane; once planted, neither the
     *  cane itself (7.1.x's {@code block != reeds}: the planter never stacks cane) nor the now-covered sand
     *  is a planting spot any more. */
    public static void planterPlantsSugarCaneOnlyOnSandBesideWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wetRel = new BlockPos(2, 1, 2);
        BlockPos waterRel = new BlockPos(3, 1, 2);
        BlockPos dryRel = new BlockPos(2, 1, 5);
        BlockPos stoneRel = new BlockPos(3, 1, 3);

        // The cup: stone under and around the water on every side but the sand's.
        helper.setBlock(waterRel.below(), Blocks.STONE);
        helper.setBlock(waterRel.relative(Direction.EAST), Blocks.STONE);
        helper.setBlock(waterRel.relative(Direction.NORTH), Blocks.STONE);
        helper.setBlock(stoneRel, Blocks.STONE); // = the cup's SOUTH wall
        helper.setBlock(wetRel, Blocks.SAND);
        helper.setBlock(waterRel, Blocks.WATER);
        helper.setBlock(dryRel, Blocks.SAND);

        ItemStack cane = new ItemStack(Items.SUGAR_CANE);
        BlockPos wet = helper.absolutePos(wetRel);
        helper.assertTrue(CropManager.canSustainPlant(level, cane, wet),
                "sand beside water with air above is a cane spot");
        helper.assertFalse(CropManager.canSustainPlant(level, cane, helper.absolutePos(dryRel)),
                "dry sand is not — cane needs water beside its soil");
        helper.assertFalse(CropManager.canSustainPlant(level, cane, helper.absolutePos(stoneRel)),
                "stone beside water is not — cane needs sand or dirt");

        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), level);
        Vec3 robotPos = Vec3.atCenterOf(helper.absolutePos(wetRel.above().relative(Direction.WEST)));
        robot.setPos(robotPos.x, robotPos.y, robotPos.z);
        robot.getBattery().addPower(5000L * MjAPI.MJ, false);
        robot.setItemInUse(new ItemStack(Items.SUGAR_CANE));

        // Hand-cycled on an unadded robot (the RobotActionAIsTester pattern): the AI is pure world
        // interaction, and the honest completion signal is the cane appearing, not a termination flag.
        AIRobotPlant planter = new AIRobotPlant(robot, wet);
        int cycles = 0;
        while (!level.getBlockState(wet.above()).is(Blocks.SUGAR_CANE) && cycles < 80) {
            planter.cycle();
            cycles++;
        }
        helper.assertBlockPresent(Blocks.SUGAR_CANE, wetRel.above());
        helper.assertTrue(planter.success(), "the planting reports success");
        helper.assertTrue(robot.getHeldItem().isEmpty(), "the robot's cane was planted, not kept");
        boolean spilled = false;
        for (var item : EntityArenaUtil.droppedItems(helper, wetRel.above(), 3)) {
            spilled |= item.getItem().is(Items.SUGAR_CANE);
            item.discard();
        }
        helper.assertFalse(spilled, "the single cane is planted, not spilled");

        helper.assertFalse(CropManager.canSustainPlant(level, cane, wet),
                "sand already carrying cane has no air above it");
        helper.assertFalse(CropManager.canSustainPlant(level, cane, wet.above()),
                "cane is never planted on cane — the planter does not stack it (7.1.x's block != reeds)");
        helper.succeed();
    }
}
