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

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import buildcraft.core.BCCore;
import buildcraft.platform.CraftingServices;

/^*
 * {@link CraftingServices} on MinecraftForge, which has neither seam: its only stack-matching ingredient compares the
 * {@code custom_data} tag, and vanilla keeps the recipe map private (NeoForge patches an accessor in, Fabric opens the
 * field).
 *
 * <p><b>Ingredient.</b> {@link ComponentsIngredient} is BuildCraft's own, written to NeoForge's non-strict data
 * component ingredient: the item, and every entry of the stack's component patch present on the candidate (a removed
 * component must be absent). It is built in code for BuildCraft's own assembly recipes and read from the gate recipes'
 * JSON, which the Forge build's data rewrite spells {@code {"type": "buildcraftunofficial:components", "items": ...,
 * "components": ...}} (Forge dispatches an ingredient object on {@code type} through {@code forge:ingredient_serializers},
 * and has no component-matching ingredient of its own). {@link #registerSerializers} registers that serializer.
 *
 * <p><b>Recipe map.</b> Read from the private {@code recipes} field through FML's reflection helper, which finds it by
 * its Mojang name (Forge on 26.x runs unobfuscated). A direct read needs a line in the shared access transformer
 * ({@code public net.minecraft.world.item.crafting.RecipeManager recipes}), which NeoForge's nodes would then apply too;
 * left reflective until that line is wanted.
 *^/
public final class ForgeCraftingServices implements CraftingServices {
    /^* The serializer's id path; {@code DataForge} (buildSrc) writes the full id into the recipe JSON. ^/
    private static final String COMPONENTS_INGREDIENT_PATH = "components";

    /^*
     * Registers the components ingredient's serializer in {@code forge:ingredient_serializers}. It has to run while the
     * mod is being constructed (the entrypoint calls it), not when the first ingredient is asked for: the registry fires
     * its register event once, and a recipe naming the type is read after that.
     ^/
    public static void registerSerializers() {
        DeferredRegister<IIngredientSerializer<?>> register =
                DeferredRegister.create(ForgeRegistries.Keys.INGREDIENT_SERIALIZERS, BCCore.MODID);
        register.register(COMPONENTS_INGREDIENT_PATH, () -> ComponentsIngredient.SERIALIZER);
        register.register(ForgeModBus.group());
    }

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
        /^* The recipe JSON's shape: {@code items} (an item id, a {@code #tag} or a list) and {@code components} (the patch). ^/
        static final MapCodec<ComponentsIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.NON_AIR_HOLDER_SET_CODEC.fieldOf("items").forGetter(i -> i.values),
                DataComponentPatch.CODEC.fieldOf("components").forGetter(i -> i.patch)
        ).apply(instance, ComponentsIngredient::new));

        static final StreamCodec<RegistryFriendlyByteBuf, ComponentsIngredient> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.holderSet(Registries.ITEM), i -> i.values,
                DataComponentPatch.STREAM_CODEC, i -> i.patch,
                ComponentsIngredient::new);

        static final IIngredientSerializer<ComponentsIngredient> SERIALIZER = IIngredientSerializer.simple(CODEC, STREAM_CODEC);

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
            return SERIALIZER;
        }
    }
}
*///?}
