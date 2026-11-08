/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge.client;

//? if neoforge {
//? if <1.21.10 {
/*import net.minecraft.client.renderer.texture.TextureAtlasSprite;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.client.ClientVanillaExtensions;

/** {@link ClientVanillaExtensions} on NeoForge: each call is the patched member itself. */
public final class NeoForgeClientVanillaExtensions implements ClientVanillaExtensions {
    @Override
    public boolean shouldRenderFace(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        BlockPos neighbourPos = pos.relative(face);
        //? if >=1.21.10 {
        return Block.shouldRenderFace(level, pos, state, level.getBlockState(neighbourPos), face);
        //?} else {
        /*return Block.shouldRenderFace(state, level, pos, face, neighbourPos);*/
        //?}
    }

    //? if <1.21.10 {
    /*@Override
    public int spritePixel(TextureAtlasSprite sprite, int frameIndex, int x, int y) {
        return sprite.getPixelRGBA(frameIndex, x, y);
    }

    @Override
    public void setTextShadow(net.minecraft.client.gui.components.EditBox box, boolean shadow) {
        box.setTextShadow(shadow);
    }

    @Override
    public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> layerQuads(
            net.minecraft.client.resources.model.BakedModel model, BlockState state, Direction side,
            net.minecraft.util.RandomSource random, boolean translucent) {
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> result = new java.util.ArrayList<>();
        net.neoforged.neoforge.model.data.ModelData data = net.neoforged.neoforge.model.data.ModelData.EMPTY;
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
        return model.getQuads(state, side, random, net.neoforged.neoforge.model.data.ModelData.EMPTY, null);
    }

    @Override
    public TextureAtlasSprite modelParticle(net.minecraft.client.resources.model.BakedModel model) {
        return model.getParticleIcon(net.neoforged.neoforge.model.data.ModelData.EMPTY);
    }

    @Override
    public void renderSingleBlock(BlockState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
        net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffers,
                light, overlay, net.neoforged.neoforge.model.data.ModelData.EMPTY, null);
    }*/
    //?}

    //? if >=26.1 {
    @Override
    public void submitPictureInPicture(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
            net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState state) {
        graphics.submitPictureInPictureRenderState(state);
    }

    @Override
    public net.minecraft.client.gui.navigation.ScreenRectangle peekScissor(
            net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        return graphics.peekScissorStack();
    }
    //?} elif >=1.21.10 {
    /*@Override
    public void submitPictureInPicture(net.minecraft.client.gui.GuiGraphics graphics,
            net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState state) {
        graphics.submitPictureInPictureRenderState(state);
    }

    @Override
    public net.minecraft.client.gui.navigation.ScreenRectangle peekScissor(net.minecraft.client.gui.GuiGraphics graphics) {
        return graphics.peekScissorStack();
    }*/
    //?}

    //? if >=1.21.10 && <26.1 {
    /*@Override
    public void renderSingleBlock(BlockState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
        net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffers,
                light, overlay, net.minecraft.world.level.EmptyBlockAndTintGetter.INSTANCE, BlockPos.ZERO);
    }

    @Override
    public net.minecraft.client.renderer.texture.TextureAtlasSprite particleIcon(BlockState state) {
        return net.minecraft.client.Minecraft.getInstance().getModelManager().getBlockModelShaper()
                .getParticleIcon(state, net.minecraft.world.level.EmptyBlockAndTintGetter.INSTANCE, BlockPos.ZERO);
    }

    @Override
    public net.minecraft.client.renderer.chunk.ChunkSectionLayer renderLayer(
            net.minecraft.client.renderer.block.model.BlockModelPart part, BlockState state) {
        return part.getRenderType(state);
    }*/
    //?}
}
//?}
