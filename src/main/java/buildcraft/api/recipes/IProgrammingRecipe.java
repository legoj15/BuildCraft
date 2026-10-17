package buildcraft.api.recipes;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.world.item.ItemStack;

/** A recipe executable by the Programming Table. Ported from 7.1.x {@code buildcraft.api.recipes.IProgrammingRecipe};
 * the {@code int} RF energy cost is now a {@code long} in micro-MJ. */
public interface IProgrammingRecipe {
    /** @return The unique id of this recipe (saved by the table alongside the selected option). */
    String getId();

    /**
     * Get a list (size at least width * height) of ItemStacks representing options.
     *
     * @param width  The width of the Programming Table panel.
     * @param height The height of the Programming Table panel.
     */
    List<ItemStack> getOptions(int width, int height);

    /** @return The energy cost, in micro-MJ, of programming the given option. */
    long getEnergyCost(@Nonnull ItemStack option);

    /** @return Whether this recipe applies to the given input stack. */
    boolean canCraft(@Nonnull ItemStack input);

    /**
     * @return True if crafting {@code option} from {@code input} would only hand back what the input already is (a
     *         board "converted" into the very board it is), so the table skips it: it neither asks for energy nor
     *         consumes the input. Only called for an input {@link #canCraft} accepts. Defaults to {@code false} - a
     *         recipe whose options can never coincide with its inputs need not override it.
     */
    default boolean isRedundant(@Nonnull ItemStack input, @Nonnull ItemStack option) {
        return false;
    }

    /** Craft the input ItemStack with the given option into an output ItemStack. The table consumes the input. */
    @Nonnull
    ItemStack craft(@Nonnull ItemStack input, @Nonnull ItemStack option);
}
