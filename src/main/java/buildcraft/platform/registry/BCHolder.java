/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * One entry a {@link BCRegistrar} registered: its id and key, available at once, and its value, available once the
 * loader has registered it ({@link #get()} throws before that, as a NeoForge {@code DeferredHolder} does).
 *
 * <p>{@code R} is the registry's element type and {@code T} the entry's own type, mirroring NeoForge's
 * {@code DeferredHolder<R, T>}: {@link #key()} and {@link #holder()} need the registry type, {@link #get()} the
 * entry's. Two holders are equal when they name the same key.
 *
 * @param <R> the registry's element type
 * @param <T> the type of this entry's value
 */
public class BCHolder<R, T extends R> implements Supplier<T> {
    private final Identifier id;
    private final ResourceKey<R> key;
    private final Supplier<? extends Holder<R>> delegate;

    /** @param delegate the loader's holder for the entry; it resolves to the value once the entry is registered */
    public BCHolder(Identifier id, ResourceKey<R> key, Holder<R> delegate) {
        this(id, key, () -> delegate);
    }

    /**
     * @param delegate the loader's holder for the entry, asked for on each use: a loader that registers an entry only
     *     when it is first needed (Fabric, replaying BuildCraft's registrations after its init) registers it there
     */
    public BCHolder(Identifier id, ResourceKey<R> key, Supplier<? extends Holder<R>> delegate) {
        this.id = id;
        this.key = key;
        this.delegate = delegate;
    }

    /** Copies {@code other}'s identity, for a subclass that only adds methods (the block and item holders). */
    protected BCHolder(BCHolder<R, ? extends T> other) {
        this(other.id, other.key, other.delegate);
    }

    /** The registry id of the entry. */
    public Identifier id() {
        return id;
    }

    /** The registry key of the entry. */
    public ResourceKey<R> key() {
        return key;
    }

    /** The vanilla holder of the entry, for an API that takes a {@link Holder}. */
    public Holder<R> holder() {
        return delegate.get();
    }

    /** The registered value. Throws while the entry is not registered yet. */
    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        return (T) delegate.get().value();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof BCHolder<?, ?> other && key.equals(other.key));
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "BCHolder{" + key + "}";
    }
}
