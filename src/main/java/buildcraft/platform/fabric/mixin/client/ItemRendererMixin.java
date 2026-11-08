/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin.client;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.10 {
/^import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.platform.client.ClassicItemModel;
import buildcraft.platform.client.ClassicItemRenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
^///?}

/^*
 * NeoForge's two item-model hooks on 1.21.1's {@code ItemRenderer.render}: a {@link ClassicItemModel} applies its own
 * transform and picks the model to draw in place of vanilla's item transforms ({@code applyTransform}), and a
 * {@link ClassicItemRenderType} names its sheet in place of the one vanilla picks for the stack
 * ({@code getRenderTypes}; BuildCraft's name one, so its first is the one NeoForge would draw). Every other model is
 * drawn as vanilla draws it. This class carries nothing on the later lines, whose items draw through
 * {@code ItemModel}, so it is an empty mixin on {@code Minecraft} there (26.x has no {@code ItemRenderer}).
 *^/
//? if <1.21.10 {
/^@Mixin(ItemRenderer.class)
^///?} else {
@Mixin(net.minecraft.client.Minecraft.class)
//?}
abstract class ItemRendererMixin {

    //? if <1.21.10 {
    /^// Inside the item's pose push, where NeoForge's handleCameraTransforms replaces the transform; vanilla then applies
    // the returned model's own transforms (empty on BuildCraft's per-context models; the chosen view's on a
    // separate-transforms model, which applies none itself).
    @ModifyVariable(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER), argsOnly = true)
    private BakedModel buildcraft$applyTransform(BakedModel model,
            @Local(argsOnly = true) ItemDisplayContext context, @Local(argsOnly = true) boolean leftHand,
            @Local(argsOnly = true) PoseStack pose) {
        return model instanceof ClassicItemModel classic ? classic.applyTransform(context, pose, leftHand) : model;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemBlockRenderTypes;getRenderType(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType buildcraft$renderType(ItemStack stack, boolean fabulous, Operation<RenderType> original,
            @Local(argsOnly = true) BakedModel model) {
        return model instanceof ClassicItemRenderType classic ? classic.getRenderTypes(stack, fabulous).get(0)
                : original.call(stack, fabulous);
    }
    ^///?}
}
*///?}
