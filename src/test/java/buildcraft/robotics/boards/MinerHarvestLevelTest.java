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

import buildcraft.VanillaSetupBaseTester;
import buildcraft.robotics.ai.MockRobotAccess;

/** The miner's tier detection: which ore property the board hunts is derived from the harvest level of
 *  the pickaxe it is holding (the modern stand-in for 7.1.x's {@code harvestLevel} NBT tag). Red until
 *  the AI step lands {@code detectHarvestLevel}'s real probe-block logic. */
public class MinerHarvestLevelTest extends VanillaSetupBaseTester {

    private final MockRobotAccess robot = new MockRobotAccess();

    @Test
    public void noToolMeansTierZero() {
        BoardRobotMiner miner = new BoardRobotMiner(robot);
        robot.setHeldItem(ItemStack.EMPTY);
        miner.detectHarvestLevel();

        Assertions.assertEquals(0, miner.getHarvestLevel(), "bare hands are tier 0");
        Assertions.assertEquals("ore@hardness=0", miner.orePropertyKey(),
                "tier 0 hunts ore@hardness=0");
    }

    @Test
    public void aWoodenPickaxeIsTierZero() {
        BoardRobotMiner miner = new BoardRobotMiner(robot);
        robot.setHeldItem(new ItemStack(Items.WOODEN_PICKAXE));
        miner.detectHarvestLevel();

        Assertions.assertEquals(0, miner.getHarvestLevel(), "wooden is tier 0");
        Assertions.assertEquals("ore@hardness=0", miner.orePropertyKey());
    }

    @Test
    public void aStonePickaxeIsTierOne() {
        BoardRobotMiner miner = new BoardRobotMiner(robot);
        robot.setHeldItem(new ItemStack(Items.STONE_PICKAXE));
        miner.detectHarvestLevel();

        Assertions.assertEquals(1, miner.getHarvestLevel(), "stone is tier 1");
        Assertions.assertEquals("ore@hardness=1", miner.orePropertyKey());
    }

    @Test
    public void aDiamondPickaxeIsTierThree() {
        BoardRobotMiner miner = new BoardRobotMiner(robot);
        robot.setHeldItem(new ItemStack(Items.DIAMOND_PICKAXE));
        miner.detectHarvestLevel();

        Assertions.assertEquals(3, miner.getHarvestLevel(), "diamond is tier 3 (0-based, like modern MC)");
        Assertions.assertEquals("ore@hardness=3", miner.orePropertyKey(),
                "tier 3 hunts ore@hardness=3");
    }

    @Test
    public void aNetheritePickaxeClampsToTheHardestProperty() {
        BoardRobotMiner miner = new BoardRobotMiner(robot);
        robot.setHeldItem(new ItemStack(Items.NETHERITE_PICKAXE));
        miner.detectHarvestLevel();

        Assertions.assertEquals(4, miner.getHarvestLevel(), "netherite is tier 4");
        Assertions.assertEquals("ore@hardness=3", miner.orePropertyKey(),
                "the properties stop at ore@hardness=3, so tier 4 clamps onto it");
    }

    @Test
    public void onlyPickaxesAreExpectedTools() {
        BoardRobotMiner miner = new BoardRobotMiner(robot);

        Assertions.assertTrue(miner.isExpectedTool(new ItemStack(Items.STONE_PICKAXE)),
                "a pickaxe works");
        Assertions.assertFalse(miner.isExpectedTool(new ItemStack(Items.IRON_SHOVEL)),
                "a shovel does not");
        Assertions.assertFalse(miner.isExpectedTool(ItemStack.EMPTY),
                "nothing held is no tool");
    }
}
