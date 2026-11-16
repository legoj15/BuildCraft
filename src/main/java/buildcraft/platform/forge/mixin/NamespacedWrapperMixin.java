/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import java.util.Map;
import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import net.minecraftforge.registries.ForgeRegistry;

/^*
 * Registry aliases through vanilla's by-name codecs, which is what NeoForge's {@code MappedRegistry} does by calling its
 * {@code resolve} in {@code get(Identifier)} and {@code get(ResourceKey)}. Forge's own aliases ({@code ForgeRegistry}'s
 * map, filled by {@code ForgeRegistrar#flushAliases} from BuildCraft's {@code LegacyAliases}) reach the lookups that go
 * through its {@code getRaw} (the wrapper's {@code getValue}, {@code getOptional}), but the two holder lookups read the
 * wrapper's own {@code holdersByName} and know no alias. {@code Registry#byNameCodec} and {@code holderByNameCodec},
 * so {@code Item.CODEC} and every saved item stack, resolve through those two, so a stack saved under a legacy
 * BuildCraft id failed to decode.
 *
 * <p><b>Injection point.</b> The tail of each of the two lookups, acting only on an empty answer: a name that is
 * registered answers exactly as before and pays nothing, and only a miss is asked of the alias map, through
 * {@code ForgeRegistry#getRaw} (which follows the alias chain, as it does for every other lookup) and
 * {@code getKey} (the value's registered name, whose holder is then read). No local variable is captured and no ordinal
 * is used; Forge's patch of these two methods is nothing (the class is Forge's own, not a patched vanilla one), and the
 * descriptors are the vanilla {@code Registry}/{@code HolderGetter} ones, which Forge cannot change. The target is
 * named as a string because the wrapper is package-private; the class is Forge's, hence {@code remap = false}.
 *
 * <p>NeoForge's {@code containsKey} does not follow aliases and Forge's does; that is left as Forge has it.
 *^/
@Mixin(targets = "net.minecraftforge.registries.NamespacedWrapper", remap = false)
abstract class NamespacedWrapperMixin<T> {
    @Shadow
    @Final
    private ForgeRegistry<T> delegate;

    @Shadow
    @Final
    private Map<Identifier, Holder.Reference<T>> holdersByName;

    @Inject(method = "get(Lnet/minecraft/resources/Identifier;)Ljava/util/Optional;", at = @At("RETURN"),
            cancellable = true)
    private void buildcraft$aliasedHolderById(Identifier id, CallbackInfoReturnable<Optional<Holder.Reference<T>>> cir) {
        if (cir.getReturnValue().isEmpty() && id != null) {
            Holder.Reference<T> holder = buildcraft$resolveAlias(id);
            if (holder != null) {
                cir.setReturnValue(Optional.of(holder));
            }
        }
    }

    @Inject(method = "get(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", at = @At("RETURN"),
            cancellable = true)
    private void buildcraft$aliasedHolderByKey(ResourceKey<T> key, CallbackInfoReturnable<Optional<Holder.Reference<T>>> cir) {
        if (cir.getReturnValue().isEmpty() && key != null) {
            Holder.Reference<T> holder = buildcraft$resolveAlias(key.identifier());
            if (holder != null) {
                cir.setReturnValue(Optional.of(holder));
            }
        }
    }

    /^* The holder of the value {@code id} is an alias of, or null when it is not one. ^/
    @Unique
    private Holder.Reference<T> buildcraft$resolveAlias(Identifier id) {
        T value = delegate.getRaw(id);
        if (value == null) {
            return null;
        }
        Identifier registered = delegate.getKey(value);
        return registered == null || registered.equals(id) ? null : holdersByName.get(registered);
    }
}
*///?}
