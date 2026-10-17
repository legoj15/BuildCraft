/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.client.guide.parts.recipe.GuideLaserTableFactory;
import buildcraft.lib.recipe.ChangingItemStack;
import buildcraft.lib.recipe.ChangingObject;

import buildcraft.robotics.recipe.RoboticsJeiSubtypes;

/** The matching and folding both robotics table lookups share. A step matches a stack when they are the same
 *  {@linkplain RoboticsJeiSubtypes#sameVariant variant} — the same board program, the same robot program — so a
 *  Miner board's page finds exactly the Miner step. Usages fall back to item-level matching when no step takes that
 *  exact variant (the tables accept any board and any robot, so a programmed Miner board can still be reprogrammed).
 *  Several matches fold into ONE cycling panel: a blank robot is used by every robot program, and one panel per
 *  program would bury the page in near-identical panels. */
final class LaserTableLookup {
    private LaserTableLookup() {}

    /** @return The steps whose result is {@code stack}'s variant, as display factories. */
    static List<GuidePartFactory> recipes(List<LaserTableStep> steps, ItemStack stack, ItemStack table) {
        List<LaserTableStep> matched = new ArrayList<>();
        for (LaserTableStep step : steps) {
            if (anyMatch(step.outputs(), stack, RoboticsJeiSubtypes::sameVariant)) {
                matched.add(step);
            }
        }
        return fold(matched, table);
    }

    /** @return The steps that take {@code stack} as an input, as display factories. */
    static List<GuidePartFactory> usages(List<LaserTableStep> steps, ItemStack stack, ItemStack table) {
        List<LaserTableStep> matched = stepsTaking(steps, stack, RoboticsJeiSubtypes::sameVariant);
        if (matched.isEmpty()) {
            matched = stepsTaking(steps, stack, ItemStack::isSameItem);
        }
        return fold(matched, table);
    }

    /** @return The step pinned by {@code id}, or null if no step has it. */
    @Nullable
    static GuidePartFactory byId(List<LaserTableStep> steps, String id, ItemStack table) {
        for (LaserTableStep step : steps) {
            if (step.id().equals(id)) {
                return fold(List.of(step), table).get(0);
            }
        }
        return null;
    }

    private static List<LaserTableStep> stepsTaking(List<LaserTableStep> steps, ItemStack stack,
            BiPredicate<ItemStack, ItemStack> same) {
        List<LaserTableStep> matched = new ArrayList<>();
        for (LaserTableStep step : steps) {
            for (List<ItemStack> slot : step.slots()) {
                if (anyMatch(slot, stack, same)) {
                    matched.add(step);
                    break;
                }
            }
        }
        return matched;
    }

    private static boolean anyMatch(List<ItemStack> candidates, ItemStack stack, BiPredicate<ItemStack, ItemStack> same) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ItemStack candidate : candidates) {
            if (same.test(candidate, stack)) {
                return true;
            }
        }
        return false;
    }

    /** One step keeps every option of each slot (an input slot that accepts several stacks cycles through them);
     *  several steps fold into one panel cycling in lock-step, one frame per step, each slot showing that step's
     *  first option. */
    static List<GuidePartFactory> fold(List<LaserTableStep> steps, ItemStack table) {
        if (steps.isEmpty()) {
            return List.of();
        }
        ChangingItemStack tableStack = new ChangingItemStack(table);
        if (steps.size() == 1) {
            LaserTableStep step = steps.get(0);
            ChangingItemStack[] input = new ChangingItemStack[step.slots().size()];
            for (int i = 0; i < input.length; i++) {
                input[i] = new ChangingItemStack(step.slots().get(i));
            }
            return List.of(new GuideLaserTableFactory(input, tableStack, new ChangingItemStack(step.outputs().get(0)),
                    new ChangingObject<>(new Long[] { step.microJoules() })));
        }
        int maxSlots = 0;
        for (LaserTableStep step : steps) {
            maxSlots = Math.max(maxSlots, step.slots().size());
        }
        ChangingItemStack[] input = new ChangingItemStack[maxSlots];
        for (int slot = 0; slot < maxSlots; slot++) {
            List<ItemStack> frames = new ArrayList<>(steps.size());
            for (LaserTableStep step : steps) {
                List<ItemStack> options = slot < step.slots().size() ? step.slots().get(slot) : List.of();
                frames.add(options.isEmpty() ? ItemStack.EMPTY : options.get(0));
            }
            input[slot] = new ChangingItemStack(frames);
        }
        List<ItemStack> outputs = new ArrayList<>(steps.size());
        Long[] costs = new Long[steps.size()];
        for (int i = 0; i < steps.size(); i++) {
            outputs.add(steps.get(i).outputs().get(0));
            costs[i] = steps.get(i).microJoules();
        }
        return List.of(new GuideLaserTableFactory(input, tableStack, new ChangingItemStack(outputs),
                new ChangingObject<>(costs)));
    }
}
