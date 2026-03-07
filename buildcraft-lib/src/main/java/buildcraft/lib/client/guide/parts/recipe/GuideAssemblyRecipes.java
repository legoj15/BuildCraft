package buildcraft.lib.client.guide.parts.recipe;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.parts.GuidePartFactory;

/** Assembly recipe guide integration.
 * Currently stubbed — requires AssemblyRecipeRegistry and BCBlocks.Silicon which are not yet ported. */
public enum GuideAssemblyRecipes implements IStackRecipes {
    INSTANCE;

    @Override
    public List<GuidePartFactory> getUsages(@Nonnull ItemStack stack) {
        // AssemblyRecipeRegistry not ported — return empty
        return ImmutableList.of();
    }

    @Override
    public List<GuidePartFactory> getRecipes(@Nonnull ItemStack stack) {
        // AssemblyRecipeRegistry not ported — return empty
        return ImmutableList.of();
    }
}
