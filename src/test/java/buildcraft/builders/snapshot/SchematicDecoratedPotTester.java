/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
//? if >=1.21.10 {
import net.minecraft.util.ProblemReporter;
//?}
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=26.3 {
import net.minecraft.world.item.ItemStackTemplate;
//?}
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}

import buildcraft.VanillaSetupBaseTester;

/**
 * A decorated pot keeps the sherds it was scanned with, and a blueprint charges for them. A pot drops its sherds when
 * it is cracked (and keeps them as an item when broken whole), so a blueprint that rebuilt a pot with sides nothing
 * priced would mint whatever its data names: the sides are charged as the items the pot would drop, the plain pot is
 * charged by the rule's own {@code item_from_block}, and a blank side (a brick, which is what a plain pot is made of)
 * is part of that price and charged nothing.
 */
public class SchematicDecoratedPotTester extends VanillaSetupBaseTester {

    private static final BlockState POT = Blocks.DECORATED_POT.defaultBlockState();

    /** What a side holding more than one item keeps: only 26.3 saves a side as a stack that can carry a count. */
    //? if >=26.3 {
    private static final int SIDE_COUNT = 5;
    //?} else {
    /*private static final int SIDE_COUNT = 1;*/
    //?}

    /** The pot's four sides (back, left, right, front), an empty stack for a blank side. */
    private static PotDecorations decorations(ItemStack back, ItemStack left, ItemStack right, ItemStack front) {
        return new PotDecorations(side(back), side(left), side(right), side(front));
    }

    //? if >=26.3 {
    private static Optional<ItemStackTemplate> side(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.of(ItemStackTemplate.fromNonEmptyStack(stack));
    }
    //?} else {
    /*private static Optional<Item> side(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.getItem());
    }*/
    //?}

    /** A pot's save with all four sides written out as bricks, the blank a plain pot has on every node (26.3 saves the
     *  bricks as stacks; the other lines write a list of item ids, which loads the same). */
    private static Tag allBrickSides() {
        //? if >=26.3 {
        CompoundTag sides = new CompoundTag();
        for (String name : List.of("back", "left", "right", "front")) {
            CompoundTag brick = new CompoundTag();
            brick.putString("id", "minecraft:brick");
            sides.put(name, brick);
        }
        return sides;
        //?} else {
        /*ListTag sides = new ListTag();
        for (int i = 0; i < 4; i++) {
            sides.add(StringTag.valueOf("minecraft:brick"));
        }
        return sides;*/
        //?}
    }

    /** A pot's sides with an angler sherd on the back and, beside it, a side naming an item that does not exist, in the
     *  node's own save format. */
    private static Tag sidesWithAnUnknownItem() {
        //? if >=26.3 {
        CompoundTag sides = new CompoundTag();
        CompoundTag angler = new CompoundTag();
        angler.putString("id", "minecraft:angler_pottery_sherd");
        sides.put("back", angler);
        CompoundTag unknown = new CompoundTag();
        unknown.putString("id", "minecraft:not_an_item");
        sides.put("left", unknown);
        return sides;
        //?} else {
        /*ListTag sides = new ListTag();
        sides.add(StringTag.valueOf("minecraft:angler_pottery_sherd"));
        sides.add(StringTag.valueOf("minecraft:not_an_item"));
        return sides;*/
        //?}
    }

    private static void load(DecoratedPotBlockEntity pot, CompoundTag tag) {
        //? if >=1.21.10 {
        pot.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, vanillaLookup(), tag));
        //?} else {
        /*pot.loadWithComponents(tag, vanillaLookup());*/
        //?}
    }

    /** The data the Architect captures for a pot carrying {@code sherds} (the node's own save format): the real block
     *  entity loads it and saves, the capture then runs it through the same sanitizing. */
    private static CompoundTag captured(Tag sherds) {
        DecoratedPotBlockEntity pot = new DecoratedPotBlockEntity(BlockPos.ZERO, POT);
        CompoundTag saved = new CompoundTag();
        saved.put("sherds", sherds);
        load(pot, saved);
        return SchematicBlockDefault.sanitizeTileNbt(pot.saveWithoutMetadata(vanillaLookup()), POT);
    }

    private static Tag encoded(PotDecorations decorations) {
        return PotDecorations.CODEC.encodeStart(NbtOps.INSTANCE, decorations).getOrThrow();
    }

    private static SchematicBlockDefault schematic(CompoundTag tileNbt) {
        SchematicBlockDefault schematic = new SchematicBlockDefault();
        schematic.blockState = POT;
        schematic.placeBlock = Blocks.DECORATED_POT;
        schematic.tileRotation = Rotation.NONE;
        schematic.tileNbt = tileNbt;
        schematic.canBeReplacedWithBlocks.add(Blocks.DECORATED_POT);
        return schematic;
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    @Test
    public void aScannedPotKeepsItsSherdsAndChargesForThem() {
        PotDecorations decorations = decorations(new ItemStack(Items.ANGLER_POTTERY_SHERD), ItemStack.EMPTY,
            ItemStack.EMPTY, new ItemStack(Items.SHEAF_POTTERY_SHERD));
        CompoundTag tileNbt = captured(encoded(decorations));
        Assertions.assertTrue(tileNbt.contains("sherds"), "the capture must keep the sherds: " + tileNbt);

        SchematicBlockDefault schematic = schematic(tileNbt);
        for (boolean includeContents : new boolean[] {true, false}) {
            // The sides are decoration, not what the pot holds: the IGNORE contents mode charges for them too.
            List<ItemStack> required = schematic.computeRequiredItems(includeContents);
            Assertions.assertEquals(3, required.size(), "the pot and its two sherds, nothing for the blank sides: " + required);
            Assertions.assertEquals(1, count(required, Items.DECORATED_POT), required.toString());
            Assertions.assertEquals(1, count(required, Items.ANGLER_POTTERY_SHERD), required.toString());
            Assertions.assertEquals(1, count(required, Items.SHEAF_POTTERY_SHERD), required.toString());
        }

        // And the rebuilt pot carries them: the data the Builder loads into the placed block entity.
        DecoratedPotBlockEntity rebuilt = new DecoratedPotBlockEntity(BlockPos.ZERO, POT);
        load(rebuilt, schematic.tileNbtToPlace(true));
        Assertions.assertEquals(decorations, rebuilt.getDecorations(), "the rebuilt pot must wear the scanned sherds");
    }

    @Test
    public void aPlainPotCostsOnlyThePot() {
        // A plain pot saves no sides on the older lines and four bricks on 26.3; either way the bricks are what the
        // pot itself is made of, already in its price.
        for (CompoundTag tileNbt : List.of(captured(allBrickSides()), new CompoundTag())) {
            List<ItemStack> required = schematic(tileNbt).computeRequiredItems();
            Assertions.assertEquals(1, required.size(), "only the pot: " + required + " for " + tileNbt);
            Assertions.assertTrue(required.get(0).is(Items.DECORATED_POT), required.toString());
        }
        CompoundTag bricks = new CompoundTag();
        bricks.put("sherds", allBrickSides());
        List<ItemStack> required = schematic(bricks).computeRequiredItems();
        Assertions.assertEquals(1, required.size(), "blank sides written out cost nothing either: " + required);
    }

    @Test
    public void aSideHoldingAnyItemIsChargedForIt() {
        // The sides are not limited to sherds: the pot drops whatever they name, so a side holding diamonds must
        // cost diamonds (as many as the node can save in one side), never come free.
        PotDecorations decorations = decorations(new ItemStack(Items.DIAMOND, SIDE_COUNT), ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY);
        List<ItemStack> required = schematic(captured(encoded(decorations))).computeRequiredItems();
        Assertions.assertEquals(SIDE_COUNT, count(required, Items.DIAMOND), required.toString());
        Assertions.assertEquals(1, count(required, Items.DECORATED_POT), required.toString());

        // Data the node would not load as sides (the wrong shape for the line, a side that is not an item) prices
        // nothing, and loads as a plain pot.
        CompoundTag garbage = new CompoundTag();
        garbage.putString("sherds", "not sides");
        Assertions.assertEquals(1, schematic(garbage).computeRequiredItems().size());
        ListTag junk = new ListTag();
        junk.add(StringTag.valueOf("not an item!"));
        CompoundTag junkNbt = new CompoundTag();
        junkNbt.put("sherds", junk);
        Assertions.assertEquals(1, schematic(junkNbt).computeRequiredItems().size());

        // A real side beside one that is not an item: what survives depends on the node (1.21.1 drops the whole list,
        // the later lines keep the sides that decode), so the price must be exactly what the placed pot wears, no
        // sherd charged that the pot never gets and none worn that was never charged.
        CompoundTag mixed = new CompoundTag();
        mixed.put("sherds", sidesWithAnUnknownItem());
        SchematicBlockDefault mixedSchematic = schematic(mixed);
        DecoratedPotBlockEntity placed = new DecoratedPotBlockEntity(BlockPos.ZERO, POT);
        load(placed, mixedSchematic.tileNbtToPlace(true));
        int worn = placed.getDecorations().equals(PotDecorations.EMPTY) ? 0 : 1;
        List<ItemStack> mixedRequired = mixedSchematic.computeRequiredItems();
        Assertions.assertEquals(worn, count(mixedRequired, Items.ANGLER_POTTERY_SHERD),
            "the sherd is charged exactly when the placed pot wears it (worn: " + placed.getDecorations() + "): " + mixedRequired);
    }
}
