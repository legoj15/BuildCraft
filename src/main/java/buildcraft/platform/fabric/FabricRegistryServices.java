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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import buildcraft.platform.registry.BCHolder;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.RegistryServices;

/^*
 * {@link RegistryServices} on Fabric. NeoForge builds every entry from its factory in its registry's register event,
 * after all of BuildCraft's register calls; building it at the call instead runs a factory while the class whose
 * static init made the call is half built (a block-entity type naming a block of a holder class still initialising).
 * So each entry is queued at its register call and built and {@code Registry.registerForHolder}ed either when first
 * asked for ({@link BCHolder#get}) or by {@link #registerPending}, which the Fabric entrypoint calls after BuildCraft's
 * init, registry by registry in NeoForge's order and in call order within one (the block and item helpers have
 * already set the id on the properties, as on NeoForge). A registrar subscribed after that (another mod's initializer
 * running after BuildCraft's, such as the game-test mod) has its queued entries built at its {@link
 * BCRegistrar#subscribe}, since nothing else would build them, and an entry such a registrar registers after its
 * subscribe is built at its register call, for the same reason. Aliases ({@link BCRegistrar#addAlias}) go through
 * Fabric API's {@code FabricRegistry.addAlias}, whose {@code MappedRegistry} lookups by id and by key resolve them as
 * NeoForge's do; they are queued like the entries and added once the entries are built, since Fabric warns about an
 * alias whose target is not registered yet.
 *^/
public final class FabricRegistryServices implements RegistryServices {
    /^* Every queued entry, in register-call order. ^/
    private static final List<Pending<?>> PENDING = new ArrayList<>();
    /^* Every queued alias, in addAlias-call order. ^/
    private static final List<PendingAlias> PENDING_ALIASES = new ArrayList<>();
    /^* Set once {@link #registerPending} has run: from then on {@code subscribe()} builds its own entries. ^/
    private static volatile boolean initRegistered;

    /^*
     * Registers every entry not yet registered: NeoForge's registry order (attributes, data components and particle
     * types first, then vanilla's own order), call order within a registry.
     *^/
    public static void registerPending() {
        List<ResourceKey<? extends Registry<?>>> order = new ArrayList<>();
        order.add(Registries.ATTRIBUTE);
        order.add(Registries.DATA_COMPONENT_TYPE);
        order.add(Registries.PARTICLE_TYPE);
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            if (!order.contains(registry.key())) {
                order.add(registry.key());
            }
        }
        // A factory that registers another entry while it runs queues it behind the snapshot taken here: go round again.
        while (true) {
            List<Pending<?>> pending;
            synchronized (PENDING) {
                if (PENDING.isEmpty()) {
                    break;
                }
                pending = new ArrayList<>(PENDING);
                PENDING.clear();
            }
            for (ResourceKey<? extends Registry<?>> registry : order) {
                for (Pending<?> entry : pending) {
                    if (entry.key.registryKey().equals(registry.identifier())) {
                        entry.holder();
                    }
                }
            }
            for (Pending<?> entry : pending) {
                entry.holder(); // any registry the order above did not name
            }
        }
        addPendingAliases();
        initRegistered = true;
    }

    /^* Adds every queued alias, now that the entries it may name are registered. ^/
    private static void addPendingAliases() {
        List<PendingAlias> aliases;
        synchronized (PENDING_ALIASES) {
            aliases = new ArrayList<>(PENDING_ALIASES);
            PENDING_ALIASES.clear();
        }
        for (PendingAlias alias : aliases) {
            alias.registry.addAlias(alias.from, alias.to);
        }
    }

    /^* One queued alias. ^/
    private record PendingAlias(Registry<?> registry, Identifier from, Identifier to) {}

    /^* One queued entry: built and registered on its first {@link #holder} call. ^/
    private static final class Pending<T> {
        final Registry<T> registry;
        final ResourceKey<T> key;
        final Identifier id;
        Function<Identifier, ? extends T> factory;
        Holder<T> holder;
        /^* What the factory threw, so a later call reports the cause and not a construction cycle. ^/
        Throwable failure;

        Pending(Registry<T> registry, ResourceKey<T> key, Identifier id, Function<Identifier, ? extends T> factory) {
            this.registry = registry;
            this.key = key;
            this.id = id;
            this.factory = factory;
        }

        synchronized Holder<T> holder() {
            if (holder == null) {
                if (failure != null) {
                    throw new IllegalStateException("Registry entry " + key + " could not be built", failure);
                }
                Function<Identifier, ? extends T> build = factory;
                factory = null;
                if (build == null) {
                    throw new IllegalStateException("Registry entry " + key + " asked for while it is being built");
                }
                try {
                    holder = Registry.registerForHolder(registry, key, build.apply(id));
                } catch (RuntimeException | Error e) {
                    failure = e;
                    throw e;
                }
            }
            return holder;
        }
    }

    @Override
    public <T> BCRegistrar<T> create(ResourceKey<? extends Registry<T>> registry, String namespace) {
        return new FabricRegistrar<>(registry, namespace);
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return FabricCreativeModeTab.builder();
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder(ResourceKey<CreativeModeTab> after) {
        // Gap: no tab ordering on Fabric; the tab follows registration order (BuildCraft registers `after` first).
        return FabricCreativeModeTab.builder();
    }

    private static final class FabricRegistrar<T> implements BCRegistrar<T> {
        private final ResourceKey<? extends Registry<T>> registryKey;
        private final String namespace;
        private final List<BCHolder<T, ? extends T>> entries = new ArrayList<>();
        private final Collection<BCHolder<T, ? extends T>> entriesView = Collections.unmodifiableList(entries);
        /^* Set once {@link #subscribe} has built this registrar's entries after {@link #registerPending}. ^/
        private boolean subscribedLate;

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
            Pending<T> pending = new Pending<>(registry(), key, id, factory);
            synchronized (PENDING) {
                PENDING.add(pending);
            }
            BCHolder<T, I> wrapped = new BCHolder<>(id, key, pending::holder);
            entries.add(wrapped);
            if (subscribedLate) {
                wrapped.holder(); // subscribe() is past: nothing else would build it
            }
            return wrapped;
        }

        @Override
        public void addAlias(Identifier from, Identifier to) {
            synchronized (PENDING_ALIASES) {
                PENDING_ALIASES.add(new PendingAlias(registry(), from, to));
            }
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
            // Entries are registered when first asked for, or by registerPending after BuildCraft's init. A registrar
            // subscribed after that has no later point to be built at, so it is built now.
            if (initRegistered) {
                subscribedLate = true;
                for (BCHolder<T, ? extends T> entry : new ArrayList<>(entries)) {
                    entry.holder();
                }
                addPendingAliases();
            }
        }
    }
}
*///?}
