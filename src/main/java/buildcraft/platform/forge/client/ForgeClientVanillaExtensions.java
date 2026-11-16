/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.client.ClientVanillaExtensions;

/^*
 * {@link ClientVanillaExtensions} on MinecraftForge (not NeoForge). Face culling is Forge's position-aware
 * {@code Block.shouldRenderFace}, the same patched member NeoForge has. The picture-in-picture submit goes straight into
 * the GUI render state (Forge adds a public getter for it where NeoForge patches in a submit method), and the scissor peek
 * reaches the private scissor stack through {@code ScissorStackMixin}.
 *^/
public final class ForgeClientVanillaExtensions implements ClientVanillaExtensions {
    @Override
    public boolean shouldRenderFace(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        return Block.shouldRenderFace(level, pos, state, level.getBlockState(pos.relative(face)), face);
    }

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
}
*///?}
