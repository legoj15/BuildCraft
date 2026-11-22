/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
//? if >=1.21.10 {
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
//?}

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import buildcraft.platform.registry.AttachmentServices;
import buildcraft.platform.registry.BCAttachment;
import buildcraft.platform.registry.BCAttachmentData;
import buildcraft.platform.registry.BCAttachmentRegistrar;

/^*
 * {@link AttachmentServices} on MinecraftForge, which has no data attachments: every attachment of a player lives in
 * one capability provider ({@link Slots}) that BuildCraft attaches to each player, and Forge saves it with the entity
 * under its {@code ForgeCaps} key. The payload is the compound its own save method writes, written and read with the
 * registries of the save in progress (the same {@code TagValueOutput}/{@code TagValueInput} route as
 * {@code FabricAttachmentServices}; on 1.21.1 the payload's own NBT methods), so a world saved on Forge does not move to
 * the other loaders.
 *
 * <p><b>Players only.</b> Forge fires its attach event for every entity created, and a provider on all of them costs an
 * allocation (and a capability dispatcher) per item entity and experience orb for attachments that are only ever read
 * on players. Every BuildCraft attachment is a player's (advancement progress), so the provider goes on players alone
 * and {@link BCAttachment#get} refuses another holder rather than hand out a value that would be lost.
 *
 * <p><b>Copy on death.</b> Forge copies no capability to a respawned player; a {@link PlayerEvent.Clone} listener does,
 * for every attachment on a clone that is not a death (an end-portal return, which NeoForge also keeps whole) and for
 * the {@code copyOnDeath} ones after a death. The new player is handed the old one's value object, not a copy: the old
 * player is discarded by then. The old player's capabilities are invalid once it is removed, so the listener revives
 * them for the read, as Forge's documentation of {@code reviveCaps} asks.
 *
 * <p><b>Unknown ids.</b> A saved attachment whose registration is gone (an older save, a removed attachment) is kept
 * as the tag it was and written back, so downgrading and upgrading does not erase it.
 *
 * <p><b>Registered at the register call</b>, like Fabric's, since there is no deferred step: {@link
 * BCAttachmentRegistrar#subscribe()} does nothing. The one attach listener and the one clone listener are added on the
 * first registration.
 *^/
public final class ForgeAttachmentServices implements AttachmentServices {
    /^* The capability BuildCraft's provider answers to; no other mod asks for it. ^/
    private static final Capability<Slots> SLOTS_CAPABILITY = CapabilityManager.get(new CapabilityToken<Slots>() {});
    private static final Identifier SLOTS_KEY = Identifier.fromNamespaceAndPath("buildcraftunofficial", "attachments");

    /^* Every registered attachment by id. Registration is during mod construction, reads are from the game threads. ^/
    private static final Map<Identifier, Definition<?>> DEFINITIONS = new LinkedHashMap<>();
    private static boolean listening;

    @Override
    public BCAttachmentRegistrar create(String namespace) {
        return new BCAttachmentRegistrar() {
            @Override
            public <T extends BCAttachmentData> BCAttachment<T> register(String name, Supplier<T> factory,
                    boolean copyOnDeath) {
                Definition<T> definition = new Definition<>(Identifier.fromNamespaceAndPath(namespace, name), factory,
                        copyOnDeath);
                define(definition);
                return definition;
            }

            @Override
            public void subscribe() {
                // Attachments were registered at their register calls.
            }
        };
    }

    private static synchronized void define(Definition<?> definition) {
        if (DEFINITIONS.putIfAbsent(definition.id, definition) != null) {
            throw new IllegalArgumentException("Attachment " + definition.id + " is already registered");
        }
        if (!listening) {
            listening = true;
            //? if >=1.21.10 {
            ForgeBus.listen(AttachCapabilitiesEvent.Entities.class, ForgeAttachmentServices::attach);
            //?} else {
            /^ForgeBus.attachCapabilities(Entity.class, ForgeAttachmentServices::attach);
            ^///?}
            ForgeBus.listen(PlayerEvent.Clone.class, ForgeAttachmentServices::copyToClone);
        }
    }

    private static synchronized Definition<?> definition(Identifier id) {
        return DEFINITIONS.get(id);
    }

    //? if >=1.21.10 {
    private static void attach(AttachCapabilitiesEvent.Entities event) {
    //?} else {
    /^private static void attach(AttachCapabilitiesEvent<Entity> event) {
    ^///?}
        if (event.getObject() instanceof Player) {
            // No invalidation listener on purpose: Forge asks providers to invalidate their LazyOptionals when the
            // player is removed, but an invalidated one stays invalid, and the clone listener has to read the old
            // player's after that removal. The provider is reachable only through its player, so nothing outlives it.
            event.addCapability(SLOTS_KEY, new Slots());
        }
    }

    private static void copyToClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        try {
            Slots from = original.getCapability(SLOTS_CAPABILITY).resolve().orElse(null);
            Slots to = event.getEntity().getCapability(SLOTS_CAPABILITY).resolve().orElse(null);
            if (from == null || to == null) {
                return;
            }
            for (Map.Entry<Identifier, BCAttachmentData> entry : from.values.entrySet()) {
                Definition<?> definition = definition(entry.getKey());
                if (!event.isWasDeath() || (definition != null && definition.copyOnDeath)) {
                    to.values.put(entry.getKey(), entry.getValue());
                }
            }
            if (!event.isWasDeath()) {
                to.unknown.merge(from.unknown);
            }
        } finally {
            original.invalidateCaps();
        }
    }

    private static Slots slots(Entity holder, Identifier id) {
        if (!(holder instanceof Player)) {
            throw new IllegalArgumentException("Attachment " + id + " asked of " + holder + ": on Forge BuildCraft's "
                    + "attachments exist on players only");
        }
        return holder.getCapability(SLOTS_CAPABILITY).resolve().orElseThrow(() -> new IllegalStateException(
                "Attachment " + id + " asked of " + holder + " before its capabilities were attached or after they "
                        + "were invalidated"));
    }

    /^* One registered attachment, and the handle common code reads and writes it through. ^/
    private static final class Definition<T extends BCAttachmentData> implements BCAttachment<T> {
        final Identifier id;
        final Supplier<T> factory;
        final boolean copyOnDeath;

        Definition(Identifier id, Supplier<T> factory, boolean copyOnDeath) {
            this.id = id;
            this.factory = factory;
            this.copyOnDeath = copyOnDeath;
        }

        @Override
        @SuppressWarnings("unchecked") // only this definition writes its id, and always a T
        public T get(Entity holder) {
            return (T) slots(holder, id).values.computeIfAbsent(id, k -> factory.get());
        }

        @Override
        public void set(Entity holder, T value) {
            slots(holder, id).values.put(id, value);
        }
    }

    /^*
     * The provider on a player: the values by attachment id, and the capability face Forge needs from a provider. The
     * values are mutated in place by their holders and read back at the next save, so there is no dirty flag.
     *^/
    private static final class Slots implements ICapabilitySerializable<CompoundTag> {
        final Map<Identifier, BCAttachmentData> values = new HashMap<>();
        /^* Saved attachments no registration claimed, kept as saved. ^/
        final CompoundTag unknown = new CompoundTag();
        final LazyOptional<Slots> self = LazyOptional.of(() -> this);

        @Override
        public <C> LazyOptional<C> getCapability(Capability<C> cap, @Nullable Direction side) {
            return SLOTS_CAPABILITY.orEmpty(cap, self);
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            CompoundTag out = unknown.copy();
            for (Map.Entry<Identifier, BCAttachmentData> entry : values.entrySet()) {
                //? if >=1.21.10 {
                TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
                entry.getValue().serialize(output);
                out.put(entry.getKey().toString(), output.buildResult());
                //?} else {
                /^out.put(entry.getKey().toString(), entry.getValue().serializeNBT(registries));
                ^///?}
            }
            return out;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            values.clear();
            //? if >=1.21.10 {
            for (String key : tag.keySet()) {
            //?} else {
            /^for (String key : tag.getAllKeys()) {
            ^///?}
                Tag saved = tag.get(key);
                Identifier id = Identifier.tryParse(key);
                Definition<?> definition = id == null ? null : definition(id);
                if (definition != null && saved instanceof CompoundTag compound) {
                    BCAttachmentData value = definition.factory.get();
                    //? if >=1.21.10 {
                    value.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, registries, compound));
                    //?} else {
                    /^value.deserializeNBT(registries, compound);
                    ^///?}
                    values.put(id, value);
                } else if (saved != null) {
                    unknown.put(key, saved);
                }
            }
        }
    }
}
*///?}
