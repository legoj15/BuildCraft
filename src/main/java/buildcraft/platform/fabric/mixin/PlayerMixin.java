/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.FakePlayer;

import buildcraft.platform.fabric.FabricGameHooks;

/^* NeoForge's {@code PlayerTickEvent.Post} on Fabric ({@link FabricGameHooks#onPlayerTickEnd}): the end of a server
 *  player's own tick; and its entity-interaction event for a fake player's click
 *  ({@link FabricGameHooks#fireFakePlayerEntityInteract}), at the same point of {@code interactOn}; and its break-speed
 *  event ({@link FabricGameHooks#fireBreakSpeed}) at the end of {@code getDestroySpeed}, for real and fake players. *^/
@Mixin(Player.class)
abstract class PlayerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void buildcraft$tickEnd(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            FabricGameHooks.firePlayerTickEnd(player);
        }
    }

    @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
    private float buildcraft$breakSpeed(float speed, BlockState state) {
        return FabricGameHooks.fireBreakSpeed((Player) (Object) this, state, speed);
    }

    // A spectator's click opens a menu and asks nothing, as on NeoForge.
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void buildcraft$fakePlayerEntityInteract(Entity entity, InteractionHand hand, Vec3 location,
            CallbackInfoReturnable<InteractionResult> cir) {
        Player self = (Player) (Object) this;
        if (self instanceof FakePlayer && !self.isSpectator()) {
            InteractionResult refused = FabricGameHooks.fireFakePlayerEntityInteract(self, entity, hand, location);
            if (refused != null) {
                cir.setReturnValue(refused);
            }
        }
    }
}
*///?}
