/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.GuidePartFactory;
import buildcraft.lib.misc.ItemStackKey;
import buildcraft.lib.recipe.ChangingItemStack;
import buildcraft.lib.recipe.ChangingObject;

/** Factory for a {@link GuideLaserTable} panel. Holds only stacks and costs, so the lookups that build it (and the
 *  unit tests that check them) never touch rendering. Several recipes folded into one panel cycle in lock-step: the
 *  N-th option of every input slot, of the output and of the cost all belong to recipe N (see
 *  {@link ChangingObject}). */
public class GuideLaserTableFactory implements GuidePartFactory {
    private final ChangingItemStack[] input;
    private final ChangingItemStack table;
    private final ChangingItemStack output;
    private final ChangingObject<Long> mjCost;
    private final int hash;

    /** @param input  One entry per input slot, drawn top to bottom.
     *  @param table  The table's own item, drawn over the arrow.
     *  @param output What the table makes.
     *  @param mjCost The laser energy each step needs, in micro-MJ. */
    public GuideLaserTableFactory(ChangingItemStack[] input, ChangingItemStack table, ChangingItemStack output,
        ChangingObject<Long> mjCost) {
        this.input = input;
        this.table = table;
        this.output = output;
        this.mjCost = mjCost;
        this.hash = Arrays.deepHashCode(new Object[] { input, table, output, mjCost });
    }

    @Override
    public GuideLaserTable createNew(GuiGuide gui) {
        return new GuideLaserTable(gui, input, table, output, mjCost);
    }

    public int getInputSlotCount() {
        return input.length;
    }

    /** @return Every stack input slot {@code slot} cycles through (one per folded recipe). */
    public List<ItemStack> getInputOptions(int slot) {
        return stacksOf(input[slot]);
    }

    /** @return Every stack the output slot cycles through (one per folded recipe). */
    public List<ItemStack> getOutputOptions() {
        return stacksOf(output);
    }

    public ItemStack getTable() {
        return table.getOptions().get(0).baseStack;
    }

    public List<Long> getMjCosts() {
        return mjCost.getOptions();
    }

    private static List<ItemStack> stacksOf(ChangingItemStack changing) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStackKey key : changing.getOptions()) {
            stacks.add(key.baseStack);
        }
        return stacks;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != getClass()) return false;
        GuideLaserTableFactory other = (GuideLaserTableFactory) obj;
        if (hash != other.hash) return false;
        return Arrays.equals(input, other.input) && table.equals(other.table) && output.equals(other.output)
            && mjCost.equals(other.mjCost);
    }
}
