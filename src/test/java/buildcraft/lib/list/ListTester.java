package buildcraft.lib.list;



import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.lists.ListMatchHandler.Type;
import buildcraft.lib.list.ListMatchHandlerTools;

public class ListTester {
    private static void assertTrue(boolean val) {
        if (!val) throw new IllegalStateException("Assertion failed!");
    }

    private static void assertFalse(boolean val) {
        if (val) throw new IllegalStateException("Assertion failed!");
    }
    
    public static void testTools(GameTestHelper helper) {
        ListMatchHandlerTools matcher = new ListMatchHandlerTools();
        ItemStack woodenAxe = new ItemStack(Items.WOODEN_AXE);
        ItemStack ironAxe = new ItemStack(Items.IRON_AXE);
        ItemStack woodenShovel = new ItemStack(Items.WOODEN_SHOVEL);
        ItemStack woodenAxeDamaged = new ItemStack(Items.WOODEN_AXE);
        woodenAxeDamaged.setDamageValue(26);
        ItemStack apple = new ItemStack(Items.APPLE);

        assertTrue(matcher.isValidSource(Type.TYPE, woodenAxe));
        assertTrue(matcher.isValidSource(Type.TYPE, woodenAxeDamaged));
        assertFalse(matcher.isValidSource(Type.TYPE, apple));

        assertTrue(matcher.matches(Type.TYPE, woodenAxe, ironAxe, false));
        assertTrue(matcher.matches(Type.TYPE, woodenAxe, woodenAxeDamaged, false));
        assertFalse(matcher.matches(Type.TYPE, woodenAxe, woodenShovel, false));
        assertFalse(matcher.matches(Type.TYPE, woodenAxe, apple, false));

        helper.succeed();
    }
}
