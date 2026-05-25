/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon;

import java.util.Set;

import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.api.recipes.AssemblyRecipe;

import buildcraft.lib.recipe.AssemblyRecipeRegistry;

import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;

/**
 * Pins the data-component-aware matching of gate-modifier assembly recipes.
 *
 * <p>All gate variants share the same {@code PLUG_GATE} {@link net.minecraft.world.item.Item} and
 * are disambiguated by data components (CUSTOM_DATA "gate" CompoundTag + CUSTOM_MODEL_DATA). An
 * earlier port regression used {@code Ingredient.of(Item)} for the input gate, which matches any
 * variant and would let an Iron AND Gate be consumed by the Gold + Lapis recipe.
 */
public final class GateRecipeVariantTester {

    private GateRecipeVariantTester() {}

    public static void testGoldLapisRecipeAcceptsGoldPlainGate(GameTestHelper helper) {
        AssemblyRecipe recipe = recipe("gate-modifier-AND-GOLD-LAPIS");
        ItemStack goldPlain = gate(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.NO_MODIFIER);
        ItemStack lapis = new ItemStack(Items.LAPIS_LAZULI);

        Set<ItemStack> outputs = recipe.getOutputs(NonNullList.of(ItemStack.EMPTY, goldPlain, lapis));

        if (outputs.isEmpty()) {
            helper.fail("Gold + Lapis recipe rejected the correct Gold AND (plain) input");
        }
        ItemStack out = outputs.iterator().next();
        GateVariant outVar = new GateVariant(buildcraft.lib.misc.NBTUtilBC.getItemData(out)
            .getCompound("gate").orElseThrow());
        if (outVar.material != EnumGateMaterial.GOLD
            || outVar.modifier != EnumGateModifier.LAPIS
            || outVar.logic != EnumGateLogic.AND) {
            helper.fail("Recipe produced wrong variant: " + outVar.getVariantName());
        }
        helper.succeed();
    }

    public static void testGoldLapisRecipeRejectsIronPlainGate(GameTestHelper helper) {
        AssemblyRecipe recipe = recipe("gate-modifier-AND-GOLD-LAPIS");
        ItemStack ironPlain = gate(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER);
        ItemStack lapis = new ItemStack(Items.LAPIS_LAZULI);

        Set<ItemStack> outputs = recipe.getOutputs(NonNullList.of(ItemStack.EMPTY, ironPlain, lapis));

        if (!outputs.isEmpty()) {
            helper.fail("Gold + Lapis recipe accepted an Iron AND gate — variant matching is broken");
        }
        helper.succeed();
    }

    public static void testGoldLapisRecipeRejectsAlreadyModifiedGate(GameTestHelper helper) {
        AssemblyRecipe recipe = recipe("gate-modifier-AND-GOLD-LAPIS");
        ItemStack goldAlreadyLapis = gate(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.LAPIS);
        ItemStack lapis = new ItemStack(Items.LAPIS_LAZULI);

        Set<ItemStack> outputs = recipe.getOutputs(NonNullList.of(ItemStack.EMPTY, goldAlreadyLapis, lapis));

        if (!outputs.isEmpty()) {
            helper.fail("Gold + Lapis recipe accepted an already-modified Gold+Lapis gate as input");
        }
        helper.succeed();
    }

    public static void testOrLogicRecipeRejectsAndLogicGate(GameTestHelper helper) {
        AssemblyRecipe recipe = recipe("gate-modifier-OR-GOLD-LAPIS");
        ItemStack goldAnd = gate(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.NO_MODIFIER);
        ItemStack lapis = new ItemStack(Items.LAPIS_LAZULI);

        Set<ItemStack> outputs = recipe.getOutputs(NonNullList.of(ItemStack.EMPTY, goldAnd, lapis));

        if (!outputs.isEmpty()) {
            helper.fail("OR-keyed recipe accepted an AND gate — logic discrimination is broken");
        }
        helper.succeed();
    }

    /**
     * The recipe-matching path was wrong before the DataComponentIngredient fix; the JEI display
     * path goes through a different channel ({@link Ingredient#display()} →
     * {@link net.minecraft.world.item.crafting.display.SlotDisplay#resolveForStacks}) which had the
     * same shape of bug — it enumerated raw items and dropped the variant data. Pins that the
     * display path now surfaces the right variant for the Gold + Lapis recipe input.
     */
    public static void testGoldLapisRecipeDisplayPreservesGoldVariant(GameTestHelper helper) {
        AssemblyRecipe recipe = recipe("gate-modifier-AND-GOLD-LAPIS");
        net.minecraft.util.context.ContextMap ctx = new net.minecraft.util.context.ContextMap.Builder()
            .create(net.minecraft.world.item.crafting.display.SlotDisplayContext.CONTEXT);

        ItemStack goldPlain = gate(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.NO_MODIFIER);
        // Slot 0 is the input gate ingredient (lapis is slot 1).
        buildcraft.api.recipes.IngredientStack gateInput =
            recipe.getInputsFor(BCSiliconItems.PLUG_GATE.get().getStack(
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.LAPIS)))
                .iterator().next();

        java.util.List<ItemStack> displayed = gateInput.ingredient.display().resolveForStacks(ctx);
        if (displayed.isEmpty()) {
            helper.fail("Gold + Lapis input ingredient resolved to no display stacks");
        }
        ItemStack first = displayed.get(0);
        GateVariant displayedVar = new GateVariant(buildcraft.lib.misc.NBTUtilBC.getItemData(first)
            .getCompound("gate").orElseThrow(() ->
                new IllegalStateException("Display stack lost the gate CompoundTag — JEI would render a bare PLUG_GATE")));
        if (displayedVar.material != EnumGateMaterial.GOLD) {
            helper.fail("Display path lost the GOLD variant; got " + displayedVar.getVariantName()
                + " — JEI would render the Gold+Lapis recipe with the wrong input gate");
        }
        // Stack identity, not just subtype identity: JEI's recipe-lookup-by-focused-stack walks
        // ItemStack.matches (Objects.equals on PatchedDataComponentMap, which compares the
        // internal patch *structurally*). DataComponentIngredient.of(boolean, ItemStack)
        // accidentally serialises every default component into the patch — when the display
        // path rebuilds the stack via ItemStackTemplate.create() those defaults end up in the
        // rebuilt patch and the stack is !equals to the canonical gate JEI keeps in its
        // ingredient list, so R/U lookup on the gate fails to find this recipe. The fix passes
        // the canonical stack's getComponentsPatch() (just the two real overrides) into the
        // explicit-patch overload — pin that here.
        if (!ItemStack.matches(goldPlain, first)) {
            helper.fail("Display stack is not ItemStack-equals to the canonical Gold AND (plain); "
                + "JEI would lose recipe lookup on the gate. canonical patch="
                + goldPlain.getComponentsPatch() + " displayed patch=" + first.getComponentsPatch());
        }
        helper.succeed();
    }

    /**
     * Pins that all six base (no-modifier) gate recipes — the chipset → basic gate ones, which
     * the user reported can't be found via JEI R/U — are actually present in the assembly
     * registry with the correct variant-bearing output, and that the JEI collector emits a
     * matching entry for each.
     */
    public static void testBasicGateRecipeExistsAndCollectorEmitsIt(GameTestHelper helper) {
        java.util.List<buildcraft.silicon.compat.jei.AssemblyRecipeJei> entries =
            buildcraft.silicon.compat.jei.AssemblyRecipeCollector.collect();
        int totalGateOutputs = 0;
        for (buildcraft.silicon.compat.jei.AssemblyRecipeJei entry : entries) {
            for (ItemStack o : entry.outputs()) {
                if (o.getItem() == BCSiliconItems.PLUG_GATE.get()) {
                    totalGateOutputs++;
                }
            }
        }

        for (EnumGateMaterial mat : new EnumGateMaterial[] {
                EnumGateMaterial.IRON, EnumGateMaterial.NETHER_BRICK, EnumGateMaterial.GOLD }) {
            for (EnumGateLogic logic : EnumGateLogic.VALUES) {
                String name = String.format("gate-%s-%s-%s",
                    logic == EnumGateLogic.AND ? "and" : "or", mat, EnumGateModifier.NO_MODIFIER);
                ItemStack canonical = gate(logic, mat, EnumGateModifier.NO_MODIFIER);

                // 1. Registry has the recipe with the right output stack.
                AssemblyRecipe r = recipe(name);
                ItemStack stored = r.getOutputPreviews().iterator().next();
                if (!ItemStack.matches(canonical, stored)) {
                    helper.fail("Stored output is not ItemStack-equals to canonical " + name
                        + ". stored=" + stored.getComponentsPatch()
                        + " canonical=" + canonical.getComponentsPatch());
                }

                // 2. Collector emits an entry whose output stack matches the canonical (this is
                //    the slot JEI uses for R-lookup; if no entry has a matching output, R
                //    returns nothing).
                boolean found = false;
                for (buildcraft.silicon.compat.jei.AssemblyRecipeJei entry : entries) {
                    for (ItemStack o : entry.outputs()) {
                        if (ItemStack.matches(canonical, o)) {
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }
                if (!found) {
                    helper.fail("Collector emitted no JEI entry whose output ItemStack.matches "
                        + canonical.getHoverName().getString() + " (" + name + "). Total "
                        + "gate-output entries in collector: " + totalGateOutputs);
                }
            }
        }
        helper.succeed();
    }

    private static AssemblyRecipe recipe(String name) {
        AssemblyRecipe r = AssemblyRecipeRegistry.REGISTRY.get(name);
        if (r == null) {
            throw new IllegalStateException("Missing assembly recipe: " + name);
        }
        return r;
    }

    private static ItemStack gate(EnumGateLogic logic, EnumGateMaterial mat, EnumGateModifier mod) {
        return BCSiliconItems.PLUG_GATE.get().getStack(new GateVariant(logic, mat, mod));
    }
}
