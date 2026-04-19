package buildcraft.lib.gui;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Game test for SlotBase.getMaxStackSize regression.
 * <p>
 * The bug: getMaxStackSize(ItemStack) was performing a simulated insert and
 * returning the remaining insertion capacity instead of the total slot capacity.
 * Vanilla uses this value as the ceiling when deciding if a slot can accept more
 * items, so returning "4 more can fit" when 60 items are present made vanilla
 * think the slot can only hold 4 total, blocking all GUI stack top-ups.
 */
public class SlotBaseTester {

    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new IllegalStateException(msg + " — expected " + expected + " but got " + actual);
        }
    }

    public static void testSlotMaxStackSize(GameTestHelper helper) {
        ItemHandlerSimple handler = new ItemHandlerSimple(3);

        // Slot 0: empty
        SlotBase emptySlot = new SlotBase(handler, 0, 0, 0);
        assertEquals(64, emptySlot.getMaxStackSize(new ItemStack(Items.COBBLESTONE, 1)),
            "Empty slot should report full item stack capacity");

        // Slot 1: partially filled with 60 cobblestone
        handler.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 60));
        SlotBase partialSlot = new SlotBase(handler, 1, 0, 0);
        assertEquals(64, partialSlot.getMaxStackSize(new ItemStack(Items.COBBLESTONE, 1)),
            "Partially filled slot must still report 64 total capacity, not remaining space");

        // Slot 2: empty, but test with ender pearls (max 16)
        SlotBase pearlSlot = new SlotBase(handler, 2, 0, 0);
        assertEquals(16, pearlSlot.getMaxStackSize(new ItemStack(Items.ENDER_PEARL, 1)),
            "Should respect item's intrinsic max stack size of 16");

        helper.succeed();
    }
}
