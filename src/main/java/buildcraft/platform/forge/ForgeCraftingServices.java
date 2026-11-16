/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Set;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import net.minecraftforge.common.crafting.ingredients.AbstractIngredient;
import net.minecraftforge.common.crafting.ingredients.IIngredientSerializer;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import buildcraft.platform.CraftingServices;

/^*
 * {@link CraftingServices} on MinecraftForge, which has neither seam: its only stack-matching ingredient compares the
 * {@code custom_data} tag, and vanilla keeps the recipe map private (NeoForge patches an accessor in, Fabric opens the
 * field).
 *
 * <p><b>Ingredient.</b> {@link ComponentsIngredient} is BuildCraft's own, written to NeoForge's non-strict data
 * component ingredient: the item, and every entry of the stack's component patch present on the candidate (a removed
 * component must be absent). It is built in code for BuildCraft's own assembly recipes, which never cross the wire as
 * vanilla recipes, so it carries no codec and registers no serializer.
 *
 * <p><b>Recipe map.</b> Read from the private {@code recipes} field through FML's reflection helper, which finds it by
 * its Mojang name (Forge on 26.x runs unobfuscated). A direct read needs a line in the shared access transformer
 * ({@code public net.minecraft.world.item.crafting.RecipeManager recipes}), which NeoForge's nodes would then apply too;
 * left reflective until that line is wanted.
 *^/
public final class ForgeCraftingServices implements CraftingServices {
    @Override
    public Ingredient componentsIngredient(ItemStack stack) {
        // The stack's *patch* (its real overrides), not every default component: a stack rebuilt from an ingredient that
        // listed the defaults too would then fail patch equality against the canonical stack, and a recipe viewer's lookup
        // would stop finding the recipe.
        return new ComponentsIngredient(HolderSet.direct(stack.typeHolder()), stack.getComponentsPatch());
    }

    @Override
    public RecipeMap recipeMap(RecipeManager manager) {
        try {
            return (RecipeMap) RecipeField.RECIPES.get(manager);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read the recipe map of " + manager, e);
        }
    }

    /^* Holds the field lookup so that only the first {@link #recipeMap} call pays for it (and can fail). ^/
    private static final class RecipeField {
        static final Field RECIPES = ObfuscationReflectionHelper.findField(RecipeManager.class, "recipes");
    }

    /^* Matches one of {@code items} whose components include everything {@code patch} adds and lack what it removes. ^/
    private static final class ComponentsIngredient extends AbstractIngredient {
        private final DataComponentPatch patch;
        private final DataComponentMap added;
        private final Set<DataComponentType<?>> removed;

        ComponentsIngredient(HolderSet<Item> items, DataComponentPatch patch) {
            super(items);
            this.patch = patch;
            DataComponentPatch.SplitResult split = patch.split();
            this.added = split.added();
            this.removed = split.removed();
        }

        @Override
        public boolean test(ItemStack candidate) {
            if (candidate == null || !candidate.is(values)) {
                return false;
            }
            for (TypedDataComponent<?> component : added) {
                if (!Objects.equals(component.value(), candidate.get(component.type()))) {
                    return false;
                }
            }
            for (DataComponentType<?> type : removed) {
                if (candidate.has(type)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean isSimple() {
            return false;
        }

        @Override
        public SlotDisplay display() {
            // The overridden stacks themselves, so a recipe viewer shows (and looks up) the exact variant.
            return new SlotDisplay.Composite(values.stream()
                .<SlotDisplay>map(item -> new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(item, 1, patch)))
                .toList());
        }

        @Override
        public IIngredientSerializer<? extends Ingredient> serializer() {
            throw new UnsupportedOperationException("BuildCraft's component ingredient is built in code and never serialised");
        }
    }
}
*///?}
