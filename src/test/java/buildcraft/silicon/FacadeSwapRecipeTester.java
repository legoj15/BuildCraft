package buildcraft.silicon;

import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Blocks;

import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.recipe.FacadeSwapRecipe;

/** Game tests for {@link FacadeSwapRecipe}: a lone facade toggles its hollow flag; two facades (or any
 *  multi-item grid) are rejected. */
public class FacadeSwapRecipeTester {
    private static final FacadeSwapRecipe RECIPE = new FacadeSwapRecipe();

    private static ItemStack stoneFacade() {
        ItemStack facade = BCSiliconItems.PLUG_FACADE.get().getFacadeForBlock(Blocks.STONE.defaultBlockState());
        // Fall back to a bare facade if the state registry has no entry for stone; getStates() still
        // yields a valid default instance, which is all the swap logic needs.
        return facade.isEmpty() ? new ItemStack(BCSiliconItems.PLUG_FACADE.get()) : facade;
    }

    public static void testSwapTogglesHollow(GameTestHelper helper) {
        ItemStack facade = stoneFacade();
        boolean before = ItemPluggableFacade.getStates(facade).isHollow;
        CraftingInput input = CraftingInput.of(1, 1, List.of(facade));
        if (!RECIPE.matches(input, helper.getLevel())) {
            helper.fail("facade swap should match a lone facade");
            return;
        }
        ItemStack out = RECIPE.assemble(input);
        if (out.isEmpty() || out.getItem() != BCSiliconItems.PLUG_FACADE.get()) {
            helper.fail("facade swap output should be a facade");
            return;
        }
        boolean after = ItemPluggableFacade.getStates(out).isHollow;
        if (after == before) {
            helper.fail("facade swap should toggle isHollow (was " + before + ", still " + after + ")");
            return;
        }
        helper.succeed();
    }

    public static void testRejectsTwoFacades(GameTestHelper helper) {
        CraftingInput input = CraftingInput.of(2, 1, List.of(stoneFacade(), stoneFacade()));
        if (RECIPE.matches(input, helper.getLevel())) {
            helper.fail("facade swap must NOT match two facades");
            return;
        }
        helper.succeed();
    }
}
