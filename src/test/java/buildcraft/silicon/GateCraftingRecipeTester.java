/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon;

import java.util.List;
import java.util.Optional;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;

/**
 * Pins the JSON crafting-table recipes for gates: the Basic (clay brick) gate from 1.12.2,
 * the Iron / Nether Brick basic gates as cheaper alternatives to the Assembly Table path,
 * the Iron modifier upgrades (lapis / quartz around an Iron AND gate), and the AND&lt;-&gt;OR
 * shapeless swap for every non-clay-brick (material, modifier) pair.
 */
public final class GateCraftingRecipeTester {

    private GateCraftingRecipeTester() {}

    // --- Basic crafting (chipset-free, early-game-friendly) ---

    public static void testBasicGateRecipe(GameTestHelper helper) {
        assertShapedCraftMatches(helper,
            new ItemStack(Items.BRICK), new ItemStack(Items.REDSTONE), new ItemStack(Items.COBBLESTONE),
            gate(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER),
            "Basic Gate (clay brick)");
    }

    public static void testIronAndBasicCraftRecipe(GameTestHelper helper) {
        assertShapedCraftMatches(helper,
            new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.COBBLESTONE),
            gate(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER),
            "Iron AND Gate basic (crafting)");
    }

    public static void testNetherBrickAndBasicCraftRecipe(GameTestHelper helper) {
        assertShapedCraftMatches(helper,
            new ItemStack(Items.NETHER_BRICK), new ItemStack(Items.REDSTONE), new ItemStack(Items.COBBLESTONE),
            gate(EnumGateLogic.AND, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.NO_MODIFIER),
            "Nether Brick AND Gate basic (crafting)");
    }

    // --- Iron modifier upgrades (lapis / quartz around an Iron AND gate) ---

    public static void testIronAndLapisCraftRecipe(GameTestHelper helper) {
        ItemStack ironAnd = gate(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER);
        ItemStack lapis = new ItemStack(Items.LAPIS_LAZULI);
        // Shape: . m . / m g m / . m . — four lapis around the gate, no corners.
        CraftingInput input = CraftingInput.of(3, 3, List.of(
            ItemStack.EMPTY, lapis, ItemStack.EMPTY,
            lapis, ironAnd, lapis,
            ItemStack.EMPTY, lapis, ItemStack.EMPTY));
        ItemStack expected = gate(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.LAPIS);
        assertCraftResult(helper, input, expected, "Iron AND + lapis -> Iron AND Lapis");
    }

    public static void testIronAndQuartzCraftRecipe(GameTestHelper helper) {
        ItemStack ironAnd = gate(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER);
        ItemStack quartz = new ItemStack(Items.QUARTZ);
        CraftingInput input = CraftingInput.of(3, 3, List.of(
            ItemStack.EMPTY, quartz, ItemStack.EMPTY,
            quartz, ironAnd, quartz,
            ItemStack.EMPTY, quartz, ItemStack.EMPTY));
        ItemStack expected = gate(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.QUARTZ);
        assertCraftResult(helper, input, expected, "Iron AND + quartz -> Iron AND Quartz");
    }

    // --- AND<->OR swap (shapeless, one of 24 per (material, modifier, direction)) ---

    public static void testIronAndToOrSwap(GameTestHelper helper) {
        assertSwap(helper, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER,
            EnumGateLogic.AND, EnumGateLogic.OR);
    }

    public static void testIronOrToAndSwap(GameTestHelper helper) {
        assertSwap(helper, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER,
            EnumGateLogic.OR, EnumGateLogic.AND);
    }

    public static void testGoldDiamondAndToOrSwap(GameTestHelper helper) {
        assertSwap(helper, EnumGateMaterial.GOLD, EnumGateModifier.DIAMOND,
            EnumGateLogic.AND, EnumGateLogic.OR);
    }

    public static void testClayBrickSwapNotAvailable(GameTestHelper helper) {
        // 1.12.2 deliberately excluded CLAY_BRICK from the swap (it's logic-locked to AND,
        // single creative-tab entry, no OR variant). Placing the Basic Gate alone in the
        // crafting grid must NOT produce anything.
        ItemStack basic = gate(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER);
        CraftingInput input = CraftingInput.of(1, 1, List.of(basic));
        ServerLevel level = helper.getLevel();
        Optional<RecipeHolder<CraftingRecipe>> match = level.getServer().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isPresent()) {
            helper.fail("Basic Gate (clay brick) unexpectedly matched a 1-slot crafting recipe — "
                + "the AND<->OR swap should skip CLAY_BRICK. Matched: " + match.get().id());
        }
        helper.succeed();
    }

    // --- Helpers ---

    private static void assertSwap(GameTestHelper helper, EnumGateMaterial mat, EnumGateModifier mod,
            EnumGateLogic from, EnumGateLogic to) {
        ItemStack input = gate(from, mat, mod);
        ItemStack expected = gate(to, mat, mod);
        CraftingInput ci = CraftingInput.of(1, 1, List.of(input));
        assertCraftResult(helper, ci, expected,
            from + " -> " + to + " swap for " + mat + "/" + mod);
    }

    /** Builds the {@code m / mrm / b} shape (Basic-Gate style) and asserts it crafts to {@code expected}. */
    private static void assertShapedCraftMatches(GameTestHelper helper, ItemStack m, ItemStack r, ItemStack b,
            ItemStack expected, String label) {
        CraftingInput input = CraftingInput.of(3, 3, List.of(
            ItemStack.EMPTY, m, ItemStack.EMPTY,
            m, r, m,
            ItemStack.EMPTY, b, ItemStack.EMPTY));
        assertCraftResult(helper, input, expected, label);
    }

    private static void assertCraftResult(GameTestHelper helper, CraftingInput input, ItemStack expected, String label) {
        ServerLevel level = helper.getLevel();
        Optional<RecipeHolder<CraftingRecipe>> match = level.getServer().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isEmpty()) {
            helper.fail("Recipe didn't match: " + label);
        }
        //? if >=26.1 {
        ItemStack out = match.get().value().assemble(input);
        //?} else {
        /*ItemStack out = match.get().value().assemble(input, level.registryAccess());*/
        //?}
        if (!ItemStack.matches(expected, out)) {
            helper.fail("Crafting output mismatch for " + label
                + ": expected " + expected.getComponentsPatch()
                + " got " + out.getComponentsPatch());
        }
        helper.succeed();
    }

    private static ItemStack gate(EnumGateLogic logic, EnumGateMaterial mat, EnumGateModifier mod) {
        return BCSiliconItems.PLUG_GATE.get().getStack(new GateVariant(logic, mat, mod));
    }
}
