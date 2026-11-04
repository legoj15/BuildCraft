/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import buildcraft.platform.fabric.FabricGameHooks;

/^* NeoForge's {@code PlayerTickEvent.Post} on Fabric ({@link FabricGameHooks#onPlayerTickEnd}): the end of a server
 *  player's own tick. *^/
@Mixin(Player.class)
abstract class PlayerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void buildcraft$tickEnd(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            FabricGameHooks.firePlayerTickEnd(player);
        }
    }
}
*///?}
