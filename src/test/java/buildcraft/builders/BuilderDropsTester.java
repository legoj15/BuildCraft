package buildcraft.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.tile.TileBuilder;

/**
 * Verifies that breaking the Builder:
 *  - Drops the block item itself (via loot_table/block/builder.json).
 *  - Drops the snapshot-slot item (blueprint/template).
 *  - Drops every non-empty slot of the 27-slot resource grid.
 *  Mirrors the 1.12.2 behaviour where {@code BlockBCTile_Neptune.breakBlock} delegated to the
 *  tile's own {@code onRemove} which iterated the resource inventory + snapshot slot.
 */
public class BuilderDropsTester {

    public static void testBuilderDropsContentsAndSelf(GameTestHelper helper) {
        BlockPos builderPos = new BlockPos(1, 2, 1);

        helper.setBlock(builderPos, BCBuildersBlocks.BUILDER.get());
        helper.assertBlockPresent(BCBuildersBlocks.BUILDER.get(), builderPos);

        TileBuilder tile = helper.getBlockEntity(builderPos, TileBuilder.class);

        // Seed the snapshot slot with a used blueprint item — the snapshot-loading logic will
        // no-op because there's no GlobalSavedDataSnapshots entry for it, but the item itself
        // must still drop when the block is broken.
        ItemStack snapshot = new ItemStack(BCBuildersItems.BLUEPRINT_USED.get());
        tile.setSnapshot(snapshot);

        // Seed a couple of resource slots.
        tile.setResource(0, new ItemStack(Items.DIAMOND, 7));
        tile.setResource(13, new ItemStack(Items.IRON_INGOT, 42));
        tile.setResource(26, new ItemStack(Items.COBBLESTONE, 64));

        helper.destroyBlock(builderPos);
        helper.assertBlockPresent(Blocks.AIR, builderPos);

        helper.runAfterDelay(10, () -> {
            // Block item itself — proves the loot table is in place.
            helper.assertItemEntityPresent(BCBuildersItems.BUILDER.get(), builderPos, 2.0);
            // Snapshot item.
            helper.assertItemEntityPresent(BCBuildersItems.BLUEPRINT_USED.get(), builderPos, 2.0);
            // Resource slot contents.
            helper.assertItemEntityPresent(Items.DIAMOND, builderPos, 2.0);
            helper.assertItemEntityPresent(Items.IRON_INGOT, builderPos, 2.0);
            helper.assertItemEntityPresent(Items.COBBLESTONE, builderPos, 2.0);
            helper.succeed();
        });
    }
}
