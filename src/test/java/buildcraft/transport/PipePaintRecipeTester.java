package buildcraft.transport;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.transport.recipe.PipePaintRecipe;

/** Game tests for {@link PipePaintRecipe}: recolour with a charged paintbrush (consuming one use per
 *  pipe), bleach with a water bucket, output count tracks pipe count, and invalid grids (mixed types,
 *  clean brush, under-charged brush, no modifier) are rejected. */
public class PipePaintRecipeTester {
    //? if >=26.1 {
    private static final PipePaintRecipe RECIPE = new PipePaintRecipe();
    //?} else {
    /*private static final PipePaintRecipe RECIPE =
            new PipePaintRecipe(net.minecraft.world.item.crafting.CraftingBookCategory.MISC);*/
    //?}

    private static ItemStack chargedBrush(DyeColor colour, int uses) {
        ItemStack brush = ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), colour);
        brush.set(BCCore.BRUSH_USES.get(), uses);
        return brush;
    }

    private static int usesOf(ItemStack brush) {
        return BCCoreItems.PAINTBRUSH.get().getBrushFromStack(brush).usesLeft;
    }

    public static void testRecolour(GameTestHelper helper) {
        ItemStack pipe = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack brush = chargedBrush(DyeColor.RED, 64);
        CraftingInput input = CraftingInput.of(2, 1, List.of(pipe, brush));
        if (!RECIPE.matches(input, helper.getLevel())) {
            helper.fail("paint recipe should match cobble pipe + charged red brush");
            return;
        }
        //? if >=26.1 {
        ItemStack out = RECIPE.assemble(input);
        //?} else {
        /*ItemStack out = RECIPE.assemble(input, helper.getLevel().registryAccess());*/
        //?}
        if (out.getItem() != BCTransportItems.PIPE_COBBLE_ITEM.get()) {
            helper.fail("output should be a cobble pipe");
            return;
        }
        if (out.get(BCTransportItems.PIPE_COLOUR.get()) != DyeColor.RED) {
            helper.fail("output pipe should be painted red, got " + out.get(BCTransportItems.PIPE_COLOUR.get()));
            return;
        }
        if (out.getCount() != 1) {
            helper.fail("expected 1 pipe out, got " + out.getCount());
            return;
        }
        NonNullList<ItemStack> remaining = RECIPE.getRemainingItems(input);
        if (usesOf(remaining.get(1)) != 63) {
            helper.fail("brush should drop from 64 to 63 uses (one per pipe), got " + usesOf(remaining.get(1)));
            return;
        }
        helper.succeed();
    }

    public static void testCountMatchesPipeSlots(GameTestHelper helper) {
        ItemStack p1 = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack p2 = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack p3 = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack brush = chargedBrush(DyeColor.GREEN, 64);
        CraftingInput input = CraftingInput.of(2, 2, List.of(p1, p2, p3, brush));
        //? if >=26.1 {
        ItemStack out = RECIPE.assemble(input);
        //?} else {
        /*ItemStack out = RECIPE.assemble(input, helper.getLevel().registryAccess());*/
        //?}
        if (out.getCount() != 3) {
            helper.fail("expected 3 painted pipes (one per pipe slot), got " + out.getCount());
            return;
        }
        if (out.get(BCTransportItems.PIPE_COLOUR.get()) != DyeColor.GREEN) {
            helper.fail("expected green pipes");
            return;
        }
        NonNullList<ItemStack> remaining = RECIPE.getRemainingItems(input);
        if (usesOf(remaining.get(3)) != 61) {
            helper.fail("brush should drop from 64 to 61 uses (one per pipe), got " + usesOf(remaining.get(3)));
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
        //? if >=26.1 {
        ItemStack out = RECIPE.assemble(input);
        //?} else {
        /*ItemStack out = RECIPE.assemble(input, helper.getLevel().registryAccess());*/
        //?}
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
        ItemStack brush = chargedBrush(DyeColor.RED, 64);
        CraftingInput mixed = CraftingInput.of(3, 1, List.of(cobble, stone, brush));
        if (RECIPE.matches(mixed, helper.getLevel())) {
            helper.fail("paint recipe must NOT match mixed pipe types");
            return;
        }
        CraftingInput noModifier = CraftingInput.of(1, 1, List.of(new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get())));
        if (RECIPE.matches(noModifier, helper.getLevel())) {
            helper.fail("paint recipe must NOT match a lone pipe with no brush/water");
            return;
        }
        helper.succeed();
    }

    public static void testRejectsCleanBrush(GameTestHelper helper) {
        ItemStack pipe = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack cleanBrush = new ItemStack(BCCoreItems.PAINTBRUSH.get());
        CraftingInput input = CraftingInput.of(2, 1, List.of(pipe, cleanBrush));
        if (RECIPE.matches(input, helper.getLevel())) {
            helper.fail("paint recipe must NOT match an uncharged (clean) brush");
            return;
        }
        helper.succeed();
    }

    public static void testRejectsUnderchargedBrush(GameTestHelper helper) {
        ItemStack p1 = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack p2 = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        ItemStack p3 = new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get());
        // 3 pipes need 3 uses; a 2-use brush is insufficient.
        CraftingInput tooFew = CraftingInput.of(2, 2, List.of(p1, p2, p3, chargedBrush(DyeColor.RED, 2)));
        if (RECIPE.matches(tooFew, helper.getLevel())) {
            helper.fail("paint recipe must NOT match when the brush has fewer uses than pipes");
            return;
        }
        // Exactly enough (3 uses for 3 pipes) must match.
        CraftingInput exact = CraftingInput.of(2, 2,
                List.of(p1.copy(), p2.copy(), p3.copy(), chargedBrush(DyeColor.RED, 3)));
        if (!RECIPE.matches(exact, helper.getLevel())) {
            helper.fail("paint recipe should match when brush uses exactly cover the pipes");
            return;
        }
        helper.succeed();
    }
}
