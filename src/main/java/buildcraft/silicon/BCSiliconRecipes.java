/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.recipes.AssemblyRecipeBasic;
import buildcraft.api.recipes.IngredientStack;

import buildcraft.core.BCCoreBlocks;

import buildcraft.factory.BCFactoryBlocks;

import buildcraft.lib.misc.ColourUtil;
import buildcraft.lib.recipe.AssemblyRecipeRegistry;

import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.recipe.FacadeAssemblyRecipes;

import buildcraft.transport.BCTransportItems;

@SuppressWarnings("deprecation")
public class BCSiliconRecipes {

    public static void init() {
        registerPlugRecipes();
        registerGateAssemblyRecipes();
        registerChipsetRecipes();
        registerLensRecipes();
        registerWireRecipes();
        registerGateCopierRecipe();
        registerFacadeRecipes();
        registerTankRecipe();
    }

    // --- Plug Recipes ---

    private static void registerPlugRecipes() {
        // Pulsar: redstone engine + 2 iron ingots (matches 1.12.2 behavior).
        {
            Set<IngredientStack> input = new HashSet<>();
            input.add(new IngredientStack(Ingredient.of(BCCoreBlocks.ENGINE_REDSTONE.get())));
            input.add(new IngredientStack(Ingredient.of(Items.IRON_INGOT), 2));
            ItemStack output = new ItemStack(BCSiliconItems.PLUG_PULSAR.get());
            AssemblyRecipeRegistry.register(
                new AssemblyRecipeBasic("plug_pulsar", 1000 * MjAPI.MJ, input, output));
        }

        // Light Sensor: daylight detector
        {
            ImmutableSet<IngredientStack> input = ImmutableSet.of(
                new IngredientStack(Ingredient.of(Blocks.DAYLIGHT_DETECTOR)));
            ItemStack output = new ItemStack(BCSiliconItems.PLUG_LIGHT_SENSOR.get());
            AssemblyRecipeRegistry.register(
                new AssemblyRecipeBasic("light-sensor", 500 * MjAPI.MJ, input, output));
        }

        // Timer: clock
        {
            ImmutableSet<IngredientStack> input = ImmutableSet.of(
                new IngredientStack(Ingredient.of(Items.CLOCK)));
            ItemStack output = new ItemStack(BCSiliconItems.PLUG_TIMER.get());
            AssemblyRecipeRegistry.register(
                new AssemblyRecipeBasic("timer", 500 * MjAPI.MJ, input, output));
        }
    }

    // --- Gate Assembly Recipes ---

    private static void registerGateAssemblyRecipes() {
        // Base gates from chipsets
        makeGateAssembly(20_000, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER,
            BCSiliconItems.CHIPSET_IRON.get());
        makeGateAssembly(40_000, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.NO_MODIFIER,
            BCSiliconItems.CHIPSET_IRON.get(),
            new IngredientStack(Ingredient.of(Blocks.NETHER_BRICKS)));
        makeGateAssembly(80_000, EnumGateMaterial.GOLD, EnumGateModifier.NO_MODIFIER,
            BCSiliconItems.CHIPSET_GOLD.get());

        // Iron modifier upgrades
        IngredientStack lapis = new IngredientStack(Ingredient.of(Items.LAPIS_LAZULI));
        makeGateModifierAssembly(40_000, EnumGateMaterial.IRON, EnumGateModifier.LAPIS, lapis);
        makeGateModifierAssembly(60_000, EnumGateMaterial.IRON, EnumGateModifier.QUARTZ,
            new IngredientStack(Ingredient.of(BCSiliconItems.CHIPSET_QUARTZ.get())));
        makeGateModifierAssembly(80_000, EnumGateMaterial.IRON, EnumGateModifier.DIAMOND,
            new IngredientStack(Ingredient.of(BCSiliconItems.CHIPSET_DIAMOND.get())));

        // Nether Brick modifier upgrades
        makeGateModifierAssembly(80_000, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.LAPIS, lapis);
        makeGateModifierAssembly(100_000, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.QUARTZ,
            new IngredientStack(Ingredient.of(BCSiliconItems.CHIPSET_QUARTZ.get())));
        makeGateModifierAssembly(120_000, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.DIAMOND,
            new IngredientStack(Ingredient.of(BCSiliconItems.CHIPSET_DIAMOND.get())));

        // Gold modifier upgrades
        makeGateModifierAssembly(100_000, EnumGateMaterial.GOLD, EnumGateModifier.LAPIS, lapis);
        makeGateModifierAssembly(140_000, EnumGateMaterial.GOLD, EnumGateModifier.QUARTZ,
            new IngredientStack(Ingredient.of(BCSiliconItems.CHIPSET_QUARTZ.get())));
        makeGateModifierAssembly(180_000, EnumGateMaterial.GOLD, EnumGateModifier.DIAMOND,
            new IngredientStack(Ingredient.of(BCSiliconItems.CHIPSET_DIAMOND.get())));
    }

    // --- Chipset Recipes ---

    private static void registerChipsetRecipes() {
        ImmutableSet<IngredientStack> input;

        input = ImmutableSet.of(new IngredientStack(Ingredient.of(Items.REDSTONE)));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("chipset_redstone",
            10_000 * MjAPI.MJ, input, new ItemStack(BCSiliconItems.CHIPSET_REDSTONE.get())));

        input = ImmutableSet.of(
            new IngredientStack(Ingredient.of(Items.REDSTONE)),
            new IngredientStack(Ingredient.of(Items.IRON_INGOT)));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("chipset_iron",
            20_000 * MjAPI.MJ, input, new ItemStack(BCSiliconItems.CHIPSET_IRON.get())));

        input = ImmutableSet.of(
            new IngredientStack(Ingredient.of(Items.REDSTONE)),
            new IngredientStack(Ingredient.of(Items.GOLD_INGOT)));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("chipset_gold",
            40_000 * MjAPI.MJ, input, new ItemStack(BCSiliconItems.CHIPSET_GOLD.get())));

        input = ImmutableSet.of(
            new IngredientStack(Ingredient.of(Items.REDSTONE)),
            new IngredientStack(Ingredient.of(Items.QUARTZ)));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("chipset_quartz",
            60_000 * MjAPI.MJ, input, new ItemStack(BCSiliconItems.CHIPSET_QUARTZ.get())));

        input = ImmutableSet.of(
            new IngredientStack(Ingredient.of(Items.REDSTONE)),
            new IngredientStack(Ingredient.of(Items.DIAMOND)));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("chipset_diamond",
            80_000 * MjAPI.MJ, input, new ItemStack(BCSiliconItems.CHIPSET_DIAMOND.get())));
    }

    // --- Lens Recipes ---

    private static void registerLensRecipes() {
        for (DyeColor colour : ColourUtil.COLOURS) {
            String name = String.format("lens-regular-%s", colour.getName());
            Block stainedGlass = getStainedGlass(colour);
            IngredientStack stainedGlassIngredient = new IngredientStack(Ingredient.of(stainedGlass));
            ImmutableSet<IngredientStack> input = ImmutableSet.of(stainedGlassIngredient);
            ItemStack output = BCSiliconItems.PLUG_LENS.get().getStack(colour, false);
            AssemblyRecipeRegistry.register(
                new AssemblyRecipeBasic(name, 500 * MjAPI.MJ, input, output));

            name = String.format("lens-filter-%s", colour.getName());
            output = BCSiliconItems.PLUG_LENS.get().getStack(colour, true);
            input = ImmutableSet.of(stainedGlassIngredient,
                new IngredientStack(Ingredient.of(Blocks.IRON_BARS)));
            AssemblyRecipeRegistry.register(
                new AssemblyRecipeBasic(name, 500 * MjAPI.MJ, input, output));
        }

        // Clear lens (no colour)
        IngredientStack glass = new IngredientStack(Ingredient.of(Blocks.GLASS));
        ImmutableSet<IngredientStack> input = ImmutableSet.of(glass);
        ItemStack output = BCSiliconItems.PLUG_LENS.get().getStack(null, false);
        AssemblyRecipeRegistry.register(
            new AssemblyRecipeBasic("lens-regular", 500 * MjAPI.MJ, input, output));

        output = BCSiliconItems.PLUG_LENS.get().getStack(null, true);
        input = ImmutableSet.of(glass, new IngredientStack(Ingredient.of(Blocks.IRON_BARS)));
        AssemblyRecipeRegistry.register(
            new AssemblyRecipeBasic("lens-filter", 500 * MjAPI.MJ, input, output));
    }

    // --- Wire Recipes ---

    private static void registerWireRecipes() {
        // 1.12.2 parity: assembly-table recipe per colour, dye + redstone + iron → 8 wires
        // at 5,000 MJ. The 1.12.2 mod only registered four (RED/BLUE/GREEN/YELLOW); we
        // register all 16 here since every colour ships as a registered item and is usable
        // in-world.
        for (DyeColor colour : ColourUtil.COLOURS) {
            String name = String.format("wire-%s", colour.getName());
            ImmutableSet<IngredientStack> input = ImmutableSet.of(
                new IngredientStack(Ingredient.of(getDyeItem(colour))),
                new IngredientStack(Ingredient.of(Items.REDSTONE)),
                new IngredientStack(Ingredient.of(Items.IRON_INGOT)));
            ItemStack output = new ItemStack(BCTransportItems.WIRE_ITEMS.get(colour).get(), 8);
            AssemblyRecipeRegistry.register(
                new AssemblyRecipeBasic(name, 5_000 * MjAPI.MJ, input, output));
        }
    }

    /** Maps a DyeColor to its corresponding vanilla dye item. */
    private static net.minecraft.world.item.Item getDyeItem(DyeColor colour) {
        return switch (colour) {
            case WHITE -> Items.WHITE_DYE;
            case ORANGE -> Items.ORANGE_DYE;
            case MAGENTA -> Items.MAGENTA_DYE;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_DYE;
            case YELLOW -> Items.YELLOW_DYE;
            case LIME -> Items.LIME_DYE;
            case PINK -> Items.PINK_DYE;
            case GRAY -> Items.GRAY_DYE;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_DYE;
            case CYAN -> Items.CYAN_DYE;
            case PURPLE -> Items.PURPLE_DYE;
            case BLUE -> Items.BLUE_DYE;
            case BROWN -> Items.BROWN_DYE;
            case GREEN -> Items.GREEN_DYE;
            case RED -> Items.RED_DYE;
            case BLACK -> Items.BLACK_DYE;
        };
    }

    // --- Gate Copier Recipe ---

    private static void registerGateCopierRecipe() {
        ImmutableSet.Builder<IngredientStack> input = ImmutableSet.builder();
        // Stick + iron as fallback (since wrench may not exist)
        input.add(new IngredientStack(Ingredient.of(Items.STICK)));
        input.add(new IngredientStack(Ingredient.of(Items.IRON_INGOT)));
        input.add(new IngredientStack(Ingredient.of(Items.REDSTONE)));
        input.add(new IngredientStack(Ingredient.of(Items.REDSTONE)));
        input.add(new IngredientStack(Ingredient.of(Items.GOLD_INGOT)));

        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic(
            "gate_copier", 500 * MjAPI.MJ, input.build(),
            new ItemStack(BCSiliconItems.GATE_COPIER.get())));
    }

    // --- Facade Recipes ---

    private static void registerFacadeRecipes() {
        AssemblyRecipeRegistry.register(FacadeAssemblyRecipes.INSTANCE);
    }

    // --- Tank Recipe ---

    private static void registerTankRecipe() {
        // A power-assisted, more efficient alternative to the 8-glass crafting recipe:
        // 6 cheap glass -> 1 BuildCraft tank. This is also how a player obtains a BuildCraft
        // tank when another mod (e.g. IronTanks) claims the 8-glass crafting grid; see GitHub
        // issue #20. "Cheap glass" is spelled out as clear glass + the 16 stained variants (the
        // vanilla c:glass_blocks/cheap contents) rather than referencing the tag, because both
        // the registry tag-getter and the Ingredient tag factory diverge across the 1.21.1
        // cliff while Ingredient.of(ItemLike...) is uniform on every Stonecutter node.
        java.util.List<net.minecraft.world.level.ItemLike> cheapGlass = new java.util.ArrayList<>();
        cheapGlass.add(Blocks.GLASS);
        for (DyeColor colour : ColourUtil.COLOURS) {
            cheapGlass.add(getStainedGlass(colour));
        }
        ImmutableSet<IngredientStack> input = ImmutableSet.of(new IngredientStack(
            Ingredient.of(cheapGlass.toArray(new net.minecraft.world.level.ItemLike[0])), 6));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic(
            "tank", 2_000 * MjAPI.MJ, input, new ItemStack(BCFactoryBlocks.TANK.get())));
    }

    // --- Helpers ---

    private static void makeGateAssembly(int multiplier, EnumGateMaterial material,
        EnumGateModifier modifier, net.minecraft.world.item.Item chipset,
        IngredientStack... additional) {
        ImmutableSet.Builder<IngredientStack> temp = ImmutableSet.builder();
        temp.add(new IngredientStack(Ingredient.of(chipset)));
        for (IngredientStack add : additional) {
            temp.add(add);
        }
        ImmutableSet<IngredientStack> input = temp.build();

        String name = String.format("gate-and-%s-%s", material, modifier);
        ItemStack output = BCSiliconItems.PLUG_GATE.get()
            .getStack(new GateVariant(EnumGateLogic.AND, material, modifier));
        AssemblyRecipeRegistry.register(
            new AssemblyRecipeBasic(name, MjAPI.MJ * multiplier, input, output));

        name = String.format("gate-or-%s-%s", material, modifier);
        output = BCSiliconItems.PLUG_GATE.get()
            .getStack(new GateVariant(EnumGateLogic.OR, material, modifier));
        AssemblyRecipeRegistry.register(
            new AssemblyRecipeBasic(name, MjAPI.MJ * multiplier, input, output));
    }

    private static void makeGateModifierAssembly(int multiplier, EnumGateMaterial material,
        EnumGateModifier modifier, IngredientStack... mods) {
        for (EnumGateLogic logic : EnumGateLogic.VALUES) {
            String name = String.format("gate-modifier-%s-%s-%s", logic, material, modifier);
            GateVariant variantFrom = new GateVariant(logic, material, EnumGateModifier.NO_MODIFIER);
            ItemStack toUpgrade = BCSiliconItems.PLUG_GATE.get().getStack(variantFrom);
            ItemStack output = BCSiliconItems.PLUG_GATE.get()
                .getStack(new GateVariant(logic, material, modifier));
            ImmutableSet.Builder<IngredientStack> inputBuilder = ImmutableSet.builder();
            // Match by data components, not just the Item: every gate variant shares the same
            // PLUG_GATE item and is disambiguated by CUSTOM_DATA's "gate" CompoundTag plus the
            // CUSTOM_MODEL_DATA variant key. Without this, Ingredient.of(Item) accepts any gate
            // variant, so the iron AND + lapis recipe would also consume a gold AND gate.
            //
            // Pass the stack's *patch* (just the two real overrides) rather than the
            // ItemStack-shaped overload. DataComponentIngredient.of(boolean, ItemStack) internally
            // calls asPatch(stack.getComponents()) — i.e. it serialises every default component
            // too. When JEI's display path later rebuilds the stack via ItemStackTemplate.create(),
            // those default-equal entries end up in the rebuilt PatchedDataComponentMap's internal
            // patch, and PatchedDataComponentMap.equals compares patches structurally — so the
            // rebuilt stack is !equals to the canonical gate stack JEI keeps in its ingredient
            // list, and R/U lookup on the basic-gate variants stops finding the recipe.
            inputBuilder.add(new IngredientStack(DataComponentIngredient.of(false,
                //? if >=26.1 {
                toUpgrade.getComponentsPatch(), toUpgrade.getItem())));
                //?} else {
                /*toUpgrade.getComponents(), toUpgrade.getItem())));*/
                //?}
            for (IngredientStack mod : mods) {
                inputBuilder.add(mod);
            }
            ImmutableSet<IngredientStack> input = inputBuilder.build();
            AssemblyRecipeRegistry.register(
                new AssemblyRecipeBasic(name, MjAPI.MJ * multiplier, input, output));
        }
    }

    /** Maps a DyeColor to its corresponding stained glass block. */
    private static Block getStainedGlass(DyeColor colour) {
        return switch (colour) {
            case WHITE -> Blocks.WHITE_STAINED_GLASS;
            case ORANGE -> Blocks.ORANGE_STAINED_GLASS;
            case MAGENTA -> Blocks.MAGENTA_STAINED_GLASS;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_STAINED_GLASS;
            case YELLOW -> Blocks.YELLOW_STAINED_GLASS;
            case LIME -> Blocks.LIME_STAINED_GLASS;
            case PINK -> Blocks.PINK_STAINED_GLASS;
            case GRAY -> Blocks.GRAY_STAINED_GLASS;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_STAINED_GLASS;
            case CYAN -> Blocks.CYAN_STAINED_GLASS;
            case PURPLE -> Blocks.PURPLE_STAINED_GLASS;
            case BLUE -> Blocks.BLUE_STAINED_GLASS;
            case BROWN -> Blocks.BROWN_STAINED_GLASS;
            case GREEN -> Blocks.GREEN_STAINED_GLASS;
            case RED -> Blocks.RED_STAINED_GLASS;
            case BLACK -> Blocks.BLACK_STAINED_GLASS;
        };
    }
}
