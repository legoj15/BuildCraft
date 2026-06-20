package buildcraft.transport;

import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;

import buildcraft.transport.recipe.DyedPipeRecipe;

/** Game tests for {@link DyedPipeRecipe}: a symmetric pipe (stone + stained glass), an asymmetric pipe
 *  (diamond-wood, both mirror orientations), and rejection of colourless glass (which the base recipe
 *  owns). */
public class DyedPipeRecipeTester {
    //? if >=26.1 {
    private static final DyedPipeRecipe RECIPE = new DyedPipeRecipe();
    //?} else {
    /*private static final DyedPipeRecipe RECIPE =
            new DyedPipeRecipe(net.minecraft.world.item.crafting.CraftingBookCategory.MISC);*/
    //?}

    public static void testSymmetricStonePipe(GameTestHelper helper) {
        ItemStack stone = new ItemStack(Items.STONE);
        //? if >=26.2 {
        /*ItemStack glass = new ItemStack(Items.STAINED_GLASS.pick(DyeColor.RED));*/
        //?} else {
        ItemStack glass = new ItemStack(Items.RED_STAINED_GLASS);
        //?}
        CraftingInput input = CraftingInput.of(3, 1, List.of(stone, glass, stone.copy()));
        if (!RECIPE.matches(input, helper.getLevel())) {
            helper.fail("dyed-pipe recipe should match stone + red stained glass + stone");
            return;
        }
        //? if >=26.1 {
        ItemStack out = RECIPE.assemble(input);
        //?} else {
        /*ItemStack out = RECIPE.assemble(input, helper.getLevel().registryAccess());*/
        //?}
        if (out.getItem() != BCTransportItems.PIPE_STONE_ITEM.get()) {
            helper.fail("expected a stone pipe, got " + out);
            return;
        }
        if (out.getCount() != 8) {
            helper.fail("expected 8 pipes (base-recipe parity), got " + out.getCount());
            return;
        }
        if (out.get(BCTransportItems.PIPE_COLOUR.get()) != DyeColor.RED) {
            helper.fail("pipe should be painted red");
            return;
        }
        helper.succeed();
    }

    public static void testAsymmetricDiamondWoodBothOrientations(GameTestHelper helper) {
        ItemStack planks = new ItemStack(Items.OAK_PLANKS);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        //? if >=26.2 {
        /*ItemStack glass = new ItemStack(Items.STAINED_GLASS.pick(DyeColor.LIME));*/
        //?} else {
        ItemStack glass = new ItemStack(Items.LIME_STAINED_GLASS);
        //?}
        // planks | glass | diamond
        //? if >=26.1 {
        ItemStack a = RECIPE.assemble(CraftingInput.of(3, 1, List.of(planks, glass, diamond)));
        //?} else {
        /*ItemStack a = RECIPE.assemble(CraftingInput.of(3, 1, List.of(planks, glass, diamond)), helper.getLevel().registryAccess());*/
        //?}
        if (a.getItem() != BCTransportItems.PIPE_DIAMOND_WOOD_ITEM.get()
                || a.get(BCTransportItems.PIPE_COLOUR.get()) != DyeColor.LIME) {
            helper.fail("planks|glass|diamond should make a lime diamond-wood pipe, got " + a);
            return;
        }
        // mirror: diamond | glass | planks
        //? if >=26.1 {
        ItemStack b = RECIPE.assemble(CraftingInput.of(3, 1, List.of(diamond, glass.copy(), planks.copy())));
        //?} else {
        /*ItemStack b = RECIPE.assemble(CraftingInput.of(3, 1, List.of(diamond, glass.copy(), planks.copy())), helper.getLevel().registryAccess());*/
        //?}
        if (b.getItem() != BCTransportItems.PIPE_DIAMOND_WOOD_ITEM.get()) {
            helper.fail("mirrored diamond|glass|planks should also make a diamond-wood pipe, got " + b);
            return;
        }
        helper.succeed();
    }

    public static void testColourlessGlassRejected(GameTestHelper helper) {
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack clear = new ItemStack(Items.GLASS);
        CraftingInput input = CraftingInput.of(3, 1, List.of(stone, clear, stone.copy()));
        if (RECIPE.matches(input, helper.getLevel())) {
            helper.fail("dyed-pipe recipe must NOT match colourless glass (the base recipe owns that)");
            return;
        }
        helper.succeed();
    }
}
