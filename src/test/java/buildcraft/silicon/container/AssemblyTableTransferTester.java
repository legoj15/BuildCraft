/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.container;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.tile.TileAssemblyTable;

/** Game tests for the server side of the JEI "+" transfer into the Assembly Table, driven the way the network
 *  payload drives it: the payload {@link AssemblyTableTransfer#write} builds (the one the JEI handler sends) is
 *  replayed into {@link ContainerAssemblyTable#readMessage} against the player's real inventory. JEI's own client
 *  glue is not headless-testable; everything that moves items is.
 *
 *  <p>The tests never tick the table between the message and the assertions, so nothing the table does on its
 *  own (resolving recipes, crafting) can blur what the transfer itself moved. */
public class AssemblyTableTransferTester {

    /** The headline bug: an ingredient that carries data components (an enchanted, renamed tool; a gate with its
     *  variant) was sent as bare {@code {id, count}}, so the server's exact item+components match never found it
     *  in the player's inventory and the transfer silently moved nothing. A bare twin must stay put. */
    public static void transferMovesComponentCarryingIngredients(GameTestHelper helper) {
        TileAssemblyTable table = placeTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerAssemblyTable menu = new ContainerAssemblyTable(0, player, table);

        ItemStack fancyPick = enchantedNamedPickaxe(helper);
        ItemStack gates = ironAndGate(2);
        inv.setItem(0, fancyPick.copy());
        inv.setItem(1, new ItemStack(Items.DIAMOND_PICKAXE)); // the bare twin the request did NOT ask for
        inv.setItem(2, gates.copy());
        inv.setItem(3, BCSiliconItems.PLUG_GATE.get().getDefaultInstance().copyWithCount(4)); // gate, wrong variant

        send(helper, menu, false, List.of(fancyPick, gates));

        expectCount(helper, table.inv, fancyPick, 1, "the enchanted, renamed pickaxe in the table");
        expectCount(helper, table.inv, gates, 2, "the iron AND gates in the table");
        expectCount(helper, table.inv, new ItemStack(Items.DIAMOND_PICKAXE), 0, "a bare pickaxe in the table");
        expectCount(helper, inv, fancyPick, 0, "the enchanted pickaxe left in the player inventory");
        expectCount(helper, inv, gates, 0, "the iron AND gates left in the player inventory");
        if (!inv.getItem(1).is(Items.DIAMOND_PICKAXE) || inv.getItem(1).getCount() != 1) {
            helper.fail("the bare pickaxe the request never named must still be in the player's slot 1, found "
                    + inv.getItem(1));
        }
        if (!inv.getItem(3).is(BCSiliconItems.PLUG_GATE.get()) || inv.getItem(3).getCount() != 4) {
            helper.fail("the wrong-variant gates the request never named must be untouched, found "
                    + inv.getItem(3));
        }
        helper.succeed();
    }

    /** The server must move only what the player really holds, however much the (possibly tampered) request
     *  claims: a request for more than the player has moves what exists, a request for something the player
     *  lacks moves nothing, and no item is created or lost. */
    public static void transferOnlyMovesWhatThePlayerHolds(GameTestHelper helper) {
        TileAssemblyTable table = placeTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerAssemblyTable menu = new ContainerAssemblyTable(0, player, table);
        inv.setItem(0, new ItemStack(Items.DIAMOND, 3));

        send(helper, menu, false, List.of(new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.EMERALD, 5)));

        expectCount(helper, table.inv, new ItemStack(Items.DIAMOND), 3, "diamonds in the table");
        expectCount(helper, table.inv, new ItemStack(Items.EMERALD), 0, "emeralds in the table");
        expectCount(helper, inv, new ItemStack(Items.DIAMOND), 0, "diamonds left with the player");
        expectCount(helper, inv, new ItemStack(Items.EMERALD), 0, "emeralds the player never had");
        helper.succeed();
    }

    /** Without the max flag the move is capped at the recipe's quantity; with it (JEI shift-click) the table
     *  takes everything the player holds of that stack. */
    public static void transferHonoursTheMaxFlag(GameTestHelper helper) {
        TileAssemblyTable table = placeTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerAssemblyTable menu = new ContainerAssemblyTable(0, player, table);
        inv.setItem(0, new ItemStack(Items.REDSTONE, 40));
        inv.setItem(1, new ItemStack(Items.REDSTONE, 30));

        send(helper, menu, false, List.of(new ItemStack(Items.REDSTONE, 5)));
        expectCount(helper, table.inv, new ItemStack(Items.REDSTONE), 5, "redstone after a one-recipe transfer");
        expectCount(helper, inv, new ItemStack(Items.REDSTONE), 65, "redstone left after a one-recipe transfer");

        send(helper, menu, true, List.of(new ItemStack(Items.REDSTONE, 5)));
        expectCount(helper, table.inv, new ItemStack(Items.REDSTONE), 70, "redstone after a max transfer");
        expectCount(helper, inv, new ItemStack(Items.REDSTONE), 0, "redstone left after a max transfer");
        helper.succeed();
    }

    /** A request naming more distinct stacks than the table has input slots cannot be a real recipe: it is
     *  dropped whole, nothing moves. */
    public static void transferDropsAnOversizedRequest(GameTestHelper helper) {
        TileAssemblyTable table = placeTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerAssemblyTable menu = new ContainerAssemblyTable(0, player, table);

        Item[] items = { Items.STICK, Items.COAL, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD,
                Items.REDSTONE, Items.LAPIS_LAZULI, Items.QUARTZ, Items.COPPER_INGOT, Items.FLINT, Items.CLAY_BALL,
                Items.PAPER };
        List<ItemStack> request = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            inv.setItem(i, new ItemStack(items[i], 2));
            request.add(new ItemStack(items[i], 2));
        }

        send(helper, menu, true, request);

        for (int i = 0; i < table.inv.getSlots(); i++) {
            if (!table.inv.getStackInSlot(i).isEmpty()) {
                helper.fail("an oversized request must move nothing, but table slot " + i + " holds "
                        + table.inv.getStackInSlot(i));
            }
        }
        for (int i = 0; i < items.length; i++) {
            if (inv.getItem(i).getCount() != 2) {
                helper.fail("an oversized request must leave the player's inventory alone, slot " + i + " is now "
                        + inv.getItem(i));
            }
        }
        helper.succeed();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static Player survivalPlayer(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.SURVIVAL);
    }

    private static TileAssemblyTable placeTable(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.ASSEMBLY_TABLE.get());
        return blockEntity(helper, pos, TileAssemblyTable.class);
    }

    private static ItemStack ironAndGate(int count) {
        return BCSiliconItems.PLUG_GATE.get()
                .getStack(new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER))
                .copyWithCount(count);
    }

    /** A pickaxe whose identity lives in a <em>dynamic-registry</em> component (the enchantment) plus a name, so
     *  the codec must resolve registry entries from the level's real registry access, not just copy bytes. */
    private static ItemStack enchantedNamedPickaxe(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Holder<Enchantment> fortune = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.enchant(fortune, 3);
        pick.set(DataComponents.CUSTOM_NAME, Component.literal("Assembly Fodder"));
        return pick;
    }

    /** Builds the payload exactly as the JEI handler does and replays it into the server-side menu (the mock
     *  player is not a ServerPlayer, so the network handler cannot run; it only forwards to {@code readMessage}). */
    private static void send(GameTestHelper helper, ContainerAssemblyTable menu, boolean maxTransfer,
            List<ItemStack> need) {
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        try {
            AssemblyTableTransfer.write(buffer, helper.getLevel().registryAccess(), maxTransfer, need);
            menu.readMessage(ContainerBC_Neptune.NET_JEI_TRANSFER_ITEMS, buffer, false, null);
        } finally {
            buffer.release();
        }
    }

    private static void expectCount(GameTestHelper helper, Inventory inv, ItemStack like, int want, String what) {
        int got = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, like)) {
                got += s.getCount();
            }
        }
        if (got != want) {
            helper.fail(what + ": expected " + want + " but found " + got);
        }
    }

    private static void expectCount(GameTestHelper helper, ItemHandlerSimple handler, ItemStack like, int want,
            String what) {
        int got = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, like)) {
                got += s.getCount();
            }
        }
        if (got != want) {
            helper.fail(what + ": expected " + want + " but found " + got);
        }
    }

    private static <T extends BlockEntity> T blockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        //? if >=1.21.10 {
        return helper.getBlockEntity(pos, type);
        //?} else {
        /*return type.cast(helper.getBlockEntity(pos));*/
        //?}
    }
}
