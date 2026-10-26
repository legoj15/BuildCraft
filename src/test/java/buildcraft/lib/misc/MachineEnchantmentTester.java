/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.core.BCCoreConfig;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.BCFactoryItems;
import buildcraft.lib.test.DropArenaUtil;
import buildcraft.lib.test.DropArenaUtil.Drops;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.MachineEnchantTestSupport;

/**
 * Pins the enchantable Quarry and Mining Well around the machines themselves: what the anvil lets onto their items,
 * what the machine does and does not make of the enchantments it carries, what it drops when broken, and what a
 * creative pick of it copies.
 *
 * <p>The block entity carries its enchantments as vanilla leaves them when it places an item: in its component map.
 * Most tests put them there with {@code applyComponentsFromItemStack}, the call placing makes; the break tests place
 * the enchanted item the real way ({@link #placeOnTopOf}).
 */
public class MachineEnchantmentTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    private static ItemStack book(GameTestHelper gth, ResourceKey<Enchantment> key, int level) {
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(MachineEnchantTestSupport.enchantment(gth, key), level);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return book;
    }

    /** What a survival player gets out of an anvil holding {@code machine} and a book of {@code key} at
     *  {@code level}: the enchantment the machine ends up with, 0 when the anvil gives no result. */
    private static int anvilLevel(GameTestHelper gth, Item machine, ResourceKey<Enchantment> key, int level) {
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(gth.getLevel(), null,
                gth.absolutePos(new BlockPos(2, 2, 2)))) {
            FakePlayer player = lease.player();
            player.setGameMode(GameType.SURVIVAL);
            AnvilMenu anvil = new AnvilMenu(0, player.getInventory());
            anvil.getSlot(0).set(new ItemStack(machine));
            anvil.getSlot(1).set(book(gth, key, level));
            ItemStack result = anvil.getSlot(2).getItem();
            return result.isEmpty() ? 0
                    : result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                            .getLevel(MachineEnchantTestSupport.enchantment(gth, key));
        }
    }

    /** The anvil puts Silk Touch, Fortune and Efficiency on the Quarry and the Mining Well, and refuses Unbreaking,
     *  Mending and Sharpness; with {@code machineEnchantments} off it refuses all of them. */
    public static void testAnvilTakesOnlyMiningEnchantments(GameTestHelper helper) {
        boolean previous = BCCoreConfig.machineEnchantments.get();
        try {
            for (Item machine : List.of(BCBuildersItems.QUARRY.get(), BCFactoryItems.MINING_WELL.get())) {
                BCCoreConfig.machineEnchantments.set(true);
                assertTrue(anvilLevel(helper, machine, Enchantments.SILK_TOUCH, 1) == 1,
                        machine + ": the anvil must take Silk Touch");
                assertTrue(anvilLevel(helper, machine, Enchantments.FORTUNE, 3) == 3,
                        machine + ": the anvil must take Fortune III");
                assertTrue(anvilLevel(helper, machine, Enchantments.EFFICIENCY, 5) == 5,
                        machine + ": the anvil must take Efficiency V");
                assertTrue(anvilLevel(helper, machine, Enchantments.UNBREAKING, 3) == 0,
                        machine + ": the anvil must refuse Unbreaking");
                assertTrue(anvilLevel(helper, machine, Enchantments.MENDING, 1) == 0,
                        machine + ": the anvil must refuse Mending");
                assertTrue(anvilLevel(helper, machine, Enchantments.SHARPNESS, 5) == 0,
                        machine + ": the anvil must refuse Sharpness");

                BCCoreConfig.machineEnchantments.set(false);
                assertTrue(anvilLevel(helper, machine, Enchantments.SILK_TOUCH, 1) == 0,
                        machine + ": with the option off the anvil must refuse Silk Touch");
                assertTrue(anvilLevel(helper, machine, Enchantments.EFFICIENCY, 5) == 0,
                        machine + ": with the option off the anvil must refuse Efficiency");
            }
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            BCCoreConfig.machineEnchantments.set(previous);
        }
    }

    /** A machine can carry more than the anvil allows (a creative anvil takes anything): only Silk Touch, Fortune and
     *  Efficiency are used, the rest is neither used nor lost, and the pretend tool carries just what is used. */
    public static void testOnlyMiningEnchantmentsAreUsed(GameTestHelper helper) {
        try {
            BlockPos pos = new BlockPos(2, 2, 2);
            helper.setBlock(pos, BCFactoryBlocks.MINING_WELL.get());
            BlockEntity well = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            assertTrue(well != null, "mining well block-entity must be present");

            ItemStack item = MachineEnchantTestSupport.enchantedItem(helper, BCFactoryItems.MINING_WELL.get(),
                    Enchantments.SILK_TOUCH, 1);
            item.enchant(MachineEnchantTestSupport.enchantment(helper, Enchantments.EFFICIENCY), 2);
            item.enchant(MachineEnchantTestSupport.enchantment(helper, Enchantments.SHARPNESS), 5);
            well.applyComponentsFromItemStack(item);

            ItemEnchantments used = MachineEnchantments.of(well);
            assertTrue(used.size() == 2, "the well must use exactly Silk Touch and Efficiency, got " + used);
            Holder<Enchantment> silk = MachineEnchantTestSupport.enchantment(helper, Enchantments.SILK_TOUCH);
            assertTrue(used.getLevel(silk) == 1, "Silk Touch must be used at its level, got " + used);
            assertTrue(MachineEnchantments.efficiency(well) == 2, "the well must work at Efficiency II");

            ItemStack tool = MachineEnchantments.tool(Items.IRON_PICKAXE, well);
            assertTrue(tool.is(Items.IRON_PICKAXE), "the pretend tool must stay the machine's tier");
            assertTrue(tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size() == 2,
                    "the pretend tool must carry only what the well uses");

            assertTrue(well.components().get(DataComponents.ENCHANTMENTS).size() == 3,
                    "the well must still carry all three enchantments");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Places {@code stack} on top of the block at {@code floor} as a survival player right-clicking it does
     * ({@code ItemStack#useOn}), so the block entity gets its components from vanilla's own placement path rather than
     * from the test. The stack is consumed.
     */
    private static void placeOnTopOf(GameTestHelper gth, ItemStack stack, BlockPos floor) {
        BlockPos floorAbs = gth.absolutePos(floor);
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(gth.getLevel(), null, floorAbs.above(64))) {
            FakePlayer player = lease.player();
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floorAbs).add(0, 0.5, 0), Direction.UP, floorAbs,
                    false);
            stack.useOn(new UseOnContext(gth.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
        }
    }

    /**
     * A machine placed from an enchanted item and then broken drops its own item with the enchantments (and the repair
     * cost the anvil set) it was placed with, so the shine and the effect survive a trip through the inventory. This
     * is vanilla's placement copying the item's components onto the block entity, then the machine's loot table
     * copying them back onto the drop, on every node's loot-table format.
     */
    private static void placedMachineKeepsEnchantmentsWhenBroken(GameTestHelper helper, Block block, Item item) {
        BlockPos floor = new BlockPos(2, 2, 2);
        BlockPos pos = floor.above();
        DropArenaUtil.awaitDrops(helper, () -> {
            helper.setBlock(floor, Blocks.STONE);
            ItemStack held = MachineEnchantTestSupport.enchantedItem(helper, item, Enchantments.SILK_TOUCH, 1);
            held.enchant(MachineEnchantTestSupport.enchantment(helper, Enchantments.EFFICIENCY), 3);
            held.set(DataComponents.REPAIR_COST, 7);
            placeOnTopOf(helper, held, floor);

            assertTrue(helper.getLevel().getBlockState(helper.absolutePos(pos)).is(block),
                    "right-clicking the floor with the item must place the machine above it");
            BlockEntity machine = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            assertTrue(machine != null, "the placed machine's block-entity must be present");
            ItemEnchantments carried = machine.components().getOrDefault(DataComponents.ENCHANTMENTS,
                    ItemEnchantments.EMPTY);
            assertTrue(carried.size() == 2
                            && carried.getLevel(MachineEnchantTestSupport.enchantment(helper,
                                    Enchantments.SILK_TOUCH)) == 1
                            && carried.getLevel(MachineEnchantTestSupport.enchantment(helper,
                                    Enchantments.EFFICIENCY)) == 3,
                    "placing the item must leave Silk Touch I and Efficiency III on the machine, got " + carried);
            assertTrue(machine.components().getOrDefault(DataComponents.REPAIR_COST, 0) == 7,
                    "placing the item must leave its repair cost on the machine");

            DropArenaUtil.breakBlock(helper, pos, new ItemStack(Items.WOODEN_PICKAXE));
        }, Drops.near(pos, 2.0).exactly(item, 1).andCheck(() -> {
            List<ItemEntity> drops = EntityArenaUtil.droppedItems(helper, pos, 2.0).stream()
                    .filter(drop -> drop.getItem().is(item)).toList();
            assertTrue(drops.size() == 1, "exactly one machine item must be dropped, got " + drops.size());
            ItemStack dropped = drops.get(0).getItem();
            ItemEnchantments enchantments = dropped.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            assertTrue(enchantments.size() == 2
                            && enchantments.getLevel(MachineEnchantTestSupport.enchantment(helper,
                                    Enchantments.SILK_TOUCH)) == 1
                            && enchantments.getLevel(MachineEnchantTestSupport.enchantment(helper,
                                    Enchantments.EFFICIENCY)) == 3,
                    "the dropped machine must carry Silk Touch I and Efficiency III, got " + enchantments);
            assertTrue(dropped.getOrDefault(DataComponents.REPAIR_COST, 0) == 7,
                    "the dropped machine must keep the anvil's repair cost, got "
                            + dropped.getOrDefault(DataComponents.REPAIR_COST, 0));
            assertTrue(dropped.isEnchanted(), "the dropped machine must keep its enchantment glint");
        }));
    }

    public static void testPlacedQuarryKeepsEnchantmentsWhenBroken(GameTestHelper helper) {
        placedMachineKeepsEnchantmentsWhenBroken(helper, BCBuildersBlocks.QUARRY.get(), BCBuildersItems.QUARRY.get());
    }

    public static void testPlacedMiningWellKeepsEnchantmentsWhenBroken(GameTestHelper helper) {
        placedMachineKeepsEnchantmentsWhenBroken(helper, BCFactoryBlocks.MINING_WELL.get(),
                BCFactoryItems.MINING_WELL.get());
    }

    /** The components in {@code tile}'s update tag, decoded as a client decodes them; fails the test when the tag has
     *  none. */
    private static DataComponentMap updateTagComponents(GameTestHelper gth, BlockEntity tile) {
        net.minecraft.core.RegistryAccess registries = gth.getLevel().registryAccess();
        CompoundTag tag = tile.getUpdateTag(registries);
        Tag components = tag.get("components");
        assertTrue(components != null, "the update tag must carry the tile's components, got " + tag);
        return DataComponentMap.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), components)
                .getOrThrow();
    }

    /** The creative pick-block (ctrl + middle click) of an enchanted Quarry gives back an enchanted Quarry. Before
     *  1.21.10 the client picks, from its copy of the tile, which holds what the update tag carried; from 1.21.10 the
     *  server picks, from its own tile. Both are checked on every node. */
    public static void testPickBlockKeepsEnchantments(GameTestHelper helper) {
        try {
            BlockPos pos = new BlockPos(2, 2, 2);
            helper.setBlock(pos, BCBuildersBlocks.QUARRY.get());
            BlockEntity quarry = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            assertTrue(quarry != null, "quarry block-entity must be present");
            quarry.applyComponentsFromItemStack(MachineEnchantTestSupport.enchantedItem(helper,
                    BCBuildersItems.QUARRY.get(), Enchantments.SILK_TOUCH, 1));
            Holder<Enchantment> silk = MachineEnchantTestSupport.enchantment(helper, Enchantments.SILK_TOUCH);

            DataComponentMap fromTag = updateTagComponents(helper, quarry);
            assertTrue(fromTag.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(silk) == 1,
                    "the update tag must carry Silk Touch I, got " + fromTag);
            DataComponentMap fromTile = quarry.collectComponents();
            assertTrue(fromTile.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(silk) == 1,
                    "the tile itself must carry Silk Touch I, got " + fromTile);

            ItemStack pickedItem = new ItemStack(BCBuildersItems.QUARRY.get());
            pickedItem.applyComponents(fromTag);
            assertTrue(pickedItem.isEnchanted(), "the picked quarry must show its enchantment glint");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * What a client's copy of a Quarry makes of the packet. The client applies it with {@code loadWithComponents},
     * which replaces the components, so an enchanted Quarry must send them for client code that prices a break (the
     * break animation) to read the server's Efficiency; and an unenchanted one sends the plain tag, with no empty
     * {@code components} entry. A second Quarry stands in for the client's copy.
     */
    public static void testClientCopySeesTheEfficiency(GameTestHelper helper) {
        try {
            BlockPos serverPos = new BlockPos(1, 2, 2);
            BlockPos clientPos = new BlockPos(4, 2, 2);
            helper.setBlock(serverPos, BCBuildersBlocks.QUARRY.get());
            helper.setBlock(clientPos, BCBuildersBlocks.QUARRY.get());
            BlockEntity server = helper.getLevel().getBlockEntity(helper.absolutePos(serverPos));
            BlockEntity client = helper.getLevel().getBlockEntity(helper.absolutePos(clientPos));
            assertTrue(server != null && client != null, "both quarry block-entities must be present");
            net.minecraft.core.RegistryAccess registries = helper.getLevel().registryAccess();

            assertTrue(server.getUpdateTag(registries).get("components") == null,
                    "a quarry with no components must send the plain tag, got " + server.getUpdateTag(registries));

            server.applyComponentsFromItemStack(MachineEnchantTestSupport.enchantedItem(helper,
                    BCBuildersItems.QUARRY.get(), Enchantments.EFFICIENCY, 4));
            CompoundTag tag = server.getUpdateTag(registries);
            //? if >=1.21.10 {
            client.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
            //?} else {
            /*client.loadWithComponents(tag, registries);*/
            //?}
            assertTrue(MachineEnchantments.efficiency(client) == 4,
                    "the client's copy must read the server's Efficiency IV, got "
                            + MachineEnchantments.efficiency(client));
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
