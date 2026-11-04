/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.capability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;

/**
 * A typed, loader-neutral token for one kind of block capability, as BuildCraft's API passes it around (where an
 * {@code Object} token used to name the loader's capability). Tokens are interned by id, so each kind has exactly one
 * instance and the {@code ==} checks the pipe flows, behaviours and pluggables make on their tokens keep working.
 *
 * <p>The platform layer resolves a token to the loader's own lookup (a NeoForge {@code BlockCapability}, a Fabric
 * {@code BlockApiLookup}) with the same id and type, so another mod reaches BuildCraft's MJ objects there as usual.
 * The storage tokens (items, fluids, energy) are BuildCraft's {@code buildcraft.platform.transfer.StorageTokens}: the
 * platform maps those onto the loader's own storage capabilities and adapts the handlers both ways.
 *
 * @param <T> the type the token finds.
 */
public final class BCCapability<T> {
    private static final Map<Identifier, BCCapability<?>> TOKENS = new ConcurrentHashMap<>();

    private final Identifier id;
    private final Class<T> type;

    private BCCapability(Identifier id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    /**
     * The token named {@code id}, created on first use.
     *
     * @throws IllegalArgumentException if a token of that id already exists for another type.
     */
    @SuppressWarnings("unchecked")
    public static <T> BCCapability<T> of(Identifier id, Class<T> type) {
        BCCapability<?> token = TOKENS.computeIfAbsent(id, key -> new BCCapability<>(key, type));
        if (token.type != type) {
            throw new IllegalArgumentException("Capability " + id + " is of " + token.type.getName() + ", not "
                + type.getName());
        }
        return (BCCapability<T>) token;
    }

    /** The token's id, which the loader's capability of the same kind carries too. */
    public Identifier id() {
        return id;
    }

    /** The type this token finds. */
    public Class<T> type() {
        return type;
    }

    @Override
    public String toString() {
        return "BCCapability[" + id + "]";
    }
}
