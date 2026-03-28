/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client.model.plug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import buildcraft.api.transport.pluggable.IPluggableStaticBaker;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.SpriteUtil;

import buildcraft.silicon.BCSilicon;
import buildcraft.silicon.client.model.key.KeyPlugLens;

public enum PlugBakerLens implements IPluggableStaticBaker<KeyPlugLens> {
    INSTANCE;

    private static final Map<KeyPlugLens, List<BakedQuad>> cached = new HashMap<>();

    public static void onModelBake() {
        cached.clear();
    }

    private static void addBox(List<MutableQuad> quads, TextureAtlasSprite sprite,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        
        Vector3f center = new Vector3f((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f);
        Vector3f radius = new Vector3f((x1 - x0) / 2f, (y1 - y0) / 2f, (z1 - z0) / 2f);
        AABB box = new AABB(x0, y0, z0, x1, y1, z1);

        for (Direction face : Direction.values()) {
            ModelUtil.UvFaceData uvs = new ModelUtil.UvFaceData();
            ModelUtil.mapBoxToUvs(box, face, uvs);
            // The 1.12.2 model maps textures manually, but standard box mapping
            // is close enough for a single color/texture frame.
            MutableQuad q = ModelUtil.createFace(face, center, radius, uvs);
            q.setSprite(sprite);
            q.vertex_0.texFromSprite(sprite);
            q.vertex_1.texFromSprite(sprite);
            q.vertex_2.texFromSprite(sprite);
            q.vertex_3.texFromSprite(sprite);
            quads.add(q);
        }
    }

    private static void addTranslucentBox(List<MutableQuad> quads, TextureAtlasSprite sprite,
            float x0, float y0, float z0, float x1, float y1, float z1) {

        Vector3f center = new Vector3f((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f);
        Vector3f radius = new Vector3f((x1 - x0) / 2f, (y1 - y0) / 2f, (z1 - z0) / 2f);
        AABB box = new AABB(x0, y0, z0, x1, y1, z1);

        // Translucent part only renders East and West faces!
        Direction[] faces = {Direction.EAST, Direction.WEST};
        for (Direction face : faces) {
            ModelUtil.UvFaceData uvs = new ModelUtil.UvFaceData();
            ModelUtil.mapBoxToUvs(box, face, uvs);
            // Custom UV for the glass: { "uv": [ 6, 6, 10, 10 ] }
            // To emulate this, we just map 6/16 to 10/16
            uvs.minU = 6 / 16f;
            uvs.maxU = 10 / 16f;
            uvs.minV = 6 / 16f;
            uvs.maxV = 10 / 16f;

            MutableQuad q = ModelUtil.createFace(face, center, radius, uvs);
            q.setSprite(sprite);
            q.vertex_0.texFromSprite(sprite);
            q.vertex_1.texFromSprite(sprite);
            q.vertex_2.texFromSprite(sprite);
            q.vertex_3.texFromSprite(sprite);
            quads.add(q);
        }
    }

    @Override
    public List<BakedQuad> bake(KeyPlugLens key) {
        if (!cached.containsKey(key)) {
            List<MutableQuad> rawQuads = new ArrayList<>();
            String layerName = key.layer != null ? key.layer.toString().toLowerCase() : "";

            if (layerName.contains("cutout")) {
                TextureAtlasSprite sprite = SpriteUtil.getSprite(Identifier.fromNamespaceAndPath(BCSilicon.MODID,
                    key.isFilter ? "plugs/filter" : "plugs/lens"));
                if (sprite == null) sprite = SpriteUtil.missingSprite();

                // West facing shape base 0-2 (2/16ths deep)
                addBox(rawQuads, sprite, 0 / 16f, 3 / 16f, 3 / 16f, 2 / 16f, 4.01f / 16f, 13 / 16f);
                addBox(rawQuads, sprite, 0 / 16f, 11.99f / 16f, 3 / 16f, 2 / 16f, 13 / 16f, 13 / 16f);
                addBox(rawQuads, sprite, 0 / 16f, 4.01f / 16f, 3 / 16f, 2 / 16f, 11.99f / 16f, 4.01f / 16f);
                addBox(rawQuads, sprite, 0 / 16f, 4.01f / 16f, 11.99f / 16f, 2 / 16f, 11.99f / 16f, 13 / 16f);
            } else if (layerName.contains("translucent")) {
                TextureAtlasSprite sprite;
                if (!key.isFilter || key.colour != null) {
                    sprite = SpriteUtil.getSprite(Identifier.fromNamespaceAndPath(BCSilicon.MODID, "plugs/overlay_lens"));
                } else {
                    sprite = SpriteUtil.getSprite(Identifier.parse("minecraft:block/water_flow"));
                }
                if (sprite == null) sprite = SpriteUtil.missingSprite();
                
                addTranslucentBox(rawQuads, sprite, 0.5f / 16f, 4 / 16f, 4 / 16f, 1.5f / 16f, 12 / 16f, 12 / 16f);
            }

            List<BakedQuad> baked = new ArrayList<>();
            int tint = -1;
            if (layerName.contains("translucent") && key.colour != null) {
                // Determine ARGB for tinting
                tint = key.colour.getTextureDiffuseColor() | 0xFF000000;
            }

            for (MutableQuad q : rawQuads) {
                if (tint != -1) {
                    q.setTint(tint); // Colorize
                }
                
                // Rotate from WEST facing to the desired side
                // rotate expects to rotate around center [0.5, 0.5, 0.5]
                q.rotate(Direction.WEST, key.side, 0.5f, 0.5f, 0.5f);
                
                q.multShade();
                baked.add(q.toBakedBlock());
            }

            cached.put(key, baked);
        }
        return cached.get(key);
    }
}
