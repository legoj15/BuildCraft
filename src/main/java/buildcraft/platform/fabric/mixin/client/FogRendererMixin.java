/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin.client;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;

import buildcraft.platform.fabric.client.FabricFluidFog;
//? if >=26.1 {
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.fog.FogData;
//?} else {
/^import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
^///?}

/^*
 * NeoForge's fluid fog hooks ({@code ClientHooks#getFogColor} and {@code #onSetupFog}) on Fabric: once vanilla has
 * picked the fog colour and distances, a BuildCraft fluid at the camera replaces them ({@link FabricFluidFog}).
 *^/
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {

    //? if >=26.1 {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void buildcraft$fluidFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker,
            float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        FabricFluidFog.apply(camera, level, cir.getReturnValue());
    }
    //?} else {
    /^// Before 26.1 setupFog writes the colour and distances straight into the fog buffer (FogData has no colour), so
    // the replacement happens on that write; the colour vector is the one setupFog also returns.
    @WrapOperation(method = "setupFog", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    private void buildcraft$fluidFog(FogRenderer self, java.nio.ByteBuffer buffer, int offset,
            org.joml.Vector4f colour, float environmentalStart, float environmentalEnd, float renderDistanceStart,
            float renderDistanceEnd, float skyEnd, float cloudEnd, Operation<Void> original,
            @Local(argsOnly = true) Camera camera, @Local(argsOnly = true) ClientLevel level) {
        float distance = FabricFluidFog.apply(camera, level, colour);
        if (distance >= 0.0F) {
            original.call(self, buffer, offset, colour, 0.0F, distance, 0.0F, distance, skyEnd, cloudEnd);
        } else {
            original.call(self, buffer, offset, colour, environmentalStart, environmentalEnd, renderDistanceStart,
                    renderDistanceEnd, skyEnd, cloudEnd);
        }
    }
    ^///?}
}
*///?}
