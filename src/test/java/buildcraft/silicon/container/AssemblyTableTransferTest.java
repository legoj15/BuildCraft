/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.container;

import java.util.List;

import com.google.common.collect.ImmutableSet;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.recipes.AssemblyRecipeBasic;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.lib.compat.viewer.TransferResult;
import buildcraft.lib.gui.BCContainerSupport;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.recipe.AssemblyRecipeRegistry;
import buildcraft.lib.tile.item.ItemHandlerSimple;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;

/**
 * The recipe-viewer "+" transfer into the Assembly Table: the request names a recipe (registry name plus the output it
 * was shown for), and the server resolves the recipe's own ingredients and takes the stacks they accept. It used to
 * send concrete stacks and match the player's on item AND components, so a renamed ingredient the table would take
 * by hand showed "missing". These tests pin the wire format, the lookup that turns a request into ingredients (an
 * unknown recipe or an output the recipe does not make yields none), the client's advisory check, and the move itself
 * against a plain {@link Container}, no level needed. The same flow through the real menu is the game test
 * {@link AssemblyTableTransferTester}.
 *
 * <p>The recipe is a throwaway one registered for the test, so no production recipe's shape is pinned.
 */
public class AssemblyTableTransferTest extends VanillaSetupBaseTester {

    private static final String RECIPE = "buildcraftunofficial:test_assembly_transfer";

    /** 2 redstone, then 1 iron ingot, then 1 more that may be an iron ingot or a gold ingot. */
    private AssemblyRecipeBasic recipe;

    @BeforeEach
    public void registerRecipe() {
        recipe = new AssemblyRecipeBasic(RECIPE, 1L, ImmutableSet.of(
                new IngredientStack(Ingredient.of(Items.REDSTONE), 2),
                new IngredientStack(Ingredient.of(Items.IRON_INGOT), 1),
                new IngredientStack(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1)),
                new ItemStack(Items.STICK));
        AssemblyRecipeRegistry.register(recipe);
    }

    @AfterEach
    public void unregisterRecipe() {
        AssemblyRecipeRegistry.REGISTRY.remove(RECIPE);
    }

    /** Static registries only: enough for every component used here, no server or level needed. */
    private static RegistryAccess registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static AssemblyTableTransfer.Request roundTrip(boolean max, String recipe, ItemStack output) {
        PacketBufferBC buf = new PacketBufferBC(Unpooled.buffer());
        try {
            AssemblyTableTransfer.write(buf, registries(), max, recipe, output);
            return AssemblyTableTransfer.read(buf, registries());
        } finally {
            buf.release();
        }
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static Container inventoryOf(ItemStack... stacks) {
        Container inv = new SimpleContainer(41); // a player inventory's size
        for (int i = 0; i < stacks.length; i++) {
            inv.setItem(i, stacks[i]);
        }
        return inv;
    }

    private static int count(Container inv, ItemStack like) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (ItemStack.isSameItemSameComponents(inv.getItem(i), like)) {
                total += inv.getItem(i).getCount();
            }
        }
        return total;
    }

    private static int count(ItemHandlerSimple handler, ItemStack like) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (ItemStack.isSameItemSameComponents(handler.getStackInSlot(i), like)) {
                total += handler.getStackInSlot(i).getCount();
            }
        }
        return total;
    }

    // ── Wire format ──

    @Test
    public void theRequestRoundTripsItsRecipeOutputAndFlag() {
        ItemStack gate = BCSiliconItems.PLUG_GATE.get().getStack(
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.LAPIS));
        Assertions.assertFalse(gate.getComponentsPatch().isEmpty(),
                "precondition: the gate must really carry a component, or this test proves nothing");

        AssemblyTableTransfer.Request got = roundTrip(true, "gate-modifier-AND-IRON-LAPIS", gate);

        Assertions.assertTrue(got.maxTransfer());
        Assertions.assertEquals("gate-modifier-AND-IRON-LAPIS", got.recipe());
        Assertions.assertTrue(ItemStack.isSameItemSameComponents(gate, got.output()),
                "the output's components must survive: wanted " + gate.getComponentsPatch() + " got "
                        + got.output().getComponentsPatch());
        Assertions.assertFalse(roundTrip(false, RECIPE, gate).maxTransfer(), "the flag round-trips both ways");
    }

    @Test
    public void aRecipeNameLongerThanTheCapIsRefused() {
        PacketBufferBC buf = new PacketBufferBC(Unpooled.buffer());
        try {
            buf.writeByte(0);
            buf.writeUtf("x".repeat(AssemblyTableTransfer.MAX_RECIPE_NAME + 1));
            ItemStack.OPTIONAL_STREAM_CODEC.encode(BCContainerSupport.registryBuf(buf, registries()),
                    new ItemStack(Items.STICK));
            Assertions.assertThrows(RuntimeException.class, () -> AssemblyTableTransfer.read(buf, registries()),
                    "an oversized name is a malformed request: the dispatcher logs the throw and drops it");
        } finally {
            buf.release();
        }
    }

    // ── The lookup: a request becomes the recipe's ingredients, or nothing ──

    @Test
    public void aKnownRecipeAndItsOutputYieldTheRecipesOwnIngredientsInOrder() {
        List<IngredientStack> inputs = AssemblyTableTransfer.inputsFor(RECIPE, new ItemStack(Items.STICK, 5));

        Assertions.assertNotNull(inputs, "the stack's count is not part of what the recipe makes");
        Assertions.assertEquals(3, inputs.size());
        Assertions.assertEquals(2, inputs.get(0).count);
        Assertions.assertTrue(inputs.get(0).ingredient.test(new ItemStack(Items.REDSTONE)));
        Assertions.assertTrue(inputs.get(1).ingredient.test(new ItemStack(Items.IRON_INGOT))
                && !inputs.get(1).ingredient.test(new ItemStack(Items.GOLD_INGOT)));
        Assertions.assertTrue(inputs.get(2).ingredient.test(new ItemStack(Items.GOLD_INGOT)));
    }

    @Test
    public void anUnknownRecipeOrAnOutputItDoesNotMakeYieldsNothing() {
        Assertions.assertNull(AssemblyTableTransfer.inputsFor("no_such_recipe", new ItemStack(Items.STICK)));
        Assertions.assertNull(AssemblyTableTransfer.inputsFor(RECIPE, new ItemStack(Items.COAL)),
                "another item");
        Assertions.assertNull(AssemblyTableTransfer.inputsFor(RECIPE, named(new ItemStack(Items.STICK), "Rod")),
                "the right item with other components is not what the recipe makes");
        Assertions.assertNull(AssemblyTableTransfer.inputsFor(RECIPE, ItemStack.EMPTY));
    }

    // ── The client's advisory check ──

    @Test
    public void theCheckAcceptsWhatTheTableAcceptsByHand() {
        List<IngredientStack> inputs = AssemblyTableTransfer.inputsFor(RECIPE, new ItemStack(Items.STICK));
        Container renamed = inventoryOf(named(new ItemStack(Items.REDSTONE, 2), "Spark"),
                named(new ItemStack(Items.IRON_INGOT, 2), "Bar"));

        Assertions.assertEquals(TransferResult.OK, AssemblyTableTransfer.check(inputs, renamed),
                "a renamed redstone and iron ingot satisfy ingredients that test the item");
    }

    @Test
    public void theCheckAnswersMissingWhenAnIngredientIsShort() {
        List<IngredientStack> inputs = AssemblyTableTransfer.inputsFor(RECIPE, new ItemStack(Items.STICK));

        Assertions.assertEquals(TransferResult.MISSING, AssemblyTableTransfer.check(inputs,
                inventoryOf(new ItemStack(Items.REDSTONE, 1), new ItemStack(Items.IRON_INGOT, 2))),
                "one redstone short");
        Assertions.assertEquals(TransferResult.MISSING, AssemblyTableTransfer.check(inputs,
                inventoryOf(new ItemStack(Items.REDSTONE, 2), new ItemStack(Items.COAL, 9))),
                "no iron or gold at all");
    }

    @Test
    public void stockTwoIngredientsBothAcceptIsNotCountedTwice() {
        List<IngredientStack> inputs = AssemblyTableTransfer.inputsFor(RECIPE, new ItemStack(Items.STICK));

        Assertions.assertEquals(TransferResult.MISSING, AssemblyTableTransfer.check(inputs,
                inventoryOf(new ItemStack(Items.REDSTONE, 2), new ItemStack(Items.IRON_INGOT, 1))),
                "one iron ingot cannot be both the iron the recipe wants and the iron-or-gold after it");
        Assertions.assertEquals(TransferResult.OK, AssemblyTableTransfer.check(inputs,
                inventoryOf(new ItemStack(Items.REDSTONE, 2), new ItemStack(Items.IRON_INGOT, 1),
                        new ItemStack(Items.GOLD_INGOT, 1))), "gold covers the either-or, iron the other");
    }

    @Test
    public void theCheckIsInvalidWhenThereIsNothingToAskFor() {
        Container anything = inventoryOf(new ItemStack(Items.REDSTONE, 64));

        Assertions.assertEquals(TransferResult.INVALID, AssemblyTableTransfer.check(null, anything));
        Assertions.assertEquals(TransferResult.INVALID, AssemblyTableTransfer.check(List.of(), anything));
    }

    // ── The move ──

    private static AssemblyTableTransfer.Request request(boolean max) {
        return new AssemblyTableTransfer.Request(max, RECIPE, new ItemStack(Items.STICK));
    }

    @Test
    public void theMoveTakesRenamedIngredientsAndDebitsThePlayerByExactlyWhatWentIn() {
        ItemStack spark = named(new ItemStack(Items.REDSTONE, 5), "Spark");
        ItemStack bar = named(new ItemStack(Items.IRON_INGOT, 3), "Bar");
        ItemStack coal = new ItemStack(Items.COAL, 7);
        Container inv = inventoryOf(spark.copy(), bar.copy(), coal.copy());
        ItemHandlerSimple table = new ItemHandlerSimple(12);

        int moved = AssemblyTableTransfer.apply(inv, table, request(false));

        Assertions.assertEquals(4, moved, "2 redstone, then 1 iron for each iron ingredient");
        Assertions.assertEquals(2, count(table, spark.copyWithCount(1)));
        Assertions.assertEquals(2, count(table, bar.copyWithCount(1)));
        Assertions.assertEquals(3, count(inv, spark.copyWithCount(1)), "5 - 2");
        Assertions.assertEquals(1, count(inv, bar.copyWithCount(1)), "3 - 2");
        Assertions.assertEquals(7, count(inv, coal), "an item no ingredient accepts is never touched");
    }

    @Test
    public void anIngredientThatAcceptsSeveralItemsTakesWhatIsLeftForIt() {
        Container inv = inventoryOf(new ItemStack(Items.REDSTONE, 2), new ItemStack(Items.IRON_INGOT, 1),
                new ItemStack(Items.GOLD_INGOT, 1));
        ItemHandlerSimple table = new ItemHandlerSimple(12);

        AssemblyTableTransfer.apply(inv, table, request(false));

        Assertions.assertEquals(2, count(table, new ItemStack(Items.REDSTONE)));
        Assertions.assertEquals(1, count(table, new ItemStack(Items.IRON_INGOT)), "the iron ingredient's");
        Assertions.assertEquals(1, count(table, new ItemStack(Items.GOLD_INGOT)),
                "the iron-or-gold ingredient came after the iron was spent, so it took the gold");
    }

    @Test
    public void whatThePlayerLacksIsNotMovedOrInvented() {
        Container inv = inventoryOf(new ItemStack(Items.REDSTONE, 1));
        ItemHandlerSimple table = new ItemHandlerSimple(12);

        int moved = AssemblyTableTransfer.apply(inv, table, request(false));

        Assertions.assertEquals(1, moved, "the one redstone the player held");
        Assertions.assertEquals(1, count(table, new ItemStack(Items.REDSTONE)));
        Assertions.assertEquals(0, count(inv, new ItemStack(Items.REDSTONE)));
    }

    @Test
    public void theMaxFlagTakesEverythingTheIngredientsAcceptAfterOneRecipesWorth() {
        Container inv = inventoryOf(new ItemStack(Items.REDSTONE, 64), new ItemStack(Items.REDSTONE, 36),
                new ItemStack(Items.IRON_INGOT, 5), new ItemStack(Items.COAL, 9));
        ItemHandlerSimple table = new ItemHandlerSimple(12);

        AssemblyTableTransfer.apply(inv, table, request(true));

        Assertions.assertEquals(100, count(table, new ItemStack(Items.REDSTONE)));
        Assertions.assertEquals(5, count(table, new ItemStack(Items.IRON_INGOT)));
        Assertions.assertEquals(0, count(inv, new ItemStack(Items.REDSTONE)));
        Assertions.assertEquals(9, count(inv, new ItemStack(Items.COAL)), "coal is nobody's ingredient");
    }

    @Test
    public void aFullTableDebitsThePlayerOnlyForWhatItTook() {
        Container inv = inventoryOf(new ItemStack(Items.REDSTONE, 64), new ItemStack(Items.REDSTONE, 36),
                new ItemStack(Items.IRON_INGOT, 1));
        ItemHandlerSimple oneSlot = new ItemHandlerSimple(1);

        int moved = AssemblyTableTransfer.apply(inv, oneSlot, request(true));

        Assertions.assertEquals(64, moved, "one slot holds one stack of redstone and nothing else");
        Assertions.assertEquals(64, count(oneSlot, new ItemStack(Items.REDSTONE)));
        Assertions.assertEquals(36, count(inv, new ItemStack(Items.REDSTONE)), "100 held, 64 taken");
        Assertions.assertEquals(1, count(inv, new ItemStack(Items.IRON_INGOT)),
                "the iron the full table could not take stays with the player");
    }

    @Test
    public void aSlotThatTakesOnlyPartOfAStackDebitsOnlyThatPart() {
        Container inv = inventoryOf(new ItemStack(Items.REDSTONE, 10));
        ItemHandlerSimple oneSlot = new ItemHandlerSimple(1);
        oneSlot.insertItem(0, new ItemStack(Items.REDSTONE, 60), false); // room for 4 more

        int moved = AssemblyTableTransfer.apply(inv, oneSlot, request(true));

        Assertions.assertEquals(4, moved, "the 4 that fit");
        Assertions.assertEquals(64, count(oneSlot, new ItemStack(Items.REDSTONE)));
        Assertions.assertEquals(6, count(inv, new ItemStack(Items.REDSTONE)),
                "10 held, 4 taken: the 6 that did not fit are still the player's");
    }

    @Test
    public void anUnknownRecipeOrWrongOutputMovesNothing() {
        Container inv = inventoryOf(new ItemStack(Items.REDSTONE, 4), new ItemStack(Items.IRON_INGOT, 4));
        ItemHandlerSimple table = new ItemHandlerSimple(12);

        Assertions.assertEquals(0, AssemblyTableTransfer.apply(inv, table,
                new AssemblyTableTransfer.Request(true, "no_such_recipe", new ItemStack(Items.STICK))));
        Assertions.assertEquals(0, AssemblyTableTransfer.apply(inv, table,
                new AssemblyTableTransfer.Request(true, RECIPE, new ItemStack(Items.COAL))));

        Assertions.assertEquals(4, count(inv, new ItemStack(Items.REDSTONE)));
        Assertions.assertEquals(0, count(table, new ItemStack(Items.REDSTONE)));
    }
}
