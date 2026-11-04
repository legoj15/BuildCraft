/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin.client;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.platform.fabric.FabricLoaderHookRoutes;

/^* The client twin of {@code ServerPlayerGameModeMixin}: the same sneak bypass in the client's predicted use. *^/
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {

    @ModifyExpressionValue(method = "performUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isSecondaryUseActive()Z"))
    private boolean buildcraft$sneakBypass(boolean sneaking, @Local(argsOnly = true) LocalPlayer player,
                                           @Local(argsOnly = true) BlockHitResult hit) {
        return sneaking && !FabricLoaderHookRoutes.sneakBypassesUse(player, player.level(), hit.getBlockPos());
    }
}
*///?}
