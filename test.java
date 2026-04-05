package test;

import net.minecraft.world.item.crafting.*;
import net.minecraft.core.registries.BuiltInRegistries;

public class Test {
    public static void main(String[] args) {
        for (var recipe : BuiltInRegistries.RECIPE_SERIALIZER) {
            System.out.println(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe) + " -> " + recipe.getClass().getName());
        }
    }
}
