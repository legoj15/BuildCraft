/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.tile;

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.recipes.AssemblyRecipeBasic;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.lib.recipe.AssemblyRecipeRegistry;
import buildcraft.lib.test.ValueIoTestUtil;
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.EnumAssemblyRecipeState;

/**
 * The Assembly Table's instruction list across a save. Each instruction's output stack is saved through
 * {@code ItemStack.CODEC}, which bounds a count to 1..99 and never checks the item's own stack limit; a recipe whose
 * output is more (an item whose stack limit a mod raised) would fail to encode and the instruction would reload without
 * its output, falling back to whatever the recipe lists first.
 */
public class TileAssemblyTableOutputNbtTest extends VanillaSetupBaseTester {

    private static final String RECIPE = "buildcraftunofficial:test_stack_count_hoard";

    @Test
    public void anInstructionOutputAboveWhatTheItemCodecCarriesSurvives() {
        ItemStack big = new ItemStack(Items.ENDER_PEARL, 128);
        big.set(DataComponents.CUSTOM_NAME, Component.literal("Hoard"));
        // The recipe lists a different, bare output first, so an output that fails to save is visible as that one.
        AssemblyRecipeBasic recipe = new AssemblyRecipeBasic(RECIPE, 1L, Set.<IngredientStack>of(),
                new ItemStack(Items.STICK));
        AssemblyRecipeRegistry.register(recipe);
        try {
            TileAssemblyTable table = newTable();
            table.recipesStates.put(new TileAssemblyTable.AssemblyInstruction(recipe, big),
                    EnumAssemblyRecipeState.POSSIBLE);
            CompoundTag saved = ValueIoTestUtil.write(table::writeData);

            TileAssemblyTable reloaded = newTable();
            reloaded.readData(ValueIoTestUtil.inputOf(saved));

            Assertions.assertEquals(1, reloaded.recipesStates.size(), "the instruction reloads");
            ItemStack got = reloaded.recipesStates.keySet().iterator().next().output;
            Assertions.assertTrue(ItemStack.isSameItemSameComponents(big, got), "item and components: " + got);
            Assertions.assertEquals(128, got.getCount(), "count above 99");
        } finally {
            AssemblyRecipeRegistry.REGISTRY.remove(RECIPE);
        }
    }

    private static TileAssemblyTable newTable() {
        return new TileAssemblyTable(BlockPos.ZERO, BCSiliconBlocks.ASSEMBLY_TABLE.get().defaultBlockState());
    }
}
