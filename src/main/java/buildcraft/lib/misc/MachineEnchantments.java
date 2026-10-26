/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.core.BCCoreConfig;

/**
 * What the Quarry and the Mining Well do with the enchantments an anvil put on their item.
 *
 * <p>Three enchantments mean something to a mining machine: Silk Touch and Fortune reach the loot roll through the
 * machine's pretend pickaxe ({@link #tool}), and Efficiency cheapens every block it breaks
 * ({@link BlockUtil#computeBlockBreakPower(net.minecraft.world.level.Level, net.minecraft.core.BlockPos, int)}). The
 * enchantments live where vanilla keeps an item's components once it is placed: in the block entity's
 * {@link BlockEntity#components() component map}, saved with it and copied back onto the item by the machine's loot
 * table, so a machine keeps them through placing and breaking.
 *
 * <p>The {@code machineEnchantments} option switches the whole feature off without touching what the machines carry:
 * the anvil refuses the enchantments ({@link buildcraft.lib.item.ItemEnchantableMachine}) and every reader here sees
 * none.
 */
public final class MachineEnchantments {

    /** Each Efficiency level divides a block's break cost by one more of these: {@code cost / (1 + step * level)}. */
    public static final double EFFICIENCY_COST_STEP = 0.25;

    private MachineEnchantments() {}

    /** Whether the {@code machineEnchantments} option is on. The option is unset in a JVM that has not built the
     *  config spec, which counts as its default, on. */
    public static boolean enabled() {
        return BCCoreConfig.machineEnchantments == null || BCCoreConfig.machineEnchantments.get();
    }

    /** Whether a mining machine does anything with {@code enchantment}: Silk Touch, Fortune and Efficiency only. */
    public static boolean isUseful(Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.SILK_TOUCH)
                || enchantment.is(Enchantments.FORTUNE)
                || enchantment.is(Enchantments.EFFICIENCY);
    }

    /** The enchantments {@code machine} carries, whatever they are, or null for none. */
    @Nullable
    private static ItemEnchantments carried(BlockEntity machine) {
        ItemEnchantments carried = machine.components().get(DataComponents.ENCHANTMENTS);
        return carried == null || carried.isEmpty() ? null : carried;
    }

    /**
     * The enchantments {@code machine} works with: those it carries that {@linkplain #isUseful are useful to it},
     * or none at all while the option is off. A machine can carry more than the anvil allows (a creative anvil takes
     * anything, so does {@code /give}), and the extras are neither used nor lost: they stay on the machine.
     */
    public static ItemEnchantments of(BlockEntity machine) {
        ItemEnchantments carried = enabled() ? carried(machine) : null;
        if (carried == null) {
            return ItemEnchantments.EMPTY;
        }
        ItemEnchantments.Mutable useful = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : carried.keySet()) {
            if (isUseful(enchantment)) {
                useful.set(enchantment, carried.getLevel(enchantment));
            }
        }
        return useful.toImmutable();
    }

    /** The pretend tool {@code machine} breaks blocks with: a fresh {@code pickaxe} (the machine's tier) carrying
     *  {@link #of the machine's enchantments}, so the block's loot table sees Silk Touch and Fortune as it would on a
     *  player's pickaxe. */
    public static ItemStack tool(Item pickaxe, BlockEntity machine) {
        ItemStack tool = new ItemStack(pickaxe);
        ItemEnchantments enchantments = of(machine);
        if (!enchantments.isEmpty()) {
            tool.set(DataComponents.ENCHANTMENTS, enchantments);
        }
        return tool;
    }

    /** The Efficiency level {@code machine} works with, 0 for none or while the option is off. Read on every power
     *  request of a break, so unlike {@link #of} it builds no filtered copy of the enchantments. */
    public static int efficiency(BlockEntity machine) {
        ItemEnchantments carried = enabled() ? carried(machine) : null;
        if (carried != null) {
            for (Holder<Enchantment> enchantment : carried.keySet()) {
                if (enchantment.is(Enchantments.EFFICIENCY)) {
                    return carried.getLevel(enchantment);
                }
            }
        }
        return 0;
    }
}
