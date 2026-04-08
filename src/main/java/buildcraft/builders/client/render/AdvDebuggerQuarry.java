/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.client.render;

import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.laser.LaserBoxRenderer;
import buildcraft.lib.misc.data.Box;

import buildcraft.builders.tile.TileQuarry;
import buildcraft.core.client.BuildCraftLaserManager;

public class AdvDebuggerQuarry {
    public final TileQuarry tile;

    public AdvDebuggerQuarry(TileQuarry tile) {
        this.tile = tile;
    }

    public void getDebugInfo(List<String> left, List<String> right) {
        // Display chunk loading info
        Set<ChunkPos> chunks = tile.getChunksToLoad();
        left.add("Chunks to load: " + (chunks != null ? chunks.size() : 0));
    }

    public void renderDebugDynamic(PoseStack poseStack, MultiBufferSource bufferSource,
                                    float partialTicks, Vec3 cameraPos) {
        if (tile.frameBox.isInitialized()) {
            LaserBoxRenderer.renderLaserBoxStatic(poseStack, tile.frameBox,
                BuildCraftLaserManager.MARKER_VOLUME_CONNECTED, true, cameraPos);
        }
    }
}
