/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.platform.fabric.FabricLoaderHookRoutes;

/^*
 * NeoForge's {@code doesSneakBypassUse} on the server: a sneaking player whose hands are empty or hold a
 * {@code SneakBypassItem} still uses the clicked block, as NeoForge's patched {@code useItemOn} lets them.
 *^/
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {

    @ModifyExpressionValue(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isSecondaryUseActive()Z"))
    private boolean buildcraft$sneakBypass(boolean sneaking, @Local(argsOnly = true) ServerPlayer player,
                                           @Local(argsOnly = true) Level level,
                                           @Local(argsOnly = true) BlockHitResult hit) {
        return sneaking && !FabricLoaderHookRoutes.sneakBypassesUse(player, level, hit.getBlockPos());
    }
}
*///?}
