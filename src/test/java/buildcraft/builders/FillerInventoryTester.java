package buildcraft.builders;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;

import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Tests that the Filler inventory only accepts items that can be placed as blocks.
 * <p>
 * In 1.12.2, the Filler used ItemBlocks.getList() to filter its inventory,
 * preventing non-block items (swords, sticks, etc.) from being inserted.
 * This test verifies that the StackInsertionChecker on TileFiller.invResources
 * correctly rejects non-BlockItem items.
 */
public class FillerInventoryTester {

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + msg);
        }
    }

    private static void assertFalse(boolean condition, String msg) {
        if (condition) {
            throw new IllegalStateException("Assertion failed (expected false): " + msg);
        }
    }

    /**
     * Tests that TileFiller's invResources only accepts block items.
     * We instantiate the filler and test its inventory handler directly.
     */
    public static void testFillerBlockItemFilter(GameTestHelper helper) {
        // Place a filler to get a real TileFiller instance with its configured checker
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.FILLER.get());

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            var filler = helper.getBlockEntity(pos, buildcraft.builders.tile.TileFiller.class);
            //?} else {
            /*buildcraft.builders.tile.TileFiller filler = helper.getBlockEntity(pos);*/
            //?}
            if (filler == null) {
                throw new IllegalStateException("Expected TileFiller at " + pos);
            }

            ItemHandlerSimple inv = filler.invResources;

            // Block items SHOULD be accepted
            assertTrue(inv.canSet(0, new ItemStack(Items.COBBLESTONE)),
                "Cobblestone (BlockItem) should be accepted by filler");
            assertTrue(inv.canSet(0, new ItemStack(Items.DIRT)),
                "Dirt (BlockItem) should be accepted by filler");
            assertTrue(inv.canSet(0, new ItemStack(Items.STONE_BRICKS)),
                "Stone Bricks (BlockItem) should be accepted by filler");
            assertTrue(inv.canSet(0, new ItemStack(Items.OAK_PLANKS)),
                "Oak Planks (BlockItem) should be accepted by filler");
            assertTrue(inv.canSet(0, new ItemStack(Items.GLASS)),
                "Glass (BlockItem) should be accepted by filler");

            // Non-block items SHOULD be rejected
            assertFalse(inv.canSet(0, new ItemStack(Items.DIAMOND_SWORD)),
                "Diamond Sword should NOT be accepted by filler");
            assertFalse(inv.canSet(0, new ItemStack(Items.STICK)),
                "Stick should NOT be accepted by filler");
            assertFalse(inv.canSet(0, new ItemStack(Items.APPLE)),
                "Apple should NOT be accepted by filler");
            assertFalse(inv.canSet(0, new ItemStack(Items.IRON_INGOT)),
                "Iron Ingot should NOT be accepted by filler");
            assertFalse(inv.canSet(0, new ItemStack(Items.ARROW)),
                "Arrow should NOT be accepted by filler");
            assertFalse(inv.canSet(0, new ItemStack(Items.BOW)),
                "Bow should NOT be accepted by filler");

            // Also verify via SlotBase.mayPlace (which is what the GUI checks)
            SlotBase slot = new SlotBase(inv, 0, 0, 0);
            assertTrue(slot.mayPlace(new ItemStack(Items.COBBLESTONE)),
                "SlotBase.mayPlace should accept Cobblestone");
            assertFalse(slot.mayPlace(new ItemStack(Items.DIAMOND_SWORD)),
                "SlotBase.mayPlace should reject Diamond Sword");

            // Verify that actual insertion is blocked for non-block items
            ItemStack leftover = inv.insertItem(0, new ItemStack(Items.STICK, 16), false);
            assertTrue(leftover.getCount() == 16,
                "Inserting sticks should return all 16 as leftover (nothing inserted)");
            assertTrue(inv.getStackInSlot(0).isEmpty(),
                "Slot 0 should remain empty after rejecting sticks");

            // Verify actual insertion works for block items
            ItemStack blockLeftover = inv.insertItem(0, new ItemStack(Items.COBBLESTONE, 32), false);
            assertTrue(blockLeftover.isEmpty(),
                "Inserting cobblestone should succeed with no leftover");
            assertTrue(inv.getStackInSlot(0).getCount() == 32,
                "Slot 0 should contain 32 cobblestone after insertion");

            helper.succeed();
        });
    }
}
