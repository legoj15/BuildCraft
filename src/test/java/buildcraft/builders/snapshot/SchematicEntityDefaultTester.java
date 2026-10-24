/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * An entity in a blueprint must be one the shipped JSON rules sanction (capture whitelist or a price) and must
 * never carry loot-table keys — an unsanctioned entity would {@code build()} from its NBT verbatim while costing
 * nothing, and a structure chest minecart's loot pair would mint loot. Unsanctioned entities DROP out of the
 * blueprint at read time rather than refusing the whole file, so a modpack removing a rule cannot brick every
 * stored blueprint that happens to contain that entity.
 */
public class SchematicEntityDefaultTester {

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

    @Test
    public void anEntityTheRulesSanctionStillReads() throws InvalidInputDataException {
        // Sanity first: the rules must be loaded in this JVM (the mod's common setup loads them), or the gate
        // below would drop every entity and this test would say nothing about which ones are unsanctioned.
        Assertions.assertFalse(
            RulesLoader.getRules(net.minecraft.resources.Identifier.parse("minecraft:armor_stand"), new CompoundTag()).isEmpty(),
            "no rules for minecraft:armor_stand — is RulesLoader.loadAll() not running in this environment?"
        );
        new SchematicEntityDefault().deserializeNbt(entitySchematicNbt("minecraft:armor_stand"));
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

        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, 1);
        blueprint.facing = Direction.NORTH;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(new SchematicBlockAir());
        blueprint.data = new int[] {0};
        blueprint.computeKey();
        CompoundTag nbt = Snapshot.writeToNBT(blueprint);
        nbt.put("entities", NBTUtilBC.writeCompoundList(java.util.stream.Stream.of(SchematicEntityManager.writeToNBT(creeper))));
        Snapshot read = Snapshot.readFromNBT(nbt);
        Assertions.assertInstanceOf(Blueprint.class, read);
        Assertions.assertTrue(((Blueprint) read).entities.isEmpty(), "the unsanctioned entity must be dropped");
    }

    @Test
    public void anEntityNbtWithoutAnIdOrWithAnUnparseableOneIsUnsanctioned() {
        Assertions.assertFalse(SchematicEntityDefault.isPricedByRules(
            NBTUtilBC.getCompound(entitySchematicNbt(null), "entityNbt")));
        Assertions.assertFalse(SchematicEntityDefault.isPricedByRules(
            NBTUtilBC.getCompound(entitySchematicNbt("not an id!"), "entityNbt")));
    }

    @Test
    public void anUnpricedEntityCostsNothingTodayWhichIsWhyTheGateDropsIt() {
        // Pins the reason for the gate: with no rule, computeRequiredItems is empty (free) while build() would
        // still spawn the entity from entityNbt.
        CompoundTag schematic = entitySchematicNbt("minecraft:creeper");
        CompoundTag entityNbt = NBTUtilBC.getCompound(schematic, "entityNbt");
        Assertions.assertTrue(RulesLoader.getRules(
            net.minecraft.resources.Identifier.parse("minecraft:creeper"), entityNbt).isEmpty());
        Assertions.assertFalse(SchematicEntityDefault.isPricedByRules(entityNbt));
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
