/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

import buildcraft.core.BCCoreConfig;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.BCFactoryItems;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.MachineEnchantments;
import buildcraft.lib.test.MachineEnchantTestSupport;

/**
 * Pins what an enchanted Mining Well does with its enchantments: they ride on the block entity's components (as
 * vanilla copies them from the placed item), the well's pretend pickaxe carries them into the loot roll, so a Silk
 * Touch well yields the stone it mines, not cobblestone, and Efficiency makes a block cost less power. The
 * {@code machineEnchantments} option turns both off without taking the enchantments off the well.
 *
 * <p>Each test drives {@link TileMiningWell#mine()} by hand on freshly placed wells, so nothing depends on chunk
 * ticking, and the option is flipped and put back inside the one tick the test body runs in.
 */
public class TileMiningWellEnchantTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** A well over one block of stone on an unbreakable floor, a chest beside it catching the drop. */
    private record Rig(TileMiningWell well, BlockPos stoneAbs, BlockPos chestLocal) {}

    private static Rig rig(GameTestHelper helper, int x) {
        BlockPos wellLocal = new BlockPos(x, 4, 2);
        BlockPos stoneLocal = new BlockPos(x, 3, 2);
        BlockPos floorLocal = new BlockPos(x, 2, 2);
        BlockPos chestLocal = new BlockPos(x == 1 ? 0 : x + 1, 4, 2);

        helper.setBlock(floorLocal, Blocks.BEDROCK);
        helper.setBlock(stoneLocal, Blocks.STONE);
        helper.setBlock(wellLocal, BCFactoryBlocks.MINING_WELL.get());
        helper.setBlock(chestLocal, Blocks.CHEST);

        TileMiningWell well = (TileMiningWell) helper.getLevel().getBlockEntity(helper.absolutePos(wellLocal));
        assertTrue(well != null, "mining well block-entity must be present");
        return new Rig(well, helper.absolutePos(stoneLocal), chestLocal);
    }

    /**
     * A powered well given {@code machineItem}'s components, the way placing that item gives them, driven until its
     * stone is gone. Returns the well.
     */
    private static TileMiningWell mineOneStone(GameTestHelper helper, ItemStack machineItem, Item expected,
            Item unexpected) {
        Rig rig = rig(helper, 2);
        rig.well().applyComponentsFromItemStack(machineItem);

        for (int i = 0; i < 64 && helper.getLevel().getBlockState(rig.stoneAbs()).is(Blocks.STONE); i++) {
            rig.well().getBattery().setStored(rig.well().getBattery().getCapacity());
            rig.well().mine();
        }
        assertTrue(!helper.getLevel().getBlockState(rig.stoneAbs()).is(Blocks.STONE), "the well must mine the stone");
        assertTrue(MachineEnchantTestSupport.chestHolds(helper, rig.chestLocal(), expected),
                "the well's drop (routed into the chest beside it) must be " + expected);
        assertTrue(!MachineEnchantTestSupport.chestHolds(helper, rig.chestLocal(), unexpected),
                "the well's drop must not be " + unexpected);
        return rig.well();
    }

    /** Silk Touch on the well: the stone drops as stone. Without the enchantment reaching the pretend pickaxe it is
     *  cobblestone, which is what an unenchanted well gives. */
    public static void testSilkTouchWellYieldsStone(GameTestHelper helper) {
        try {
            ItemStack silk = MachineEnchantTestSupport.enchantedItem(helper, BCFactoryItems.MINING_WELL.get(),
                    Enchantments.SILK_TOUCH, 1);
            mineOneStone(helper, silk, Items.STONE, Items.COBBLESTONE);
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** Efficiency IV halves the cost of a block ({@code cost / (1 + 0.25 * 4)}). Two wells over the same stone, one
     *  Efficiency IV, are given exactly that halved cost: the enchanted one breaks the stone, the plain one is half
     *  way there. */
    public static void testEfficiencyWellBreaksOnLessPower(GameTestHelper helper) {
        try {
            Rig plain = rig(helper, 1);
            Rig efficient = rig(helper, 4);
            efficient.well().applyComponentsFromItemStack(MachineEnchantTestSupport.enchantedItem(helper,
                    BCFactoryItems.MINING_WELL.get(), Enchantments.EFFICIENCY, 4));

            long plainCost = BlockUtil.computeBlockBreakPower(helper.getLevel(), plain.stoneAbs());
            long halfCost = BlockUtil.computeBlockBreakPower(helper.getLevel(), efficient.stoneAbs(), 4);
            assertTrue(plainCost > 0 && halfCost == plainCost / 2,
                    "Efficiency IV must halve the cost of the stone: plain " + plainCost + ", Efficiency IV "
                            + halfCost);

            plain.well().mine(); // each well's first call only picks its target
            efficient.well().mine();
            plain.well().getBattery().setStored(halfCost);
            efficient.well().getBattery().setStored(halfCost);
            plain.well().mine();
            efficient.well().mine();

            assertTrue(helper.getLevel().getBlockState(plain.stoneAbs()).is(Blocks.STONE),
                    "a plain well given half the cost must not have broken the stone yet");
            assertTrue(!helper.getLevel().getBlockState(efficient.stoneAbs()).is(Blocks.STONE),
                    "an Efficiency IV well given half the cost must have broken the stone");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** With {@code machineEnchantments} off the well mines as if unenchanted (cobblestone under Silk Touch, no
     *  Efficiency discount) and keeps every enchantment, so turning the option back on gives them back. */
    public static void testOptionOffMinesPlainAndKeepsEnchantments(GameTestHelper helper) {
        boolean previous = BCCoreConfig.machineEnchantments.get();
        try {
            BCCoreConfig.machineEnchantments.set(false);

            ItemStack both = MachineEnchantTestSupport.enchantedItem(helper, BCFactoryItems.MINING_WELL.get(),
                    Enchantments.SILK_TOUCH, 1);
            both.enchant(MachineEnchantTestSupport.enchantment(helper, Enchantments.EFFICIENCY), 4);
            TileMiningWell well = mineOneStone(helper, both, Items.COBBLESTONE, Items.STONE);

            assertTrue(well.components().get(DataComponents.ENCHANTMENTS).size() == 2,
                    "the well must keep both enchantments while the option is off");
            assertTrue(MachineEnchantments.efficiency(well) == 0,
                    "the well must work with no Efficiency while the option is off");

            BCCoreConfig.machineEnchantments.set(true);
            assertTrue(MachineEnchantments.efficiency(well) == 4,
                    "the well must have its Efficiency back once the option is on again");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            BCCoreConfig.machineEnchantments.set(previous);
        }
    }
}
