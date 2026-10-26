/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.container;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.recipes.AssemblyRecipe;

import buildcraft.factory.BCFactoryBlocks;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.misc.RegistryUtilBC;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.recipe.AssemblyRecipeRegistry;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.BCSiliconRecipes;
import buildcraft.silicon.compat.viewer.AssemblyRecipeCollector;
import buildcraft.silicon.compat.viewer.AssemblyRecipeEntry;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.plug.FacadeStateManager;
import buildcraft.silicon.recipe.FacadeAssemblyRecipes;
import buildcraft.silicon.tile.TileAssemblyTable;

/** Game tests for the server side of the recipe-viewer "+" transfer into the Assembly Table, driven the way the
 *  network payload drives it: the payload {@link AssemblyTableTransfer#write} builds (the one the JEI and REI handlers
 *  send) is replayed into {@link ContainerAssemblyTable#readMessage} against the player's real inventory. The viewers'
 *  own client glue is not headless-testable; everything that moves items is.
 *
 *  <p>The tests never tick the table between the message and the assertions, so nothing the table does on its
 *  own (resolving recipes, crafting) can blur what the transfer itself moved. */
public class AssemblyTableTransferTester {

    private static final String CHIPSET_IRON = "chipset_iron";

    /** The table accepts an ingredient by the recipe's own test ({@code Ingredient.test}: the item, not its data),
     *  so a renamed redstone or iron ingot is as good as a plain one when placed by hand. The "+" transfer used to
     *  ask for the plain stacks the viewer entry listed and match the player's on item AND components, so a renamed
     *  ingredient was never found and nothing moved. It must take the same stacks the table does. */
    public static void transferAcceptsIngredientsTheWayTheTableDoes(GameTestHelper helper) {
        TileAssemblyTable table = placeTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerAssemblyTable menu = new ContainerAssemblyTable(0, player, table);

        ItemStack namedRedstone = new ItemStack(Items.REDSTONE, 3);
        namedRedstone.set(DataComponents.CUSTOM_NAME, Component.literal("Spark"));
        ItemStack namedIron = new ItemStack(Items.IRON_INGOT, 2);
        namedIron.set(DataComponents.CUSTOM_NAME, Component.literal("Bar"));
        inv.setItem(0, namedRedstone.copy());
        inv.setItem(1, namedIron.copy());
        inv.setItem(2, new ItemStack(Items.GOLD_INGOT, 5)); // no ingredient of the recipe

        send(helper, menu, false, CHIPSET_IRON, outputOf(CHIPSET_IRON));

        expectCount(helper, table.inv, namedRedstone, 1, "the renamed redstone in the table");
        expectCount(helper, table.inv, namedIron, 1, "the renamed iron ingot in the table");
        expectCount(helper, inv, namedRedstone, 2, "the renamed redstone left with the player");
        expectCount(helper, inv, namedIron, 1, "the renamed iron ingot left with the player");
        expectCount(helper, inv, new ItemStack(Items.GOLD_INGOT), 5, "gold the recipe never asked for");
        helper.succeed();
    }

    /** A recipe whose ingredient is data-sensitive (the gate upgrade recipes take one gate VARIANT, matched by
     *  component) takes a renamed gate of that variant but leaves the other variant alone: the table's test, not
     *  "any stack of the item". */
    public static void transferMovesComponentCarryingIngredients(GameTestHelper helper) {
        TileAssemblyTable table = placeTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerAssemblyTable menu = new ContainerAssemblyTable(0, player, table);

        ItemStack namedIronGates = ironAndGate(2);
        namedIronGates.set(DataComponents.CUSTOM_NAME, Component.literal("Gatekeeper"));
        ItemStack goldGates = BCSiliconItems.PLUG_GATE.get()
                .getStack(new GateVariant(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.NO_MODIFIER))
                .copyWithCount(4);
        inv.setItem(0, namedIronGates.copy());
        inv.setItem(1, goldGates.copy());
        inv.setItem(2, new ItemStack(Items.LAPIS_LAZULI, 3));

        ItemStack upgraded = BCSiliconItems.PLUG_GATE.get()
                .getStack(new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.LAPIS));
        send(helper, menu, false, recipeMaking(upgraded), upgraded);

        expectCount(helper, table.inv, namedIronGates, 1, "the renamed iron AND gate in the table");
        expectCount(helper, table.inv, goldGates, 0, "the gold AND gates in the table");
        expectCount(helper, table.inv, new ItemStack(Items.LAPIS_LAZULI), 1, "lapis in the table");
        expectCount(helper, inv, namedIronGates, 1, "the renamed iron AND gate left with the player");
        expectCount(helper, inv, goldGates, 4, "the gold AND gates the recipe does not accept");
        expectCount(helper, inv, new ItemStack(Items.LAPIS_LAZULI), 2, "lapis left with the player");
        helper.succeed();
    }

    /** The server must move only what the player really holds, however much the (possibly tampered) request
     *  claims: an ingredient the player cannot fully cover moves what exists, one the player lacks moves nothing,
     *  nothing is created or lost, and an ingredient that accepts several items takes them all. */
    public static void transferOnlyMovesWhatThePlayerHolds(GameTestHelper helper) {
        TileAssemblyTable table = placeTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerAssemblyTable menu = new ContainerAssemblyTable(0, player, table);

        // The tank recipe wants 6 of "cheap glass" (any of several items); the player holds five, in two stacks.
        ItemStack namedGlass = new ItemStack(Items.GLASS, 1);
        namedGlass.set(DataComponents.CUSTOM_NAME, Component.literal("Pane of Glass"));
        inv.setItem(0, new ItemStack(Items.GLASS, 4));
        inv.setItem(1, namedGlass.copy());
        ItemStack tank = new ItemStack(BCFactoryBlocks.TANK.get());
        send(helper, menu, false, recipeMaking(tank), tank);

        expectCount(helper, table.inv, new ItemStack(Items.GLASS), 4, "clear glass in the table");
        expectCount(helper, table.inv, namedGlass, 1, "named glass in the table");
        expectCount(helper, inv, new ItemStack(Items.GLASS), 0, "clear glass left with the player");
        expectCount(helper, inv, namedGlass, 0, "named glass left with the player");

        // Redstone + a gold ingot the player never had: the redstone moves, no gold appears.
        inv.setItem(0, new ItemStack(Items.REDSTONE, 3));
        ItemStack goldChipset = new ItemStack(BCSiliconItems.CHIPSET_GOLD.get());
        send(helper, menu, false, recipeMaking(goldChipset), goldChipset);

        expectCount(helper, table.inv, new ItemStack(Items.REDSTONE), 1, "redstone in the table");
        expectCount(helper, table.inv, new ItemStack(Items.GOLD_INGOT), 0, "gold in the table");
        expectCount(helper, inv, new ItemStack(Items.REDSTONE), 2, "redstone left with the player");
        expectCount(helper, inv, new ItemStack(Items.GOLD_INGOT), 0, "gold the player never had");
        helper.succeed();
    }

    /** Without the max flag the move is capped at the recipe's quantity; with it (shift-click) the table takes
     *  everything the player holds that the recipe's ingredients accept. */
    public static void transferHonoursTheMaxFlag(GameTestHelper helper) {
        TileAssemblyTable table = placeTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerAssemblyTable menu = new ContainerAssemblyTable(0, player, table);
        inv.setItem(0, new ItemStack(Items.REDSTONE, 40));
        inv.setItem(1, new ItemStack(Items.REDSTONE, 30));
        ItemStack chipset = new ItemStack(BCSiliconItems.CHIPSET_REDSTONE.get());
        String recipe = recipeMaking(chipset);

        send(helper, menu, false, recipe, chipset);
        expectCount(helper, table.inv, new ItemStack(Items.REDSTONE), 1, "redstone after a one-recipe transfer");
        expectCount(helper, inv, new ItemStack(Items.REDSTONE), 69, "redstone left after a one-recipe transfer");

        send(helper, menu, true, recipe, chipset);
        expectCount(helper, table.inv, new ItemStack(Items.REDSTONE), 70, "redstone after a max transfer");
        expectCount(helper, inv, new ItemStack(Items.REDSTONE), 0, "redstone left after a max transfer");
        helper.succeed();
    }

    /** A request naming a recipe the server does not know, or an output the recipe does not make, moves nothing
     *  (the gold-ingot and facade-recipe cases are the ones a guess could get wrong); the same player and table then
     *  take the proper request, so the silence is the request's and not an empty rig. */
    public static void transferIgnoresAnUnknownRecipeOrOutput(GameTestHelper helper) {
        TileAssemblyTable table = placeTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerAssemblyTable menu = new ContainerAssemblyTable(0, player, table);
        inv.setItem(0, new ItemStack(Items.REDSTONE, 4));
        inv.setItem(1, new ItemStack(Items.IRON_INGOT, 4));
        inv.setItem(2, new ItemStack(Items.GOLD_INGOT, 4));
        ItemStack ironChipset = outputOf(CHIPSET_IRON);
        ItemStack renamedChipset = ironChipset.copy();
        renamedChipset.set(DataComponents.CUSTOM_NAME, Component.literal("Not Quite"));

        send(helper, menu, true, "no_such_recipe", ironChipset);
        send(helper, menu, true, CHIPSET_IRON, new ItemStack(BCSiliconItems.CHIPSET_GOLD.get())); // another recipe's
        send(helper, menu, true, CHIPSET_IRON, renamedChipset); // right item, wrong components
        send(helper, menu, true, CHIPSET_IRON, ItemStack.EMPTY);
        send(helper, menu, true, FacadeAssemblyRecipes.INSTANCE.getRegistryName(), ironChipset); // not a facade
        send(helper, menu, true, FacadeAssemblyRecipes.INSTANCE.getRegistryName(),
                new ItemStack(BCSiliconItems.PLUG_FACADE.get())); // a bare facade names no block

        for (int i = 0; i < table.inv.getSlots(); i++) {
            if (!table.inv.getStackInSlot(i).isEmpty()) {
                helper.fail("a request for an unknown recipe or an output it does not make must move nothing, but "
                        + "table slot " + i + " holds " + table.inv.getStackInSlot(i));
            }
        }
        expectCount(helper, inv, new ItemStack(Items.REDSTONE), 4, "redstone left with the player");
        expectCount(helper, inv, new ItemStack(Items.IRON_INGOT), 4, "iron left with the player");
        expectCount(helper, inv, new ItemStack(Items.GOLD_INGOT), 4, "gold left with the player");

        send(helper, menu, false, CHIPSET_IRON, ironChipset);
        expectCount(helper, table.inv, new ItemStack(Items.REDSTONE), 1, "redstone after the proper request");
        expectCount(helper, table.inv, new ItemStack(Items.IRON_INGOT), 1, "iron after the proper request");
        helper.succeed();
    }

    /** A cycling entry's "+" names the one output it shows. The facade recipe makes a facade per block, and what it
     *  asks for depends on that output: the block it mimics (renamed or not) and three structure pipes. Reads the
     *  entry the viewers get from the collector, so the recipe name and outputs are the ones they send. */
    public static void transferFacadeOutputMovesTheBlockItNeeds(GameTestHelper helper) {
        BCSiliconRecipes.ensureInitialized();
        FacadeStateManager.ensureInitialized();
        TileAssemblyTable table = placeTable(helper);
        Player player = survivalPlayer(helper);
        Inventory inv = player.getInventory();
        ContainerAssemblyTable menu = new ContainerAssemblyTable(0, player, table);

        AssemblyRecipeEntry entry = null;
        for (AssemblyRecipeEntry candidate : AssemblyRecipeCollector.collect()) {
            if (candidate.id().endsWith(":basic")) {
                entry = candidate;
                break;
            }
        }
        if (entry == null) {
            helper.fail("the collector produced no basic facade entry");
            return;
        }
        // The entry pairs outputs[i] with the block in the focus-linked input slot's alternative i.
        int pick = entry.outputs().size() / 2;
        ItemStack output = entry.outputs().get(pick);
        ItemStack block = entry.inputSlots().get(entry.focusLinkInputIndex()).get(pick);
        ItemStack renamedBlock = block.copyWithCount(3);
        renamedBlock.set(DataComponents.CUSTOM_NAME, Component.literal("Facade Fodder"));
        Item structurePipe = RegistryUtilBC.getValue(BuiltInRegistries.ITEM,
                Identifier.parse("buildcraftunofficial:pipe_structure"));
        inv.setItem(0, renamedBlock.copy());
        inv.setItem(1, new ItemStack(structurePipe, 5));
        inv.setItem(2, new ItemStack(Items.GOLD_INGOT, 4)); // no part of any facade

        send(helper, menu, false, entry.recipe(), output);

        expectCount(helper, table.inv, renamedBlock, 1, "the renamed block in the table");
        expectCount(helper, table.inv, new ItemStack(structurePipe), 3, "structure pipes in the table");
        expectCount(helper, inv, renamedBlock, 2, "the renamed block left with the player");
        expectCount(helper, inv, new ItemStack(structurePipe), 2, "structure pipes left with the player");
        expectCount(helper, inv, new ItemStack(Items.GOLD_INGOT), 4, "gold the facade never asked for");
        for (int i = 0; i < table.inv.getSlots(); i++) {
            ItemStack in = table.inv.getStackInSlot(i);
            if (!in.isEmpty() && !in.is(structurePipe) && !in.is(block.getItem())) {
                helper.fail("only the facade's block and the pipes may move, but table slot " + i + " holds " + in);
            }
        }
        helper.succeed();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static Player survivalPlayer(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.SURVIVAL);
    }

    private static TileAssemblyTable placeTable(GameTestHelper helper) {
        BCSiliconRecipes.ensureInitialized();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.ASSEMBLY_TABLE.get());
        return blockEntity(helper, pos, TileAssemblyTable.class);
    }

    private static ItemStack ironAndGate(int count) {
        return BCSiliconItems.PLUG_GATE.get()
                .getStack(new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER))
                .copyWithCount(count);
    }

    /** The registry name of the assembly recipe whose previews include {@code output}. */
    private static String recipeMaking(ItemStack output) {
        for (AssemblyRecipe recipe : AssemblyRecipeRegistry.REGISTRY.values()) {
            for (ItemStack made : recipe.getOutputPreviews()) {
                if (ItemStack.isSameItemSameComponents(made, output)) {
                    return recipe.getRegistryName();
                }
            }
        }
        throw new IllegalStateException("no assembly recipe makes " + output + " " + output.getComponentsPatch());
    }

    private static ItemStack outputOf(String recipeName) {
        AssemblyRecipe recipe = AssemblyRecipeRegistry.REGISTRY.get(recipeName);
        if (recipe == null) {
            throw new IllegalStateException("no assembly recipe named " + recipeName);
        }
        return recipe.getOutputPreviews().iterator().next();
    }

    /** Builds the payload exactly as the viewers' shared transfer does and replays it into the server-side menu (the
     *  mock player is not a ServerPlayer, so the network handler cannot run; it only forwards to {@code readMessage}). */
    private static void send(GameTestHelper helper, ContainerAssemblyTable menu, boolean maxTransfer, String recipe,
            ItemStack output) {
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        try {
            AssemblyTableTransfer.write(buffer, helper.getLevel().registryAccess(), maxTransfer, recipe, output);
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
