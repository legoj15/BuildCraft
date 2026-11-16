/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import java.util.OptionalInt;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

import buildcraft.platform.forge.ForgeMenus;
import buildcraft.platform.menu.MenuOpeningData;

/^*
 * NeoForge's {@code writeClientSideData} on Forge: a {@link MenuOpeningData} provider handed straight to vanilla's
 * {@code ServerPlayer#openMenu(MenuProvider)} (the spectator path of {@code ServerPlayerGameMode#useItemOn}, which Forge
 * adds only an event beside) is opened through Forge's own {@code openMenu(provider, writer)} with the provider's opening
 * data, so the client factory gets the buffer it gets from {@code BCMenus#open} and not the {@code null} Forge's
 * {@code IContainerFactory} defaults to for a menu that arrives through the vanilla packet. The work is
 * {@link ForgeMenus#openWithOpeningData}; this class only routes to it.
 *
 * <p><b>Injection point.</b> The head of {@code openMenu(MenuProvider)}, cancelling with the result. Forge's patch of this
 * method adds one call at the tail (the {@code onPlayerOpenContainer} event, which Forge's two-argument {@code openMenu}
 * posts too), so nothing at the head is touched by it, and a head injection depends on no local variable, ordinal or
 * call target inside the body. The method's own descriptor is vanilla's and Forge cannot change it. A provider that is not
 * a {@code MenuOpeningData} (every vanilla and foreign menu) falls through to vanilla untouched. There is no recursion:
 * Forge's two-argument {@code openMenu} never calls the one-argument one.
 *^/
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;", at = @At("HEAD"),
            cancellable = true)
    private void buildcraft$openingData(@Nullable MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (provider instanceof MenuOpeningData data) {
            cir.setReturnValue(ForgeMenus.openWithOpeningData((ServerPlayer) (Object) this, provider, data));
        }
    }
}
*///?}
