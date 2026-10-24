/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicEntity;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * An entity in a blueprint must be one the shipped JSON rules sanction (capture whitelist or a price) and must
 * never carry loot-table keys — an unsanctioned entity would {@code build()} from its NBT verbatim while costing
 * nothing, and a structure chest minecart's loot pair would mint loot. Unsanctioned entities DROP out of the
 * blueprint at read time rather than refusing the whole file, so a modpack removing a rule cannot brick every
 * stored blueprint that happens to contain that entity.
 */
public class SchematicEntityDefaultTester extends VanillaSetupBaseTester {

    /** A schematic entity's serialized NBT wrapping an entity NBT with the given id — exactly the untrusted shape. */
    private static CompoundTag entitySchematicNbt(String entityId) {
        CompoundTag entityNbt = new CompoundTag();
        if (entityId != null) {
            entityNbt.putString("id", entityId);
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("entityNbt", entityNbt);
        nbt.put("pos", NBTUtilBC.writeVec3(new Vec3(0.5, 0, 0.5)));
        nbt.put("hangingPos", NBTUtilBC.writeBlockPos(BlockPos.ZERO));
        nbt.put("hangingFacing", NBTUtilBC.writeEnum(Direction.NORTH));
        nbt.put("entityRotation", NBTUtilBC.writeEnum(Rotation.NONE));
        return nbt;
    }

    /** An empty one-cell blueprint's NBT with {@code entity} spliced into its entity list through the real factory
     *  envelope, so the read exercises the gate, not the envelope. */
    private static CompoundTag blueprintHolding(SchematicEntityDefault entity) {
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, 1);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(new SchematicBlockAir());
        blueprint.data = new int[] {0};
        blueprint.computeKey();
        CompoundTag nbt = Snapshot.writeToNBT(blueprint);
        nbt.put("entities", NBTUtilBC.writeCompoundList(Stream.of(SchematicEntityManager.writeToNBT(entity))));
        return nbt;
    }

    private static CompoundTag stackNbt(String itemId) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", itemId);
        stack.putInt("count", 1);
        return stack;
    }

    @Test
    public void anEntityTheRulesSanctionStillReads() {
        // Sanity first: the rules must be loaded in this JVM (the mod's common setup loads them), or the gate
        // below would drop every entity and this test would say nothing about which ones are unsanctioned.
        Assertions.assertFalse(
            RulesLoader.getRules(net.minecraft.resources.Identifier.parse("minecraft:armor_stand"), new CompoundTag()).isEmpty(),
            "no rules for minecraft:armor_stand — is RulesLoader.loadAll() not running in this environment?"
        );
        Assertions.assertTrue(SchematicEntityDefault.isPricedByRules(
            NBTUtilBC.getCompound(entitySchematicNbt("minecraft:armor_stand"), "entityNbt")));
    }

    @Test
    public void anUnsanctionedEntityIsDroppedNotRefusedWholeFile() throws InvalidInputDataException {
        // The blueprint path is what an Electronic Library upload unpacks: the entity must not survive it, but the
        // blueprint itself must still load (drop, don't brick the file). The entity tag is built through the real
        // factory envelope (SchematicEntityManager.writeToNBT) so the read exercises the gate, not the envelope.
        SchematicEntityDefault creeper = new SchematicEntityDefault();
        creeper.deserializeNbt(entitySchematicNbt("minecraft:creeper"));
        Assertions.assertFalse(SchematicEntityDefault.isPricedByRules(creeper.entityNbt));

        Snapshot read = Snapshot.readFromNBT(blueprintHolding(creeper));
        Assertions.assertInstanceOf(Blueprint.class, read);
        Assertions.assertTrue(((Blueprint) read).entities.isEmpty(), "the unsanctioned entity must be dropped");
    }

    @Test
    public void aSanctionedEntitySurvivesTheBlueprintRead() throws InvalidInputDataException {
        // The positive half of the gate above: an inverted gate would drop the creeper there and pass.
        SchematicEntityDefault armorStand = new SchematicEntityDefault();
        armorStand.deserializeNbt(entitySchematicNbt("minecraft:armor_stand"));
        Snapshot read = Snapshot.readFromNBT(blueprintHolding(armorStand));
        Assertions.assertInstanceOf(Blueprint.class, read);
        Assertions.assertEquals(1, ((Blueprint) read).entities.size(), "the armor stand must stay in the blueprint");
    }

    @Test
    public void anArmorStandsListedGearIsChargedAndItsEmptySlotsAreNot() throws InvalidInputDataException {
        // MC 1.21.1 saves an armor stand's gear as the ArmorItems (feet, legs, chest, head) and HandItems lists, an
        // empty slot as an empty compound; later lines save an "equipment" map instead. build() spawns the entity
        // from this NBT, so the lists must be priced wherever they appear.
        CompoundTag schematic = entitySchematicNbt("minecraft:armor_stand");
        CompoundTag entityNbt = NBTUtilBC.getCompound(schematic, "entityNbt");
        ListTag armor = new ListTag();
        armor.add(new CompoundTag());
        armor.add(new CompoundTag());
        armor.add(stackNbt("minecraft:diamond_chestplate"));
        armor.add(new CompoundTag());
        entityNbt.put("ArmorItems", armor);
        ListTag hands = new ListTag();
        hands.add(stackNbt("minecraft:diamond_sword"));
        hands.add(new CompoundTag());
        entityNbt.put("HandItems", hands);

        SchematicEntityDefault armorStand = new SchematicEntityDefault();
        armorStand.deserializeNbt(schematic);
        List<ItemStack> required = armorStand.computeRequiredItems();

        Assertions.assertEquals(3, required.size(), "the stand, the chestplate and the sword, nothing for the empty slots: " + required);
        Assertions.assertTrue(required.stream().anyMatch(stack -> stack.is(Items.ARMOR_STAND)), required.toString());
        Assertions.assertTrue(required.stream().anyMatch(stack -> stack.is(Items.DIAMOND_CHESTPLATE)), required.toString());
        Assertions.assertTrue(required.stream().anyMatch(stack -> stack.is(Items.DIAMOND_SWORD)), required.toString());
    }

    @Test
    public void anEntityNbtWithoutAnIdOrWithAnUnparseableOneIsUnsanctioned() {
        Assertions.assertFalse(SchematicEntityDefault.isPricedByRules(
            NBTUtilBC.getCompound(entitySchematicNbt(null), "entityNbt")));
        Assertions.assertFalse(SchematicEntityDefault.isPricedByRules(
            NBTUtilBC.getCompound(entitySchematicNbt("not an id!"), "entityNbt")));
    }

    @Test
    public void entityLootTableKeysAreStrippedOnRead() throws InvalidInputDataException {
        // A structure chest minecart carries the loot pair in its entity NBT exactly as container block entities
        // do — the entity path gets the same strip the tileNbt path gets.
        CompoundTag schematic = entitySchematicNbt("minecraft:chest_minecart");
        CompoundTag entityNbt = NBTUtilBC.getCompound(schematic, "entityNbt");
        entityNbt.putString(RandomizableContainer.LOOT_TABLE_TAG, "minecraft:chests/simple_dungeon");
        entityNbt.putLong(RandomizableContainer.LOOT_TABLE_SEED_TAG, 12345L);

        SchematicEntityDefault read = new SchematicEntityDefault();
        read.deserializeNbt(schematic);

        Assertions.assertFalse(read.entityNbt.contains(RandomizableContainer.LOOT_TABLE_TAG));
        Assertions.assertFalse(read.entityNbt.contains(RandomizableContainer.LOOT_TABLE_SEED_TAG));
    }

    /** A schematic of the entity with the given id, its captured NBT filled in by {@code fill}. */
    private static SchematicEntityDefault schematicOf(String entityId, Consumer<CompoundTag> fill)
            throws InvalidInputDataException {
        CompoundTag schematic = entitySchematicNbt(entityId);
        fill.accept(NBTUtilBC.getCompound(schematic, "entityNbt"));
        SchematicEntityDefault entity = new SchematicEntityDefault();
        entity.deserializeNbt(schematic);
        return entity;
    }

    private static boolean charges(List<ItemStack> required, Item item) {
        return required.stream().anyMatch(stack -> stack.is(item));
    }

    @Test
    public void anItemFramesItemIsChargedAndPlacedOnlyWhenContentsAreIncluded() throws InvalidInputDataException {
        SchematicEntityDefault frame = schematicOf("minecraft:item_frame",
            nbt -> nbt.put("Item", stackNbt("minecraft:diamond")));

        // The plain pricing is the include-contents one.
        Assertions.assertEquals(2, frame.computeRequiredItems().size(), frame.computeRequiredItems().toString());
        List<ItemStack> included = frame.computeRequiredItems(true);
        Assertions.assertTrue(charges(included, Items.ITEM_FRAME), included.toString());
        Assertions.assertTrue(charges(included, Items.DIAMOND), included.toString());
        Assertions.assertTrue(frame.entityNbtToPlace(BlockPos.ZERO, true).contains("Item"),
            "the included frame must spawn holding its item");

        // IGNORE: the frame itself is still owed (it is the entity, never contents); its item is neither charged
        // nor placed.
        List<ItemStack> ignored = frame.computeRequiredItems(false);
        Assertions.assertEquals(1, ignored.size(), "only the frame: " + ignored);
        Assertions.assertTrue(charges(ignored, Items.ITEM_FRAME), ignored.toString());
        Assertions.assertFalse(frame.entityNbtToPlace(BlockPos.ZERO, false).contains("Item"),
            "the ignored frame must spawn empty");
        Assertions.assertTrue(frame.entityNbt.contains("Item"),
            "the captured NBT is shared across rebuilds and must keep the item");
    }

    @Test
    public void anArmorStandsGearIsNeitherChargedNorPlacedWhenContentsAreIgnored() throws InvalidInputDataException {
        // Both saved shapes at once: the ArmorItems/HandItems lists of MC 1.21.1 and the equipment map of later lines.
        SchematicEntityDefault stand = schematicOf("minecraft:armor_stand", nbt -> {
            ListTag armor = new ListTag();
            armor.add(new CompoundTag());
            armor.add(new CompoundTag());
            armor.add(stackNbt("minecraft:diamond_chestplate"));
            armor.add(new CompoundTag());
            nbt.put("ArmorItems", armor);
            ListTag hands = new ListTag();
            hands.add(stackNbt("minecraft:diamond_sword"));
            hands.add(new CompoundTag());
            nbt.put("HandItems", hands);
            CompoundTag equipment = new CompoundTag();
            equipment.put("head", stackNbt("minecraft:diamond_helmet"));
            nbt.put("equipment", equipment);
            nbt.putByte("ShowArms", (byte) 1);
        });

        List<ItemStack> included = stand.computeRequiredItems(true);
        Assertions.assertEquals(4, included.size(), "the stand, the chestplate, the sword, the helmet: " + included);
        CompoundTag filled = stand.entityNbtToPlace(BlockPos.ZERO, true);
        Assertions.assertTrue(filled.contains("ArmorItems") && filled.contains("HandItems") && filled.contains("equipment"));

        List<ItemStack> ignored = stand.computeRequiredItems(false);
        Assertions.assertEquals(1, ignored.size(), "only the stand: " + ignored);
        Assertions.assertTrue(charges(ignored, Items.ARMOR_STAND), ignored.toString());
        CompoundTag placed = stand.entityNbtToPlace(BlockPos.ZERO, false);
        Assertions.assertFalse(placed.contains("ArmorItems"), "worn gear must not be placed: " + placed);
        Assertions.assertFalse(placed.contains("HandItems"), "held items must not be placed: " + placed);
        Assertions.assertFalse(NBTUtilBC.getCompound(placed, "equipment").contains("head"),
            "equipment must not be placed: " + placed);
        Assertions.assertTrue(placed.contains("ShowArms"), "the stand's own settings stay: " + placed);
    }

    @Test
    public void aStorageMinecartsInventoryFollowsTheContentsMode() throws InvalidInputDataException {
        for (String cart : new String[] {"minecraft:chest_minecart", "minecraft:hopper_minecart"}) {
            SchematicEntityDefault minecart = schematicOf(cart, nbt -> {
                ListTag items = new ListTag();
                CompoundTag slot = stackNbt("minecraft:gold_ingot");
                slot.putByte("Slot", (byte) 0);
                items.add(slot);
                nbt.put("Items", items);
                nbt.putByte("Invulnerable", (byte) 1);
            });
            Assertions.assertTrue(charges(minecart.computeRequiredItems(true), Items.GOLD_INGOT), cart);
            Assertions.assertTrue(minecart.entityNbtToPlace(BlockPos.ZERO, true).contains("Items"), cart);

            List<ItemStack> ignored = minecart.computeRequiredItems(false);
            Assertions.assertEquals(1, ignored.size(), cart + " owes only the cart: " + ignored);
            Assertions.assertFalse(charges(ignored, Items.GOLD_INGOT), cart);
            CompoundTag placed = minecart.entityNbtToPlace(BlockPos.ZERO, false);
            Assertions.assertFalse(placed.contains("Items"), cart + " must spawn empty: " + placed);
            Assertions.assertTrue(placed.contains("Invulnerable"), cart + " keeps its other data: " + placed);
        }
    }

    @Test
    public void everyCapturableEntityStillOwesItselfWhenContentsAreIgnored() throws InvalidInputDataException {
        // IGNORE skips only the extractors that price what an entity holds; the entity itself is priced by a
        // constant extractor in each rule. A rule that lost its constant (or never had one: capture alone whitelists
        // an entity but prices nothing, and entities have no item_from_block fallback) would build that entity for
        // free in either mode, so the shipped rules must all carry one.
        List<String> capturable = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (!RulesLoader.READ_DOMAINS.contains(id.getNamespace())) continue;
            CompoundTag idOnly = new CompoundTag();
            idOnly.putString("id", id.toString());
            if (RulesLoader.getRules(id, idOnly).stream().noneMatch(rule -> rule.capture)) continue;
            capturable.add(id.toString());
            SchematicEntityDefault entity = new SchematicEntityDefault();
            entity.deserializeNbt(entitySchematicNbt(id.toString()));
            Assertions.assertFalse(entity.computeRequiredItems(false).isEmpty(),
                id + " is captured by a rule but owes nothing for itself once contents are ignored");
        }
        // Liveness: the loop must have met the entities the other tests lean on, or it checked nothing.
        Assertions.assertTrue(capturable.containsAll(List.of("minecraft:armor_stand", "minecraft:item_frame",
            "minecraft:chest_minecart")), "captured entities seen: " + capturable);
    }

    @Test
    public void aBlueprintsEntityCostIsRebakedWhenTheContentsModeChanges() throws InvalidInputDataException {
        SchematicEntityDefault frame = schematicOf("minecraft:item_frame",
            nbt -> nbt.put("Item", stackNbt("minecraft:diamond")));
        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, 1);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(new SchematicBlockAir());
        blueprint.data = new int[] {0};
        blueprint.entities.add(frame);
        blueprint.computeKey();

        Blueprint.BuildingInfo info = blueprint.new BuildingInfo(BlockPos.ZERO, Rotation.NONE);
        ISchematicEntity placed = info.entities.iterator().next();
        // A fresh building info prices everything (the Builder re-bakes it for IGNORE on load).
        Assertions.assertTrue(charges(info.entitiesRequiredItems.get(placed), Items.DIAMOND));

        info.refreshRequiredItemsForContentsMode(EnumContainerContentsMode.IGNORE);
        List<ItemStack> ignored = info.entitiesRequiredItems.get(placed);
        Assertions.assertEquals(1, ignored.size(), "only the frame once contents are ignored: " + ignored);
        Assertions.assertTrue(charges(ignored, Items.ITEM_FRAME));

        info.refreshRequiredItemsForContentsMode(EnumContainerContentsMode.INCLUDE);
        Assertions.assertTrue(charges(info.entitiesRequiredItems.get(placed), Items.DIAMOND),
            "toggling back must price the item again");
    }
}
