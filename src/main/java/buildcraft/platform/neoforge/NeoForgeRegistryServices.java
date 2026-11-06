/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.platform.registry.BCHolder;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.RegistryServices;

/**
 * {@link RegistryServices} on NeoForge: each registrar wraps a {@link DeferredRegister}, and each holder a
 * {@link DeferredHolder}. {@link BCRegistrar#subscribe()} attaches the deferred register to BuildCraft's mod bus
 * ({@link NeoForgeModBus}; the calls are made from its init), exactly where {@code register(modBus)} was called.
 */
public final class NeoForgeRegistryServices implements RegistryServices {
    @Override
    public <T> BCRegistrar<T> create(ResourceKey<? extends Registry<T>> registry, String namespace) {
        return new NeoForgeRegistrar<>(DeferredRegister.create(registry, namespace));
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder(ResourceKey<CreativeModeTab> after) {
        return CreativeModeTab.builder().withTabsBefore(after);
    }

    @Override
    public boolean contributeToTabLate(ResourceKey<CreativeModeTab> tab, CreativeModeTab.DisplayItemsGenerator contribution) {
        // LOW, the priority of BuildCraft's own tab-contents listeners before the platform layer (transport LOW,
        // silicon LOWEST): after every normal and high priority listener. The subsystems' listeners keep their init
        // order at one shared priority; BuildCraft's bus has no other listener for this event, so nothing else is
        // ordered against them and one priority reproduces the old LOW/LOWEST pair.
        NeoForgeModBus.get().addListener(EventPriority.LOW, (BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(tab)) {
                contribution.accept(event.getParameters(), event);
            }
        });
        return true;
    }

    private static final class NeoForgeRegistrar<T> implements BCRegistrar<T> {
        private final DeferredRegister<T> register;
        private final List<BCHolder<T, ? extends T>> entries = new ArrayList<>();
        private final Collection<BCHolder<T, ? extends T>> entriesView = Collections.unmodifiableList(entries);

        NeoForgeRegistrar(DeferredRegister<T> register) {
            this.register = register;
        }

        @Override
        public <I extends T> BCHolder<T, I> register(String name, Function<Identifier, ? extends I> factory) {
            DeferredHolder<T, I> holder = register.register(name, factory);
            // The key is rebuilt from the id: DeferredHolder#getKey is deprecated for removal on 26.3.
            ResourceKey<T> key = ResourceKey.create(register.getRegistryKey(), holder.getId());
            BCHolder<T, I> wrapped = new BCHolder<>(holder.getId(), key, holder);
            entries.add(wrapped);
            return wrapped;
        }

        @Override
        public void addAlias(Identifier from, Identifier to) {
            register.addAlias(from, to);
        }

        @Override
        public Collection<BCHolder<T, ? extends T>> getEntries() {
            return entriesView;
        }

        @Override
        public ResourceKey<? extends Registry<T>> getRegistryKey() {
            return register.getRegistryKey();
        }

        @Override
        public String getNamespace() {
            return register.getNamespace();
        }

        @Override
        public void subscribe() {
            register.register(NeoForgeModBus.get());
        }
    }
}
//?}
