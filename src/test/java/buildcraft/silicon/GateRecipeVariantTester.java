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
        //? if >=1.21.10 {
        GateVariant outVar = new GateVariant(buildcraft.lib.misc.NBTUtilBC.getItemData(out)
            .getCompound("gate").orElseThrow());
        //?} else {
        /*GateVariant outVar = new GateVariant(buildcraft.lib.misc.NBTUtilBC.getCompound(
            buildcraft.lib.misc.NBTUtilBC.getItemData(out), "gate"));*/
        //?}
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
        //? if >=1.21.10 {
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
        //?} else {
        /*// SlotDisplay system (net.minecraft.world.item.crafting.display.* + net.minecraft.util.context.*)
        // does not exist pre-1.21.5; this deferred game test is compile-only on the 1.21.1 node.
        helper.fail("testGoldLapisRecipeDisplayPreservesGoldVariant is not supported on MC 1.21.1 "
            + "(SlotDisplay API absent); should not be invoked on this node");*/
        //?}
    }

    /**
     * Mirrors JEI's U-lookup: build the canonical basic gate stack a player would have in their
     * inventory (creative tab, /give, or crafted), key it the same way JEI's subtype interpreter
     * does ({@link GateVariant#getVariantName()}), then scan every JEI entry the collector emits
     * and assert that the basic gate appears as an *input* in at least three entries — one for
     * each modifier upgrade recipe (lapis, quartz, diamond). The user-visible symptom of this
     * test failing is "pressing U on a Basic Gate does nothing" — JEI walks the same input slots
     * and finds no recipes that consume the held stack.
     *
     * <p>{@link #testGoldLapisRecipeDisplayPreservesGoldVariant} pins the lower-level contract
     * for one specific recipe; this pins the higher-level "all three modifier upgrades for the
     * iron AND gate are discoverable from the basic gate" contract that's what the player
     * actually experiences in JEI.
     */
    public static void testBasicGateAppearsAsInputInModifierRecipes(GameTestHelper helper) {
        java.util.List<buildcraft.silicon.compat.jei.AssemblyRecipeJei> entries =
            buildcraft.silicon.compat.jei.AssemblyRecipeCollector.collect();

        for (EnumGateMaterial mat : new EnumGateMaterial[] {
                EnumGateMaterial.IRON, EnumGateMaterial.NETHER_BRICK, EnumGateMaterial.GOLD }) {
            for (EnumGateLogic logic : EnumGateLogic.VALUES) {
                ItemStack basic = gate(logic, mat, EnumGateModifier.NO_MODIFIER);
                String basicKey = buildcraft.silicon.item.ItemPluggableGate.getVariant(basic)
                    .getVariantName();
                int matchingEntries = 0;
                for (buildcraft.silicon.compat.jei.AssemblyRecipeJei entry : entries) {
                    boolean inputMatches = false;
                    for (java.util.List<ItemStack> slot : entry.inputSlots()) {
                        for (ItemStack candidate : slot) {
                            if (candidate.getItem() != BCSiliconItems.PLUG_GATE.get()) continue;
                            String candidateKey = buildcraft.silicon.item.ItemPluggableGate
                                .getVariant(candidate).getVariantName();
                            if (basicKey.equals(candidateKey)) {
                                inputMatches = true;
                                break;
                            }
                        }
                        if (inputMatches) break;
                    }
                    if (inputMatches) matchingEntries++;
                }
                if (matchingEntries < 3) {
                    helper.fail("Basic gate " + basicKey + " should be an input in >= 3 JEI "
                        + "entries (lapis/quartz/diamond modifier upgrades). Found "
                        + matchingEntries + " entries. JEI U-lookup on this gate will be empty.");
                }
            }
        }
        helper.succeed();
    }

    /**
     * Mirrors JEI's R-lookup: build a canonical higher-level (modifier) gate stack and assert
     * that at least one JEI entry's output is keyed the same way under
     * {@link GateVariant#getVariantName()}. Covers all 12 modifier variants (3 materials × 2
     * logics × {lapis, quartz, diamond}; the four "modifier upgrade" recipes per material/logic
     * each produce one of these). The user-visible symptom of this test failing is "pressing R
     * on a higher-level gate doesn't show its assembly table recipe."
     */
    public static void testHigherLevelGateAppearsAsOutputInModifierRecipes(GameTestHelper helper) {
        java.util.List<buildcraft.silicon.compat.jei.AssemblyRecipeJei> entries =
            buildcraft.silicon.compat.jei.AssemblyRecipeCollector.collect();

        for (EnumGateMaterial mat : new EnumGateMaterial[] {
                EnumGateMaterial.IRON, EnumGateMaterial.NETHER_BRICK, EnumGateMaterial.GOLD }) {
            for (EnumGateLogic logic : EnumGateLogic.VALUES) {
                for (EnumGateModifier modifier : new EnumGateModifier[] {
                        EnumGateModifier.LAPIS, EnumGateModifier.QUARTZ, EnumGateModifier.DIAMOND }) {
                    ItemStack canonical = gate(logic, mat, modifier);
                    String canonicalKey = buildcraft.silicon.item.ItemPluggableGate
                        .getVariant(canonical).getVariantName();
                    boolean found = false;
                    for (buildcraft.silicon.compat.jei.AssemblyRecipeJei entry : entries) {
                        for (ItemStack out : entry.outputs()) {
                            if (out.getItem() != BCSiliconItems.PLUG_GATE.get()) continue;
                            String outKey = buildcraft.silicon.item.ItemPluggableGate
                                .getVariant(out).getVariantName();
                            if (canonicalKey.equals(outKey)) {
                                found = true;
                                break;
                            }
                        }
                        if (found) break;
                    }
                    if (!found) {
                        helper.fail("Higher-level gate " + canonicalKey + " should be an output "
                            + "in at least one JEI entry; not found. JEI R-lookup will be empty.");
                    }
                }
            }
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

    /**
     * Pins the guide-book recipe-display contract for modifier-upgrade recipes. Before this
     * fix, {@link buildcraft.lib.client.guide.parts.recipe.GuideAssemblyRecipes#createFactory}
     * used {@code Ingredient.items()} to build the input slots, which strips data-component
     * patches — so the Iron AND + Lapis recipe's gate input rendered as a bare
     * {@code new ItemStack(PLUG_GATE)}, displayed by {@link ItemPluggableGate#getName} as the
     * default GateVariant (CLAY_BRICK, the in-game "Basic Gate"). The Basic Gate's guide page
     * then showed a Basic-Gate-as-input row for every modifier-upgrade recipe — the source of
     * the user-reported "Basic Gate is shown as a key component in higher-tier gates."
     *
     * <p>The fix routes through {@code Ingredient.display().resolveForStacks(ctx)} which
     * preserves the patch. This test pins that the rendered gate-input stack now reports the
     * correct variant (Iron AND, not CLAY_BRICK AND default).
     */
    public static void testGuideBookModifierRecipeRendersCorrectInputVariant(GameTestHelper helper) {
        AssemblyRecipe recipe = recipe("gate-modifier-AND-IRON-LAPIS");
        java.util.List<buildcraft.lib.client.guide.parts.GuidePartFactory> factories =
            buildcraft.lib.client.guide.parts.recipe.GuideAssemblyRecipes.INSTANCE.getRecipes(
                gate(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.LAPIS));
        boolean foundExpected = false;
        for (buildcraft.lib.client.guide.parts.GuidePartFactory factory : factories) {
            if (!(factory instanceof buildcraft.lib.client.guide.parts.recipe.GuideAssemblyFactory)) continue;
            try {
                java.lang.reflect.Field f = factory.getClass().getDeclaredField("input");
                f.setAccessible(true);
                buildcraft.lib.recipe.ChangingItemStack[] input =
                    (buildcraft.lib.recipe.ChangingItemStack[]) f.get(factory);
                for (buildcraft.lib.recipe.ChangingItemStack slot : input) {
                    ItemStack rep = slot.get().baseStack;
                    if (rep.getItem() != BCSiliconItems.PLUG_GATE.get()) continue;
                    GateVariant displayed = buildcraft.silicon.item.ItemPluggableGate.getVariant(rep);
                    if (displayed.material == EnumGateMaterial.IRON
                        && displayed.logic == EnumGateLogic.AND
                        && displayed.modifier == EnumGateModifier.NO_MODIFIER) {
                        foundExpected = true;
                    } else if (displayed.material == EnumGateMaterial.CLAY_BRICK) {
                        helper.fail("Guide-book Iron+Lapis modifier-upgrade recipe rendered "
                            + "the gate input as the default CLAY_BRICK 'Basic Gate' variant — "
                            + "DataComponentIngredient patch was dropped on the display path. "
                            + "Players see this and conclude the Basic Gate is an ingredient.");
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                helper.fail("Could not reflect on GuideAssemblyFactory.input: " + e);
            }
        }
        if (!foundExpected) {
            helper.fail("Guide-book Iron+Lapis modifier-upgrade recipe did not render an Iron "
                + "AND (plain) input — no input slot resolved to the expected variant.");
        }
        helper.succeed();
    }

    /**
     * The CLAY_BRICK "Basic Gate" is never an output of any assembly recipe (only crafted via
     * the {@code gate_basic.json} crafting recipe). Pin that {@link GuideAssemblyRecipes#getUsages}
     * also returns empty for it (no assembly recipe consumes the Basic Gate either). The
     * combined effect of {@link #testGuideBookModifierRecipeRendersCorrectInputVariant} plus
     * this test is that the guide-book entry for the Basic Gate never shows it as an
     * ingredient in any assembly recipe — which is the design.
     */
    public static void testBasicClayBrickGateHasNoAssemblyUsages(GameTestHelper helper) {
        ItemStack clayBasic = gate(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER);
        java.util.List<buildcraft.lib.client.guide.parts.GuidePartFactory> usages =
            buildcraft.lib.client.guide.parts.recipe.GuideAssemblyRecipes.INSTANCE.getUsages(clayBasic);
        if (!usages.isEmpty()) {
            helper.fail("CLAY_BRICK Basic Gate should not be an input in any assembly recipe; "
                + "guide book reports " + usages.size() + " usages. This is the bug that "
                + "led the user to expect U-on-Basic-Gate to find higher-tier gate recipes.");
        }
        helper.succeed();
    }

    /**
     * The Logic Gates guide page surfaces each gate's Assembly recipes through
     * {@code <recipe_cycle match="..."/>}, and the AND↔OR swaps through two more. Each {@code match}
     * substring must select exactly the intended recipe set; a rename in {@link BCSiliconRecipes} or a
     * missing swap JSON would otherwise silently mis-cycle or red-text the panel — visible only by
     * opening the book in-client. This pins those selectors: every assembly match picks one variant's
     * AND+OR pair (2 recipes), and each swap direction has its full 12 crafting recipes.
     */
    public static void testGuideGateRecipeCycleSelectors(GameTestHelper helper) {
        // Assembly: each match selects exactly one variant's AND+OR pair (2 recipes). These are the
        // assembly match substrings used in plug_gate.md.
        String[] assemblyMatches = {
            "-IRON-NO_MODIFIER", "-NETHER_BRICK-NO_MODIFIER", "-GOLD-NO_MODIFIER",
            "-IRON-LAPIS", "-IRON-QUARTZ", "-IRON-DIAMOND",
            "-NETHER_BRICK-LAPIS", "-NETHER_BRICK-QUARTZ", "-NETHER_BRICK-DIAMOND",
            "-GOLD-LAPIS", "-GOLD-QUARTZ", "-GOLD-DIAMOND",
        };
        for (String match : assemblyMatches) {
            java.util.List<AssemblyRecipe> matched =
                buildcraft.lib.client.guide.parts.recipe.GuideAssemblyRecipes.gatherByNameMatch(match);
            if (matched.size() != 2) {
                helper.fail("Assembly <recipe_cycle match=\"" + match + "\"> should select exactly the"
                    + " AND+OR pair (2 recipes) but matched " + matched.size() + ": "
                    + matched.stream().map(AssemblyRecipe::getRegistryName).toList()
                    + " — check BCSiliconRecipes naming vs plug_gate.md.");
                return;
            }
        }

        // Swaps: each direction must have its full 12 crafting recipes (3 materials x 4 modifiers).
        // GuideCraftingRecipes.gatherByIdMatch can't run here (its recipe manager is client-only), so
        // count off the server's recipe manager directly.
        net.minecraft.world.item.crafting.RecipeManager recipeManager =
            helper.getLevel().getServer().getRecipeManager();
        int toOr = 0;
        int toAnd = 0;
        for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : recipeManager.getRecipes()) {
            if (!(holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe)) {
                continue;
            }
            String id = buildcraft.lib.misc.RegistryKeyUtil.id(holder.id()).toString();
            if (id.contains("_to_or")) {
                toOr++;
            } else if (id.contains("_to_and")) {
                toAnd++;
            }
        }
        if (toOr != 12 || toAnd != 12) {
            helper.fail("AND↔OR swap <recipe_cycle> selectors expect 12 recipes each but found "
                + toOr + " *_to_or and " + toAnd + " *_to_and crafting recipes.");
            return;
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
