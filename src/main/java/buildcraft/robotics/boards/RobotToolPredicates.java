/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.Map;
import java.util.Set;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The tool predicates shared by the Ph5 work boards (axe / pickaxe / sword / hoe).
 *
 *  <p>Since the 26.1 line the vanilla tool classes (a {@code PickaxeItem}, a {@code SwordItem}, …) are
 *  gone — tools are plain {@link Item}s carrying a {@code Tool} data component — so an
 *  {@code instanceof} check is no longer possible. Each predicate therefore tries identity against the
 *  five vanilla tools of the family first (which lets the unit tests pin them without any data-tag
 *  loading), and then falls back to the vanilla item tag, which is what recognises a modded tool in
 *  game (data tags never load in the unit-test JVM, so the identity side is all that runs there).
 *
 *  <p>The pickaxe tiers follow 7.1.x's harvest levels: wood 0, stone 1, iron 2, diamond 3, netherite 4.
 *  Tier detection is identity-only on purpose: the {@code Tool} component's mining speed cannot tell a
 *  pickaxe from an axe of the same material (an iron axe mines at the same speed as an iron pickaxe),
 *  so a tag-detected modded pickaxe reports tier 0 — it still breaks tier-0 ore, which is the right
 *  answer for a generic pickaxe. */
public final class RobotToolPredicates {

    private static final Set<Item> AXES = Set.of(
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE);
    private static final Set<Item> SWORDS = Set.of(
            Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
    private static final Set<Item> HOES = Set.of(
            Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE);
    /** The 7.1.x harvest levels of the vanilla pickaxes; everything else (an empty hand, or a
     *  tag-detected modded pickaxe) is tier 0. */
    private static final Map<Item, Integer> PICKAXE_TIERS = Map.of(
            Items.WOODEN_PICKAXE, 0,
            Items.STONE_PICKAXE, 1,
            Items.IRON_PICKAXE, 2,
            Items.DIAMOND_PICKAXE, 3,
            Items.NETHERITE_PICKAXE, 4);

    private RobotToolPredicates() {
    }

    public static boolean isAxe(ItemStack stack) {
        return !stack.isEmpty() && (AXES.contains(stack.getItem()) || stack.is(ItemTags.AXES));
    }

    public static boolean isPickaxe(ItemStack stack) {
        return !stack.isEmpty() && (PICKAXE_TIERS.containsKey(stack.getItem()) || stack.is(ItemTags.PICKAXES));
    }

    public static boolean isSword(ItemStack stack) {
        return !stack.isEmpty() && (SWORDS.contains(stack.getItem()) || stack.is(ItemTags.SWORDS));
    }

    public static boolean isHoe(ItemStack stack) {
        return !stack.isEmpty() && (HOES.contains(stack.getItem()) || stack.is(ItemTags.HOES));
    }

    /** The 7.1.x harvest level of the held pickaxe (0 for an empty hand or an unrecognised tool). */
    public static int pickaxeTier(ItemStack stack) {
        return stack.isEmpty() ? 0 : PICKAXE_TIERS.getOrDefault(stack.getItem(), 0);
    }
}
