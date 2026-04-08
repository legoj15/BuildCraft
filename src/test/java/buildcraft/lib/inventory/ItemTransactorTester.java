package buildcraft.lib.inventory;

import org.junit.jupiter.api.Assertions;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import net.neoforged.testframework.gametest.GameTest;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.annotation.TestHolder;

import buildcraft.api.inventory.IItemTransactor;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.lib.tile.item.StackInsertionFunction;

public class ItemTransactorTester {
    @GameTest
    @EmptyTemplate
    @TestHolder(description = "Testing transactor simple moving")
    public static void testSimpleMoving(ExtendedGameTestHelper helper) {
        IItemTransactor trans = new ItemHandlerSimple(2, null);

        Assertions.assertTrue(trans.extract(null, 1, 1, false).isEmpty());

        ItemStack insert = new ItemStack(Items.APPLE);
        ItemStack leftOver = trans.insert(insert.copy(), false, false);

        Assertions.assertTrue(leftOver.isEmpty());

        ItemStack extracted = trans.extract(null, 1, 1, false);

        Assertions.assertTrue(ItemStack.isSameItemSameComponents(insert, extracted));

        extracted = trans.extract(null, 1, 1, false);

        Assertions.assertTrue(extracted.isEmpty());
    }

    @GameTest
    @EmptyTemplate
    @TestHolder(description = "Testing transactor limited inventory")
    public static void testLimitedInventory(ExtendedGameTestHelper helper) {
        IItemTransactor limited = new ItemHandlerSimple(2, (i, s) -> true, StackInsertionFunction.getInsertionFunction(4), null);

        ItemStack toInsert = new ItemStack(Items.APPLE, 9);
        ItemStack toInsertCopy = toInsert.copy();
        ItemStack supposedLeftOver = new ItemStack(Items.APPLE);

        ItemStack actuallyLeftOver = limited.insert(toInsert, false, false);

        Assertions.assertTrue(ItemStack.isSameItemSameComponents(toInsert, toInsertCopy));
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(supposedLeftOver, actuallyLeftOver));
    }
}
