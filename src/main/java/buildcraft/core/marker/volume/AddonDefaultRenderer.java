/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.marker.volume;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import buildcraft.api.core.render.ISprite;

public class AddonDefaultRenderer<T extends Addon> implements IFastAddonRenderer<T> {
    // UV coordinates — use 0,0 to 1,1 as a simple white quad
    private float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
    private ISprite sprite;

    public AddonDefaultRenderer() {
        // Default: use full UV range (plain white)
    }

    public AddonDefaultRenderer(ISprite sprite) {
        this.sprite = sprite;
    }

    @Override
    public void renderAddonFast(T addon, Player player, float partialTicks, VertexConsumer builder) {
        AABB bb = addon.getBoundingBox();

        // North face (-Z)
        vertex(builder, bb.minX, bb.maxY, bb.minZ, 204, 204, 204, 255, u0, v0);
        vertex(builder, bb.maxX, bb.maxY, bb.minZ, 204, 204, 204, 255, u0, v1);
        vertex(builder, bb.maxX, bb.minY, bb.minZ, 204, 204, 204, 255, u1, v1);
        vertex(builder, bb.minX, bb.minY, bb.minZ, 204, 204, 204, 255, u1, v0);

        // South face (+Z)
        vertex(builder, bb.minX, bb.minY, bb.maxZ, 204, 204, 204, 255, u0, v0);
        vertex(builder, bb.maxX, bb.minY, bb.maxZ, 204, 204, 204, 255, u0, v1);
        vertex(builder, bb.maxX, bb.maxY, bb.maxZ, 204, 204, 204, 255, u1, v1);
        vertex(builder, bb.minX, bb.maxY, bb.maxZ, 204, 204, 204, 255, u1, v0);

        // Bottom face (-Y)
        vertex(builder, bb.minX, bb.minY, bb.minZ, 127, 127, 127, 255, u0, v0);
        vertex(builder, bb.maxX, bb.minY, bb.minZ, 127, 127, 127, 255, u0, v1);
        vertex(builder, bb.maxX, bb.minY, bb.maxZ, 127, 127, 127, 255, u1, v1);
        vertex(builder, bb.minX, bb.minY, bb.maxZ, 127, 127, 127, 255, u1, v0);

        // Top face (+Y)
        vertex(builder, bb.minX, bb.maxY, bb.maxZ, 255, 255, 255, 255, u0, v0);
        vertex(builder, bb.maxX, bb.maxY, bb.maxZ, 255, 255, 255, 255, u0, v1);
        vertex(builder, bb.maxX, bb.maxY, bb.minZ, 255, 255, 255, 255, u1, v1);
        vertex(builder, bb.minX, bb.maxY, bb.minZ, 255, 255, 255, 255, u1, v0);

        // West face (-X)
        vertex(builder, bb.minX, bb.minY, bb.maxZ, 153, 153, 153, 255, u0, v0);
        vertex(builder, bb.minX, bb.maxY, bb.maxZ, 153, 153, 153, 255, u0, v1);
        vertex(builder, bb.minX, bb.maxY, bb.minZ, 153, 153, 153, 255, u1, v1);
        vertex(builder, bb.minX, bb.minY, bb.minZ, 153, 153, 153, 255, u1, v0);

        // East face (+X)
        vertex(builder, bb.maxX, bb.minY, bb.minZ, 153, 153, 153, 255, u0, v0);
        vertex(builder, bb.maxX, bb.maxY, bb.minZ, 153, 153, 153, 255, u0, v1);
        vertex(builder, bb.maxX, bb.maxY, bb.maxZ, 153, 153, 153, 255, u1, v1);
        vertex(builder, bb.maxX, bb.minY, bb.maxZ, 153, 153, 153, 255, u1, v0);
    }

    private void vertex(VertexConsumer vb, double x, double y, double z,
                         int r, int g, int b, int a, float u, float v) {
        vb.addVertex((float) x, (float) y, (float) z)
            .setColor(r, g, b, a)
            .setUv(u, v)
            .setLight(0xF000F0);
    }
}
