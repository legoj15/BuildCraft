/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.InvalidInputDataException;

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
}
