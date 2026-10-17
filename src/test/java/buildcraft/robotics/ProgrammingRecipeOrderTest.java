/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.recipes.IProgrammingRecipe;
import buildcraft.lib.recipe.ProgrammingRecipeRegistry;
import buildcraft.silicon.tile.TileProgrammingTable;

/** Which Programming Table recipe a board lands in must not depend on a {@code HashMap}'s bucket order. The
 *  registry keeps its recipes in id order, so when two recipes accept one input the table, the JEI transfer and
 *  the JEI collector all resolve it the same way on every run, every JVM, client and server alike - and the
 *  registry says out loud when a recipe offers more programs than the table's 6x4 grid can ever show. */
public class ProgrammingRecipeOrderTest extends VanillaSetupBaseTester {

    /** A recipe that accepts whatever the predicate says and offers {@code optionCount} options. */
    private static final class StubRecipe implements IProgrammingRecipe {
        private final String id;
        private final Predicate<ItemStack> accepts;
        private final int optionCount;

        StubRecipe(String id, Predicate<ItemStack> accepts, int optionCount) {
            this.id = id;
            this.accepts = accepts;
            this.optionCount = optionCount;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public List<ItemStack> getOptions(int width, int height) {
            List<ItemStack> options = new ArrayList<>();
            for (int i = 0; i < optionCount; i++) {
                options.add(new ItemStack(Items.PAPER));
            }
            return options;
        }

        @Override
        public long getEnergyCost(ItemStack option) {
            return 0;
        }

        @Override
        public boolean canCraft(ItemStack input) {
            return accepts.test(input);
        }

        @Override
        public ItemStack craft(ItemStack input, ItemStack option) {
            return option.copy();
        }
    }

    private static List<String> scrambledIds() {
        // Sixteen ids in an order that is neither sorted nor any HashMap's: the odds of a hash-ordered registry
        // handing them back sorted by accident are about one in twenty trillion.
        return List.of("mod:m_programmer", "mod:b_robots", "mod:x_gates", "mod:d_boards", "mod:p_pumps",
                "mod:a_first", "mod:k_kinesis", "mod:z_last", "mod:f_filters", "mod:t_tanks", "mod:h_heat",
                "mod:r_requests", "mod:c_carriers", "mod:w_wires", "mod:j_jobs", "mod:n_nodes");
    }

    private static List<String> idsOf(Iterable<IProgrammingRecipe> recipes) {
        List<String> ids = new ArrayList<>();
        recipes.forEach(recipe -> ids.add(recipe.getId()));
        return ids;
    }

    @Test
    public void recipesComeBackInIdOrderWhateverTheRegistrationOrder() {
        ProgrammingRecipeRegistry registry = new ProgrammingRecipeRegistry();
        for (String id : scrambledIds()) {
            registry.addRecipe(new StubRecipe(id, stack -> false, 1));
        }

        List<String> sorted = new ArrayList<>(scrambledIds());
        Collections.sort(sorted);
        Assertions.assertEquals(sorted, idsOf(registry.getRecipes()),
                "iteration order is part of the contract: it decides which recipe a shared input lands in");
    }

    @Test
    public void removingAndReAddingARecipeDoesNotMoveIt() {
        ProgrammingRecipeRegistry registry = new ProgrammingRecipeRegistry();
        for (String id : List.of("mod:b", "mod:a", "mod:c")) {
            registry.addRecipe(new StubRecipe(id, stack -> false, 1));
        }

        registry.removeRecipe("mod:a");
        registry.addRecipe(new StubRecipe("mod:a", stack -> false, 1));

        Assertions.assertEquals(List.of("mod:a", "mod:b", "mod:c"), idsOf(registry.getRecipes()),
                "position follows the id, not the moment of registration");
    }

    @Test
    public void whenTwoRecipesAcceptAnInputTheLowestIdWinsInTheLiveTable() {
        // The live registry, as the table and the JEI transfer see it: several recipes claim the same marker
        // input, registered in an order that is not the id order.
        List<StubRecipe> stubs = new ArrayList<>();
        for (String id : List.of("order_test:g", "order_test:c", "order_test:h", "order_test:a", "order_test:e",
                "order_test:b", "order_test:f", "order_test:d")) {
            stubs.add(new StubRecipe(id, stack -> stack.is(Items.CARROT_ON_A_STICK), 1));
        }
        try {
            stubs.forEach(ProgrammingRecipeRegistry.INSTANCE::addRecipe);

            IProgrammingRecipe match = TileProgrammingTable.matchRecipe(new ItemStack(Items.CARROT_ON_A_STICK));

            Assertions.assertNotNull(match, "precondition: the marker input is accepted");
            Assertions.assertEquals("order_test:a", match.getId(),
                    "the lowest id takes the input, not whichever the hash buckets reach first");
        } finally {
            stubs.forEach(stub -> ProgrammingRecipeRegistry.INSTANCE.removeRecipe(stub.getId()));
        }
    }

    @Test
    public void aProgramListThatFitsTheGridDrawsNoWarning() {
        int cells = ProgrammingRecipeRegistry.GRID_CELLS;
        Assertions.assertEquals(TileProgrammingTable.OPTION_COLS * TileProgrammingTable.OPTION_ROWS, cells,
                "the registry's idea of the grid is the table's grid");

        Assertions.assertNull(ProgrammingRecipeRegistry.unreachableOptionsWarning(
                new StubRecipe("mod:full", stack -> true, cells)),
                "exactly a full grid is fine");
        Assertions.assertNull(ProgrammingRecipeRegistry.unreachableOptionsWarning(
                new StubRecipe("mod:small", stack -> true, 3)));
    }

    @Test
    public void aProgramListPastTheGridIsReportedWithTheCountThatCannotBeReached() {
        int cells = ProgrammingRecipeRegistry.GRID_CELLS;

        String warning = ProgrammingRecipeRegistry.unreachableOptionsWarning(
                new StubRecipe("mod:too_many", stack -> true, cells + 3));

        Assertions.assertNotNull(warning, "the 25th program is unreachable in the GUI, JEI and the transfer");
        Assertions.assertTrue(warning.contains("mod:too_many"), "names the recipe: " + warning);
        Assertions.assertTrue(warning.contains(String.valueOf(cells + 3)), "gives its option count: " + warning);
        Assertions.assertTrue(warning.contains(String.valueOf(cells)), "gives the grid size: " + warning);
    }

    @Test
    public void registeringAnOversizedRecipeStillRegistersIt() {
        ProgrammingRecipeRegistry registry = new ProgrammingRecipeRegistry();
        StubRecipe oversized = new StubRecipe("mod:oversized", stack -> true, ProgrammingRecipeRegistry.GRID_CELLS + 1);

        registry.addRecipe(oversized);

        Assertions.assertSame(oversized, registry.getRecipe("mod:oversized"),
                "the warning is advice, not a veto: the first 24 programs still work");
    }
}
