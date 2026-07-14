/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.api.schematics.SchematicBlockFactoryRegistry;

/** Block-scoped facade over the shared {@link SchematicManager}. Block scans MUST resolve a
 *  schematic, so this manager throws on miss (unlike the entity twin, which returns null). */
public class SchematicBlockManager {
    private static final SchematicManager<SchematicBlockContext, ISchematicBlock> MANAGER =
        new SchematicManager<>(
            SchematicBlockFactoryRegistry::getFactories,
            SchematicBlockFactoryRegistry::getFactoryByName,
            SchematicBlockFactoryRegistry::getFactoryByInstance,
            /* throwOnMiss = */ true
        );

    @SuppressWarnings("WeakerAccess")
    public static ISchematicBlock getSchematicBlock(SchematicBlockContext context) {
        return MANAGER.getSchematic(context);
    }

    @SuppressWarnings("WeakerAccess")
    public static <S extends ISchematicBlock> S createCleanCopy(S schematicBlock) {
        return MANAGER.createCleanCopy(schematicBlock);
    }

    @Nonnull
    public static <S extends ISchematicBlock> CompoundTag writeToNBT(S schematicBlock) {
        return MANAGER.writeToNBT(schematicBlock);
    }

    @Nonnull
    public static ISchematicBlock readFromNBT(CompoundTag schematicBlockTag) throws InvalidInputDataException {
        return MANAGER.readFromNBT(schematicBlockTag);
    }

    /** Check if the given schematic type name is registered. */
    public static boolean isSchematicTypeRegistered(Identifier name) {
        return MANAGER.isSchematicTypeRegistered(name);
    }
}
