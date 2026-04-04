package buildcraft.test.lib.list;

import org.junit.jupiter.api.Assertions;

import net.neoforged.testframework.gametest.GameTest;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.testframework.annotation.TestHolder;

import buildcraft.api.lists.ListMatchHandler.Type;
import buildcraft.lib.list.ListMatchHandlerTools;

public class ListTester {
    
    @GameTest
    @EmptyTemplate
    @TestHolder(description = "buildcraftunofficial")
    public static void testTools(ExtendedGameTestHelper helper) {
        ListMatchHandlerTools matcher = new ListMatchHandlerTools();
        ItemStack woodenAxe = new ItemStack(Items.WOODEN_AXE);
        ItemStack ironAxe = new ItemStack(Items.IRON_AXE);
        ItemStack woodenShovel = new ItemStack(Items.WOODEN_SHOVEL);
        ItemStack woodenAxeDamaged = new ItemStack(Items.WOODEN_AXE);
        woodenAxeDamaged.setDamageValue(26);
        ItemStack apple = new ItemStack(Items.APPLE);

        Assertions.assertTrue(matcher.isValidSource(Type.TYPE, woodenAxe));
        Assertions.assertTrue(matcher.isValidSource(Type.TYPE, woodenAxeDamaged));
        Assertions.assertFalse(matcher.isValidSource(Type.TYPE, apple));

        Assertions.assertTrue(matcher.matches(Type.TYPE, woodenAxe, ironAxe, false));
        Assertions.assertTrue(matcher.matches(Type.TYPE, woodenAxe, woodenAxeDamaged, false));
        Assertions.assertFalse(matcher.matches(Type.TYPE, woodenAxe, woodenShovel, false));
        Assertions.assertFalse(matcher.matches(Type.TYPE, woodenAxe, apple, false));

        helper.succeed();
    }
}
