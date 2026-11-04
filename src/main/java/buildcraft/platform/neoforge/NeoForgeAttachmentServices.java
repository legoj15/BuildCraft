/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.function.Supplier;

//? if <1.21.10 {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
*///?}
import net.minecraft.world.entity.Entity;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import buildcraft.platform.registry.AttachmentServices;
import buildcraft.platform.registry.BCAttachment;
import buildcraft.platform.registry.BCAttachmentData;
import buildcraft.platform.registry.BCAttachmentRegistrar;

/**
 * {@link AttachmentServices} on NeoForge: an {@link AttachmentType} per attachment in a {@link DeferredRegister} of
 * attachment types. The serializer is the one {@code AttachmentType.serializable} builds (read into a fresh default
 * value, write through the payload's own save method), so a payload saves exactly the bytes it saved as a
 * {@code ValueIOSerializable} (1.21.10+) or {@code INBTSerializable} (1.21.1) attachment.
 */
public final class NeoForgeAttachmentServices implements AttachmentServices {
    @Override
    public BCAttachmentRegistrar create(String namespace) {
        return new Registrar(DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, namespace));
    }

    /** The NeoForge type behind {@code attachment}, for tests that drive NeoForge's own attachment holder. */
    @SuppressWarnings("unchecked")
    static <T extends BCAttachmentData> AttachmentType<T> typeOf(BCAttachment<T> attachment) {
        return ((Attachment<T>) attachment).holder.get();
    }

    private static final class Registrar implements BCAttachmentRegistrar {
        private final DeferredRegister<AttachmentType<?>> register;

        Registrar(DeferredRegister<AttachmentType<?>> register) {
            this.register = register;
        }

        @Override
        public <T extends BCAttachmentData> BCAttachment<T> register(String name, Supplier<T> factory,
                boolean copyOnDeath) {
            DeferredHolder<AttachmentType<?>, AttachmentType<T>> holder = register.register(name, () -> {
                AttachmentType.Builder<T> builder = AttachmentType.builder(factory).serialize(serializer(factory));
                if (copyOnDeath) {
                    builder.copyOnDeath();
                }
                return builder.build();
            });
            return new Attachment<>(holder);
        }

        @Override
        public void subscribe() {
            register.register(ModLoadingContext.get().getActiveContainer().getEventBus());
        }
    }

    //? if >=1.21.10 {
    private static <T extends BCAttachmentData> IAttachmentSerializer<T> serializer(Supplier<T> factory) {
        return new IAttachmentSerializer<>() {
            @Override
            public T read(IAttachmentHolder holder, ValueInput input) {
                T value = factory.get();
                value.deserialize(input);
                return value;
            }

            @Override
            public boolean write(T attachment, ValueOutput output) {
                attachment.serialize(output);
                return true;
            }
        };
    }
    //?} else {
    /*private static <T extends BCAttachmentData> IAttachmentSerializer<CompoundTag, T> serializer(Supplier<T> factory) {
        return new IAttachmentSerializer<>() {
            @Override
            public T read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                T value = factory.get();
                value.deserializeNBT(provider, tag);
                return value;
            }

            @Override
            public CompoundTag write(T attachment, HolderLookup.Provider provider) {
                return attachment.serializeNBT(provider);
            }
        };
    }
    *///?}

    private static final class Attachment<T extends BCAttachmentData> implements BCAttachment<T> {
        private final DeferredHolder<AttachmentType<?>, AttachmentType<T>> holder;

        Attachment(DeferredHolder<AttachmentType<?>, AttachmentType<T>> holder) {
            this.holder = holder;
        }

        @Override
        public T get(Entity entity) {
            return entity.getData(holder.get());
        }

        @Override
        public void set(Entity entity, T value) {
            entity.setData(holder.get(), value);
        }
    }
}
//?}
