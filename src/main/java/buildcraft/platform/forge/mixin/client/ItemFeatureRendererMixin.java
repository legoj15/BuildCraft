/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.QuadInstance;

import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;

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
 *^/
@Mixin(ItemFeatureRenderer.class)
abstract class ItemFeatureRendererMixin {
    @Shadow
    @Final
    private QuadInstance quadInstance;

    @Inject(method = "prepareMainSubmit", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/QuadInstance;setColor(I)V", shift = At.Shift.AFTER))
    private void buildcraft$vertexColours(CallbackInfo ci, @Local BakedQuad quad) {
        ForgeClientModels.applyVertexColours(quad, quadInstance);
    }
}
*///?}
