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
        return Block.shouldRenderFace(state, level.getBlockState(pos.relative(face)), face);
    }

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
    //?} else {
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

    //? if >=1.21.10 && <26.1 {
    /^@Override
    public void renderSingleBlock(net.minecraft.world.level.block.state.BlockState state,
            com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers,
            int light, int overlay) {
        net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffers,
                light, overlay);
    }

    @Override
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
