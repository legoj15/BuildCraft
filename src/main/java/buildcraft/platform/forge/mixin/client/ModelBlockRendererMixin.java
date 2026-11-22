/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.10 {
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.QuadInstance;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.forge.client.ForgeClientModels;
//?} else {
/^import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
^///?}

/^*
 * The per-vertex colours BuildCraft bakes into its block quads (NeoForge: the quad's own {@code BakedColors}; Fabric:
 * the pipe model's {@code QuadEmitter}), in the chunk mesh and every other block-model draw that goes through
 * {@code ModelBlockRenderer}: vanilla 26.3's quad has no colour slot, so they ride beside it ({@link ForgeClientModels})
 * and are multiplied into the {@code QuadInstance} colours here.
 *
 * <p><b>Injection point.</b> The call to {@code BlockQuadOutput.put} in {@code putQuadWithTint}, where every block quad
 * leaves the renderer (flat and smooth lighting both end there), after the lighting and the tint are in the instance.
 * Forge does not patch {@code putQuadWithTint}, and the call is its only one.
 *
 * <p>On 1.21.1 the quad carries its colours in its own vertex data and vanilla reads their RGB, but not their alpha:
 * {@code putQuadData} hands {@code putBulkData} one alpha (1) for the whole quad, and Forge 52 leaves both as vanilla has
 * them, where NeoForge 1.21.1's {@code putBulkData} multiplies in each vertex's baked alpha. BuildCraft's painted-pipe
 * mask quads (alpha 76, {@code PipeBaseModelGenStandard.generateMaskMutable}) then drew opaque. The redirect scales the
 * call's alpha by the quad's baked alpha, for a quad on one of BuildCraft's own sprites only (another mod's rendering
 * stays Forge's). One alpha per quad: BuildCraft's translucent block quads carry the same alpha on all four vertices.
 *^/
//? if >=1.21.10 {
@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
    @Shadow
    @Final
    private QuadInstance quadInstance;

    @Inject(method = "putQuadWithTint", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void buildcraft$vertexColours(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
            BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
        ForgeClientModels.applyVertexColours(quad, quadInstance);
    }
}
//?} else {
/^@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
    @Redirect(method = "putQuadData", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"))
    private void buildcraft$bakedAlpha(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, float[] brightness,
            float red, float green, float blue, float alpha, int[] lights, int overlay, boolean readExistingColor) {
        float scaled = alpha;
        int[] vertices = quad.getVertices();
        // The BLOCK format's colour is the fourth int of each 8-int vertex, ABGR: alpha in the top byte.
        int baked = vertices.length >= 4 ? (vertices[3] >>> 24) : 0xFF;
        if (readExistingColor && baked != 0xFF
                && "buildcraftunofficial".equals(quad.getSprite().contents().name().getNamespace())) {
            scaled = alpha * baked / 255.0F;
        }
        consumer.putBulkData(pose, quad, brightness, red, green, blue, scaled, lights, overlay, readExistingColor);
    }
}
^///?}
*///?}
