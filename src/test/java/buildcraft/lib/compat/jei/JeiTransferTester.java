package buildcraft.lib.compat.jei;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * Exercises {@link JeiTransferUtil}, the server-side core behind the JEI "+" recipe-transfer
 * handlers for the Assembly Table / Distiller / Heat Exchanger. The JEI client UI isn't
 * headless-testable, but the move logic — what actually shuffles items between the player and the
 * machine — is, and that's where the bugs would bite (over-moving, duping, debiting wrongly).
 */
public class JeiTransferTester {

    /** Moving items debits the player by exactly the moved amount and credits the destination. */
    public static void testTransferMovesItems(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Items.REDSTONE, 8));

        ItemHandlerSimple dest = new ItemHandlerSimple(12);
        int moved = JeiTransferUtil.moveMatchingToHandler(inv, new ItemStack(Items.REDSTONE, 4), 4, dest);

        if (moved != 4) {
            throw new IllegalStateException("Expected to move 4 redstone, moved " + moved);
        }
        if (countHandler(dest, Items.REDSTONE) != 4) {
            throw new IllegalStateException("Destination should hold 4 redstone, has " + countHandler(dest, Items.REDSTONE));
        }
        if (JeiTransferUtil.countMatching(inv, new ItemStack(Items.REDSTONE)) != 4) {
            throw new IllegalStateException("Player should retain 4 redstone after the move");
        }
        helper.succeed();
    }

    /** Moving a bucket lands one in the single-bucket slot and removes it from the player. */
    public static void testTransferMovesBucket(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Items.WATER_BUCKET, 1));

        ItemHandlerSimple slots = new ItemHandlerSimple(3, 1);
        boolean ok = JeiTransferUtil.moveBucketToSlot(inv, Items.WATER_BUCKET, slots, 0);

        if (!ok) {
            throw new IllegalStateException("Bucket transfer should have succeeded");
        }
        if (!slots.getStackInSlot(0).is(Items.WATER_BUCKET)) {
            throw new IllegalStateException("Slot 0 should hold the water bucket");
        }
        if (JeiTransferUtil.countMatching(inv, new ItemStack(Items.WATER_BUCKET)) != 0) {
            throw new IllegalStateException("Player should have no water bucket left after the move");
        }
        helper.succeed();
    }

    /** With nothing to move, both helpers no-op (no items materialise, button would grey out). */
    public static void testTransferSkipsMissing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inv = player.getInventory(); // empty

        ItemHandlerSimple dest = new ItemHandlerSimple(12);
        int moved = JeiTransferUtil.moveMatchingToHandler(inv, new ItemStack(Items.DIAMOND, 2), 2, dest);
        if (moved != 0) {
            throw new IllegalStateException("Nothing should move when the player lacks the item, moved " + moved);
        }
        if (countHandler(dest, Items.DIAMOND) != 0) {
            throw new IllegalStateException("Destination must stay empty when nothing was available");
        }
        if (JeiTransferUtil.moveBucketToSlot(inv, Items.LAVA_BUCKET, new ItemHandlerSimple(1, 1), 0)) {
            throw new IllegalStateException("Bucket transfer should fail when the player has none");
        }
        helper.succeed();
    }

    private static int countHandler(ItemHandlerSimple handler, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty() && s.is(item)) {
                total += s.getCount();
            }
        }
        return total;
    }
}
