/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import javax.annotation.Nullable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.FakePlayer;

import buildcraft.platform.fabric.FabricGameHooks;
import buildcraft.platform.fabric.FabricVanillaExtensions;

/^* NeoForge's {@code PlayerTickEvent.Post} on Fabric ({@link FabricGameHooks#onPlayerTickEnd}): the end of a server
 *  player's own tick; and its entity-interaction event for a fake player's click
 *  ({@link FabricGameHooks#fireFakePlayerEntityInteract}), at the same point of {@code interactOn}; and its break-speed
 *  event ({@link FabricGameHooks#fireBreakSpeed}) at the end of {@code getDestroySpeed}, for real and fake players;
 *  and the positions NeoForge passes to the combination and muffled step sounds
 *  ({@link FabricVanillaExtensions.StepSoundPositions}). *^/
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

    /^* The primary step position as vanilla computed it, shared with the combination wrap below. Taken from the call
     *  itself rather than by local ordinal: a new {@code BlockPos} local in {@code playStepSound} would shift an ordinal
     *  onto the wrong position silently. A vanished call fails at boot; the share is per invocation and starts null, so
     *  a reorder that read it first would only drop the primary sound's position, never use another one. *^/
    @ModifyExpressionValue(method = "playStepSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getPrimaryStepSoundBlockPos(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos buildcraft$capturePrimaryStepPos(BlockPos primaryPos,
            @Share("primaryStepPos") LocalRef<BlockPos> primaryStepPos) {
        primaryStepPos.set(primaryPos);
        return primaryPos;
    }

    /^* On a carpet or snow layer: the primary block above, the muffled one below ({@code onPos}). *^/
    @WrapOperation(method = "playStepSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;playCombinationStepSounds(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void buildcraft$combinationStepPositions(Player self, BlockState primary, BlockState muffled,
            Operation<Void> original, @Local(argsOnly = true) BlockPos onPos,
            @Share("primaryStepPos") LocalRef<BlockPos> primaryStepPos) {
        withStepPositions(self, primaryStepPos.get(), onPos, () -> original.call(self, primary, muffled));
    }

    /^* In water: the muffled step on {@code onPos}. *^/
    @WrapOperation(method = "playStepSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;playMuffledStepSound(Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void buildcraft$muffledStepPosition(Player self, BlockState muffled, Operation<Void> original,
            @Local(argsOnly = true) BlockPos onPos) {
        withStepPositions(self, null, onPos, () -> original.call(self, muffled));
    }

    @Unique
    private static void withStepPositions(Player self, @Nullable BlockPos primary, BlockPos muffled, Runnable play) {
        FabricVanillaExtensions.StepSoundPositions positions = (FabricVanillaExtensions.StepSoundPositions) self;
        positions.buildcraft$setStepSoundPositions(primary, muffled);
        try {
            play.run();
        } finally {
            positions.buildcraft$setStepSoundPositions(null, null);
        }
    }

    // A spectator's click opens a menu and asks nothing, as on NeoForge.
    // Before 26.1 interactOn has no hit location (Fabric API's own plain-interact path passes no hit result either).
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    //? if >=26.1 {
    private void buildcraft$fakePlayerEntityInteract(Entity entity, InteractionHand hand, Vec3 location,
            CallbackInfoReturnable<InteractionResult> cir) {
    //?} else {
    /^private void buildcraft$fakePlayerEntityInteract(Entity entity, InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        Vec3 location = null;
    ^///?}
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
