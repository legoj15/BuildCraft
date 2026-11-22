/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

import buildcraft.platform.registry.BCHolder;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.RegistryServices;

/^*
 * {@link RegistryServices} on MinecraftForge: each registrar wraps a {@link DeferredRegister}, and each holder a
 * {@link RegistryObject}. It is the NeoForge twin's shape (entries queue, then register in the loader's register event,
 * so none of Fabric's build-on-first-ask timing applies); {@link BCRegistrar#subscribe()} attaches the deferred
 * register to BuildCraft's mod bus ({@link ForgeBus#register}).
 *
 * <p><b>Holders.</b> A {@link RegistryObject} is not a vanilla {@link Holder} (NeoForge's {@code DeferredHolder} is), so
 * {@link BCHolder#holder()} is fed by {@link HolderSource}, which asks the object for its holder once and keeps it. Forge
 * gives an entry of an intrusive registry (blocks, items) no holder at all before its register event, where NeoForge
 * hands back an unbound one: asked early, {@code holder()} and {@code get()} throw here, as {@code get()} also does there.
 *
 * <p><b>Aliases.</b> Forge has no public alias API. The queue of {@link BCRegistrar#addAlias} calls is flushed in a
 * {@link RegisterEvent} listener of the registrar's own registry, where Forge has unfrozen it, through
 * {@link ForgeRegistry#addAlias}: public, but Forge's own TODO calls it "add as public API", and its own mod class
 * reaches it by a cast too ({@code ForgeRegistry} itself is marked internal). Only a Forge-owned registry takes one
 * (all five BuildCraft flips are: items, blocks, block entities, fluids, data components); any other fails loudly at
 * the event.
 *
 * <p><b>Alias gap, closed by a mixin.</b> The alias reaches Forge's own id lookups ({@code getRaw}: the wrapper's
 * {@code getValue} and {@code getOptional}) but not the vanilla-facing wrapper's {@code get(Identifier)} and
 * {@code get(ResourceKey)}, which read its own holder map: vanilla's codecs ({@code Item.CODEC}, so every saved item
 * stack, and every {@code holderByNameCodec}) go through those. {@code platform.forge.mixin.NamespacedWrapperMixin} makes
 * both consult the alias map when they miss, as NeoForge's {@code MappedRegistry} does by resolving in them.
 *
 * <p><b>Creative tabs.</b> Forge patches vanilla's no-argument {@code CreativeModeTab.builder()} and
 * {@code withTabsBefore} (as NeoForge does), and posts {@link BuildCreativeModeTabContentsEvent} on its own static game
 * bus (NeoForge: the mod bus), not on the mod bus group.
 *^/
public final class ForgeRegistryServices implements RegistryServices {
    @Override
    public <T> BCRegistrar<T> create(ResourceKey<? extends Registry<T>> registry, String namespace) {
        return new ForgeRegistrar<>(DeferredRegister.create(registry, namespace), registry, namespace);
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
        // LOW, as on NeoForge: after every normal and high priority listener of the event. The listeners of one
        // priority run in the order they were added, which is BuildCraft's init order.
        ForgeBus.listenCreativeTabsLow(event -> {
            if (event.getTabKey().equals(tab)) {
                contribution.accept(event.getParameters(), event);
            }
        });
        return true;
    }

    private static final class ForgeRegistrar<T> implements BCRegistrar<T> {
        private final DeferredRegister<T> register;
        private final ResourceKey<? extends Registry<T>> registryKey;
        private final String namespace;
        private final List<BCHolder<T, ? extends T>> entries = new ArrayList<>();
        private final Collection<BCHolder<T, ? extends T>> entriesView = Collections.unmodifiableList(entries);
        /^* Aliases waiting for this registry's register event. ^/
        private final List<Alias> aliases = new ArrayList<>();
        /^* Set once the register event of this registry has flushed {@link #aliases}: a later alias is too late. ^/
        private boolean aliasesFlushed;

        ForgeRegistrar(DeferredRegister<T> register, ResourceKey<? extends Registry<T>> registryKey, String namespace) {
            this.register = register;
            this.registryKey = registryKey;
            this.namespace = namespace;
        }

        @Override
        public <I extends T> BCHolder<T, I> register(String name, Function<Identifier, ? extends I> factory) {
            Identifier id = Identifier.fromNamespaceAndPath(namespace, name);
            RegistryObject<I> object = register.<I>register(name, () -> factory.apply(id));
            // The key is built from the id, as on NeoForge; RegistryObject#getKey is nullable.
            ResourceKey<T> key = ResourceKey.create(registryKey, id);
            BCHolder<T, I> wrapped = new BCHolder<>(id, key, new HolderSource<T, I>(object));
            entries.add(wrapped);
            return wrapped;
        }

        @Override
        public synchronized void addAlias(Identifier from, Identifier to) {
            if (aliasesFlushed) {
                throw new IllegalStateException("Alias " + from + " -> " + to + " added after the register event of "
                        + registryKey + "; add aliases during mod construction");
            }
            aliases.add(new Alias(from, to));
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
            ForgeBus.register(register);
            ForgeBus.listenMod(RegisterEvent.class, this::flushAliases);
        }

        private synchronized void flushAliases(RegisterEvent event) {
            if (!event.getRegistryKey().equals(registryKey)) {
                return;
            }
            aliasesFlushed = true;
            if (aliases.isEmpty()) {
                return;
            }
            IForgeRegistry<T> forge = event.getForgeRegistry();
            if (!(forge instanceof ForgeRegistry<T> registry)) {
                throw new IllegalStateException("Registry " + registryKey + " is not a Forge registry, so it takes no "
                        + "aliases (" + aliases.size() + " were added)");
            }
            for (Alias alias : aliases) {
                registry.addAlias(alias.from(), alias.to());
            }
            aliases.clear();
        }

        private record Alias(Identifier from, Identifier to) {}
    }

    /^*
     * The holder of a {@link RegistryObject}, asked for on each use and kept once bound. The registry may rebind its
     * holders when it is rebuilt from a server's snapshot, so a kept holder that has stopped being bound is asked for
     * again.
     *^/
    private static final class HolderSource<T, I extends T> implements Supplier<Holder<T>> {
        private final RegistryObject<I> object;
        private volatile Holder<T> bound;

        HolderSource(RegistryObject<I> object) {
            this.object = object;
        }

        @Override
        @SuppressWarnings("unchecked") // the object is an entry of type I of a registry of T, so its holder holds a T
        public Holder<T> get() {
            Holder<T> holder = bound;
            if (holder == null || !holder.isBound()) {
                holder = (Holder<T>) (Holder<?>) object.getHolder().orElseThrow(() -> new IllegalStateException(
                        "Registry entry " + object.getId() + " asked for before it was registered"));
                bound = holder;
            }
            return holder;
        }
    }
}
*///?}
