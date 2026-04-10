package buildcraft.lib.inventory;



import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import net.minecraft.gametest.framework.GameTestHelper;

import buildcraft.api.inventory.IItemTransactor;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.lib.tile.item.StackInsertionFunction;

public class ItemTransactorTester {
    private static void assertTrue(boolean val) {
        if (!val) throw new IllegalStateException("Assertion failed!");
    }

    private static void assertFalse(boolean val) {
        if (val) throw new IllegalStateException("Assertion failed!");
    }
    public static void testSimpleMoving(GameTestHelper helper) {
        IItemTransactor trans = new ItemHandlerSimple(2, null);

        System.out.println("Assertion 1: Is newly created trans empty?");
        assertTrue(trans.extract(null, 1, 1, false).isEmpty());

        ItemStack insert = new ItemStack(Items.APPLE);
        ItemStack leftOver = trans.insert(insert.copy(), false, false);

        System.out.println("Assertion 2: Is leftOver empty after insert?");
        assertTrue(leftOver.isEmpty());

        ItemStack extracted = trans.extract(null, 1, 1, false);

        System.out.println("Assertion 3: Extracted match inserted?");
        System.out.println("Insert Item: " + insert.getItem() + " Components: " + insert.getComponents());
        System.out.println("Extract Item: " + extracted.getItem() + " Components: " + extracted.getComponents());
        assertTrue(ItemStack.isSameItemSameComponents(insert, extracted));

        extracted = trans.extract(null, 1, 1, false);

        System.out.println("Assertion 4: Is empty after second extract?");
        assertTrue(extracted.isEmpty());
        helper.succeed();
    }

    public static void testLimitedInventory(GameTestHelper helper) {
        IItemTransactor limited = new ItemHandlerSimple(2, (i, s) -> true, StackInsertionFunction.getInsertionFunction(4), null);

        ItemStack toInsert = new ItemStack(Items.APPLE, 9);
        ItemStack toInsertCopy = toInsert.copy();
        ItemStack supposedLeftOver = new ItemStack(Items.APPLE);

        ItemStack actuallyLeftOver = limited.insert(toInsert, false, false);

        assertTrue(ItemStack.isSameItemSameComponents(toInsert, toInsertCopy));
        assertTrue(ItemStack.isSameItemSameComponents(supposedLeftOver, actuallyLeftOver));
        helper.succeed();
    }
}
