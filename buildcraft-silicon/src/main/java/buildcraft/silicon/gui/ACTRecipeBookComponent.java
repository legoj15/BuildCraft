/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.gui;

import java.util.List;

import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;

import buildcraft.silicon.container.ContainerAdvancedCraftingTable;

/**
 * Recipe book component for the Advanced Crafting Table.
 * Mirrors vanilla CraftingRecipeBookComponent but works with our container.
 */
public class ACTRecipeBookComponent extends RecipeBookComponent<ContainerAdvancedCraftingTable> {
    private static final WidgetSprites FILTER_BUTTON_SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("recipe_book/filter_enabled"),
        ResourceLocation.withDefaultNamespace("recipe_book/filter_disabled"),
        ResourceLocation.withDefaultNamespace("recipe_book/filter_enabled_highlighted"),
        ResourceLocation.withDefaultNamespace("recipe_book/filter_disabled_highlighted")
    );
    private static final Component ONLY_CRAFTABLES_TOOLTIP = Component.translatable("gui.recipebook.toggleRecipes.craftable");
    private static final List<RecipeBookComponent.TabInfo> TABS = List.of(
        new RecipeBookComponent.TabInfo(SearchRecipeBookCategory.CRAFTING),
        new RecipeBookComponent.TabInfo(Items.IRON_AXE, Items.GOLDEN_SWORD, RecipeBookCategories.CRAFTING_EQUIPMENT),
        new RecipeBookComponent.TabInfo(Items.BRICKS, RecipeBookCategories.CRAFTING_BUILDING_BLOCKS),
        new RecipeBookComponent.TabInfo(Items.LAVA_BUCKET, Items.APPLE, RecipeBookCategories.CRAFTING_MISC),
        new RecipeBookComponent.TabInfo(Items.REDSTONE, RecipeBookCategories.CRAFTING_REDSTONE)
    );

    public ACTRecipeBookComponent(ContainerAdvancedCraftingTable menu) {
        super(menu, TABS);
    }

    @Override
    protected boolean isCraftingSlot(Slot slot) {
        return this.menu.getResultSlot() == slot || this.menu.getInputGridSlots().contains(slot);
    }

    private boolean canDisplay(RecipeDisplay display) {
        int w = this.menu.getGridWidth();
        int h = this.menu.getGridHeight();

        return switch (display) {
            case ShapedCraftingRecipeDisplay shaped -> w >= shaped.width() && h >= shaped.height();
            case ShapelessCraftingRecipeDisplay shapeless -> w * h >= shapeless.ingredients().size();
            default -> false;
        };
    }

    @Override
    protected void fillGhostRecipe(GhostSlots ghostSlots, RecipeDisplay display, ContextMap context) {
        ghostSlots.setResult(this.menu.getResultSlot(), context, display.result());
        switch (display) {
            case ShapedCraftingRecipeDisplay shaped: {
                List<Slot> slots = this.menu.getInputGridSlots();
                PlaceRecipeHelper.placeRecipe(
                    this.menu.getGridWidth(),
                    this.menu.getGridHeight(),
                    shaped.width(),
                    shaped.height(),
                    shaped.ingredients(),
                    (slotDisplay, gridIdx, x, y) -> {
                        Slot slot = slots.get(gridIdx);
                        ghostSlots.setInput(slot, context, slotDisplay);
                    }
                );
                break;
            }
            case ShapelessCraftingRecipeDisplay shapeless: {
                List<Slot> slots = this.menu.getInputGridSlots();
                int count = Math.min(shapeless.ingredients().size(), slots.size());
                for (int i = 0; i < count; i++) {
                    ghostSlots.setInput(slots.get(i), context, shapeless.ingredients().get(i));
                }
                break;
            }
            default: break;
        }
    }

    protected WidgetSprites getFilterButtonTextures() {
        return FILTER_BUTTON_SPRITES;
    }
    
    @Override
    protected void initFilterButtonTextures() {
    }

    @Override
    protected Component getRecipeFilterName() {
        return ONLY_CRAFTABLES_TOOLTIP;
    }

    @Override
    protected void selectMatchingRecipes(RecipeCollection collection, StackedItemContents contents) {
        collection.selectRecipes(contents, this::canDisplay);
    }
}
