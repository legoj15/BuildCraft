/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
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
 * {@link ClientVanillaExtensions} on Fabric: vanilla's own members, opened by the class tweaker where NeoForge
 * patches a public twin in. Gap: face culling compares the two block states only, as vanilla does (no per-position
 * occlusion hook).
 *^/
public final class FabricClientVanillaExtensions implements ClientVanillaExtensions {
    @Override
    public boolean shouldRenderFace(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        return Block.shouldRenderFace(state, level.getBlockState(pos.relative(face)), face);
    }

    @Override
    public void submitPictureInPicture(GuiGraphicsExtractor graphics, PictureInPictureRenderState state) {
        graphics.guiRenderState.addPicturesInPictureState(state);
    }

    @Override
    public ScreenRectangle peekScissor(GuiGraphicsExtractor graphics) {
        return graphics.scissorStack.peek();
    }
}
*///?}
