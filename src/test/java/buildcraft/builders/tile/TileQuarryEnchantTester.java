/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.test.MachineEnchantTestSupport;

/**
 * Pins what an enchanted Quarry does with its enchantments in {@link TileQuarry.TaskBreakBlock}: Silk Touch reaches
 * the pretend pickaxe the block is broken with (stone drops as stone, not cobblestone), and Efficiency lowers the
 * task's power target, which the power request and the progress sum both read.
 *
 * <p>Same rig as {@link TileQuarryDropsTester}: a task is made by hand and fed power, so nothing depends on the
 * quarry's frame or chunk ticking.
 */
public class TileQuarryEnchantTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    private static final BlockPos QUARRY = new BlockPos(2, 2, 2);
    private static final BlockPos CHEST = new BlockPos(3, 2, 2);
    private static final BlockPos STONE = new BlockPos(2, 3, 2);

    private static TileQuarry rig(GameTestHelper helper) {
        helper.setBlock(QUARRY, BCBuildersBlocks.QUARRY.get());
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.setBlock(STONE, Blocks.STONE);

        TileQuarry quarry = (TileQuarry) helper.getLevel().getBlockEntity(helper.absolutePos(QUARRY));
        assertTrue(quarry != null, "quarry block-entity must be present");
        quarry.drillPos = Vec3.atLowerCornerOf(helper.absolutePos(STONE));
        return quarry;
    }

    /** Silk Touch on the quarry: the stone it breaks drops as stone. Without the enchantment reaching the pretend
     *  pickaxe it is cobblestone, which is what an unenchanted quarry gives. */
    public static void testSilkTouchQuarryYieldsStone(GameTestHelper helper) {
        try {
            TileQuarry quarry = rig(helper);
            quarry.applyComponentsFromItemStack(
                    MachineEnchantTestSupport.enchantedItem(helper, BCBuildersItems.QUARRY.get(),
                            Enchantments.SILK_TOUCH, 1));

            TileQuarry.TaskBreakBlock task = quarry.new TaskBreakBlock(helper.absolutePos(STONE));
            assertTrue(task.addPower(task.getTarget()), "feeding the task its whole target must finish the break");

            assertTrue(helper.getLevel().getBlockState(helper.absolutePos(STONE)).isAir(),
                    "the stone must be broken after the task finishes");
            assertTrue(MachineEnchantTestSupport.chestHolds(helper, CHEST, Items.STONE),
                    "the chest beside a Silk Touch quarry must receive the stone itself");
            assertTrue(!MachineEnchantTestSupport.chestHolds(helper, CHEST, Items.COBBLESTONE),
                    "a Silk Touch quarry must not drop cobblestone for stone");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** Efficiency IV halves the task's target, live: the same task prices the stone at the plain cost before the
     *  quarry has the enchantment and at half of it after, and feeding it that half finishes the break. */
    public static void testEfficiencyQuarryHalvesTheTarget(GameTestHelper helper) {
        try {
            TileQuarry quarry = rig(helper);
            long plainCost = BlockUtil.computeBlockBreakPower(helper.getLevel(), helper.absolutePos(STONE));

            TileQuarry.TaskBreakBlock task = quarry.new TaskBreakBlock(helper.absolutePos(STONE));
            assertTrue(plainCost > 0 && task.getTarget() == plainCost,
                    "an unenchanted quarry prices the stone at " + plainCost + ", got " + task.getTarget());

            quarry.applyComponentsFromItemStack(
                    MachineEnchantTestSupport.enchantedItem(helper, BCBuildersItems.QUARRY.get(),
                            Enchantments.EFFICIENCY, 4));
            assertTrue(task.getTarget() == plainCost / 2,
                    "an Efficiency IV quarry must price the stone at " + plainCost / 2 + ", got " + task.getTarget());
            assertTrue(task.getRequiredPowerThisTick() <= plainCost / 2,
                    "the power request must follow the cheaper target, got " + task.getRequiredPowerThisTick());

            assertTrue(!task.addPower(plainCost / 2 - 1), "one microjoule short of the cheaper target must not finish");
            assertTrue(task.addPower(1), "reaching the cheaper target must finish the break");
            assertTrue(helper.getLevel().getBlockState(helper.absolutePos(STONE)).isAir(),
                    "the stone must be broken once the cheaper target is paid");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
