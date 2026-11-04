package buildcraft.api.recipes;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import buildcraft.api.fluids.BCFluidStack;

public interface IRefineryRecipeManager {
    IHeatableRecipe createHeatingRecipe(BCFluidStack in, BCFluidStack out, int heatFrom, int heatTo);

    default IHeatableRecipe addHeatableRecipe(BCFluidStack in, BCFluidStack out, int heatFrom, int heatTo) {
        return getHeatableRegistry().addRecipe(createHeatingRecipe(in, out, heatFrom, heatTo));
    }

    ICoolableRecipe createCoolableRecipe(BCFluidStack in, BCFluidStack out, int heatFrom, int heatTo);

    default ICoolableRecipe addCoolableRecipe(BCFluidStack in, BCFluidStack out, int heatFrom, int heatTo) {
        return getCoolableRegistry().addRecipe(createCoolableRecipe(in, out, heatFrom, heatTo));
    }

    IDistillationRecipe createDistillationRecipe(BCFluidStack in, BCFluidStack outGas, BCFluidStack outLiquid, long powerRequired);

    default IDistillationRecipe addDistillationRecipe(BCFluidStack in, BCFluidStack outGas, BCFluidStack outLiquid, long powerRequired) {
        return getDistillationRegistry().addRecipe(createDistillationRecipe(in, outGas, outLiquid, powerRequired));
    }

    IRefineryRegistry<IHeatableRecipe> getHeatableRegistry();

    IRefineryRegistry<ICoolableRecipe> getCoolableRegistry();

    IRefineryRegistry<IDistillationRecipe> getDistillationRegistry();

    interface IRefineryRegistry<R extends IRefineryRecipe> {
        /** @return an unmodifiable collection containing all of the distillation recipes that satisfy the given
         *         predicate. All of the recipe objects are guaranteed to never be null. */
        Stream<R> getRecipes(Predicate<R> toReturn);

        /** @return an unmodifiable set containing all of the distillation recipes. */
        Collection<R> getAllRecipes();

        @Nullable
        R getRecipeForInput(@Nullable BCFluidStack fluid);

        Collection<R> removeRecipes(Predicate<R> toRemove);

        /** Adds the given recipe to the registry. Note that this will remove any existing recipes for the passed
         * recipe's {@link IRefineryRecipe#in()}
         * 
         * @param recipe The recipe to add.
         * @return The input recipe. */
        R addRecipe(R recipe);
    }

    interface IRefineryRecipe {
        BCFluidStack in();
    }

    interface IHeatExchangerRecipe extends IRefineryRecipe {
        @Nullable
        BCFluidStack out();

        int heatFrom();

        int heatTo();
    }

    interface IHeatableRecipe extends IHeatExchangerRecipe {}

    interface ICoolableRecipe extends IHeatExchangerRecipe {}

    interface IDistillationRecipe extends IRefineryRecipe {
        long powerRequired();

        BCFluidStack outGas();

        BCFluidStack outLiquid();
    }
}

