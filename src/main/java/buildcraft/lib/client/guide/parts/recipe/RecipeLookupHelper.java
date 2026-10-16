/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.parts.GuidePartFactory;

public class RecipeLookupHelper {
    /** Iteration order is the order a page lists its recipe panels in. Insertion-ordered so it is stable: crafting,
     *  assembling, smelting is the order the old hash map happened to give, and registered lookups follow in
     *  registration order (robotics adds programming before integration, so a board's page reads in the order a
     *  robot is made). */
    public static final Map<String, IStackRecipes> handlerTypes = new LinkedHashMap<>();

    static {
        handlerTypes.put("crafting", GuideCraftingRecipes.INSTANCE);
        handlerTypes.put("assembling", GuideAssemblyRecipes.INSTANCE);
        handlerTypes.put("smelting", GuideSmeltingRecipes.INSTANCE);
    }

    /** Adds a recipe lookup under {@code type} — the name a page's {@code type="..."} attribute uses — so every
     *  {@code <recipes>}/{@code <usages>}/{@code <recipes_usages>} tag and every stand-in page consults it. Lets a
     *  subsystem bring its own table recipes (robotics: {@code programming}, {@code integration}) without lib
     *  having to know about them. Call during client init, before any guide page loads. */
    public static void register(String type, IStackRecipes handler) {
        handlerTypes.put(type, handler);
    }

    public static List<GuidePartFactory> getAllUsages(@Nonnull ItemStack stack) {
        List<GuidePartFactory> list = new ArrayList<>();
        for (IStackRecipes handler : handlerTypes.values()) {
            List<GuidePartFactory> recipes = handler.getUsages(stack);
            if (recipes != null) {
                list.addAll(recipes);
            }
        }
        return list;
    }

    public static List<GuidePartFactory> getAllRecipes(@Nonnull ItemStack stack) {
        List<GuidePartFactory> list = new ArrayList<>();
        for (IStackRecipes handler : handlerTypes.values()) {
            List<GuidePartFactory> recipes = handler.getRecipes(stack);
            if (recipes != null) {
                list.addAll(recipes);
            }
        }
        return list;
    }
}
