/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCFluidStorage;
import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.CapabilityRegistrar;

/^*
 * How BuildCraft's block entities expose their storages on Forge, where {@code TransferServices.registerCapabilities}
 * has nothing to register into: NeoForge keeps a table of providers per block entity type that its
 * {@code RegisterCapabilitiesEvent} fills and that lookups consult, Forge has no such event on 26.x (the one on 1.21.1
 * is for removal and only names a capability type) and no table. A Forge block entity is itself a capability provider
 * ({@code BlockEntity.getCapability(cap, side)}, the one Forge's own {@code FluidUtil} and hopper hooks call), which a
 * mod extends per instance through {@code AttachCapabilitiesEvent.BlockEntities} (on 1.21.1 the one generic
 * {@code AttachCapabilitiesEvent} for the {@code BlockEntity} class), fired from the block entity's constructor. So:
 *
 * <ul>
 * <li><b>Registration.</b> The registration callbacks (they call {@code .get()} on deferred block entity types, so they
 * cannot run during mod construction) are queued and run in call order inside {@code FMLCommonSetupEvent}, which Forge
 * fires after every registry event and before any world exists: the closest Forge has to NeoForge's event. Each
 * registration only fills this class's table, keyed by block entity type, and a call after that event is refused, as
 * NeoForge refuses one after its event. Several providers for one type and capability are asked in registration order,
 * the first non-null answer wins (NeoForge's rule).</li>
 * <li><b>Attachment.</b> One listener on {@code AttachCapabilitiesEvent.BlockEntities}, added on the first call to
 * {@link #queue} (Forge fires the event only if it has a listener when the block entity is constructed, and mod
 * construction is before any block entity exists), attaches a {@link TileProvider} to each block entity whose type is in
 * the table and nothing to any other; the provider asks the registered providers lazily, because the event fires before
 * the tile's own constructor has run.</li>
 * <li><b>Handlers and invalidation.</b> A query is answered with a {@code LazyOptional} of the Forge adapter of what the
 * registered provider returned for that tile and side. The provider is asked on every query (BuildCraft's answers can
 * change without a block update, a pipe's per side with its plugs), and the same {@code LazyOptional} is handed back for
 * as long as it returns the same object. When the answer changes, or the tile is removed or its chunk unloaded
 * ({@code AttachCapabilitiesEvent.addListener}, which Forge's patched {@code setRemoved} and {@code onChunkUnloaded}
 * run), the one handed out before is invalidated, which is Forge's signal to anyone holding it through
 * {@code LazyOptional.addListener}. {@code BlockEntity.getCapability} already answers empty once the tile is
 * invalidated. Identity is the test, so a provider that builds a new object on every call (several of BuildCraft's
 * energy and tank providers do) replaces and invalidates the optional on every query: the allocation NeoForge pays per
 * uncached lookup, plus a listener call that only a foreign mod holding the optional ever hears.</li>
 * </ul>
 ^/
final class ForgeTileCapabilities {
    private ForgeTileCapabilities() {}

    private static final Identifier KEY = Identifier.fromNamespaceAndPath("buildcraftunofficial", "transfer");

    /^* What is registered per block entity type; filled during {@code FMLCommonSetupEvent}, read-only after. ^/
    private static final Map<BlockEntityType<?>, Registrations> TYPES = new ConcurrentHashMap<>();
    private static final List<Consumer<CapabilityRegistrar>> QUEUE = new ArrayList<>();
    private static boolean subscribed;
    private static boolean fired;

    private static final class Registrations {
        final List<CapabilityRegistrar.BlockProvider<BlockEntity, BCItemStorage>> items = new CopyOnWriteArrayList<>();
        final List<CapabilityRegistrar.BlockProvider<BlockEntity, BCFluidStorage>> fluids = new CopyOnWriteArrayList<>();
        final List<CapabilityRegistrar.BlockProvider<BlockEntity, BCEnergyStorage>> energy = new CopyOnWriteArrayList<>();
        final Map<Capability<?>, List<CapabilityRegistrar.BlockProvider<BlockEntity, Object>>> direct =
            new ConcurrentHashMap<>();
    }

    /^*
     * Queues {@code registration} to run in {@code FMLCommonSetupEvent}, in call order.
     *
     * @throws IllegalStateException if that event has already fired, when the registration could never run
     ^/
    static synchronized void queue(Consumer<CapabilityRegistrar> registration) {
        if (fired) {
            throw new IllegalStateException("registerCapabilities was called after FMLCommonSetupEvent fired; "
                + "BuildCraft's capabilities must be registered from its init");
        }
        if (!subscribed) {
            subscribed = true;
            //? if >=1.21.10 {
            ForgeBus.listen(AttachCapabilitiesEvent.BlockEntities.class, ForgeTileCapabilities::attach);
            //?} else {
            /^ForgeBus.attachCapabilities(BlockEntity.class, ForgeTileCapabilities::attach);
            ^///?}
            ForgeBus.listenMod(FMLCommonSetupEvent.class, ForgeTileCapabilities::fire);
        }
        QUEUE.add(registration);
    }

    private static synchronized void fire(FMLCommonSetupEvent event) {
        fired = true;
        Registrar registrar = new Registrar();
        for (Consumer<CapabilityRegistrar> registration : QUEUE) {
            registration.accept(registrar);
        }
        QUEUE.clear();
    }

    //? if >=1.21.10 {
    private static void attach(AttachCapabilitiesEvent.BlockEntities event) {
    //?} else {
    /^private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
    ^///?}
        BlockEntity tile = event.getObject();
        Registrations registrations = TYPES.get(tile.getType());
        if (registrations == null) {
            return;
        }
        TileProvider provider = new TileProvider(tile, registrations);
        event.addCapability(KEY, provider);
        event.addListener(provider::invalidate);
    }

    private static Registrations registrations(BlockEntityType<?> type) {
        return TYPES.computeIfAbsent(type, key -> new Registrations());
    }

    // Each provider is called with the tile of its own type, which is the only tile the type's registrations see.
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity, S> CapabilityRegistrar.BlockProvider<BlockEntity, S> erase(
            CapabilityRegistrar.BlockProvider<T, ? extends S> provider) {
        return (CapabilityRegistrar.BlockProvider<BlockEntity, S>) (CapabilityRegistrar.BlockProvider<?, ?>) provider;
    }

    private static final class Registrar implements CapabilityRegistrar {
        @Override
        public <T extends BlockEntity> void blockItems(BlockEntityType<T> type, BlockProvider<T, BCItemStorage> provider) {
            registrations(type).items.add(erase(provider));
        }

        @Override
        public <T extends BlockEntity> void blockFluids(BlockEntityType<T> type, BlockProvider<T, BCFluidStorage> provider) {
            registrations(type).fluids.add(erase(provider));
        }

        @Override
        public <T extends BlockEntity> void blockEnergy(BlockEntityType<T> type, BlockProvider<T, BCEnergyStorage> provider) {
            registrations(type).energy.add(erase(provider));
        }

        @Override
        public <T extends BlockEntity, S> void blockDirect(buildcraft.api.capability.BCCapability<S> capability,
                BlockEntityType<T> type, BlockProvider<T, ? extends S> provider) {
            registrations(type).direct
                .computeIfAbsent(ForgeCapabilityTokens.of(capability), key -> new CopyOnWriteArrayList<>())
                .add(erase(provider));
        }

        @Override
        public void itemFluidsDrainOnlyConsumable(ItemLike item, Supplier<? extends DataComponentType<?>> content,
                int capacity) {
            ForgeFluidContainers.registerDrainOnlyConsumable(item, content, capacity);
        }
    }

    /^*
     * What one block entity answers: the registered providers of its type, asked per query, with the handed-out
     * {@code LazyOptional}s kept per side. Attached with the key {@code buildcraftunofficial:transfer}.
     ^/
    private static final class TileProvider implements ICapabilityProvider {
        private final BlockEntity tile;
        private final Registrations registrations;
        private Faces<BCItemStorage> items;
        private Faces<BCFluidStorage> fluids;
        private Faces<BCEnergyStorage> energy;
        private Map<Capability<?>, Faces<Object>> direct;

        TileProvider(BlockEntity tile, Registrations registrations) {
            this.tile = tile;
            this.registrations = registrations;
        }

        @Override
        public synchronized <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.ITEM_HANDLER) {
                if (registrations.items.isEmpty()) {
                    return LazyOptional.empty();
                }
                if (items == null) {
                    items = new Faces<>();
                }
                return items.resolve(side, first(registrations.items, side), ForgeItemStorages::toForge).cast();
            }
            if (cap == ForgeCapabilities.FLUID_HANDLER) {
                if (registrations.fluids.isEmpty()) {
                    return LazyOptional.empty();
                }
                if (fluids == null) {
                    fluids = new Faces<>();
                }
                return fluids.resolve(side, first(registrations.fluids, side), ForgeFluidStorages::toForge).cast();
            }
            if (cap == ForgeCapabilities.ENERGY) {
                if (registrations.energy.isEmpty()) {
                    return LazyOptional.empty();
                }
                if (energy == null) {
                    energy = new Faces<>();
                }
                return energy.resolve(side, first(registrations.energy, side), ForgeEnergyStorages::toForge).cast();
            }
            List<CapabilityRegistrar.BlockProvider<BlockEntity, Object>> providers = registrations.direct.get(cap);
            if (providers == null) {
                return LazyOptional.empty();
            }
            if (direct == null) {
                direct = new HashMap<>();
            }
            // Handed out as they are: no adapter, the object is the capability.
            return direct.computeIfAbsent(cap, key -> new Faces<>())
                .resolve(side, first(providers, side), Function.identity()).cast();
        }

        private <S> S first(List<CapabilityRegistrar.BlockProvider<BlockEntity, S>> providers, @Nullable Direction side) {
            for (CapabilityRegistrar.BlockProvider<BlockEntity, S> provider : providers) {
                S found = provider.get(tile, side);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        /^* The tile is being removed or its chunk unloaded: invalidate everything handed out. ^/
        synchronized void invalidate() {
            if (items != null) {
                items.invalidate();
            }
            if (fluids != null) {
                fluids.invalidate();
            }
            if (energy != null) {
                energy.invalidate();
            }
            if (direct != null) {
                direct.values().forEach(Faces::invalidate);
                direct.clear();
            }
        }
    }

    /^*
     * The {@code LazyOptional}s handed out for one kind of capability, one per side (the last slot is the unsided
     * query), each remembering the BuildCraft object it was made for. The arrays are allocated on the first answer: a
     * pipe holder has thousands of siblings and most sides are never asked.
     ^/
    private static final class Faces<S> {
        private static final int UNSIDED = Direction.values().length;

        private Object[] sources;
        private LazyOptional<?>[] optionals;

        /^*
         * The optional for {@code source} on {@code side}: the one already handed out if it was made for this very
         * object, else a new one (the old one invalidated, so whoever holds it learns the answer changed), or empty for
         * no object.
         ^/
        synchronized LazyOptional<?> resolve(@Nullable Direction side, @Nullable S source, Function<S, ?> adapt) {
            int face = side == null ? UNSIDED : side.ordinal();
            if (optionals != null) {
                LazyOptional<?> held = optionals[face];
                if (held != null) {
                    if (sources[face] == source && held.isPresent()) {
                        return held;
                    }
                    optionals[face] = null;
                    sources[face] = null;
                    held.invalidate();
                }
            }
            if (source == null) {
                return LazyOptional.empty();
            }
            if (optionals == null) {
                optionals = new LazyOptional<?>[UNSIDED + 1];
                sources = new Object[UNSIDED + 1];
            }
            Object handler = adapt.apply(source);
            LazyOptional<Object> made = LazyOptional.of(() -> handler);
            optionals[face] = made;
            sources[face] = source;
            return made;
        }

        synchronized void invalidate() {
            if (optionals == null) {
                return;
            }
            for (int face = 0; face < optionals.length; face++) {
                LazyOptional<?> held = optionals[face];
                optionals[face] = null;
                sources[face] = null;
                if (held != null) {
                    held.invalidate();
                }
            }
        }
    }
}
*///?}
