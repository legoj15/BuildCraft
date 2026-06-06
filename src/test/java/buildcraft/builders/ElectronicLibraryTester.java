package buildcraft.builders;

import java.util.Date;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.tile.TileElectronicLibrary;

/**
 * Verifies the Electronic Library tile entity:
 * - invDownIn slot filter (only used snapshots)
 * - invUpIn slot filter (any snapshot, used or clean)
 * - Download cycle: item transfers from invDownIn → invDownOut after 50 ticks
 * - Upload cycle: progressUp increments when selected + invUpIn item present
 */
public class ElectronicLibraryTester {

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + msg);
    }

    private static void assertFalse(boolean condition, String msg) {
        if (condition) throw new IllegalStateException("Assertion failed (expected false): " + msg);
    }

    private static Snapshot.Header fakeHeader() {
        Snapshot.Key key = new Snapshot.Key(new CompoundTag());
        return new Snapshot.Header(key, new UUID(0, 0), new Date(0), "TestSnapshot");
    }

    /**
     * Verifies slot filter rules:
     * - invDownIn accepts used blueprints/templates, rejects clean ones.
     * - invUpIn accepts all snapshot items.
     * (Output-only behaviour of invDownOut/invUpOut is an EnumAccess.EXTRACT concern, not a
     * canSet filter — those slots carry no filter, so canSet is intentionally accept-all.)
     */
    public static void testSlotFiltering(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
            //?} else {
            /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
            //?}
            if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);

            Snapshot.Header header = fakeHeader();
            ItemStack usedBlueprint = BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(header);
            ItemStack usedTemplate  = BCBuildersItems.TEMPLATE_USED.get().createUsedStack(header);
            ItemStack cleanBlueprint = new ItemStack(BCBuildersItems.BLUEPRINT_CLEAN.get());
            ItemStack cleanTemplate  = new ItemStack(BCBuildersItems.TEMPLATE_CLEAN.get());

            // invDownIn: only used snapshots
            assertTrue(tile.invDownIn.canSet(0, usedBlueprint),  "invDownIn must accept used blueprint");
            assertTrue(tile.invDownIn.canSet(0, usedTemplate),   "invDownIn must accept used template");
            assertFalse(tile.invDownIn.canSet(0, cleanBlueprint), "invDownIn must reject clean blueprint");
            assertFalse(tile.invDownIn.canSet(0, cleanTemplate),  "invDownIn must reject clean template");

            // invUpIn: any snapshot item
            assertTrue(tile.invUpIn.canSet(0, usedBlueprint),   "invUpIn must accept used blueprint");
            assertTrue(tile.invUpIn.canSet(0, usedTemplate),    "invUpIn must accept used template");
            assertTrue(tile.invUpIn.canSet(0, cleanBlueprint),  "invUpIn must accept clean blueprint");
            assertTrue(tile.invUpIn.canSet(0, cleanTemplate),   "invUpIn must accept clean template");

            helper.succeed();
        });
    }

    /**
     * Verifies the full download cycle: after 50 ticks with a used snapshot in invDownIn
     * and invDownOut empty, the item moves to invDownOut.
     */
    public static void testDownloadCycle(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
            //?} else {
            /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
            //?}
            if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);

            assertTrue(tile.progressDown == -1, "progressDown should be idle (-1) initially");

            Snapshot.Header header = fakeHeader();
            ItemStack usedBlueprint = BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(header);
            tile.invDownIn.setStackInSlot(0, usedBlueprint);

            // Wait long enough for the 50-tick download cycle to complete
            helper.runAfterDelay(55, () -> {
                assertTrue(tile.invDownIn.getStackInSlot(0).isEmpty(),
                    "invDownIn should be empty after download completes");
                assertFalse(tile.invDownOut.getStackInSlot(0).isEmpty(),
                    "invDownOut should hold the item after download completes");
                assertTrue(tile.invDownOut.getStackInSlot(0).getItem() == BCBuildersItems.BLUEPRINT_USED.get(),
                    "invDownOut should contain the used blueprint");
                assertTrue(tile.progressDown == -1,
                    "progressDown should return to idle after download");
                helper.succeed();
            });
        });
    }

    /**
     * Verifies that progressUp increments each tick when selected key + invUpIn item are present,
     * and that the counter does not complete in fewer than 50 ticks.
     */
    public static void testUploadProgressIncrements(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
            //?} else {
            /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
            //?}
            if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);

            // Seed: selected key + clean blueprint in invUpIn (simulates player picked a snapshot
            // and inserted a blank item to write to)
            tile.selected = new Snapshot.Key(new CompoundTag());
            tile.invUpIn.setStackInSlot(0, new ItemStack(BCBuildersItems.BLUEPRINT_CLEAN.get()));

            helper.runAfterDelay(10, () -> {
                // After 10 ticks: progressUp should be somewhere between 1 and 49
                assertTrue(tile.progressUp > 0,
                    "progressUp should be > 0 after 10 ticks, got: " + tile.progressUp);
                assertTrue(tile.progressUp < 50,
                    "progressUp should not complete in just 10 ticks, got: " + tile.progressUp);
                helper.succeed();
            });
        });
    }

    /**
     * Verifies that progressDown stays at -1 when invDownIn is empty (idle condition).
     * And that progressDown resets to -1 when invDownIn becomes empty mid-cycle.
     */
    public static void testDownloadIdleWhenEmpty(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, BCBuildersBlocks.LIBRARY.get());

        helper.runAfterDelay(5, () -> {
            //? if >=1.21.10 {
            TileElectronicLibrary tile = helper.getBlockEntity(pos, TileElectronicLibrary.class);
            //?} else {
            /*TileElectronicLibrary tile = helper.getBlockEntity(pos);*/
            //?}
            if (tile == null) throw new IllegalStateException("Expected TileElectronicLibrary at " + pos);

            // Nothing in invDownIn: progress must stay idle
            assertTrue(tile.progressDown == -1,
                "progressDown should be -1 (idle) with empty invDownIn, got: " + tile.progressDown);
            assertTrue(tile.progressUp == -1,
                "progressUp should be -1 (idle) with no conditions met, got: " + tile.progressUp);

            // Seed, wait a bit, remove item, wait again — progress should reset
            Snapshot.Header header = fakeHeader();
            ItemStack usedBlueprint = BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(header);
            tile.invDownIn.setStackInSlot(0, usedBlueprint);

            helper.runAfterDelay(10, () -> {
                assertTrue(tile.progressDown > 0, "progressDown should have started after seeding");
                // Remove the item — progress should reset
                tile.invDownIn.setStackInSlot(0, ItemStack.EMPTY);
                helper.runAfterDelay(2, () -> {
                    assertTrue(tile.progressDown == -1,
                        "progressDown should reset to -1 after invDownIn cleared, got: " + tile.progressDown);
                    helper.succeed();
                });
            });
        });
    }
}
