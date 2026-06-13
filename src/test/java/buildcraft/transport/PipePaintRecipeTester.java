package buildcraft.transport;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;

import buildcraft.transport.recipe.PipePaintRecipe;

/** Game tests for {@link PipePaintRecipe}: recolour with a dye, bleach with a water bucket, output count
 *  tracks the number of pipes supplied, and invalid grids (mixed pipe types / no modifier) are rejected. */
public class PipePaintRecipeTester {
    private static final PipePaintRecipe RECIPE = new PipePaintRecipe();

    public static void testRecolour(GameTestHelper helper) {
        ItemStack pipe = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack dye = new ItemStack(Items.RED_DYE);
        CraftingInput input = CraftingInput.of(2, 1, List.of(pipe, dye));
        if (!RECIPE.matches(input, helper.getLevel())) {
            helper.fail("paint recipe should match cobble pipe + red dye");
            return;
        }
        ItemStack out = RECIPE.assemble(input);
        if (out.getItem() != BCTransportItems.PIPE_COBBLE_ITEM.get()) {
            helper.fail("output should be a cobble pipe");
            return;
        }
        DyeColor col = out.get(BCTransportItems.PIPE_COLOUR.get());
        if (col != DyeColor.RED) {
            helper.fail("output pipe should be painted red, got " + col);
            return;
        }
        if (out.getCount() != 1) {
            helper.fail("expected 1 pipe out, got " + out.getCount());
            return;
        }
        helper.succeed();
    }

    public static void testCountMatchesPipeSlots(GameTestHelper helper) {
        ItemStack p1 = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack p2 = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack p3 = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack dye = new ItemStack(Items.GREEN_DYE);
        CraftingInput input = CraftingInput.of(2, 2, List.of(p1, p2, p3, dye));
        ItemStack out = RECIPE.assemble(input);
        if (out.getCount() != 3) {
            helper.fail("expected 3 painted pipes (one per pipe slot), got " + out.getCount());
            return;
        }
        if (out.get(BCTransportItems.PIPE_COLOUR.get()) != DyeColor.GREEN) {
            helper.fail("expected green pipes");
            return;
        }
        helper.succeed();
    }

    public static void testBleach(GameTestHelper helper) {
        ItemStack pipe = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        pipe.set(BCTransportItems.PIPE_COLOUR.get(), DyeColor.BLUE);
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        CraftingInput input = CraftingInput.of(2, 1, List.of(pipe, bucket));
        if (!RECIPE.matches(input, helper.getLevel())) {
            helper.fail("paint recipe should match coloured pipe + water bucket (bleach)");
            return;
        }
        ItemStack out = RECIPE.assemble(input);
        if (out.get(BCTransportItems.PIPE_COLOUR.get()) != null) {
            helper.fail("bleached pipe should carry no colour");
            return;
        }
        NonNullList<ItemStack> remaining = RECIPE.getRemainingItems(input);
        if (!remaining.get(1).is(Items.BUCKET)) {
            helper.fail("bleaching should leave an empty bucket, got " + remaining.get(1));
            return;
        }
        helper.succeed();
    }

    public static void testMixedPipeTypesRejected(GameTestHelper helper) {
        ItemStack cobble = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack stone = new ItemStack(BCTransportItems.PIPE_STONE_ITEM.get());
        ItemStack dye = new ItemStack(Items.RED_DYE);
        CraftingInput mixed = CraftingInput.of(3, 1, List.of(cobble, stone, dye));
        if (RECIPE.matches(mixed, helper.getLevel())) {
            helper.fail("paint recipe must NOT match mixed pipe types");
            return;
        }
        CraftingInput noModifier = CraftingInput.of(1, 1, List.of(new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get())));
        if (RECIPE.matches(noModifier, helper.getLevel())) {
            helper.fail("paint recipe must NOT match a lone pipe with no dye/water");
            return;
        }
        helper.succeed();
    }
}
