/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
//? if >=1.21.10 {
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
//?}

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import buildcraft.platform.Platform;
import buildcraft.platform.registry.AttachmentServices;
import buildcraft.platform.registry.BCAttachment;
import buildcraft.platform.registry.BCAttachmentData;
import buildcraft.platform.registry.BCAttachmentRegistrar;

/^*
 * {@link AttachmentServices} on Fabric: fabric-data-attachment-api-v1, registered at the register call. The payload is
 * persisted as the compound its own save method writes, written and read with the running server's registries (the
 * level's): vanilla keeps the provider of the ops Fabric API hands the codec private, and attachments persist
 * only while a server runs. With no server it falls back to no registries, which still carries primitive payloads.
 * The saved layout differs from NeoForge's (worlds do not move between loaders).
 *^/
public final class FabricAttachmentServices implements AttachmentServices {
    @Override
    public BCAttachmentRegistrar create(String namespace) {
        return new BCAttachmentRegistrar() {
            @Override
            public <T extends BCAttachmentData> BCAttachment<T> register(String name, Supplier<T> factory,
                    boolean copyOnDeath) {
                AttachmentType<T> type = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(namespace, name),
                        builder -> {
                            builder.initializer(factory).persistent(codec(factory));
                            if (copyOnDeath) {
                                builder.copyOnDeath();
                            }
                        });
                return new BCAttachment<>() {
                    @Override
                    public T get(Entity holder) {
                        return ((AttachmentTarget) holder).getAttachedOrCreate(type);
                    }

                    @Override
                    public void set(Entity holder, T value) {
                        ((AttachmentTarget) holder).setAttached(type, value);
                    }
                };
            }

            @Override
            public void subscribe() {
                // Attachments were registered at their register calls.
            }
        };
    }

    private static <T extends BCAttachmentData> Codec<T> codec(Supplier<T> factory) {
        //? if >=1.21.10 {
        return CompoundTag.CODEC.xmap(tag -> {
            T value = factory.get();
            value.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, registries(), tag));
            return value;
        }, value -> {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries());
            value.serialize(output);
            return output.buildResult();
        });
        //?} else {
        /^return CompoundTag.CODEC.xmap(tag -> {
            T value = factory.get();
            value.deserializeNBT(registries(), tag);
            return value;
        }, value -> value.serializeNBT(registries()));
        ^///?}
    }

    /^* The running server's registries, else none: primitives round-trip, registry-backed fields default. *^/
    private static HolderLookup.Provider registries() {
        MinecraftServer server = Platform.loader().currentServer();
        return server == null ? RegistryAccess.EMPTY : server.registryAccess();
    }
}
*///?}
