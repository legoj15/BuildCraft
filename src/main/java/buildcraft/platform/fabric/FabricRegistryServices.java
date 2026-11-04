/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import buildcraft.platform.registry.BCHolder;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.RegistryServices;

/^*
 * {@link RegistryServices} on Fabric: registration is eager, {@code Registry.registerForHolder} at the register call
 * (the block and item helpers have already set the id on the properties, as on NeoForge). Gap: Fabric has no registry
 * alias API, so {@link BCRegistrar#addAlias} records nothing and old ids do not resolve.
 *^/
public final class FabricRegistryServices implements RegistryServices {
    @Override
    public <T> BCRegistrar<T> create(ResourceKey<? extends Registry<T>> registry, String namespace) {
        return new FabricRegistrar<>(registry, namespace);
    }

    private static final class FabricRegistrar<T> implements BCRegistrar<T> {
        private final ResourceKey<? extends Registry<T>> registryKey;
        private final String namespace;
        private final List<BCHolder<T, ? extends T>> entries = new ArrayList<>();
        private final Collection<BCHolder<T, ? extends T>> entriesView = Collections.unmodifiableList(entries);

        FabricRegistrar(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
            this.registryKey = registryKey;
            this.namespace = namespace;
        }

        @SuppressWarnings("unchecked")
        private Registry<T> registry() {
            Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.getValue(registryKey.identifier());
            if (registry == null) {
                throw new IllegalStateException("No built-in registry " + registryKey.identifier());
            }
            return registry;
        }

        @Override
        public <I extends T> BCHolder<T, I> register(String name, Function<Identifier, ? extends I> factory) {
            Identifier id = Identifier.fromNamespaceAndPath(namespace, name);
            ResourceKey<T> key = ResourceKey.create(registryKey, id);
            T value = factory.apply(id);
            Holder<T> holder = Registry.registerForHolder(registry(), key, value);
            BCHolder<T, I> wrapped = new BCHolder<>(id, key, holder);
            entries.add(wrapped);
            return wrapped;
        }

        @Override
        public void addAlias(Identifier from, Identifier to) {
            // Gap: no Fabric alias API (see the class comment).
        }

        @Override
        public Collection<BCHolder<T, ? extends T>> getEntries() {
            return entriesView;
        }

        @Override
        public ResourceKey<? extends Registry<T>> getRegistryKey() {
            return registryKey;
        }

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public void subscribe() {
            // Entries were registered at their register calls.
        }
    }
}
*///?}
