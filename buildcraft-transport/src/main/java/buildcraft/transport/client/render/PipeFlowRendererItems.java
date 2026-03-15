/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.render;

import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.pipe.IPipeFlowRenderer;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.ModelUtil.UvFaceData;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.render.ItemRenderUtil;
import buildcraft.lib.misc.ColourUtil;

import buildcraft.transport.BCTransportSprites;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.TravellingItem;

/** Renders items travelling through item pipes. Renders 3D item models and
 *  colour overlay boxes for dye-tagged items. */
public enum PipeFlowRendererItems implements IPipeFlowRenderer<PipeFlowItems> {
    INSTANCE;

    private static final MutableQuad[] COLOURED_QUADS = new MutableQuad[6];
    private static boolean colouredQuadsInitialized = false;

    /** Lazily initializes the coloured overlay quads the first time they're needed.
     *  This ensures the sprite atlas has been baked before we try to read UV coordinates. */
    private static void ensureColouredQuads() {
        if (colouredQuadsInitialized) return;
        colouredQuadsInitialized = true;

        Vector3f center = new Vector3f();
        Vector3f radius = new Vector3f(0.2f, 0.2f, 0.2f);

        var sprite = BCTransportSprites.COLOUR_ITEM_BOX;
        UvFaceData uvs = new UvFaceData();
        uvs.minU = (float) sprite.getInterpU(0);
        uvs.maxU = (float) sprite.getInterpU(1);
        uvs.minV = (float) sprite.getInterpV(0);
        uvs.maxV = (float) sprite.getInterpV(1);

        for (Direction face : Direction.values()) {
            MutableQuad q = ModelUtil.createFace(face, center, radius, uvs);
            q.setCalculatedDiffuse();
            COLOURED_QUADS[face.ordinal()] = q;
        }
    }

    @Override
    public void render(PipeFlowItems flow, double x, double y, double z, float partialTicks, VertexConsumer bb) {
        ensureColouredQuads();
        Level world = flow.pipe.getHolder().getPipeWorld();
        if (world == null) return;
        long now = world.getGameTime();
        BlockPos pipePos = flow.pipe.getHolder().getPipePos();
        int lightc = LevelRenderer.getLightColor(world, pipePos);

        List<TravellingItem> toRender = flow.getAllItemsForRender();

        for (TravellingItem item : toRender) {
            Vec3 pos = item.getRenderPosition(BlockPos.ZERO, now, partialTicks, flow);

            // Render the actual item stack model
            ItemStack stack = item.clientItemLink.get();
            if (stack == null || stack.isEmpty()) {
                // Fallback: try the server-side stack field (works for items arriving via packet)
                stack = item.getStack();
            }
            if (stack != null && !stack.isEmpty()) {
                ItemRenderUtil.renderItemStack(x + pos.x, y + pos.y, z + pos.z,
                        stack, item.stackSize > 0 ? item.stackSize : stack.getCount(),
                        lightc, item.getRenderDirection(now, partialTicks), bb);
            }

            // Render colour overlay box for dye-tagged items using PoseStack transforms
            if (item.colour != null) {
                PoseStack ps = ItemRenderUtil.getCurrentPoseStack();
                if (ps != null) {
                    int col = ColourUtil.getLightHex(item.colour);
                    int r = (col >> 16) & 0xFF;
                    int g = (col >> 8) & 0xFF;
                    int b_col = col & 0xFF;
                    ps.pushPose();
                    ps.translate(x + pos.x, y + pos.y, z + pos.z);
                    for (MutableQuad q : COLOURED_QUADS) {
                        if (q == null) continue;
                        MutableQuad q2 = new MutableQuad(q);
                        q2.lighti(15, 15);
                        q2.multColouri(r, g, b_col, 255);
                        q2.render(ps.last(), bb);
                    }
                    ps.popPose();
                }
            }
        }

        ItemRenderUtil.endItemBatch();
    }
}
