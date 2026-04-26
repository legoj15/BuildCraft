package buildcraft.api.lists;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;

public abstract class ListMatchHandler {
    public enum Type {
        TYPE,
        MATERIAL,
        CLASS
    }

    public abstract boolean matches(Type type, @Nonnull ItemStack stack, @Nonnull ItemStack target, boolean precise);

    public abstract boolean isValidSource(Type type, @Nonnull ItemStack stack);

    /** Get custom client examples.
     *
     * @param type
     * @param stack
     * @return A List (even empty!) if the examples satisfy this handler, null if iteration and .matches should be used
     *         instead. */
    @Nullable
    public NonNullList<ItemStack> getClientExamples(Type type, @Nonnull ItemStack stack) {
        return null;
    }

    /** Human-readable strings describing why this handler claims a given exemplar — e.g. for the
     * Tags handler "#minecraft:planks", for Tools "#minecraft:axes (any axe)", for Fluid the
     * fluid-id of contents. Used by the list GUI's match-info ledger to show players what their
     * filter actually selects. Default implementation returns an empty list (handler claims the
     * source but offers no human-readable description). Implementors should return one entry per
     * distinct contributing tag/property; the GUI prepends the handler's name as a section header.
     *
     * @param type the active mode (TYPE or MATERIAL); CLASS is also passed through but few handlers
     *             use it
     * @param stack the exemplar item the user placed in slot 0
     * @return ordered, deduplicated list of description strings; empty if not claimed */
    @Nonnull
    public List<String> describeMatch(Type type, @Nonnull ItemStack stack) {
        return List.of();
    }
}

