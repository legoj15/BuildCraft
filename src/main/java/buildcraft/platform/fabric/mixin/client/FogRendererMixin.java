/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin.client;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;

import buildcraft.platform.fabric.client.FabricFluidFog;

/^*
 * NeoForge's fluid fog hooks ({@code ClientHooks#getFogColor} and {@code #onSetupFog}) on Fabric: once vanilla has
 * picked the fog colour and distances, a BuildCraft fluid at the camera replaces them ({@link FabricFluidFog}).
 *^/
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void buildcraft$fluidFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker,
            float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        FabricFluidFog.apply(camera, level, cir.getReturnValue());
    }
}
*///?}
