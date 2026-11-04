/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge.client;

//? if neoforge {
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

//? if >=1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;*/
//?}

/**
 * Draws a curio worn in a Curios slot on the wearer's head the way the vanilla head layer draws a non-armour item in
 * the helmet slot: the item's HEAD display-context model, placed with {@link CustomHeadLayer#translateToHead}. Only
 * the player path is mirrored (the Curios head slot is given to players alone); vanilla's 1.21.1 baby and villager
 * offsets are left out.
 * Curios draws nothing for an item without a renderer, so without this the Robot Goggles were worn but invisible.
 *
 * <p>Implements a Curios type, so this class may only be loaded when Curios is installed: callers check
 * that Curios is loaded before touching it ({@code NeoForgeClientRegistrationSinks.headAccessoryRenderer}).
 */
public final class NeoForgeCuriosHeadRenderer implements ICurioRenderer {

    /** Registers this renderer for {@code item}. Client setup only, and only with Curios loaded. */
    public static void register(Item item) {
        //? if >=1.21.10 {
        ICurioRenderer.register(item, NeoForgeCuriosHeadRenderer::new);
        //?} else {
        /*top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(item, NeoForgeCuriosHeadRenderer::new);*/
        //?}
    }

    //? if >=1.21.10 {
    private final ItemStackRenderState itemState = new ItemStackRenderState();

    @Override
    public <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void render(ItemStack stack,
            SlotContext slotContext, PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
            S renderState, RenderLayerParent<S, M> parent, EntityRendererProvider.Context context, float yRotation,
            float xRotation) {
        M model = parent.getModel();
        if (!(model instanceof HeadedModel headed)) {
            return;
        }
        // One state per renderer, re-filled each draw: submit copies the pose and clear() replaces the layer lists
        // rather than mutating them (ItemRenderUtil reuses a state the same way), and rendering is single-threaded.
        ItemStackRenderState item = this.itemState;
        context.getItemModelResolver().updateForLiving(item, stack, ItemDisplayContext.HEAD, slotContext.entity());
        if (item.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        headed.translateToHead(poseStack);
        CustomHeadLayer.translateToHead(poseStack, CustomHeadLayer.Transforms.DEFAULT);
        item.submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, renderState.outlineColor);
        poseStack.popPose();
    }
    //?} else {
    /*@Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
            PoseStack poseStack, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light, float limbSwing,
            float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(parent.getModel() instanceof HeadedModel headed)) {
            return;
        }
        LivingEntity wearer = slotContext.entity();
        poseStack.pushPose();
        headed.getHead().translateAndRotate(poseStack);
        CustomHeadLayer.translateToHead(poseStack, false);
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()
                .renderItem(wearer, stack, ItemDisplayContext.HEAD, false, poseStack, buffers, light);
        poseStack.popPose();
    }*/
    //?}
}
//?}
