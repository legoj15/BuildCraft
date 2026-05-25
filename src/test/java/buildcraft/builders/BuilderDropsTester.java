package buildcraft.builders;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.tile.TileArchitectTable;
import buildcraft.builders.tile.TileBuilder;

/**
 * Verifies that breaking the Builder with a survival-mode player holding a wooden pickaxe:
 *  - Drops the block item itself (via loot_table/block/builder.json, gated by
 *    requiresCorrectToolForDrops + minecraft:mineable/pickaxe).
 *  - Drops the snapshot-slot item (blueprint/template) via playerWillDestroy.
 *  - Drops every non-empty slot of the 27-slot resource grid.
 * <p>
 * Mirrors the 1.12.2 setup where {@code Material.IRON} blocks required a pickaxe to yield
 * drops (bare hand breaks the block but produces nothing). A wooden pickaxe is the minimum
 * tier that should work.
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

        // Seed a couple of resource slots at varying indices.
        tile.setResource(0, new ItemStack(Items.DIAMOND, 7));
        tile.setResource(13, new ItemStack(Items.IRON_INGOT, 42));
        tile.setResource(26, new ItemStack(Items.COBBLESTONE, 64));

        // Simulate a survival-mode player holding a wooden pickaxe breaking the block. Goes
        // through the full playerWillDestroy → removeBlock → playerDestroy flow that real
        // gameplay uses, so this actually exercises the tool-requirement gate that plain
        // level.destroyBlock skips.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(builderPos);
        BlockState state = level.getBlockState(absPos);
        BlockEntity be = level.getBlockEntity(absPos);
        state.getBlock().playerWillDestroy(level, absPos, state, player);
        level.removeBlock(absPos, false);
        state.getBlock().playerDestroy(level, player, absPos, state, be, player.getMainHandItem());

        helper.assertBlockPresent(Blocks.AIR, builderPos);

        helper.runAfterDelay(10, () -> {
            // Block item itself — proves the loot table fires with a pickaxe as the tool.
            helper.assertItemEntityPresent(BCBuildersItems.BUILDER.get(), builderPos, 2.0);
            // Snapshot item (popped by BlockBuilder.playerWillDestroy).
            helper.assertItemEntityPresent(BCBuildersItems.BLUEPRINT_USED.get(), builderPos, 2.0);
            // Resource slot contents.
            helper.assertItemEntityPresent(Items.DIAMOND, builderPos, 2.0);
            helper.assertItemEntityPresent(Items.IRON_INGOT, builderPos, 2.0);
            helper.assertItemEntityPresent(Items.COBBLESTONE, builderPos, 2.0);
            helper.succeed();
        });
    }

    /**
     * Companion test for the Architect Table: survival + wooden pickaxe must drop the block
     * itself plus the contents of both the snapshot-in and snapshot-out slots.
     */
    public static void testArchitectDropsContentsAndSelf(GameTestHelper helper) {
        BlockPos architectPos = new BlockPos(1, 2, 1);

        helper.setBlock(architectPos, BCBuildersBlocks.ARCHITECT.get());
        helper.assertBlockPresent(BCBuildersBlocks.ARCHITECT.get(), architectPos);

        TileArchitectTable tile = helper.getBlockEntity(architectPos, TileArchitectTable.class);
        tile.setSnapshotIn(new ItemStack(BCBuildersItems.BLUEPRINT_CLEAN.get()));
        tile.setSnapshotOut(new ItemStack(BCBuildersItems.BLUEPRINT_USED.get()));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(architectPos);
        BlockState state = level.getBlockState(absPos);
        BlockEntity be = level.getBlockEntity(absPos);
        state.getBlock().playerWillDestroy(level, absPos, state, player);
        level.removeBlock(absPos, false);
        state.getBlock().playerDestroy(level, player, absPos, state, be, player.getMainHandItem());

        helper.assertBlockPresent(Blocks.AIR, architectPos);

        helper.runAfterDelay(10, () -> {
            helper.assertItemEntityPresent(BCBuildersItems.ARCHITECT.get(), architectPos, 2.0);
            helper.assertItemEntityPresent(BCBuildersItems.BLUEPRINT_CLEAN.get(), architectPos, 2.0);
            helper.assertItemEntityPresent(BCBuildersItems.BLUEPRINT_USED.get(), architectPos, 2.0);
            helper.succeed();
        });
    }

    /**
     * Pins the Builder's consumed path-marker chain through the NBT round-trip the periodic BE
     * update packet uses ({@code saveCustomOnly → loadCustomOnly}). Without the path being
     * serialized, two things break silently: the client never sees the route and the path-laser
     * render is dead code; and the server itself loses the route on chunk reload, collapsing
     * {@link TileBuilder#updateBasePoses()} to the single-position fallback.
     */
    public static void testBuilderPathSurvivesNbtRoundTrip(GameTestHelper helper) {
        BlockPos builderPos = new BlockPos(1, 2, 1);
        helper.setBlock(builderPos, BCBuildersBlocks.BUILDER.get());
        TileBuilder tile = helper.getBlockEntity(builderPos, TileBuilder.class);

        helper.assertTrue(tile.path == null, "fresh builder must have null path");

        // Inject a synthetic 3-point path (same shape onPlacedBy stamps after consuming a
        // path-marker chain). Absolute world positions so we don't accidentally depend on the
        // structure-relative offset getting applied.
        BlockPos p1 = new BlockPos(10, 64, 20);
        BlockPos p2 = new BlockPos(15, 64, 20);
        BlockPos p3 = new BlockPos(20, 64, 25);
        tile.path = ImmutableList.of(p1, p2, p3);

        ServerLevel level = helper.getLevel();
        CompoundTag tag = tile.saveCustomOnly(level.registryAccess());
        tile.path = null;  // clear to prove load is what restores it
        tile.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));

        helper.assertTrue(tile.path != null, "path must survive the NBT round-trip");
        helper.assertTrue(tile.path.size() == 3,
                "path must keep all 3 positions — got " + (tile.path == null ? "null" : tile.path.size()));
        helper.assertTrue(tile.path.get(0).equals(p1), "path[0] must equal " + p1 + " — got " + tile.path.get(0));
        helper.assertTrue(tile.path.get(1).equals(p2), "path[1] must equal " + p2 + " — got " + tile.path.get(1));
        helper.assertTrue(tile.path.get(2).equals(p3), "path[2] must equal " + p3 + " — got " + tile.path.get(2));

        // Round-trip with no path must leave path null (not coerce to an empty list — that would
        // make updateBasePoses() crash on path.get(0) the next time it runs).
        tile.path = null;
        CompoundTag emptyTag = tile.saveCustomOnly(level.registryAccess());
        tile.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), emptyTag));
        helper.assertTrue(tile.path == null, "round-tripping with no path must leave path null");

        helper.succeed();
    }
}
