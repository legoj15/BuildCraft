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

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.QuadInstance;

import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/^import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
^///?}

import buildcraft.platform.forge.client.ForgeClientModels;

/^*
 * The per-vertex colours of BuildCraft's item quads ({@code ClientModels#setItemQuads}; NeoForge: the quad's own
 * {@code BakedColors}; Fabric: a {@code QuadEmitter} on the item layer), multiplied into the {@code QuadInstance}
 * after the layer tint is set ({@link ForgeClientModels}).
 *
 * <p><b>Injection point.</b> In {@code prepareMainSubmit}'s loop over the quads, just after the instance colour is set to
 * the layer tint ({@code QuadInstance.setColor(int)}, the method's only call of it) and before the quad is put; the
 * quad is the method's one {@code BakedQuad} local. Forge does not patch {@code ItemFeatureRenderer}. The outline pass
 * is left alone: it draws one flat colour.
 *
 * <p><b>1.21.11.</b> No {@code QuadInstance}, and {@code ItemFeatureRenderer} hands each submit to
 * {@code ItemRenderer.renderItem}, so this mixin targets {@code ItemRenderer} there: the {@code putBulkData} call in
 * the static {@code renderQuadList} (its only one, after the layer tint is worked out), where a coloured quad is written
 * by {@code ForgeClientModels.putColouredQuad} instead and any other goes to vanilla's call. The outline pass draws
 * through the same method into a flat-colour buffer, which ignores the vertex colour. Forge does not patch it.
 *^/
//? if >=26.1 {
@Mixin(ItemFeatureRenderer.class)
//?} else {
/^@Mixin(ItemRenderer.class)
^///?}
abstract class ItemFeatureRendererMixin {
    //? if >=26.1 {
    @Shadow
    @Final
    private QuadInstance quadInstance;

    @Inject(method = "prepareMainSubmit", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/QuadInstance;setColor(I)V", shift = At.Shift.AFTER))
    private void buildcraft$vertexColours(CallbackInfo ci, @Local BakedQuad quad) {
        ForgeClientModels.applyVertexColours(quad, quadInstance);
    }
    //?} else {
    /^@WrapOperation(method = "renderQuadList", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFII)V"))
    private static void buildcraft$vertexColours(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
            float red, float green, float blue, float alpha, int light, int overlay, Operation<Void> original) {
        if (!ForgeClientModels.putColouredQuad(consumer, pose, quad, red, green, blue, alpha, light, overlay)) {
            original.call(consumer, pose, quad, red, green, blue, alpha, light, overlay);
        }
    }
    ^///?}
}
*///?}
