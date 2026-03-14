/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.render;

import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.pipe.IPipeFlowRenderer;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.ModelUtil.UvFaceData;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.ColourUtil;

import buildcraft.transport.BCTransportSprites;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.TravellingItem;

/** Renders items travelling through item pipes. Currently only renders the colour
 *  overlay box — full item rendering requires ItemRenderUtil which isn't yet ported. */
public enum PipeFlowRendererItems implements IPipeFlowRenderer<PipeFlowItems> {
    INSTANCE;

    private static final MutableQuad[] COLOURED_QUADS = new MutableQuad[6];

    public static void onModelBake() {
        Vector3f center = new Vector3f();
        Vector3f radius = new Vector3f(0.2f, 0.2f, 0.2f);

        // Use the COLOUR_ITEM_BOX sprite ISprite for UV lookup
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
        Level world = flow.pipe.getHolder().getPipeWorld();
        if (world == null) return;
        long now = world.getGameTime();

        List<TravellingItem> toRender = flow.getAllItemsForRender();

        for (TravellingItem item : toRender) {
            Vec3 pos = item.getRenderPosition(BlockPos.ZERO, now, partialTicks, flow);

            // TODO: Render the actual item stack when ItemRenderUtil is ported
            // ItemStack stack = item.clientItemLink.get();
            // if (stack != null && !stack.isEmpty()) { ... }

            if (item.colour != null) {
                int col = ColourUtil.getLightHex(item.colour);
                int r = (col >> 16) & 0xFF;
                int g = (col >> 8) & 0xFF;
                int b = col & 0xFF;
                for (MutableQuad q : COLOURED_QUADS) {
                    if (q == null) continue;
                    MutableQuad q2 = new MutableQuad(q);
                    q2.lighti(15, 15);
                    q2.multColouri(r, g, b, 255);
                    // Translate to item position
                    q2.vertex_0.translatef((float)(x + pos.x), (float)(y + pos.y), (float)(z + pos.z));
                    q2.vertex_1.translatef((float)(x + pos.x), (float)(y + pos.y), (float)(z + pos.z));
                    q2.vertex_2.translatef((float)(x + pos.x), (float)(y + pos.y), (float)(z + pos.z));
                    q2.vertex_3.translatef((float)(x + pos.x), (float)(y + pos.y), (float)(z + pos.z));
                    q2.render(bb);
                }
            }
        }
    }
}
