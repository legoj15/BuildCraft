/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.list;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import buildcraft.api.lists.ListMatchHandler.Type;
import buildcraft.api.lists.ListRegistry;

public class ListTester {
    private static void assertTrue(boolean val, String msg) {
        if (!val) throw new IllegalStateException("Assertion failed: " + msg);
    }

    private static void assertFalse(boolean val, String msg) {
        if (val) throw new IllegalStateException("Assertion failed: " + msg);
    }

    public static void testTools(GameTestHelper helper) {
        ListMatchHandlerTools matcher = new ListMatchHandlerTools();
        ItemStack woodenAxe = new ItemStack(Items.WOODEN_AXE);
        ItemStack ironAxe = new ItemStack(Items.IRON_AXE);
        ItemStack woodenShovel = new ItemStack(Items.WOODEN_SHOVEL);
        ItemStack woodenAxeDamaged = new ItemStack(Items.WOODEN_AXE);
        woodenAxeDamaged.setDamageValue(26);
        ItemStack apple = new ItemStack(Items.APPLE);

        assertTrue(matcher.isValidSource(Type.TYPE, woodenAxe), "axe is a valid TYPE source");
        assertTrue(matcher.isValidSource(Type.TYPE, woodenAxeDamaged), "damaged axe is a valid TYPE source");
        assertFalse(matcher.isValidSource(Type.TYPE, apple), "apple is not a tool");

        assertTrue(matcher.matches(Type.TYPE, woodenAxe, ironAxe, false), "wooden axe matches iron axe by tag");
        assertTrue(matcher.matches(Type.TYPE, woodenAxe, woodenAxeDamaged, false), "matches damaged variant");
        assertFalse(matcher.matches(Type.TYPE, woodenAxe, woodenShovel, false), "axe should not match shovel");
        assertFalse(matcher.matches(Type.TYPE, woodenAxe, apple, false), "axe should not match apple");

        helper.succeed();
    }

    public static void testTags(GameTestHelper helper) {
        ListMatchHandlerTags matcher = new ListMatchHandlerTags();
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack goldIngot = new ItemStack(Items.GOLD_INGOT);
        ItemStack ironBlock = new ItemStack(Items.IRON_BLOCK);
        ItemStack stick = new ItemStack(Items.STICK);

        assertTrue(matcher.isValidSource(Type.TYPE, ironIngot), "iron ingot has tags");
        assertTrue(matcher.isValidSource(Type.MATERIAL, ironIngot), "iron ingot has slash-tag for material");

        // TYPE: iron ingot is in c:ingots/iron, gold ingot is in c:ingots/gold — both share type "ingots"
        assertTrue(matcher.matches(Type.TYPE, ironIngot, goldIngot, false), "iron ingot TYPE matches gold ingot");
        // MATERIAL: iron ingot c:ingots/iron, iron block c:storage_blocks/iron — both share material "iron"
        assertTrue(matcher.matches(Type.MATERIAL, ironIngot, ironBlock, false), "iron ingot MATERIAL matches iron block");
        // TYPE: iron ingot vs iron block — types differ ("ingots" vs "storage_blocks")
        assertFalse(matcher.matches(Type.TYPE, ironIngot, ironBlock, false), "iron ingot TYPE differs from iron block");
        // MATERIAL: iron ingot vs gold ingot — materials differ ("iron" vs "gold")
        assertFalse(matcher.matches(Type.MATERIAL, ironIngot, goldIngot, false), "iron ingot MATERIAL differs from gold ingot");
        // No relevant overlap with stick
        assertFalse(matcher.matches(Type.TYPE, ironIngot, stick, false), "iron ingot TYPE does not match stick");

        helper.succeed();
    }

    /** Regression: an enchanted exemplar in Precise (no Variations / no Equivalents) mode must
     * round-trip its components through {@link ListHandler#saveLines}/{@link ListHandler#getLines}.
     * Before the ItemStack.CODEC fix, the saved tag dropped components, so the exemplar reloaded
     * as a vanilla unenchanted item — Precise then matched plain pickaxes (the loaded form) and
     * rejected the originally-placed Fortune III pickaxe. */
    public static void testPreciseEnchantmentRoundTrip(GameTestHelper helper) {
        if (ListRegistry.getHandlers().isEmpty()) {
            VanillaListHandlers.register();
        }

        // Build the enchanted exemplar via the registry-aware enchant() helper so the Holder
        // resolution mirrors how a player-crafted enchanted pickaxe is constructed.
        ItemStack fortunePick = new ItemStack(Items.DIAMOND_PICKAXE);
        net.minecraft.core.RegistryAccess registries = helper.getLevel().registryAccess();
        net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> fortune =
                registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE);
        fortunePick.enchant(fortune, 3);

        ItemStack list = new ItemStack(Items.PAPER);
        ListHandler.Line[] lines = ListHandler.getLines(list);
        lines[0].stacks.set(0, fortunePick.copy());
        lines[0].precise = true;
        lines[0].byType = false;
        lines[0].byMaterial = false;
        ListHandler.saveLines(list, lines);

        // After save+reload, the exemplar must still carry the Fortune III enchantment.
        ItemStack plainPick = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack matchingFortune = fortunePick.copy();

        assertTrue(ListHandler.matches(list, matchingFortune),
                "Precise list with Fortune III pickaxe should match an identical Fortune III pickaxe");
        assertFalse(ListHandler.matches(list, plainPick),
                "Precise list with Fortune III pickaxe should NOT match a plain unenchanted pickaxe");

        helper.succeed();
    }

    /** Both byType + byMaterial selected = union of matches (was: silently drop byMaterial because
     * the dispatcher's {@code byType ? TYPE : MATERIAL} ternary picked TYPE first). Iron ingot
     * with both flags should match BOTH gold ingot (TYPE: {@code c:ingots/<x>}) AND iron block
     * (MATERIAL: {@code c:<x>/iron}) — neither alone covers both. */
    public static void testEndToEndBothFlagsUnion(GameTestHelper helper) {
        if (ListRegistry.getHandlers().isEmpty()) {
            VanillaListHandlers.register();
        }

        ItemStack list = new ItemStack(Items.PAPER);
        ListHandler.Line[] lines = ListHandler.getLines(list);
        lines[0].stacks.set(0, new ItemStack(Items.IRON_INGOT));
        lines[0].byType = true;
        lines[0].byMaterial = true;
        ListHandler.saveLines(list, lines);

        // TYPE-driven match: gold ingot is in c:ingots/gold, shares TYPE "ingots"
        assertTrue(ListHandler.matches(list, new ItemStack(Items.GOLD_INGOT)),
                "iron-ingot list (TYPE+MATERIAL) should match gold ingot via TYPE part");
        // MATERIAL-driven match: iron block is in c:storage_blocks/iron, shares MATERIAL "iron"
        assertTrue(ListHandler.matches(list, new ItemStack(Items.IRON_BLOCK)),
                "iron-ingot list (TYPE+MATERIAL) should match iron block via MATERIAL part");
        // Sticks share neither
        assertFalse(ListHandler.matches(list, new ItemStack(Items.STICK)),
                "iron-ingot list (TYPE+MATERIAL) should NOT match stick");

        helper.succeed();
    }

    /** Single-segment tag fallback: cherry planks and oak planks both carry only single-segment
     * {@code minecraft:planks} (no slash), but MATERIAL mode should still match them through
     * shared full-tag-path treatment — that's the regression the user hit when "Accept
     * Equivalents" only matched the exact input. */
    public static void testTagsSingleSegmentMaterialFallback(GameTestHelper helper) {
        ListMatchHandlerTags matcher = new ListMatchHandlerTags();
        ItemStack oakPlanks = new ItemStack(Items.OAK_PLANKS);
        ItemStack birchPlanks = new ItemStack(Items.BIRCH_PLANKS);
        ItemStack oakStairs = new ItemStack(Items.OAK_STAIRS);
        ItemStack stick = new ItemStack(Items.STICK);

        // Sanity: source still recognized in MATERIAL even without any slash-tags
        assertTrue(matcher.isValidSource(Type.MATERIAL, oakPlanks),
                "oak planks should be a valid MATERIAL source via single-segment tag fallback");

        // The fix: both planks share minecraft:planks → MATERIAL match via full-path treatment
        assertTrue(matcher.matches(Type.MATERIAL, oakPlanks, birchPlanks, false),
                "oak planks MATERIAL should match birch planks via shared minecraft:planks tag");
        // Also TYPE works, same reason
        assertTrue(matcher.matches(Type.TYPE, oakPlanks, birchPlanks, false),
                "oak planks TYPE should match birch planks via shared minecraft:planks tag");

        // Cross item-type within same material is NOT supported (no shared tag exists)
        assertFalse(matcher.matches(Type.MATERIAL, oakPlanks, oakStairs, false),
                "oak planks MATERIAL should NOT match oak stairs (no shared tag in vanilla)");

        // Sticks still excluded
        assertFalse(matcher.matches(Type.MATERIAL, oakPlanks, stick, false),
                "oak planks MATERIAL should not match stick");

        helper.succeed();
    }

    /** Regression: {@code minecraft:completes_find_tree_tutorial} is a vanilla grouping tag that
     * lumps logs, leaves, and wart blocks together (it drives the "punch a tree" tutorial hint).
     * Because a log and a leaf share only that single tag, it used to bridge them — an oak-log
     * exemplar matched (and auto-filled examples with) oak leaves in both Accept Variations (TYPE)
     * and Accept Equivalents (MATERIAL) mode. The Tags handler now ignores it, so logs and leaves
     * are unrelated again while genuine log-to-log equivalence still holds. */
    public static void testTagsIgnoreTreeTutorialTag(GameTestHelper helper) {
        ListMatchHandlerTags matcher = new ListMatchHandlerTags();
        ItemStack oakLog = new ItemStack(Items.OAK_LOG);
        ItemStack birchLog = new ItemStack(Items.BIRCH_LOG);
        ItemStack oakLeaves = new ItemStack(Items.OAK_LEAVES);

        // Control: logs still match other logs (they share minecraft:logs / minecraft:logs_that_burn,
        // not just the ignored tutorial tag), in both modes.
        assertTrue(matcher.matches(Type.MATERIAL, oakLog, birchLog, false),
                "oak log MATERIAL should still match birch log via shared log tags");
        assertTrue(matcher.matches(Type.TYPE, oakLog, birchLog, false),
                "oak log TYPE should still match birch log via shared log tags");

        // The fix: the only tag a log and a leaf share is completes_find_tree_tutorial — once it's
        // ignored there is no shared part, so they must not match in either mode (either direction).
        assertFalse(matcher.matches(Type.MATERIAL, oakLog, oakLeaves, false),
                "oak log MATERIAL must NOT match oak leaves (completes_find_tree_tutorial ignored)");
        assertFalse(matcher.matches(Type.TYPE, oakLog, oakLeaves, false),
                "oak log TYPE must NOT match oak leaves (completes_find_tree_tutorial ignored)");
        assertFalse(matcher.matches(Type.MATERIAL, oakLeaves, oakLog, false),
                "oak leaves MATERIAL must NOT match oak log (symmetric)");

        helper.succeed();
    }

    /** Regression: NeoForge convention tags use the {@code c:<family>/<variant>} shape, and the
     * plain member of each family lives under {@code .../normal} — {@code c:bricks/normal},
     * {@code c:cobblestones/normal}, {@code c:pumpkins/normal}, {@code c:obsidians/normal}. The
     * MATERIAL split keys off the after-slash segment, so a baked brick, cobblestone and a pumpkin
     * all resolved to material "normal" and matched each other in Accept Equivalents. "normal" is a
     * form qualifier, not a material, so it's now dropped from MATERIAL parts — while a genuine
     * material leaf ({@code c:ingots/iron}) is untouched. */
    public static void testTagsNormalVariantNotMaterial(GameTestHelper helper) {
        ListMatchHandlerTags matcher = new ListMatchHandlerTags();
        ItemStack brick = new ItemStack(Items.BRICK);
        ItemStack cobblestone = new ItemStack(Items.COBBLESTONE);
        ItemStack pumpkin = new ItemStack(Items.PUMPKIN);

        // The bug: shared "normal" leaf must no longer bridge these unrelated families in MATERIAL.
        assertFalse(matcher.matches(Type.MATERIAL, brick, cobblestone, false),
                "baked brick MATERIAL must NOT match cobblestone (shared 'normal' qualifier is not a material)");
        assertFalse(matcher.matches(Type.MATERIAL, cobblestone, pumpkin, false),
                "cobblestone MATERIAL must NOT match pumpkin");
        assertFalse(matcher.matches(Type.MATERIAL, pumpkin, brick, false),
                "pumpkin MATERIAL must NOT match baked brick");

        // With no real material, cobblestone is not a MATERIAL exemplar — it falls through to
        // identity matching. Its only convention material tag is c:cobblestones/normal (the "normal"
        // form qualifier is dropped). On MC 26.2 cobblestone ALSO carries the vanilla gameplay tag
        // minecraft:sulfur_cube_archetype/slow_bouncy, whose "/" is NOT a c: family/material split,
        // so it's correctly ignored (see ListMatchHandlerTags.partOf) rather than read as material
        // "slow_bouncy" — without that, cobblestone would wrongly become a material source on 26.2.
        assertFalse(matcher.isValidSource(Type.MATERIAL, cobblestone),
                "cobblestone has no real material part, so it is not a MATERIAL source");

        // Control: a genuine material leaf still matches across forms (c:ingots/iron ↔ c:storage_blocks/iron).
        assertTrue(matcher.matches(Type.MATERIAL, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_BLOCK), false),
                "iron ingot MATERIAL still matches iron block (real material 'iron' unaffected)");

        // End-to-end through the registered handlers: a cobblestone Accept-Equivalents list now
        // matches only cobblestone (identity fallback), not every other "normal" block.
        if (ListRegistry.getHandlers().isEmpty()) {
            VanillaListHandlers.register();
        }
        ItemStack list = new ItemStack(Items.PAPER);
        ListHandler.Line[] lines = ListHandler.getLines(list);
        lines[0].stacks.set(0, new ItemStack(Items.COBBLESTONE));
        lines[0].byMaterial = true;
        ListHandler.saveLines(list, lines);
        assertTrue(ListHandler.matches(list, new ItemStack(Items.COBBLESTONE)),
                "cobblestone Accept-Equivalents list matches cobblestone (identity fallback)");
        assertFalse(ListHandler.matches(list, new ItemStack(Items.BRICK)),
                "cobblestone Accept-Equivalents list must NOT match baked brick");
        assertFalse(ListHandler.matches(list, new ItemStack(Items.PUMPKIN)),
                "cobblestone Accept-Equivalents list must NOT match pumpkin");

        helper.succeed();
    }

    public static void testArmor(GameTestHelper helper) {
        ListMatchHandlerArmor matcher = new ListMatchHandlerArmor();
        ItemStack ironHelmet = new ItemStack(Items.IRON_HELMET);
        ItemStack diamondHelmet = new ItemStack(Items.DIAMOND_HELMET);
        ItemStack ironChestplate = new ItemStack(Items.IRON_CHESTPLATE);
        ItemStack stick = new ItemStack(Items.STICK);

        assertTrue(matcher.isValidSource(Type.TYPE, ironHelmet), "helmet is a valid TYPE source");
        assertFalse(matcher.isValidSource(Type.TYPE, stick), "stick is not equippable");

        assertTrue(matcher.matches(Type.TYPE, ironHelmet, diamondHelmet, false), "any helmet matches any helmet");
        assertFalse(matcher.matches(Type.TYPE, ironHelmet, ironChestplate, false), "helmet should not match chestplate");
        assertFalse(matcher.matches(Type.TYPE, ironHelmet, stick, false), "helmet should not match stick");

        helper.succeed();
    }

    public static void testFluid(GameTestHelper helper) {
        ListMatchHandlerFluid matcher = new ListMatchHandlerFluid();
        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
        ItemStack lavaBucket = new ItemStack(Items.LAVA_BUCKET);
        ItemStack emptyBucket = new ItemStack(Items.BUCKET);
        ItemStack stick = new ItemStack(Items.STICK);

        assertTrue(matcher.isValidSource(Type.TYPE, waterBucket), "water bucket exposes fluid cap");
        assertTrue(matcher.isValidSource(Type.TYPE, emptyBucket), "empty bucket exposes fluid cap (TYPE)");
        assertTrue(matcher.isValidSource(Type.MATERIAL, waterBucket), "water bucket has fluid contents (MATERIAL)");
        assertFalse(matcher.isValidSource(Type.MATERIAL, emptyBucket), "empty bucket has no fluid (MATERIAL)");
        assertFalse(matcher.isValidSource(Type.TYPE, stick), "stick has no fluid cap");

        // TYPE accepts any fluid container (intentional: "is a fluid container" filter)
        assertTrue(matcher.matches(Type.TYPE, waterBucket, lavaBucket, false), "any bucket matches any bucket by TYPE");
        assertTrue(matcher.matches(Type.TYPE, waterBucket, emptyBucket, false), "water bucket TYPE matches empty bucket");
        // MATERIAL distinguishes water from lava
        assertTrue(matcher.matches(Type.MATERIAL, waterBucket, waterBucket, false), "water matches water by MATERIAL");
        assertFalse(matcher.matches(Type.MATERIAL, waterBucket, lavaBucket, false), "water differs from lava by MATERIAL");
        assertFalse(matcher.matches(Type.TYPE, waterBucket, stick, false), "stick is not a fluid container");

        helper.succeed();
    }

    /** Integration: build an actual list ItemStack with a saved Line, check end-to-end matching
     * via {@link ListHandler#matches(ItemStack, ItemStack)}, going through the registered handlers. */
    public static void testEndToEndByType(GameTestHelper helper) {
        // Ensure handlers are registered (in case test runs before BCCore.init in some harness).
        if (ListRegistry.getHandlers().isEmpty()) {
            VanillaListHandlers.register();
        }

        ItemStack list = new ItemStack(Items.PAPER); // any item carrying CustomData works for the test
        ListHandler.Line[] lines = ListHandler.getLines(list);
        lines[0].stacks.set(0, new ItemStack(Items.IRON_INGOT));
        lines[0].byType = true;
        ListHandler.saveLines(list, lines);

        assertTrue(ListHandler.matches(list, new ItemStack(Items.GOLD_INGOT)), "iron-ingot list (TYPE) should match gold ingot");
        assertTrue(ListHandler.matches(list, new ItemStack(Items.COPPER_INGOT)), "iron-ingot list (TYPE) should match copper ingot");
        assertFalse(ListHandler.matches(list, new ItemStack(Items.STICK)), "iron-ingot list (TYPE) should NOT match stick");
        assertFalse(ListHandler.matches(list, new ItemStack(Items.IRON_BLOCK)), "iron-ingot list (TYPE) should NOT match iron block (different type)");

        helper.succeed();
    }
}
