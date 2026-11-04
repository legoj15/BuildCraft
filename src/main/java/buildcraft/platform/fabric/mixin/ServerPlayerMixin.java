/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import java.util.OptionalInt;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;

import buildcraft.platform.fabric.FabricMenus;
import buildcraft.platform.menu.MenuOpeningData;

/^*
 * NeoForge's {@code writeClientSideData} on Fabric: a {@link MenuOpeningData} provider handed straight to
 * {@code openMenu} (vanilla's spectator path) is opened as an extended menu provider writing its own opening data, so
 * the client factory gets the same bytes it gets from {@code BCMenus#open}.
 *^/
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;", at = @At("HEAD"),
            cancellable = true)
    private void buildcraft$openingData(@Nullable MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (provider instanceof MenuOpeningData data && !(provider instanceof ExtendedMenuProvider<?>)) {
            cir.setReturnValue(((ServerPlayer) (Object) this).openMenu(
                FabricMenus.withOpeningData(provider, data::writeOpeningData)));
        }
    }
}
*///?}
