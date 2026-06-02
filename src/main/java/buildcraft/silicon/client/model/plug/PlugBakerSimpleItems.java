package buildcraft.silicon.client.model.plug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import buildcraft.api.transport.pluggable.IPluggableStaticBaker;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.SpriteUtil;

import buildcraft.silicon.BCSilicon;
import buildcraft.silicon.client.model.key.KeyPlugSimple;

public enum PlugBakerSimpleItems implements IPluggableStaticBaker<KeyPlugSimple> {
    INSTANCE;

    private static final Map<KeyPlugSimple, List<BakedQuad>> cached = new HashMap<>();

    public static void onModelBake() {
        cached.clear();
    }

    private static void addBox(List<MutableQuad> quads, TextureAtlasSprite sprite,
            float x0, float y0, float z0, float x1, float y1, float z1, ModelUtil.UvFaceData[] faceUvs) {
        
        Vector3f center = new Vector3f((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f);
        Vector3f radius = new Vector3f((x1 - x0) / 2f, (y1 - y0) / 2f, (z1 - z0) / 2f);
        AABB box = new AABB(x0, y0, z0, x1, y1, z1);

        for (Direction face : Direction.values()) {
            ModelUtil.UvFaceData uvs = faceUvs != null ? faceUvs[face.ordinal()] : new ModelUtil.UvFaceData();
            if (faceUvs == null) {
                ModelUtil.mapBoxToUvs(box, face, uvs);
            }
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
    public List<BakedQuad> bake(KeyPlugSimple key) {
        if (!cached.containsKey(key)) {
            List<MutableQuad> rawQuads = new ArrayList<>();
            String layerName = key.layer != null ? key.layer.toString().toLowerCase() : "";

            if (layerName.contains("cutout")) {
                String texturePath = "block/plugs/" + key.identifier;
                if (key.identifier.equals("pulsar")) {
                    texturePath = "block/plugs/pulsar_static";
                }
                TextureAtlasSprite sprite = SpriteUtil.getSprite(Identifier.fromNamespaceAndPath(BCSilicon.MODID, texturePath));
                if (sprite == null) sprite = SpriteUtil.missingSprite();

                // Pluggable UVs from 1.12.2 json files
                ModelUtil.UvFaceData[] explicitUvs;
                if (key.identifier.equals("pulsar")) {
                    explicitUvs = new ModelUtil.UvFaceData[] {
                        ModelUtil.UvFaceData.from16(2, 5, 5, 11), // DOWN
                        ModelUtil.UvFaceData.from16(2, 5, 5, 11), // UP
                        ModelUtil.UvFaceData.from16(2, 5, 5, 11), // NORTH
                        ModelUtil.UvFaceData.from16(2, 5, 5, 11), // SOUTH
                        ModelUtil.UvFaceData.from16(5, 5, 11, 11), // WEST
                        ModelUtil.UvFaceData.from16(5, 5, 11, 11)  // EAST
                    };
                } else { // Light sensor / Timer 
                    explicitUvs = new ModelUtil.UvFaceData[] {
                        ModelUtil.UvFaceData.from16(3, 5, 5, 11), // DOWN
                        ModelUtil.UvFaceData.from16(3, 5, 5, 11), // UP
                        ModelUtil.UvFaceData.from16(3, 5, 5, 11), // NORTH
                        ModelUtil.UvFaceData.from16(3, 5, 5, 11), // SOUTH
                        ModelUtil.UvFaceData.from16(5, 5, 11, 11), // WEST
                        ModelUtil.UvFaceData.from16(5, 5, 11, 11)  // EAST
                    };
                }

                // West facing shape base 0-2 (2/16ths deep) - coordinates come from PluggableTimer.BOXES WEST
                // new AABB(ll, min, min, lu, max, max) -> (2/16, 5/16, 5/16, 4/16, 11/16, 11/16)
                addBox(rawQuads, sprite, 2 / 16f, 5 / 16f, 5 / 16f, 4 / 16f, 11 / 16f, 11 / 16f, explicitUvs);
            }

            List<BakedQuad> baked = new ArrayList<>();
            for (MutableQuad q : rawQuads) {
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
