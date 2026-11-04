/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import buildcraft.platform.Platform;

/**
 * Registers the entries of one registry under one namespace, the loader-neutral twin of NeoForge's
 * {@code DeferredRegister}. Each {@code register} call hands back a {@link BCHolder} at once.
 *
 * <p><b>Timing.</b> On NeoForge entries are queued and registered in the loader's register event, after
 * {@link #subscribe()} attached the registrar to the mod being constructed. On Fabric each entry is registered at its
 * {@code register} call and {@link #subscribe()} does nothing. Either way, entries are registered in call order and
 * must be made during mod construction.
 *
 * <p>{@link Blocks} and {@link Items} carry the block and item helpers: they absorb the property cliff (1.21.10+
 * defers the properties so the id can be set on them first, 1.21.1 takes them eagerly), and {@link DataComponents}
 * builds component types.
 *
 * <p>This is a contract (see the package documentation of {@code buildcraft.platform}): JDK and vanilla types only.
 *
 * @param <T> the registry's element type
 */
public interface BCRegistrar<T> {

    /** A new registrar for {@code registry}, naming its entries in {@code namespace}. */
    static <T> BCRegistrar<T> create(ResourceKey<? extends Registry<T>> registry, String namespace) {
        return Platform.registries().create(registry, namespace);
    }

    /** A new block registrar naming its entries in {@code namespace}. */
    static Blocks createBlocks(String namespace) {
        return new Blocks(create(Registries.BLOCK, namespace));
    }

    /** A new item registrar naming its entries in {@code namespace}. */
    static Items createItems(String namespace) {
        return new Items(create(Registries.ITEM, namespace));
    }

    /** A new data-component registrar naming its entries in {@code namespace}. */
    static DataComponents createDataComponents(String namespace) {
        return new DataComponents(create(Registries.DATA_COMPONENT_TYPE, namespace));
    }

    /** Registers {@code namespace:name}, its value built by {@code factory} from the entry's id. */
    <I extends T> BCHolder<T, I> register(String name, Function<Identifier, ? extends I> factory);

    /** Registers {@code namespace:name}, its value built by {@code factory}. */
    default <I extends T> BCHolder<T, I> register(String name, Supplier<? extends I> factory) {
        return register(name, id -> factory.get());
    }

    /** Makes the old id {@code from} resolve to {@code to} in this registry (an id kept for old worlds). */
    void addAlias(Identifier from, Identifier to);

    /** Every entry registered so far, in call order. */
    Collection<BCHolder<T, ? extends T>> getEntries();

    /** The registry this registrar adds to. */
    ResourceKey<? extends Registry<T>> getRegistryKey();

    /** The namespace of this registrar's entries. */
    String getNamespace();

    /**
     * Attaches the registrar to the loader, during mod construction. Call it once, where the registrar's
     * {@code DeferredRegister.register(modBus)} used to be: on NeoForge the order of these calls is the order the
     * registrars of one registry register in. On Fabric entries are already registered, so it does nothing.
     */
    void subscribe();

    /** A registrar that forwards every call to another; the base of the typed helpers. */
    abstract class Forwarding<T> implements BCRegistrar<T> {
        protected final BCRegistrar<T> delegate;

        protected Forwarding(BCRegistrar<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public <I extends T> BCHolder<T, I> register(String name, Function<Identifier, ? extends I> factory) {
            return delegate.register(name, factory);
        }

        @Override
        public void addAlias(Identifier from, Identifier to) {
            delegate.addAlias(from, to);
        }

        @Override
        public Collection<BCHolder<T, ? extends T>> getEntries() {
            return delegate.getEntries();
        }

        @Override
        public ResourceKey<? extends Registry<T>> getRegistryKey() {
            return delegate.getRegistryKey();
        }

        @Override
        public String getNamespace() {
            return delegate.getNamespace();
        }

        @Override
        public void subscribe() {
            delegate.subscribe();
        }
    }

    /** Block registration, with the properties handled as NeoForge's {@code DeferredRegister.Blocks} does. */
    final class Blocks extends Forwarding<Block> {
        Blocks(BCRegistrar<Block> delegate) {
            super(delegate);
        }

        /** Registers a block built by {@code factory} from {@code properties} (with the block's id set on 1.21.10+). */
        public <B extends Block> BCBlockHolder<B> registerBlock(String name,
                Function<BlockBehaviour.Properties, ? extends B> factory, Supplier<BlockBehaviour.Properties> properties) {
            //? if >=1.21.10 {
            return new BCBlockHolder<B>(register(name,
                    id -> factory.apply(properties.get().setId(ResourceKey.create(Registries.BLOCK, id)))));
            //?} else {
            /*BlockBehaviour.Properties props = properties.get();
            return new BCBlockHolder<B>(register(name, id -> factory.apply(props)));
            *///?}
        }

        /** Registers a block built by {@code factory} from default properties. */
        public <B extends Block> BCBlockHolder<B> registerBlock(String name,
                Function<BlockBehaviour.Properties, ? extends B> factory) {
            return registerBlock(name, factory, BlockBehaviour.Properties::of);
        }

        /** Registers a plain {@link Block} with {@code properties}. */
        public BCBlockHolder<Block> registerSimpleBlock(String name, Supplier<BlockBehaviour.Properties> properties) {
            return registerBlock(name, Block::new, properties);
        }
    }

    /** Item registration, with the properties handled as NeoForge's {@code DeferredRegister.Items} does. */
    final class Items extends Forwarding<Item> {
        Items(BCRegistrar<Item> delegate) {
            super(delegate);
        }

        /** Registers an item built by {@code factory} from {@code properties} (with the item's id set on 1.21.10+). */
        public <I extends Item> BCItemHolder<I> registerItem(String name,
                Function<Item.Properties, ? extends I> factory, Supplier<Item.Properties> properties) {
            //? if >=1.21.10 {
            return new BCItemHolder<I>(register(name,
                    id -> factory.apply(properties.get().setId(ResourceKey.create(Registries.ITEM, id)))));
            //?} else {
            /*Item.Properties props = properties.get();
            return new BCItemHolder<I>(register(name, id -> factory.apply(props)));
            *///?}
        }

        /** Registers an item built by {@code factory} from fresh properties that {@code properties} adjusts. */
        public <I extends Item> BCItemHolder<I> registerItem(String name,
                Function<Item.Properties, ? extends I> factory, UnaryOperator<Item.Properties> properties) {
            return registerItem(name, factory, () -> properties.apply(new Item.Properties()));
        }

        /** Registers an item built by {@code factory} from default properties. */
        public <I extends Item> BCItemHolder<I> registerItem(String name, Function<Item.Properties, ? extends I> factory) {
            return registerItem(name, factory, UnaryOperator.identity());
        }

        /** Registers a plain {@link Item} with default properties. */
        public BCItemHolder<Item> registerSimpleItem(String name) {
            return registerItem(name, Item::new);
        }

        /** Registers a plain {@link Item} whose properties {@code properties} adjusts. */
        public BCItemHolder<Item> registerSimpleItem(String name, UnaryOperator<Item.Properties> properties) {
            return registerItem(name, Item::new, properties);
        }

        /** Registers a plain {@link BlockItem} for {@code block}, named after it. */
        public BCItemHolder<BlockItem> registerSimpleBlockItem(BCBlockHolder<?> block) {
            return registerBlockItem(block, BlockItem::new);
        }

        /**
         * Registers a {@link BlockItem} of a class of BuildCraft's own for {@code block}, named after the block and
         * set up as {@link #registerSimpleBlockItem} sets one up (1.21.10+ takes its description from the block).
         */
        public <B extends Block, I extends BlockItem> BCItemHolder<I> registerBlockItem(BCBlockHolder<B> block,
                BiFunction<? super B, Item.Properties, ? extends I> factory) {
            return registerItem(block.id().getPath(), props -> factory.apply(block.get(), props),
                    //? if >=1.21.10 {
                    props -> props.useBlockDescriptionPrefix());
                    //?} else {
                    /*props -> props);*/
                    //?}
        }
    }

    /** Data-component registration, as NeoForge's {@code DeferredRegister.DataComponents} does it. */
    final class DataComponents extends Forwarding<DataComponentType<?>> {
        DataComponents(BCRegistrar<DataComponentType<?>> delegate) {
            super(delegate);
        }

        /** Registers a component type that {@code builder} configures. */
        public <D> BCHolder<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name,
                UnaryOperator<DataComponentType.Builder<D>> builder) {
            return register(name, () -> builder.apply(DataComponentType.builder()).build());
        }
    }
}
