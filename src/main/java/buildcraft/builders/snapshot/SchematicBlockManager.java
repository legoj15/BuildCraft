/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.api.schematics.SchematicBlockFactoryRegistry;

import buildcraft.lib.misc.NBTUtilBC;

import buildcraft.transport.block.BlockPipeHolder;

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

    /**
     * {@link #readFromNBT}, then the read gates of an uploaded or hand-made schematic ({@link #upgradeLegacy},
     * {@link #captureGateRefusal}), for a schematic read on its own: a single-schematic item, which the Replacer also
     * reads its from/to pair from. One that fails the capture gate is refused, since there is no stored file to keep
     * readable around it.
     */
    @Nonnull
    public static ISchematicBlock readCapturableFromNBT(CompoundTag schematicBlockTag)
        throws InvalidInputDataException {
        ISchematicBlock block = upgradeLegacy(readFromNBT(schematicBlockTag), schematicBlockTag);
        String refusedId = captureGateRefusal(block);
        if (refusedId != null) {
            throw new InvalidInputDataException("No Architect scan captures the schematic of " + refusedId);
        }
        return block;
    }

    /**
     * The read-time repairs of a schematic saved by an older version, in one call: {@link #upgradeLegacyPipe} and
     * {@link #upgradeLegacyFluid}. They never apply to the same schematic: one reads default entries, the other fluid
     * ones.
     */
    @Nonnull
    static ISchematicBlock upgradeLegacy(ISchematicBlock block, CompoundTag schematicBlockTag)
        throws InvalidInputDataException {
        return upgradeLegacyFluid(upgradeLegacyPipe(block, schematicBlockTag));
    }

    /**
     * A schematic saved before the pipe schematic existed (2026-06) typed its pipes as default, which prices no
     * pluggable. The same data is re-read as the pipe schematic a scan picks today, so the pipe keeps its place and its
     * facades, gates and lenses are charged. Anything else is returned as it is.
     */
    @Nonnull
    static ISchematicBlock upgradeLegacyPipe(ISchematicBlock block, CompoundTag schematicBlockTag)
        throws InvalidInputDataException {
        if (block.getClass() == SchematicBlockDefault.class
            && ((SchematicBlockDefault) block).blockState != null
            && ((SchematicBlockDefault) block).blockState.getBlock() instanceof BlockPipeHolder) {
            SchematicBlockPipe pipe = new SchematicBlockPipe();
            pipe.deserializeNbt(NBTUtilBC.getCompound(schematicBlockTag, "data"));
            return pipe;
        }
        return block;
    }

    /**
     * A schematic saved while the fluid schematic took every state with a fluid typed a waterlogged slab, wall or pipe
     * as fluid: it charged 1000 mB of water and no item, and kept no tile data. That entry is re-read as the default
     * schematic a scan picks for the same state now, so the block is charged and rebuilt as itself, still waterlogged.
     * The tile data was never saved, so there is none to restore (a chest comes back empty). A pipe holder is the case
     * that cannot do without it: the default schematic of one fails {@link #captureGateRefusal}, so the entry reads as
     * air, like any other the gate refuses. Anything else is returned as it is.
     */
    @Nonnull
    static ISchematicBlock upgradeLegacyFluid(ISchematicBlock block) {
        if (block instanceof SchematicBlockFluid fluid && fluid.namesFluidHoldingBlock()) {
            return SchematicBlockDefault.ofBareState(fluid.getBlockStateForRender(), fluid.readFully());
        }
        return block;
    }

    /**
     * The Architect's capture gate, re-run on a read: the registry id of the block {@code block} names when no scan
     * would have captured it (an ignored ender chest, a block of a mod that ships no rules, a block priced as another,
     * a schematic class the scan would not pick for it), else null. The id is a registry key, so it is log-safe.
     */
    @Nullable
    static String captureGateRefusal(ISchematicBlock block) {
        if (block instanceof SchematicBlockDefault schematic && !schematic.capturableByScan()) {
            return blockId(schematic.blockState);
        }
        if (block instanceof SchematicBlockFluid fluid && !fluid.capturableByScan()) {
            return blockId(fluid.getBlockStateForRender());
        }
        return null;
    }

    private static String blockId(@Nullable BlockState state) {
        return state == null ? "?" : String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /** Check if the given schematic type name is registered. */
    public static boolean isSchematicTypeRegistered(Identifier name) {
        return MANAGER.isSchematicTypeRegistered(name);
    }
}
