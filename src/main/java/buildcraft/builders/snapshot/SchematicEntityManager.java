/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicEntity;
import buildcraft.api.schematics.SchematicEntityContext;
import buildcraft.api.schematics.SchematicEntityFactoryRegistry;

/** Entity-scoped facade over the shared {@link SchematicManager}. Entity scans legitimately skip
 *  unknown entities, so this manager returns {@code null} on miss (unlike the block twin, which
 *  throws). */
public class SchematicEntityManager {
    private static final SchematicManager<SchematicEntityContext, ISchematicEntity> MANAGER =
        new SchematicManager<>(
            SchematicEntityFactoryRegistry::getFactories,
            SchematicEntityFactoryRegistry::getFactoryByName,
            SchematicEntityFactoryRegistry::getFactoryByInstance,
            /* throwOnMiss = */ false
        );

    @SuppressWarnings("WeakerAccess")
    public static ISchematicEntity getSchematicEntity(SchematicEntityContext context) {
        return MANAGER.getSchematic(context);
    }

    @SuppressWarnings("WeakerAccess")
    public static <S extends ISchematicEntity> S createCleanCopy(S schematicEntity) {
        return MANAGER.createCleanCopy(schematicEntity);
    }

    @Nonnull
    public static <S extends ISchematicEntity> CompoundTag writeToNBT(S schematicEntity) {
        return MANAGER.writeToNBT(schematicEntity);
    }

    @Nonnull
    public static ISchematicEntity readFromNBT(CompoundTag schematicEntityTag) throws InvalidInputDataException {
        return MANAGER.readFromNBT(schematicEntityTag);
    }
}
