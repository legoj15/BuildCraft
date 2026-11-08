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
//? if >=1.21.10 {
import net.minecraft.client.renderer.fog.FogRenderer;
//?} else {
/^import net.minecraft.client.renderer.FogRenderer;
^///?}

import buildcraft.platform.fabric.client.FabricFluidFog;
//? if >=26.1 {
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.fog.FogData;
//?} elif >=1.21.10 {
/^import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
^///?} else {
/^import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.Minecraft;
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
    //?} elif >=1.21.10 {
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
    ^///?} else {
    /^// Before 1.21.10 FogRenderer is static. setupColor's last call clears the screen to its colour fields, which the
    // level's fog colour later reads (levelFogColor): NeoForge's getFogColor replaces them just before it, so the
    // colour is set there. setupFog ends by setting the shader fog distances, where NeoForge's onFogRender runs.
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V", ordinal = 1))
    private static void buildcraft$fluidFogColour(float red, float green, float blue, float alpha,
            Operation<Void> original, @Local(argsOnly = true) Camera camera,
            @Local(argsOnly = true) ClientLevel level) {
        float[] colour = FabricFluidFog.colourAt(camera, level);
        if (colour != null) {
            fogRed = colour[0];
            fogGreen = colour[1];
            fogBlue = colour[2];
            original.call(fogRed, fogGreen, fogBlue, alpha);
        } else {
            original.call(red, green, blue, alpha);
        }
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void buildcraft$fluidFogDistance(Camera camera, FogRenderer.FogMode mode, float renderDistance,
            boolean thickFog, float partialTick, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        float distance = level == null ? -1.0F : FabricFluidFog.distanceAt(camera, level);
        if (distance >= 0.0F) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(0.0F);
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(distance);
        }
    }
    ^///?}
}
*///?}
