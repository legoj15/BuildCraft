/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

/**
 * Diagnostic for the chest-content dupe report: when the Architect captures a chest with items
 * in it, are those items included in the Builder's "required items" list?
 *
 * <p>If yes → the user pays for the items at build time but the chest is also placed full
 * (because {@code tileNbt} is restored as-is in {@link SchematicBlockDefault#build}) — items
 * appear in inventory AND in the placed chest, which is the dupe.
 *
 * <p>If no → the {@code items_list} JSON extractor in {@code blocks_simple_inventories.json} is
 * silently broken under 26.1 (likely the post-1.20.5 ItemStack codec change), so the user gets
 * the chest contents for free even from the inventory perspective.
 *
 * <p>Either way the fix is the contents-mode toggle, but the diagnosis tells us whether
 * INCLUDE mode is a no-op or also needs an extractor-side repair.
 */
public class ContainerContentsScanTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    private static int countMatching(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack s : stacks) {
            if (s.is(item)) total += s.getCount();
        }
        return total;
    }

    private static String renderList(List<ItemStack> stacks) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) sb.append(", ");
            ItemStack s = stacks.get(i);
            sb.append(s.getCount()).append("×")
              .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()));
        }
        return sb.append("]").toString();
    }

    /**
     * Place a chest, populate it with known items, run a schematic scan, and verify
     * computeRequiredItems() includes the contents.
     */
    public static void testChestContentsListedAsRequiredItems(GameTestHelper helper) {
        try {
            ServerLevel level = helper.getLevel();
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);

            level.setBlock(abs, Blocks.CHEST.defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(abs);
            assertTrue(be instanceof Container,
                    "chest BE should implement Container; got " + (be == null ? "null" : be.getClass()));
            Container chest = (Container) be;
            chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
            chest.setItem(3, new ItemStack(Items.GOLD_INGOT, 3));
            be.setChanged();

            // Capture exactly what the architect would see — the same saveWithoutMetadata call
            // used by SchematicBlockDefault.setTileNbt.
            net.minecraft.nbt.CompoundTag tileNbt = be.saveWithoutMetadata(level.registryAccess());
            assertTrue(tileNbt.contains("Items"),
                    "expected Items tag on chest tileNbt; got keys: " + tileNbt.keySet());

            BlockState chestState = level.getBlockState(abs);
            SchematicBlockContext context = new SchematicBlockContext(
                    level, BlockPos.ZERO, abs, chestState, chestState.getBlock());
            ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(context);
            assertTrue(schematic instanceof SchematicBlockDefault,
                    "expected SchematicBlockDefault for vanilla chest; got " + schematic.getClass());

            List<ItemStack> required = schematic.computeRequiredItems();
            int diamonds = countMatching(required, Items.DIAMOND);
            int gold = countMatching(required, Items.GOLD_INGOT);
            int chestItems = countMatching(required, Items.CHEST);

            assertTrue(chestItems == 1,
                    "expected 1 chest item in required; got " + chestItems + " — full list: " + renderList(required));
            assertTrue(diamonds == 5,
                    "expected 5 diamonds in required (from chest contents); got " + diamonds
                            + " — full list: " + renderList(required));
            assertTrue(gold == 3,
                    "expected 3 gold ingots in required (from chest contents); got " + gold
                            + " — full list: " + renderList(required));

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Place a chest, scan it, then call {@code build()} at a fresh position and verify the
     * placed chest holds the original items. Confirms the "items appear in placed chest" half of
     * the dupe — the player both paid for the items (via the required-items list) AND receives
     * them back (in the chest's restored inventory). Net effect: each chest content item is
     * duplicated.
     */
    public static void testChestBuiltWithContentsFromSchematic(GameTestHelper helper) {
        try {
            ServerLevel level = helper.getLevel();

            // Source chest with known contents.
            BlockPos srcLocal = new BlockPos(1, 2, 1);
            BlockPos src = helper.absolutePos(srcLocal);
            level.setBlock(src, Blocks.CHEST.defaultBlockState(), 3);
            BlockEntity srcBe = level.getBlockEntity(src);
            assertTrue(srcBe instanceof Container, "source chest should be a Container");
            Container srcChest = (Container) srcBe;
            srcChest.setItem(0, new ItemStack(Items.DIAMOND, 5));
            srcChest.setItem(3, new ItemStack(Items.GOLD_INGOT, 3));
            srcBe.setChanged();

            BlockState srcState = level.getBlockState(src);
            SchematicBlockContext context = new SchematicBlockContext(
                    level, BlockPos.ZERO, src, srcState, srcState.getBlock());
            ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(context);

            // Build at a clean target position.
            BlockPos dstLocal = new BlockPos(3, 2, 1);
            BlockPos dst = helper.absolutePos(dstLocal);
            assertTrue(level.isEmptyBlock(dst), "target should be empty pre-build");
            boolean placed = schematic.build(level, dst);
            assertTrue(placed, "build() should report success");

            BlockEntity dstBe = level.getBlockEntity(dst);
            assertTrue(dstBe instanceof Container,
                    "target should hold a Container after build; got " + (dstBe == null ? "null" : dstBe.getClass()));
            Container dstChest = (Container) dstBe;

            ItemStack slot0 = dstChest.getItem(0);
            ItemStack slot3 = dstChest.getItem(3);
            assertTrue(slot0.is(Items.DIAMOND) && slot0.getCount() == 5,
                    "expected 5 diamonds at slot 0 of built chest; got " + slot0.getCount() + "× "
                            + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slot0.getItem()));
            assertTrue(slot3.is(Items.GOLD_INGOT) && slot3.getCount() == 3,
                    "expected 3 gold at slot 3 of built chest; got " + slot3.getCount() + "× "
                            + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slot3.getItem()));

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * IGNORE mode contract — part 1: computeRequiredItems(false) drops the chest's captured
     * inventory from the required-items list but keeps the chest item itself. Locks in the
     * filter applied via {@code RequiredExtractorItemsList} instanceof check.
     */
    public static void testIgnoreModeOmitsContentsFromRequiredItems(GameTestHelper helper) {
        try {
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 1));
            level.setBlock(abs, Blocks.CHEST.defaultBlockState(), 3);
            Container chest = (Container) level.getBlockEntity(abs);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
            chest.setItem(3, new ItemStack(Items.GOLD_INGOT, 3));
            ((BlockEntity) chest).setChanged();

            BlockState chestState = level.getBlockState(abs);
            ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                    new SchematicBlockContext(level, BlockPos.ZERO, abs, chestState, chestState.getBlock()));
            assertTrue(schematic instanceof SchematicBlockDefault,
                    "expected SchematicBlockDefault; got " + schematic.getClass());

            List<ItemStack> required = ((SchematicBlockDefault) schematic).computeRequiredItems(false);
            int chestCount = countMatching(required, Items.CHEST);
            int diamondCount = countMatching(required, Items.DIAMOND);
            int goldCount = countMatching(required, Items.GOLD_INGOT);

            assertTrue(chestCount == 1,
                    "IGNORE: chest item should still be required; got " + chestCount
                            + " — list: " + renderList(required));
            assertTrue(diamondCount == 0,
                    "IGNORE: diamonds (chest contents) should NOT be required; got " + diamondCount
                            + " — list: " + renderList(required));
            assertTrue(goldCount == 0,
                    "IGNORE: gold ingots (chest contents) should NOT be required; got " + goldCount
                            + " — list: " + renderList(required));

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * IGNORE mode contract — part 2: build(..., includeContents=false) places the chest with an
     * empty inventory, even though tileNbt carries the captured contents. Locks in the
     * stripContainerContentsFromNbt step.
     */
    public static void testIgnoreModeBuildsEmptyChest(GameTestHelper helper) {
        try {
            ServerLevel level = helper.getLevel();
            BlockPos src = helper.absolutePos(new BlockPos(1, 2, 1));
            level.setBlock(src, Blocks.CHEST.defaultBlockState(), 3);
            Container srcChest = (Container) level.getBlockEntity(src);
            srcChest.setItem(0, new ItemStack(Items.DIAMOND, 5));
            srcChest.setItem(3, new ItemStack(Items.GOLD_INGOT, 3));
            ((BlockEntity) srcChest).setChanged();

            BlockState chestState = level.getBlockState(src);
            ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                    new SchematicBlockContext(level, BlockPos.ZERO, src, chestState, chestState.getBlock()));
            assertTrue(schematic instanceof SchematicBlockDefault,
                    "expected SchematicBlockDefault; got " + schematic.getClass());

            BlockPos dst = helper.absolutePos(new BlockPos(3, 2, 1));
            boolean placed = ((SchematicBlockDefault) schematic).build(
                    level, dst, EnumFluidHandlingMode.NO_REPLACE, false);
            assertTrue(placed, "build() should succeed");

            Container dstChest = (Container) level.getBlockEntity(dst);
            assertTrue(dstChest != null, "target should hold a Container post-build");
            for (int i = 0; i < dstChest.getContainerSize(); i++) {
                ItemStack inSlot = dstChest.getItem(i);
                assertTrue(inSlot.isEmpty(),
                        "IGNORE: placed chest should be empty; found slot " + i + " holding "
                                + inSlot.getCount() + "× "
                                + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(inSlot.getItem()));
            }

            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
