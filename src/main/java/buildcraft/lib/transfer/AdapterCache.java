/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.transfer;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import com.google.common.collect.MapMaker;

/**
 * One adapter per wrapped object, for the views a transfer boundary hands out. While anyone holds the adapter of a
 * storage or handler, asking again gives that same object: a capability query answers the same handler twice, as
 * the BuildCraft storage itself did before the platform layer, and a steady neighbour looked up every tick costs no
 * allocation. Keys are compared by identity (never {@code equals}) and held weakly. The adapters are held weakly
 * too, since each holds its key, so an adapter nobody holds is collected and the next query makes a new one, which
 * no caller can tell apart because none still holds the old one.
 */
public final class AdapterCache<K, V> {
    private final ConcurrentMap<K, V> adapters = new MapMaker().weakKeys().weakValues().makeMap();
    private final Function<? super K, ? extends V> factory;

    public AdapterCache(Function<? super K, ? extends V> factory) {
        this.factory = factory;
    }

    /** The adapter of {@code key}, made now if it has none in use. */
    public V get(K key) {
        V adapter = adapters.get(key);
        if (adapter == null) {
            V made = factory.apply(key);
            adapter = adapters.putIfAbsent(key, made);
            if (adapter == null) {
                adapter = made;
            }
        }
        return adapter;
    }
}
