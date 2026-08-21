/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.robotics.ai.MockRobotAccess;

/** The pure-state tool/block predicates of the Ph5 work boards, driven without a Level. Every predicate
 *  is the board's identity — which tool it carries and what it hunts — so each is pinned directly.
 *  Where a predicate's state side needs a data tag to resolve (the "wood" property), the unit JVM cannot
 *  pin it — the FML-JUnit environment does no resource load — and that side is pinned by the
 *  {@code world_properties_match} game test instead. */
public class BoardPredicateSweepTest extends VanillaSetupBaseTester {

    private final MockRobotAccess robot = new MockRobotAccess();

    @Test
    public void theLumberjackCarriesAnAxe() {
        BoardRobotLumberjack lumberjack = new BoardRobotLumberjack(robot);

        Assertions.assertTrue(lumberjack.isExpectedTool(new ItemStack(Items.WOODEN_AXE)),
                "an axe is the lumberjack's tool");
        Assertions.assertFalse(lumberjack.isExpectedTool(new ItemStack(Items.DIAMOND_PICKAXE)),
                "a pickaxe is not an axe");
        // The block side (isExpectedBlock on a log) is tag-backed and pinned by the
        // world_properties_match game test, not here.
    }

    @Test
    public void theHarvesterHuntsMatureCrops() {
        BoardRobotHarvester harvester = new BoardRobotHarvester(robot);

        Assertions.assertTrue(harvester.isExpectedBlock(
                Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)),
                "a fully-grown wheat crop is harvestable");
        Assertions.assertFalse(harvester.isExpectedBlock(Blocks.STONE.defaultBlockState()),
                "stone is not a crop");
    }

    @Test
    public void theMinerCarriesAPickaxeAndStartsAtTierZero() {
        BoardRobotMiner miner = new BoardRobotMiner(robot);

        Assertions.assertEquals(0, miner.getHarvestLevel(),
                "no tool equipped is harvest tier 0");
        Assertions.assertEquals("ore@hardness=0", miner.orePropertyKey(),
                "tier 0 hunts the hardest property it can break — ore@hardness=0");
        Assertions.assertTrue(miner.isExpectedTool(new ItemStack(Items.DIAMOND_PICKAXE)),
                "a pickaxe is the miner's tool");
        Assertions.assertFalse(miner.isExpectedTool(new ItemStack(Items.IRON_AXE)),
                "an axe is not a pickaxe");
    }

    @Test
    public void thePlanterCarriesASeed() {
        BoardRobotPlanter planter = new BoardRobotPlanter(robot);

        Assertions.assertTrue(planter.matchesSeed(new ItemStack(Items.WHEAT_SEEDS)),
                "wheat seeds are a seed");
        Assertions.assertFalse(planter.matchesSeed(new ItemStack(Items.COBBLESTONE)),
                "cobblestone is not a seed");
    }

    @Test
    public void theFarmerCarriesAHoe() {
        BoardRobotFarmer farmer = new BoardRobotFarmer(robot);

        Assertions.assertTrue(farmer.isExpectedHoe(new ItemStack(Items.IRON_HOE)),
                "a hoe is the farmer's tool");
        Assertions.assertFalse(farmer.isExpectedHoe(new ItemStack(Items.IRON_SWORD)),
                "a sword is not a hoe");
    }

    @Test
    public void theKnightCarriesASword() {
        BoardRobotKnight knight = new BoardRobotKnight(robot);

        Assertions.assertTrue(knight.isExpectedSword(new ItemStack(Items.IRON_SWORD)),
                "a sword is the knight's tool");
        Assertions.assertFalse(knight.isExpectedSword(new ItemStack(Items.BOW)),
                "a bow is not a sword");
    }

    @Test
    public void theButcherCarriesASword() {
        BoardRobotButcher butcher = new BoardRobotButcher(robot);

        Assertions.assertTrue(butcher.isExpectedSword(new ItemStack(Items.STONE_SWORD)),
                "a sword is the butcher's tool");
        Assertions.assertFalse(butcher.isExpectedSword(new ItemStack(Items.BOW)),
                "a bow is not a sword");
    }
}
