/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import com.mojang.serialization.Lifecycle;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;

import buildcraft.core.BCCore;

/^*
 * BuildCraft's data-driven registry entries register as stable on Forge, as they do on NeoForge and Fabric (#213).
 *
 * <p>Vanilla gives an entry loaded from a data pack {@code Lifecycle.experimental()} unless the pack is a known vanilla pack
 * ({@code ResourceManagerRegistryLoadTask}'s registration-info cache), and Forge hands its mod data packs no known-pack
 * information at all. So BuildCraft's worldgen data (its configured and placed features and its biome modifiers, the
 * only non-stable entries a boot showed) made every registry access experimental: creating a world raised vanilla's
 * "experimental settings" warning and loading it stopped at {@code BackupConfirmScreen}.
 *
 * <p><b>Injection point.</b> The registration-info argument at the head of {@code MappedRegistry#register(ResourceKey, T,
 * RegistrationInfo)}, the one method every data-pack entry is registered through (vanilla's loader computes the info in
 * a lambda, which is no stable target). Only an entry in BuildCraft's namespace whose info is not stable is changed, and
 * its known-pack information is kept; every other mod's entries and Forge's keep Forge's answer. Plain {@code
 * ModifyVariable}, so the same mixin serves Forge 52 (1.21.1), which ships no MixinExtras.
 *^/
@Mixin(MappedRegistry.class)
abstract class MappedRegistryMixin<T> {
    @ModifyVariable(method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
            at = @At("HEAD"), argsOnly = true)
    private RegistrationInfo buildcraft$stableOwnEntries(RegistrationInfo info, ResourceKey<T> key) {
        //? if >=1.21.10 {
        String namespace = key.identifier().getNamespace();
        //?} else {
        /^String namespace = key.location().getNamespace();
        ^///?}
        if (info.lifecycle() != Lifecycle.stable() && BCCore.MODID.equals(namespace)) {
            return new RegistrationInfo(info.knownPackInfo(), Lifecycle.stable());
        }
        return info;
    }
}
*///?}
