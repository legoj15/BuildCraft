/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge && >=1.21.10 {
/*import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
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

/^*
 * The per-vertex colours BuildCraft bakes into its block quads (NeoForge: the quad's own {@code BakedColors}; Fabric:
 * the pipe model's {@code QuadEmitter}), in the chunk mesh and every other block-model draw that goes through
 * {@code ModelBlockRenderer}: vanilla 26.3's quad has no colour slot, so they ride beside it ({@link ForgeClientModels})
 * and are multiplied into the {@code QuadInstance} colours here.
 *
 * <p><b>Injection point.</b> The call to {@code BlockQuadOutput.put} in {@code putQuadWithTint}, where every block quad
 * leaves the renderer (flat and smooth lighting both end there), after the lighting and the tint are in the instance.
 * Forge does not patch {@code putQuadWithTint}, and the call is its only one.
 *^/
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
*///?}
