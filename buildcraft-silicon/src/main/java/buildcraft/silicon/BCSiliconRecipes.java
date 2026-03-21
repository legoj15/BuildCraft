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

import buildcraft.api.mj.MjAPI;
import buildcraft.api.recipes.AssemblyRecipeBasic;
import buildcraft.api.recipes.IngredientStack;

import buildcraft.lib.misc.ColourUtil;
import buildcraft.lib.recipe.AssemblyRecipeRegistry;

import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;

public class BCSiliconRecipes {

    public static void init() {
        registerPlugRecipes();
        registerGateAssemblyRecipes();
        registerChipsetRecipes();
        registerLensRecipes();
        registerGateCopierRecipe();
        registerFacadeRecipes();
    }

    // --- Plug Recipes ---

    private static void registerPlugRecipes() {
        // Pulsar: redstone engine (or redstone block) + 2 iron ingots
        {
            Set<IngredientStack> input = new HashSet<>();
            input.add(new IngredientStack(Ingredient.of(Blocks.REDSTONE_BLOCK)));
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
            BCSiliconItems.REDSTONE_IRON_CHIPSET.get());
        makeGateAssembly(40_000, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.NO_MODIFIER,
            BCSiliconItems.REDSTONE_IRON_CHIPSET.get(),
            new IngredientStack(Ingredient.of(Blocks.NETHER_BRICKS)));
        makeGateAssembly(80_000, EnumGateMaterial.GOLD, EnumGateModifier.NO_MODIFIER,
            BCSiliconItems.REDSTONE_GOLD_CHIPSET.get());

        // Iron modifier upgrades
        IngredientStack lapis = new IngredientStack(Ingredient.of(Items.LAPIS_LAZULI));
        makeGateModifierAssembly(40_000, EnumGateMaterial.IRON, EnumGateModifier.LAPIS, lapis);
        makeGateModifierAssembly(60_000, EnumGateMaterial.IRON, EnumGateModifier.QUARTZ,
            new IngredientStack(Ingredient.of(BCSiliconItems.REDSTONE_QUARTZ_CHIPSET.get())));
        makeGateModifierAssembly(80_000, EnumGateMaterial.IRON, EnumGateModifier.DIAMOND,
            new IngredientStack(Ingredient.of(BCSiliconItems.REDSTONE_DIAMOND_CHIPSET.get())));

        // Nether Brick modifier upgrades
        makeGateModifierAssembly(80_000, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.LAPIS, lapis);
        makeGateModifierAssembly(100_000, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.QUARTZ,
            new IngredientStack(Ingredient.of(BCSiliconItems.REDSTONE_QUARTZ_CHIPSET.get())));
        makeGateModifierAssembly(120_000, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.DIAMOND,
            new IngredientStack(Ingredient.of(BCSiliconItems.REDSTONE_DIAMOND_CHIPSET.get())));

        // Gold modifier upgrades
        makeGateModifierAssembly(100_000, EnumGateMaterial.GOLD, EnumGateModifier.LAPIS, lapis);
        makeGateModifierAssembly(140_000, EnumGateMaterial.GOLD, EnumGateModifier.QUARTZ,
            new IngredientStack(Ingredient.of(BCSiliconItems.REDSTONE_QUARTZ_CHIPSET.get())));
        makeGateModifierAssembly(180_000, EnumGateMaterial.GOLD, EnumGateModifier.DIAMOND,
            new IngredientStack(Ingredient.of(BCSiliconItems.REDSTONE_DIAMOND_CHIPSET.get())));
    }

    // --- Chipset Recipes ---

    private static void registerChipsetRecipes() {
        ImmutableSet<IngredientStack> input;

        input = ImmutableSet.of(new IngredientStack(Ingredient.of(Items.REDSTONE)));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("redstone_chipset",
            10_000 * MjAPI.MJ, input, new ItemStack(BCSiliconItems.REDSTONE_RED_CHIPSET.get())));

        input = ImmutableSet.of(
            new IngredientStack(Ingredient.of(Items.REDSTONE)),
            new IngredientStack(Ingredient.of(Items.IRON_INGOT)));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("iron_chipset",
            20_000 * MjAPI.MJ, input, new ItemStack(BCSiliconItems.REDSTONE_IRON_CHIPSET.get())));

        input = ImmutableSet.of(
            new IngredientStack(Ingredient.of(Items.REDSTONE)),
            new IngredientStack(Ingredient.of(Items.GOLD_INGOT)));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("gold_chipset",
            40_000 * MjAPI.MJ, input, new ItemStack(BCSiliconItems.REDSTONE_GOLD_CHIPSET.get())));

        input = ImmutableSet.of(
            new IngredientStack(Ingredient.of(Items.REDSTONE)),
            new IngredientStack(Ingredient.of(Items.QUARTZ)));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("quartz_chipset",
            60_000 * MjAPI.MJ, input, new ItemStack(BCSiliconItems.REDSTONE_QUARTZ_CHIPSET.get())));

        input = ImmutableSet.of(
            new IngredientStack(Ingredient.of(Items.REDSTONE)),
            new IngredientStack(Ingredient.of(Items.DIAMOND)));
        AssemblyRecipeRegistry.register(new AssemblyRecipeBasic("diamond_chipset",
            80_000 * MjAPI.MJ, input, new ItemStack(BCSiliconItems.REDSTONE_DIAMOND_CHIPSET.get())));
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
        // TODO: FacadeAssemblyRecipes dynamic recipe
        // AssemblyRecipeRegistry.register(FacadeAssemblyRecipes.INSTANCE);
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
            inputBuilder.add(new IngredientStack(Ingredient.of(toUpgrade.getItem())));
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
