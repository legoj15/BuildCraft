/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge && >=1.21.10 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.1 {
import com.llamalad7.mixinextras.sugar.Local;
//?}
import com.mojang.blaze3d.vertex.PoseStack;

//? if >=26.1 {
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?} else {
/^import com.mojang.blaze3d.textures.GpuSampler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
^///?}

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
 *
 * <p><b>1.21.11.</b> No submit pass for custom geometry: BuildCraft draws immediately into the main buffer source after
 * the translucent terrain, where NeoForge posts {@code RenderLevelStageEvent.AfterTranslucentBlocks} and Fabric runs
 * {@code WorldRenderEvents.END_MAIN}. That point is inside the main frame pass's lambda in {@code addMainPass}, whose
 * synthetic name is the compiler's and need not match between the dev and the shipped game, so the mixin targets
 * {@code ChunkSectionsToRender} there instead: the return of {@code renderGroup} for the
 * {@code ChunkSectionLayerGroup.TRANSLUCENT} group, which the main pass calls exactly once a frame, between the
 * translucent and the tripwire groups (no other caller). The pose stack is a fresh one, as the main pass's own is (the
 * camera rotation is on the model-view stack); the camera position is the main camera's, which the level render state
 * was extracted from. Forge does not patch {@code ChunkSectionsToRender}.
 *^/
//? if >=26.1 {
@Mixin(LevelRenderer.class)
//?} else {
/^@Mixin(ChunkSectionsToRender.class)
^///?}
abstract class LevelRendererMixin {
    //? if >=26.1 {
    @Inject(method = "submitFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizeGizmoCollection()V"))
    private void buildcraft$submitCustomGeometry(LevelRenderState levelRenderState,
            SubmitNodeCollector submitNodeCollector, boolean renderOutline, CallbackInfo ci,
            @Local PoseStack poseStack) {
        ForgeClientHooks.submitLevelRender(poseStack, levelRenderState.cameraRenderState.pos, submitNodeCollector);
    }
    //?} else {
    /^@Inject(method = "renderGroup", at = @At("RETURN"))
    private void buildcraft$afterTranslucentBlocks(ChunkSectionLayerGroup group, GpuSampler sampler,
            CallbackInfo ci) {
        if (group == ChunkSectionLayerGroup.TRANSLUCENT) {
            Minecraft minecraft = Minecraft.getInstance();
            ForgeClientHooks.submitLevelRender(new PoseStack(), minecraft.gameRenderer.getMainCamera().position(),
                    minecraft.renderBuffers().bufferSource());
        }
    }
    ^///?}
}
*///?}
