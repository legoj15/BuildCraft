/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
//? if >=1.21.10 {
import net.minecraft.util.ProblemReporter;
//?}
import net.minecraft.world.entity.Entity;
//? if >=1.21.10 {
import net.minecraft.world.entity.EntitySpawnReason;
//?}
//? if >=26.2 {
import net.minecraft.world.entity.EntitySpawnRequest;
//?}
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
//?}
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicEntity;
import buildcraft.api.schematics.SchematicEntityContext;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.RotationUtil;

public class SchematicEntityDefault implements ISchematicEntity {
    private static final org.apache.logging.log4j.Logger LOGGER =
        org.apache.logging.log4j.LogManager.getLogger("BCBlueprint");

    /** Package-private: the blueprint read loop names the id in its drop warning. */
    CompoundTag entityNbt;
    private Vec3 pos;
    private BlockPos hangingPos;
    private Direction hangingFacing;
    private Rotation entityRotation = Rotation.NONE;
    /** Set once {@link #build} found that the entity's own loader throws on {@link #entityNbt}: later calls give up
     *  at once, so a Builder retrying the spawn logs once instead of every tick. Not part of the schematic's value. */
    private boolean loadFailed;

    public static boolean predicate(SchematicEntityContext context) {
        Identifier registryName = BuiltInRegistries.ENTITY_TYPE.getKey(context.entity.getType());
        if (registryName == null) return false;
        if (!RulesLoader.READ_DOMAINS.contains(registryName.getNamespace())) return false;
        //? if >=1.21.10 {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, context.world.registryAccess());
        context.entity.save(output);
        CompoundTag entityNbt = output.buildResult();
        //?} else {
        /*CompoundTag entityNbt = new CompoundTag();
        context.entity.save(entityNbt);*/
        //?}
        return RulesLoader.getRules(registryName, entityNbt)
            .stream()
            .anyMatch(rule -> rule.capture);
    }

    @Override
    public void init(SchematicEntityContext context) {
        //? if >=1.21.10 {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, context.world.registryAccess());
        context.entity.save(output);
        entityNbt = output.buildResult();
        //?} else {
        /*entityNbt = new CompoundTag();
        context.entity.save(entityNbt);*/
        //?}
        pos = context.entity.position().subtract(Vec3.atLowerCornerOf(context.basePos));
        if (context.entity instanceof HangingEntity hangingEntity) {
            hangingPos = hangingEntity.getPos().subtract(context.basePos);
            hangingFacing = hangingEntity.getDirection();
        } else {
            hangingPos = BlockPos.containing(pos);
            hangingFacing = Direction.NORTH;
        }
    }

    @Override
    public Vec3 getPos() {
        return pos;
    }

    @Nonnull
    @Override
    public List<ItemStack> computeRequiredItems() {
        Identifier entityId = Identifier.parse(NBTUtilBC.getString(entityNbt, "id", ""));
        Set<JsonRule> rules = RulesLoader.getRules(entityId, entityNbt);
        if (rules.isEmpty()) {
            return Collections.emptyList();
        }
        return rules.stream()
            .map(rule -> rule.requiredExtractors)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .flatMap(extractor -> extractor.extractItemsFromEntity(entityNbt).stream())
            .filter(stack -> !stack.isEmpty())
            .collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public List<FluidStack> computeRequiredFluids() {
        Identifier entityId = Identifier.parse(NBTUtilBC.getString(entityNbt, "id", ""));
        Set<JsonRule> rules = RulesLoader.getRules(entityId, entityNbt);
        return rules.stream()
            .map(rule -> rule.requiredExtractors)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .flatMap(extractor -> extractor.extractFluidsFromEntity(entityNbt).stream())
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public SchematicEntityDefault getRotated(Rotation rotation) {
        SchematicEntityDefault schematicEntity = SchematicEntityManager.createCleanCopy(this);
        schematicEntity.entityNbt = entityNbt;
        schematicEntity.pos = RotationUtil.rotateVec3(pos, rotation);
        schematicEntity.hangingPos = hangingPos.rotate(rotation);
        schematicEntity.hangingFacing = rotation.rotate(hangingFacing);
        schematicEntity.entityRotation = entityRotation.getRotated(rotation);
        return schematicEntity;
    }

    @Override
    @Nullable
    public Entity build(Level level, BlockPos basePos) {
        if (loadFailed) {
            return null;
        }
        Vec3 placePos = Vec3.atLowerCornerOf(basePos).add(pos);
        BlockPos placeHangingPos = basePos.offset(hangingPos);
        CompoundTag newEntityNbt = entityNbt.copy();
        newEntityNbt.put("Pos", NBTUtilBC.writeVec3(placePos));
        NBTUtilBC.putUUID(newEntityNbt, "UUID", UUID.randomUUID());
        boolean rotate = true;
        if (newEntityNbt.contains("TileX") &&
            newEntityNbt.contains("TileY") &&
            newEntityNbt.contains("TileZ") &&
            newEntityNbt.contains("Facing")) {
            newEntityNbt.putInt("TileX", placeHangingPos.getX());
            newEntityNbt.putInt("TileY", placeHangingPos.getY());
            newEntityNbt.putInt("TileZ", placeHangingPos.getZ());
            newEntityNbt.putByte("Facing", (byte) hangingFacing.get2DDataValue());
            rotate = false;
        }
        // 1.12.2's replaceNbt: the matching rules' overrides, merged over the data about to be loaded. Matched on the
        // captured NBT, as the selectors saw it at scan time.
        newEntityNbt = JsonRule.applyReplaceNbt(newEntityNbt,
            RulesLoader.getRules(Identifier.parse(NBTUtilBC.getString(entityNbt, "id", "")), entityNbt));
        // Load entity from NBT via EntityType.create. Vanilla's Entity.load rethrows any loader failure as a
        // ReportedException rather than skipping the entity, so malformed NBT (an upload, a mod's fragile loader)
        // would crash the server tick, and again on every restart while the Builder retries the spawn.
        Optional<Entity> optEntity;
        try {
            optEntity = createEntity(level, newEntityNbt);
        } catch (RuntimeException e) {
            loadFailed = true;
            String id = NBTUtilBC.getString(newEntityNbt, "id", "");
            LOGGER.warn("Not spawning the blueprint entity \"{}\" at {}: it failed to load ({})",
                Snapshot.logSafe(id, 64), placePos, Snapshot.logSafe(e, 256));
            return null;
        }
        if (optEntity.isPresent()) {
            Entity entity = optEntity.get();
            if (rotate && entityRotation != Rotation.NONE) {
                float yaw = entity.getYRot();
                float rotatedYaw = entity.rotate(entityRotation);
                entity.setYRot(yaw + (yaw - rotatedYaw));
            }
            level.addFreshEntity(entity);
            return entity;
        }
        return null;
    }

    private static Optional<Entity> createEntity(Level level, CompoundTag newEntityNbt) {
        //? if >=26.2 {
        return EntityType.create(
            TagValueInput.create(
                ProblemReporter.DISCARDING,
                level.registryAccess(),
                newEntityNbt
            ),
            level,
            new EntitySpawnRequest(EntitySpawnReason.COMMAND, false)
        );
        //?} elif >=1.21.10 {
        /*return EntityType.create(
            TagValueInput.create(
                ProblemReporter.DISCARDING,
                level.registryAccess(),
                newEntityNbt
            ),
            level,
            EntitySpawnReason.COMMAND
        );
        *///?} else {
        /*return EntityType.create(newEntityNbt, level);*/
        //?}
    }

    @Override
    @Nullable
    public Entity buildWithoutChecks(Level level, BlockPos basePos) {
        return build(level, basePos);
    }

    @Override
    public CompoundTag serializeNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("entityNbt", entityNbt);
        nbt.put("pos", NBTUtilBC.writeVec3(pos));
        nbt.put("hangingPos", NBTUtilBC.writeBlockPos(hangingPos));
        nbt.put("hangingFacing", NBTUtilBC.writeEnum(hangingFacing));
        nbt.put("entityRotation", NBTUtilBC.writeEnum(entityRotation));
        return nbt;
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) throws InvalidInputDataException {
        entityNbt = NBTUtilBC.getCompound(nbt, "entityNbt");
        // Entities get the same loot-strip blocks get (structure chest minecarts carry the loot-table pair in their
        // entity NBT exactly as the block entities do), so a blueprint entity can never mint loot. The pricing gate
        // (isPricedByRules) is NOT applied here — a single unruled entity drops out of the blueprint at read time
        // (see Blueprint) instead of refusing the whole file, so a modpack removing a rule cannot brick every
        // stored blueprint that happens to contain that entity.
        SchematicBlockDefault.sanitizeTileNbt(entityNbt);
        pos = NBTUtilBC.readVec3(nbt.get("pos"));
        if (pos == null) {
            pos = Vec3.ZERO;
        }
        hangingPos = NBTUtilBC.readBlockPos(NBTUtilBC.getCompound(nbt, "hangingPos"));
        hangingFacing = NBTUtilBC.readEnum(nbt.get("hangingFacing"), Direction.class);
        if (hangingFacing == null) hangingFacing = Direction.NORTH;
        entityRotation = NBTUtilBC.readEnum(nbt.get("entityRotation"), Rotation.class);
        if (entityRotation == null) entityRotation = Rotation.NONE;
    }

    /**
     * Whether this entity is one a blueprint may hold: its NBT names an entity the shipped JSON rules sanction —
     * a rule that either whitelists it for capture (what the architect's own scan predicate demands) or charges for
     * it (non-null {@code requiredExtractors}, the exact mechanism {@link #computeRequiredItems()} prices through).
     * Deny by default: an entity no rule mentions would {@code build()} from its NBT verbatim while costing
     * nothing. A rule that only matches without doing either is not enough — the gate must be at least as strict
     * as the price. Package-private and static so the blueprint read loop and the tests share one definition.
     */
    static boolean isPricedByRules(CompoundTag entityNbt) {
        String id = NBTUtilBC.getString(entityNbt, "id", "");
        Identifier entityId = Identifier.tryParse(id);
        if (entityId == null) {
            return false;
        }
        return RulesLoader.getRules(entityId, entityNbt).stream()
            .anyMatch(rule -> rule.capture || rule.requiredExtractors != null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchematicEntityDefault that = (SchematicEntityDefault) o;
        return entityNbt.equals(that.entityNbt) &&
            pos.equals(that.pos) &&
            hangingPos.equals(that.hangingPos) &&
            hangingFacing == that.hangingFacing &&
            entityRotation == that.entityRotation;
    }

    @Override
    public int hashCode() {
        int result = entityNbt.hashCode();
        result = 31 * result + pos.hashCode();
        result = 31 * result + hangingPos.hashCode();
        result = 31 * result + hangingFacing.hashCode();
        result = 31 * result + entityRotation.hashCode();
        return result;
    }
}
