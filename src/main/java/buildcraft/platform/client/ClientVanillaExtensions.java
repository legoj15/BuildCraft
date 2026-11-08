/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

//? if <1.21.10 {
/*import net.minecraft.client.renderer.texture.TextureAtlasSprite;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The client-side twin of {@link buildcraft.platform.VanillaExtensions}: the vanilla client calls NeoForge patches a
 * context-aware or public twin into. Fabric reaches vanilla's own (class-tweaked) members instead.
 */
public interface ClientVanillaExtensions {
    /**
     * Whether the {@code face} of {@code state} at {@code pos} is drawn: not hidden by the neighbouring block (NeoForge:
     * the position-aware {@code Block.shouldRenderFace}; Fabric: vanilla's, which compares the two states only).
     */
    boolean shouldRenderFace(BlockGetter level, BlockPos pos, BlockState state, Direction face);

    //? if <1.21.10 {
    /*/^**
     * The pixel at ({@code x}, {@code y}) of one cell ({@code frameIndex}, the sprite-sheet cell, not the animation
     * order) of {@code sprite}'s original image, as the image stores it (ABGR). 1.21.1 only: its one caller is the
     * fragile shard's {@code ItemColor}, and later lines have nothing to read a sprite pixel for.
     *^/
    int spritePixel(TextureAtlasSprite sprite, int frameIndex, int x, int y);*/
    //?}

    //? if >=26.1 {
    /** Queues a picture-in-picture element on this frame's GUI render state (NeoForge patches a public method in). */
    void submitPictureInPicture(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
            net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState state);

    /** The innermost scissor rectangle {@code graphics} clips to, or null for none. */
    net.minecraft.client.gui.navigation.ScreenRectangle peekScissor(net.minecraft.client.gui.GuiGraphicsExtractor graphics);
    //?} elif >=1.21.10 {
    /*/^* Queues a picture-in-picture element on this frame's GUI render state (NeoForge patches a public method in). *^/
    void submitPictureInPicture(net.minecraft.client.gui.GuiGraphics graphics,
            net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState state);

    /^* The innermost scissor rectangle {@code graphics} clips to, or null for none. *^/
    net.minecraft.client.gui.navigation.ScreenRectangle peekScissor(net.minecraft.client.gui.GuiGraphics graphics);*/
    //?}

    //? if >=1.21.10 && <26.1 {
    /*/^*
     * Draws {@code state}'s block model into {@code buffers} as a lone block (no level, no neighbours): NeoForge's
     * context-aware {@code renderSingleBlock}, given an empty level at the origin; Fabric: vanilla's.
     *^/
    void renderSingleBlock(BlockState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay);

    /^*
     * The particle sprite of {@code state}'s block model, the one vanilla's break and sprint particles use: NeoForge's
     * context-aware {@code getParticleIcon}, given an empty level at the origin; Fabric: vanilla's.
     *^/
    net.minecraft.client.renderer.texture.TextureAtlasSprite particleIcon(BlockState state);

    /^*
     * The chunk layer {@code part} of {@code state}'s model draws on: NeoForge's per-part {@code getRenderType};
     * Fabric: the block's own layer ({@code ItemBlockRenderTypes}), which is all vanilla tracks.
     *^/
    net.minecraft.client.renderer.chunk.ChunkSectionLayer renderLayer(
            net.minecraft.client.renderer.block.model.BlockModelPart part, BlockState state);*/
    //?}
}
