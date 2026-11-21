/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge && >=1.21.10 {
/*//? if >=26.1 {
import org.spongepowered.asm.mixin.Final;
//?}
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.1 {
import org.spongepowered.asm.mixin.Shadow;
//?}
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.1 {
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.QuadInstance;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
//?} else {
/^import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
^///?}
import net.minecraft.client.renderer.block.ModelBlockRenderer;
//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
//?} else {
/^import net.minecraft.client.renderer.block.model.BakedQuad;
^///?}

import buildcraft.platform.forge.client.ForgeClientModels;

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
 * <p><b>1.21.11.</b> No {@code QuadInstance}: a block quad leaves the renderer through {@code VertexConsumer.putBulkData},
 * so both of its calls are wrapped and a coloured quad is written by {@code ForgeClientModels.putColouredQuad} instead
 * (vanilla's loop with the colours multiplied in); any other quad goes to vanilla's call. {@code putQuadData} (the
 * nine-argument call, its only one) is where the chunk mesh and every lit level draw end, flat and smooth lighting both;
 * the private static {@code renderQuadList} (the eight-argument call, its only one) is the model draw with no level
 * ({@code renderModel}: block-entity renderers, {@code renderSingleBlock}). Forge patches neither method (its
 * {@code renderModel} patch only adds the model data and layer that reach {@code collectParts}).
 *^/
@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
    //? if >=26.1 {
    @Shadow
    @Final
    private QuadInstance quadInstance;

    @Inject(method = "putQuadWithTint", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void buildcraft$vertexColours(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
            BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
        ForgeClientModels.applyVertexColours(quad, quadInstance);
    }
    //?} else {
    /^@WrapOperation(method = "putQuadData", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[II)V"))
    private void buildcraft$vertexColours(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
            float[] brightness, float red, float green, float blue, float alpha, int[] lightmap, int overlay,
            Operation<Void> original) {
        if (!ForgeClientModels.putColouredQuad(consumer, pose, quad, brightness, red, green, blue, alpha, lightmap,
                overlay)) {
            original.call(consumer, pose, quad, brightness, red, green, blue, alpha, lightmap, overlay);
        }
    }

    @WrapOperation(method = "renderQuadList", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFII)V"))
    private static void buildcraft$vertexColoursNoLevel(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
            float red, float green, float blue, float alpha, int light, int overlay, Operation<Void> original) {
        if (!ForgeClientModels.putColouredQuad(consumer, pose, quad, red, green, blue, alpha, light, overlay)) {
            original.call(consumer, pose, quad, red, green, blue, alpha, light, overlay);
        }
    }
    ^///?}
}
*///?}
