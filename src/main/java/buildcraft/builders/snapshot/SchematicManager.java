/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematic;
import buildcraft.api.schematics.SchematicFactory;

import buildcraft.lib.misc.NBTUtilBC;

/**
 * Shared core of {@link SchematicBlockManager} and {@link SchematicEntityManager}, which were a
 * verbatim parallel system (get / clean-copy / NBT round-trip) differing only in their context and
 * schematic types plus the on-miss policy. Those two are now thin static facades delegating to a
 * single instance of this class; the registry lookups are injected as function hooks so this stays
 * decoupled from the two api-package registries (their public surface is unchanged).
 * <p>
 * The one genuine behavioural difference is parameterised, not erased: the block scan MUST produce a
 * schematic (throws on miss) while the entity scan legitimately skips unknown entities (returns
 * {@code null}). {@code throwOnMiss} carries that.
 *
 * @param <C> the scan-context type.
 * @param <S> the schematic type (e.g. {@code ISchematicBlock}).
 */
public class SchematicManager<C, S extends ISchematic<C>> {
    private final Supplier<List<? extends SchematicFactory<C, ?>>> factoriesGetter;
    private final Function<Object, ? extends SchematicFactory<C, ?>> byName;
    private final Function<S, ? extends SchematicFactory<C, ?>> byInstance;
    private final boolean throwOnMiss;

    public SchematicManager(Supplier<List<? extends SchematicFactory<C, ?>>> factoriesGetter,
                            Function<Object, ? extends SchematicFactory<C, ?>> byName,
                            Function<S, ? extends SchematicFactory<C, ?>> byInstance,
                            boolean throwOnMiss) {
        this.factoriesGetter = factoriesGetter;
        this.byName = byName;
        this.byInstance = byInstance;
        this.throwOnMiss = throwOnMiss;
    }

    @Nullable
    public S getSchematic(C context) {
        // Highest priority first: getFactories() is sorted ascending, so scan it in reverse.
        List<? extends SchematicFactory<C, ?>> factories = factoriesGetter.get();
        for (int i = factories.size() - 1; i >= 0; i--) {
            SchematicFactory<C, ?> factory = factories.get(i);
            if (factory.predicate.test(context)) {
                ISchematic<C> schematic = factory.supplier.get();
                schematic.init(context);
                @SuppressWarnings("unchecked")
                S result = (S) schematic;
                return result;
            }
        }
        if (throwOnMiss) {
            throw new UnsupportedOperationException();
        }
        return null;
    }

    public <T extends S> T createCleanCopy(T schematic) {
        @SuppressWarnings("unchecked")
        T copy = (T) byInstance.apply(schematic).supplier.get();
        return copy;
    }

    @Nonnull
    public CompoundTag writeToNBT(S schematic) {
        CompoundTag schematicTag = new CompoundTag();
        schematicTag.putString("name", byInstance.apply(schematic).name.toString());
        schematicTag.put("data", schematic.serializeNbt());
        return schematicTag;
    }

    @Nonnull
    public S readFromNBT(CompoundTag schematicTag) throws InvalidInputDataException {
        Identifier name = Identifier.parse(NBTUtilBC.getString(schematicTag, "name", ""));
        SchematicFactory<C, ?> factory = byName.apply(name);
        if (factory == null) {
            throw new InvalidInputDataException("Unknown schematic type " + name);
        }
        ISchematic<C> schematic = factory.supplier.get();
        CompoundTag data = NBTUtilBC.getCompound(schematicTag, "data");
        try {
            schematic.deserializeNbt(data);
            @SuppressWarnings("unchecked")
            S result = (S) schematic;
            return result;
        } catch (InvalidInputDataException e) {
            throw new InvalidInputDataException("Failed to load the schematic from " + data, e);
        }
    }

    /** Check if the given schematic type name is registered. */
    public boolean isSchematicTypeRegistered(Identifier name) {
        return byName.apply(name) != null;
    }
}
