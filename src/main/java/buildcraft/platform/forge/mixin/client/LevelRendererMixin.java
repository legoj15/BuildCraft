/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

import buildcraft.platform.forge.client.ForgeClientHooks;

/^*
 * NeoForge's {@code SubmitCustomGeometryEvent}, for {@code ClientHooks#onLevelRender}: Forge 26.3 has no level-render
 * hook in the submit pass (its {@code AddFramePassEvent} adds whole frame-graph passes, with no node collector).
 *
 * <p><b>Injection point.</b> The call to {@code finalizeGizmoCollection()} in {@code submitFeatures}, after the entities,
 * block entities, breaking progress, particles and block outline have been submitted and before the gizmos: where
 * NeoForge posts its event from the same method. Forge's patch of {@code submitFeatures} only widens the outline test
 * before it, and the call is the method's only one. The listeners get the method's own {@code PoseStack} (its one local
 * of that type) and node collector, as on NeoForge.
 *^/
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "submitFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizeGizmoCollection()V"))
    private void buildcraft$submitCustomGeometry(LevelRenderState levelRenderState,
            SubmitNodeCollector submitNodeCollector, boolean renderOutline, CallbackInfo ci,
            @Local PoseStack poseStack) {
        ForgeClientHooks.submitLevelRender(poseStack, levelRenderState.cameraRenderState.pos, submitNodeCollector);
    }
}
*///?}
