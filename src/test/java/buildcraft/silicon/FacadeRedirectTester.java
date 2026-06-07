/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import buildcraft.lib.misc.ItemStackKey;

import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadeStateManager;
import buildcraft.silicon.recipe.FacadeAssemblyRecipes;

/**
 * Pins the server-authoritative contract of facade recipe redirects — the seam behind the
 * "Facade redirects are client-only on dedicated servers" limitation.
 *
 * <p>Redirects (e.g. an item that, on the client, maps to a visually-identical surviving facade) are
 * computed client-side from baked block models and published into
 * {@link FacadeStateManager#stackRedirects} only when an integrated server shares the client's JVM (see
 * {@link buildcraft.silicon.client.FacadeDeduplicator#applyRedirectAuthority()}). On a dedicated server
 * the map is always empty. {@link FacadeAssemblyRecipes} — which runs on the logical server during the
 * assembly-table tick — reads that map, so its redirect behavior is fully determined by the map's
 * contents. These tests drive the recipe directly against a hand-seeded {@code stackRedirects} to pin
 * both halves of the invariant without a real dedicated server.
 *
 * <p><b>Fixture choice — why a stick, not a brick_slab.</b> The {@code todos.md} entry describes the
 * redirect as "brick_slab → bricks", but brick_slab is a misleading test fixture: a slab's
 * {@code type=double} state <em>is</em> a full cube, so {@code brick_slab[double]} is its own valid
 * facade in {@code stackFacades} regardless of any redirect. (An earlier version of this test asserted
 * brick_slab produced nothing with an empty map and correctly failed — brick_slab yields its own
 * double-slab facade. That means the real dedicated-server impact on slabs is only that they resolve to
 * a different, visually identical blockstate than single-player's redirect would — they still craft a
 * working facade.) To exercise the redirect path in isolation we use an item with no facade of its own
 * — a plain {@link Items#STICK} — so the only way it can yield a facade is through {@code stackRedirects}.
 * {@link FacadeAssemblyRecipes#getOutputs} keys inputs by {@link ItemStackKey} (item + components), which
 * is agnostic to whether the item is a block, so a stick is a faithful stand-in for "an input that can
 * only craft a facade via a redirect."
 *
 * <p>Each test brackets its mutation of the {@code static volatile stackRedirects} field in a
 * try/finally that restores the prior reference, so it neither depends on nor corrupts the ambient
 * (game-test server: empty) redirect state for other tests.
 */
public final class FacadeRedirectTester {

    private FacadeRedirectTester() {}

    /**
     * Empty {@code stackRedirects} (the dedicated-server / withheld-authority state): an item with no
     * facade of its own (a stick) produces no facade. Pins that the accepted limitation holds — with no
     * redirect table, only items that map to a facade directly via {@code stackFacades} can be crafted —
     * and that nothing else silently fabricates a redirect. The direct path is verified to still work
     * (bricks → bricks facade) so the empty result is attributable to the missing redirect, not a broken
     * recipe.
     */
    public static void testEmptyRedirectsProduceNoFacadeForMappedItem(GameTestHelper helper) {
        FacadeStateManager.ensureInitialized();
        Map<ItemStackKey, List<FacadeBlockStateInfo>> prior = FacadeStateManager.stackRedirects;
        try {
            FacadeStateManager.stackRedirects = Map.of();

            ItemStack stick = new ItemStack(Items.STICK);
            assertNoFacadeOutput(helper, stick, "a stick with empty redirects (dedicated-server state)");

            // Sanity: the *direct* path still works — bricks itself yields its facade with no redirects.
            FacadeBlockStateInfo bricks = requireFacade(helper, Blocks.BRICKS);
            Set<ItemStack> direct = facadeOutputsFor(new ItemStack(Blocks.BRICKS.asItem()));
            if (direct.isEmpty()) {
                helper.fail("Direct bricks→facade crafting produced nothing even with the canonical "
                    + "facade present — stackFacades lookup is broken, unrelated to redirects.");
                return;
            }
            assertProducesFacadeOf(helper, direct, bricks, "direct bricks input");
            helper.succeed();
        } finally {
            FacadeStateManager.stackRedirects = prior;
        }
    }

    /**
     * Populated {@code stackRedirects} (the integrated-server / authoritative state): seed
     * {@code stick → bricks} and confirm the recipe now yields the bricks facade from a stick, and that
     * {@code getInputsFor(bricksFacade)} lists the stick as an accepted input. Pins that the feature
     * works whenever the table is legitimately present — i.e. the gating in
     * {@link buildcraft.silicon.client.FacadeDeduplicator#applyRedirectAuthority()} is the only thing
     * standing between "redirects work" and "redirects don't", so single-player keeps working unchanged.
     */
    public static void testPopulatedRedirectsMakeFacadeForMappedItem(GameTestHelper helper) {
        FacadeStateManager.ensureInitialized();
        Map<ItemStackKey, List<FacadeBlockStateInfo>> prior = FacadeStateManager.stackRedirects;
        try {
            FacadeBlockStateInfo bricks = requireFacade(helper, Blocks.BRICKS);
            ItemStack stick = new ItemStack(Items.STICK);
            FacadeStateManager.stackRedirects = Map.of(new ItemStackKey(stick), List.of(bricks));

            // getOutputs: stick now yields the bricks facade (basic + hollow).
            Set<ItemStack> outputs = facadeOutputsFor(stick);
            if (outputs.isEmpty()) {
                helper.fail("With stick→bricks seeded into stackRedirects, the assembly recipe still "
                    + "produced no facade from a stick — the redirect read path in getOutputs is broken.");
                return;
            }
            assertProducesFacadeOf(helper, outputs, bricks, "redirected stick input");

            // getInputsFor: the bricks facade should now accept the stick as an input ingredient.
            ItemStack bricksFacade = FacadeAssemblyRecipes.createFacadeStack(bricks, false);
            boolean stickAccepted = false;
            for (buildcraft.api.recipes.IngredientStack ing : FacadeAssemblyRecipes.INSTANCE.getInputsFor(bricksFacade)) {
                if (ing.ingredient.test(stick)) {
                    stickAccepted = true;
                    break;
                }
            }
            if (!stickAccepted) {
                helper.fail("getInputsFor(bricks facade) did not accept the seeded stick redirect — "
                    + "the redirect branch of getInputsFor is broken.");
                return;
            }
            helper.succeed();
        } finally {
            FacadeStateManager.stackRedirects = prior;
        }
    }

    // --- helpers ---

    private static FacadeBlockStateInfo requireFacade(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        FacadeBlockStateInfo info = FacadeStateManager.validFacadeStates.get(block.defaultBlockState());
        if (info == null) {
            helper.fail("Expected a valid facade for " + block + " but none was registered; "
                + "FacadeStateManager.init may not have run.");
            throw new IllegalStateException("no facade for " + block);
        }
        return info;
    }

    /** Outputs of the facade recipe for a single input stack plus the required base (3x structure pipe). */
    private static Set<ItemStack> facadeOutputsFor(ItemStack input) {
        NonNullList<ItemStack> inv = NonNullList.create();
        inv.add(baseRequirement());
        inv.add(input.copy());
        return FacadeAssemblyRecipes.INSTANCE.getOutputs(inv);
    }

    private static ItemStack baseRequirement() {
        // Mirror FacadeAssemblyRecipes.baseRequirementStack(): 3x structure pipe, or the cobblestone-wall
        // fallback if (somehow) the pipe item isn't registered in this test environment.
        //? if >=1.21.10 {
        net.minecraft.world.item.Item pipe = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
            net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_structure"));
        //?} else {
        /*net.minecraft.world.item.Item pipe = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.Identifier.parse("buildcraftunofficial:pipe_structure"));*/
        //?}
        if (pipe == Items.AIR) {
            return new ItemStack(Items.COBBLESTONE_WALL);
        }
        return new ItemStack(pipe, 3);
    }

    private static void assertNoFacadeOutput(GameTestHelper helper, ItemStack input, String label) {
        Set<ItemStack> outputs = facadeOutputsFor(input);
        if (!outputs.isEmpty()) {
            helper.fail("Expected no facade output for " + label + " but got " + outputs.size()
                + " — a redirect was applied when stackRedirects was empty.");
            throw new IllegalStateException("unexpected output for " + label);
        }
    }

    /** Asserts that at least one output stack is a facade carrying {@code expected}'s blockstate. */
    private static void assertProducesFacadeOf(GameTestHelper helper, Set<ItemStack> outputs,
                                               FacadeBlockStateInfo expected, String label) {
        for (ItemStack out : outputs) {
            FacadeInstance inst = buildcraft.silicon.item.ItemPluggableFacade.getStates(out);
            if (inst.phasedStates.length == 1
                && inst.phasedStates[0].stateInfo.state == expected.state) {
                return;
            }
        }
        helper.fail("None of the " + outputs.size() + " facade outputs for " + label
            + " carried the expected blockstate " + expected.state + ".");
        throw new IllegalStateException("missing expected facade for " + label);
    }
}
