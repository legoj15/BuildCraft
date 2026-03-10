package net.minecraft.world.inventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.crafting.RecipeHolder;

public abstract class RecipeBookMenu extends AbstractContainerMenu {
    public RecipeBookMenu(MenuType<?> p_40115_, int p_40116_) {
        super(p_40115_, p_40116_);
    }

    public abstract RecipeBookMenu.PostPlaceAction handlePlacement(
        boolean p_40119_, boolean p_362739_, RecipeHolder<?> p_300860_, ServerLevel p_379372_, Inventory p_363345_
    );

    public abstract void fillCraftSlotsStackedContents(StackedItemContents p_362236_);

    public abstract RecipeBookType getRecipeBookType();

    public static enum PostPlaceAction {
        NOTHING,
        PLACE_GHOST_RECIPE;
    }
}
