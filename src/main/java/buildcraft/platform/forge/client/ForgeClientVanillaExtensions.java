/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge {
/*
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} elif >=1.21.10 {
/^import net.minecraft.client.gui.GuiGraphics;
^///?}
//? if >=1.21.10 {
import net.minecraft.client.gui.navigation.ScreenRectangle;
//?}
//? if >=26.1 {
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
//?} elif >=1.21.10 {
/^import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
^///?} else {
/^import net.minecraft.client.renderer.texture.TextureAtlasSprite;
^///?}
import net.minecraft.core.BlockPos;
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
 * in). On 1.21.11 the picture-in-picture submit and the scissor peek take {@code GuiGraphics}, and the model calls are
 * Forge's model-data overloads given no data (Forge deprecates the data-free ones there).
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

    //? if >=26.1 {
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
    //?} elif >=1.21.10 {
    /^@Override
    public void submitPictureInPicture(GuiGraphics graphics, PictureInPictureRenderState state) {
        graphics.getRenderState().submitPicturesInPictureState(state);
    }

    @Override
    public ScreenRectangle peekScissor(GuiGraphics graphics) {
        // Forge 61's stack class is public; the same duck as on 26.x keeps one path.
        Object stack = graphics.getScissorStack();
        return ((ForgeScissorPeek) stack).buildcraft$peek();
    }

    // Forge deprecates the model-data-free overloads; these hand the model no data (a lone block, no level).
    @Override
    public void renderSingleBlock(BlockState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
        net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffers,
                light, overlay, net.minecraftforge.client.model.data.ModelData.EMPTY, null);
    }

    @Override
    public net.minecraft.client.renderer.texture.TextureAtlasSprite particleIcon(BlockState state) {
        return net.minecraft.client.Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(state)
                .particleIcon(net.minecraftforge.client.model.data.ModelData.EMPTY);
    }

    @Override
    public net.minecraft.client.renderer.chunk.ChunkSectionLayer renderLayer(
            net.minecraft.client.renderer.block.model.BlockModelPart part, BlockState state) {
        // Forge's per-part layer (IForgeBlockModelPart, set from a model JSON's render_type), else the layers the
        // block's model draws on (Forge's model-aware replacement for ItemBlockRenderTypes, with renderModel's seed):
        // translucent when one of them is.
        net.minecraft.client.renderer.chunk.ChunkSectionLayer layer = part.layer();
        if (layer != null) {
            return layer;
        }
        java.util.Collection<net.minecraft.client.renderer.chunk.ChunkSectionLayer> layers =
                net.minecraft.client.Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(state)
                        .getRenderTypes(state, net.minecraft.util.RandomSource.create(42L),
                                net.minecraftforge.client.model.data.ModelData.EMPTY);
        if (layers.contains(net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT)) {
            return net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT;
        }
        return layers.isEmpty() ? net.minecraft.client.renderer.chunk.ChunkSectionLayer.SOLID : layers.iterator().next();
    }
    ^///?} else {
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
