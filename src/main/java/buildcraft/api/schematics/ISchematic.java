package buildcraft.api.schematics;

import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.InvalidInputDataException;

/**
 * Shared super-interface for {@link ISchematicBlock} and {@link ISchematicEntity}. It captures the
 * three operations the schematic managers (see {@code SchematicManager} in
 * {@code buildcraft.builders.snapshot}) drive generically: context-based init, NBT serialize, and
 * NBT deserialize. Each concrete schematic interface binds {@code C} to its own context type and
 * re-declares these methods with the exact signatures it already exposed, so this is a purely
 * additive super-interface — existing implementors need no changes.
 *
 * @param <C> the context type passed to {@link #init(Object)} (block- or entity-scan context).
 */
public interface ISchematic<C> {
    void init(C context);

    CompoundTag serializeNBT();

    /** @throws InvalidInputDataException If the input data wasn't correct or didn't make sense. */
    void deserializeNBT(CompoundTag nbt) throws InvalidInputDataException;
}
