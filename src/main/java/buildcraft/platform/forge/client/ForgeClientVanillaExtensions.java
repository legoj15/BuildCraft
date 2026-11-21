/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge {
/*import net.minecraft.core.BlockPos;
//? if >=1.21.10 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
//?} else {
/^import net.minecraft.client.renderer.texture.TextureAtlasSprite;
^///?}
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.client.ClientVanillaExtensions;

/^*
 * {@link ClientVanillaExtensions} on MinecraftForge (not NeoForge). Face culling is Forge's position-aware
 * {@code Block.shouldRenderFace}, the same patched member NeoForge has (its 1.21.1 shape takes the neighbour's position).
 * On 26.3 the picture-in-picture submit goes straight into the GUI render state (Forge adds a public getter for it where
 * NeoForge patches in a submit method), and the scissor peek reaches the private scissor stack through
 * {@code ScissorStackMixin}. On 1.21.1 the model calls are Forge's model-data overloads given no data, as on NeoForge
 * 1.21.1, and the edit box's text shadow goes through {@code EditBoxMixin} (Forge 52 has no setter; NeoForge patches one
 * in).
 *^/
public final class ForgeClientVanillaExtensions implements ClientVanillaExtensions {
    @Override
    public boolean shouldRenderFace(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        //? if >=1.21.10 {
        return Block.shouldRenderFace(level, pos, state, level.getBlockState(pos.relative(face)), face);
        //?} else {
        /^return Block.shouldRenderFace(state, level, pos, face, pos.relative(face));
        ^///?}
    }

    //? if >=1.21.10 {
    @Override
    public void submitPictureInPicture(GuiGraphicsExtractor graphics, PictureInPictureRenderState state) {
        graphics.getRenderState().addPicturesInPictureState(state);
    }

    @Override
    public ScreenRectangle peekScissor(GuiGraphicsExtractor graphics) {
        // The stack's class is private: reach it as an Object and go through the duck interface the mixin adds.
        Object stack = graphics.getScissorStack();
        return ((ForgeScissorPeek) stack).buildcraft$peek();
    }
    //?} else {
    /^@Override
    public int spritePixel(TextureAtlasSprite sprite, int frameIndex, int x, int y) {
        return sprite.getPixelRGBA(frameIndex, x, y);
    }

    @Override
    public void setTextShadow(net.minecraft.client.gui.components.EditBox box, boolean shadow) {
        ((ForgeEditBoxShadow) box).buildcraft$setTextShadow(shadow);
    }

    @Override
    public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> layerQuads(
            net.minecraft.client.resources.model.BakedModel model, BlockState state, Direction side,
            net.minecraft.util.RandomSource random, boolean translucent) {
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> result = new java.util.ArrayList<>();
        net.minecraftforge.client.model.data.ModelData data = net.minecraftforge.client.model.data.ModelData.EMPTY;
        for (net.minecraft.client.renderer.rendertype.RenderType renderType : model.getRenderTypes(state, random, data)) {
            if ((renderType == net.minecraft.client.renderer.rendertype.RenderTypes.translucent()) == translucent) {
                result.addAll(model.getQuads(state, side, random, data, renderType));
            }
        }
        return result;
    }

    @Override
    public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> modelQuads(
            net.minecraft.client.resources.model.BakedModel model, BlockState state, Direction side,
            net.minecraft.util.RandomSource random) {
        return model.getQuads(state, side, random, net.minecraftforge.client.model.data.ModelData.EMPTY, null);
    }

    @Override
    public TextureAtlasSprite modelParticle(net.minecraft.client.resources.model.BakedModel model) {
        return model.getParticleIcon(net.minecraftforge.client.model.data.ModelData.EMPTY);
    }

    @Override
    public void renderSingleBlock(BlockState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
        net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffers,
                light, overlay, net.minecraftforge.client.model.data.ModelData.EMPTY, null);
    }
    ^///?}
}
*///?}
