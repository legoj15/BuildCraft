/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.client.render;

import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.debug.DebugRenderHelper;

import buildcraft.builders.entity.EntityQuarryRig;
import buildcraft.builders.tile.TileQuarry;

/**
 * Advanced-debug overlay for the Quarry. Draws:
 * <ul>
 *   <li>opaque cuboids over the actual {@link EntityQuarryRig} <em>collision boxes</em> of the moving
 *       boom arms — the real entity bounding boxes the player collides with, so the collision shape can
 *       be compared directly against the rendered rig;</li>
 *   <li>a translucent green volume over every chunk the quarry keeps force-loaded (one box per
 *       {@link TileQuarry#getChunksToLoad()} entry, inset 0.5 from each chunk edge).</li>
 * </ul>
 * Faithful port of the 1.12.2 {@code AdvDebuggerQuarry} for the chunk highlights, plus the rig-collision
 * visualisation.
 */
public final class AdvDebuggerQuarry {
    /** Green, matching the 1.12.2 chunk-highlight colour (0x55_99_FF_99 ARGB). */
    private static final int COLOUR_CHUNK = 0x55_99FF99;
    /** Opaque red for the boom-arm collision boxes, so the real collision shape stands out. */
    private static final int COLOUR_BOOM_ARM = 0xFF_DD3030;

    private AdvDebuggerQuarry() {}

    public static void render(TileQuarry tile, PoseStack poseStack, Vec3 cameraPos) {
        if (!tile.frameBox.isInitialized()) {
            return;
        }

        renderBoomArmCollision(tile, poseStack, cameraPos);
        renderForcedChunks(tile, poseStack, cameraPos);
    }

    /**
     * Opaque cuboids over the real boom-arm collision entities. Searches the frame volume (where the
     * horizontal beams sit) for {@link EntityQuarryRig}s and draws the actual {@code getBoundingBox()} of
     * the horizontal ones. The vertical drill column is skipped — it's the "boom arms" we're visualising,
     * and its box hangs hundreds of blocks down, which would dominate the view; a box is treated as a boom
     * arm when it is wider in X or Z than it is tall.
     */
    private static void renderBoomArmCollision(TileQuarry tile, PoseStack poseStack, Vec3 cameraPos) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        AABB search = new AABB(
            tile.frameBox.min().getX(), tile.frameBox.min().getY(), tile.frameBox.min().getZ(),
            tile.frameBox.max().getX() + 1, tile.frameBox.max().getY() + 1, tile.frameBox.max().getZ() + 1
        ).inflate(2.0);
        for (EntityQuarryRig rig : level.getEntitiesOfClass(EntityQuarryRig.class, search)) {
            AABB box = rig.getBoundingBox();
            boolean horizontal = box.getYsize() < box.getXsize() || box.getYsize() < box.getZsize();
            if (horizontal) {
                DebugRenderHelper.renderSolidBox(poseStack, box, cameraPos, COLOUR_BOOM_ARM);
            }
        }
    }

    private static void renderForcedChunks(TileQuarry tile, PoseStack poseStack, Vec3 cameraPos) {
        Set<ChunkPos> chunks = tile.getChunksToLoad();
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        double minY = tile.frameBox.min().getY();
        double maxY = tile.frameBox.max().getY() + 1;
        for (ChunkPos chunkPos : chunks) {
            // Inset 0.5 from each chunk edge so neighbouring highlighted chunks show a clean gap
            // at their shared border instead of merging into one indistinct volume.
            AABB box = new AABB(
                chunkPos.getMinBlockX() + 0.5, minY, chunkPos.getMinBlockZ() + 0.5,
                chunkPos.getMaxBlockX() + 0.5, maxY, chunkPos.getMaxBlockZ() + 0.5
            );
            DebugRenderHelper.renderTranslucentBox(poseStack, box, cameraPos, COLOUR_CHUNK);
        }
    }
}
