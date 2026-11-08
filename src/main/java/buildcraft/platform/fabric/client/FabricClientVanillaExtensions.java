/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.client.ClientVanillaExtensions;

/^*
 * {@link ClientVanillaExtensions} on Fabric: vanilla's own members, opened by the class tweaker where NeoForge
 * patches a public twin in. Gap: face culling compares the two block states only, as vanilla does (no per-position
 * occlusion hook).
 *^/
public final class FabricClientVanillaExtensions implements ClientVanillaExtensions {
    @Override
    public boolean shouldRenderFace(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        //? if >=1.21.10 {
        return Block.shouldRenderFace(state, level.getBlockState(pos.relative(face)), face);
        //?} else {
        /^// 1.21.1's vanilla test still reads the neighbour from the level itself.
        return Block.shouldRenderFace(state, level, pos, face, pos.relative(face));
        ^///?}
    }

    //? if <1.21.10 {
    /^@Override
    public int spritePixel(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int frameIndex, int x,
            int y) {
        // NeoForge's TextureAtlasSprite.getPixelRGBA, from the class-tweaked original image and frame grid.
        net.minecraft.client.renderer.texture.SpriteContents contents = sprite.contents();
        if (contents.animatedTexture != null) {
            x += contents.animatedTexture.getFrameX(frameIndex) * contents.width();
            y += contents.animatedTexture.getFrameY(frameIndex) * contents.height();
        }
        return contents.originalImage.getPixelRGBA(x, y);
    }

    @Override
    public void setTextShadow(net.minecraft.client.gui.components.EditBox box, boolean shadow) {
        ((buildcraft.platform.fabric.client.FabricEditBoxShadow) box).buildcraft$setTextShadow(shadow);
    }

    @Override
    public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> layerQuads(
            net.minecraft.client.resources.model.BakedModel model, BlockState state, Direction side,
            net.minecraft.util.RandomSource random, boolean translucent) {
        // Vanilla tracks one layer per block: all of the model's quads draw on it.
        boolean onTranslucent = net.minecraft.client.renderer.ItemBlockRenderTypes.getChunkRenderType(state)
                == net.minecraft.client.renderer.rendertype.RenderTypes.translucent();
        return onTranslucent == translucent ? model.getQuads(state, side, random) : java.util.List.of();
    }

    @Override
    public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> modelQuads(
            net.minecraft.client.resources.model.BakedModel model, BlockState state, Direction side,
            net.minecraft.util.RandomSource random) {
        return model.getQuads(state, side, random);
    }

    @Override
    public net.minecraft.client.renderer.texture.TextureAtlasSprite modelParticle(
            net.minecraft.client.resources.model.BakedModel model) {
        return model.getParticleIcon();
    }
    ^///?}

    //? if >=26.1 {
    @Override
    public void submitPictureInPicture(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
            net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState state) {
        graphics.guiRenderState.addPicturesInPictureState(state);
    }

    @Override
    public ScreenRectangle peekScissor(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        return graphics.scissorStack.peek();
    }
    //?} elif >=1.21.10 {
    /^@Override
    public void submitPictureInPicture(net.minecraft.client.gui.GuiGraphics graphics,
            net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState state) {
        graphics.guiRenderState.submitPicturesInPictureState(state);
    }

    @Override
    public ScreenRectangle peekScissor(net.minecraft.client.gui.GuiGraphics graphics) {
        return graphics.scissorStack.peek();
    }
    ^///?}

    //? if <26.1 {
    /^@Override
    public void renderSingleBlock(net.minecraft.world.level.block.state.BlockState state,
            com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers,
            int light, int overlay) {
        net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffers,
                light, overlay);
    }
    ^///?}

    //? if >=1.21.10 && <26.1 {
    /^    @Override
    public net.minecraft.client.renderer.texture.TextureAtlasSprite particleIcon(
            net.minecraft.world.level.block.state.BlockState state) {
        return net.minecraft.client.Minecraft.getInstance().getModelManager().getBlockModelShaper()
                .getParticleIcon(state);
    }

    @Override
    public net.minecraft.client.renderer.chunk.ChunkSectionLayer renderLayer(
            net.minecraft.client.renderer.block.model.BlockModelPart part,
            net.minecraft.world.level.block.state.BlockState state) {
        // Vanilla tracks one layer per block; Fabric API's per-quad render type lives on its mesh, not the part.
        return net.minecraft.client.renderer.ItemBlockRenderTypes.getChunkRenderType(state);
    }
    ^///?}
}
*///?}
