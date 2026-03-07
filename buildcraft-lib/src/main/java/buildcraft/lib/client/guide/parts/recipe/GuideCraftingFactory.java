package buildcraft.lib.client.guide.parts.recipe;

import javax.annotation.Nonnull;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.client.guide.parts.GuidePartFactory;

/** Factory for creating crafting recipe display parts.
 * 
 * Note: In 1.21 the Recipe API changed significantly — Recipe no longer exposes
 * getIngredients()/getResultItem() on the base interface. The recipe grid factory
 * methods that iterate recipes are stubbed until the recipe system is rewritten
 * for 1.21's RecipeDisplay/PlacementInfo model. */
public class GuideCraftingFactory implements GuidePartFactory {

    private final @Nonnull ItemStack output;
    private final int hash;

    public GuideCraftingFactory(ItemStack output) {
        this.output = output.isEmpty() ? ItemStack.EMPTY : output;
        this.hash = ItemStack.hashItemAndComponents(this.output);
    }

    /** Create a factory from a recipe. Stubbed — needs 1.21 Recipe API rewrite.
     * @return null (stubbed) */
    public static GuidePartFactory getFactory(Object recipe) {
        // In 1.21: Recipe no longer has getIngredients()/getResultItem() on the base interface.
        // ShapedRecipe.getIngredients() returns List<Optional<Ingredient>>.
        // Recipe results are obtained via RecipeDisplay, not a direct method.
        // TODO: Rewrite using 1.21 ShapedRecipe.pattern / RecipeDisplay API
        return null;
    }

    @Override
    public GuidePart createNew(GuiGuide gui) {
        // Would create a GuideCrafting with proper grid — stubbed
        return null;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != getClass()) return false;
        GuideCraftingFactory other = (GuideCraftingFactory) obj;
        if (hash != other.hash) return false;
        return ItemStack.isSameItemSameComponents(output, other.output);
    }
}
