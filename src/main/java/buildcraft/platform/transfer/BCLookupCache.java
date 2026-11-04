/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

/**
 * A cached lookup of one storage at a fixed position and side, for code that asks every tick: NeoForge's
 * {@code BlockCapabilityCache}, Fabric's {@code BlockApiCache}. The BuildCraft view is kept while the loader's
 * handler object stays the same, so a steady neighbour costs no allocation per query.
 */
@FunctionalInterface
public interface BCLookupCache<S> {
    /** The storage there now, or null. */
    S get();
}
