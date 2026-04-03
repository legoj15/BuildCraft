package buildcraft.silicon.client.model.plug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

import buildcraft.api.transport.pluggable.IPluggableStaticBaker;
import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.silicon.client.model.key.KeyPlugGate;

public class PlugGateBaker implements IPluggableStaticBaker<KeyPlugGate> {
    public static final PlugGateBaker INSTANCE = new PlugGateBaker();

    private static final Map<KeyPlugGate, List<BakedQuad>> cached = new java.util.concurrent.ConcurrentHashMap<>();

    public static void onModelBake() {
        cached.clear();
    }

    private TextureAtlasSprite getSprite(String path) {
        net.minecraft.client.renderer.texture.TextureAtlas atlas = (net.minecraft.client.renderer.texture.TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        return atlas.getSprite(Identifier.parse(path));
    }

    private String getMaterialSpritePath(buildcraft.silicon.gate.EnumGateMaterial material) {
        switch (material) {
            case CLAY_BRICK: return "minecraft:block/bricks";
            case IRON: return "minecraft:block/iron_block";
            case NETHER_BRICK: return "minecraft:block/nether_bricks";
            case GOLD: return "minecraft:block/gold_block";
            default: return "minecraft:block/bricks";
        }
    }

    private String getModifierSpritePath(buildcraft.silicon.gate.EnumGateModifier mod) {
        switch (mod) {
            case LAPIS: return "minecraft:block/lapis_block";
            case QUARTZ: return "minecraft:block/quartz_block_top";
            case DIAMOND: return "minecraft:block/diamond_block";
            default: return "minecraft:block/stone";
        }
    }

    @Override
    public List<BakedQuad> bake(KeyPlugGate key) {
        if (cached.containsKey(key)) {
            return cached.get(key);
        }

        List<BakedQuad> list = new ArrayList<>();

        // Helper to convert 16th scale coordinates to a box
        float baseMinX = 2f / 16f, baseMaxX = 4.01f / 16f;
        float baseYMin = 5f / 16f, baseYMax = 11f / 16f;
        float baseZMin = 5f / 16f, baseZMax = 11f / 16f;

        // Base Material
        TextureAtlasSprite matSprite = getSprite(getMaterialSpritePath(key.variant.material));
        buildBox(list, baseMinX, baseYMin, baseZMin, baseMaxX, baseYMax, baseZMax, matSprite, key.side, true, 0);

        // Logic Component
        if (key.variant.material != buildcraft.silicon.gate.EnumGateMaterial.CLAY_BRICK) {
            TextureAtlasSprite logicSprite = getSprite("buildcraftunofficial:block/gates/gate_" + key.variant.logic.tag);
            buildBox(list, 1.8f / 16f, 7f / 16f, 7f / 16f, 4.2f / 16f, 9f / 16f, 9f / 16f, logicSprite, key.side, true, 0);
        }

        // Dynamic State Component
        // In 1.12.2, gate_dynamic.json forced "light": "on ? 15 : 0" on this quad.
        // At light=0 the renderer made gate_off.png (dark red pixels) appear pure black.
        // Static baked quads can't force a lightmap override, so we darken vertex colors instead.
        TextureAtlasSprite dynSprite = getSprite("buildcraftunofficial:block/gates/gate_" + (key.active ? "on" : "off"));
        int dynListStart = list.size();
        buildBox(list, 1.9f / 16f, 6f / 16f, 6f / 16f, 4.1f / 16f, 10f / 16f, 10f / 16f, dynSprite, key.side, true, key.active ? 15 : 0);
        if (!key.active) {
            // Darken the off-state quads so the dark red gate_off.png appears black
            for (int i = dynListStart; i < list.size(); i++) {
                MutableQuad mq = new MutableQuad();
                mq.fromBakedBlock(list.get(i));
                mq.multColourd(0.05);
                list.set(i, mq.toBakedBlock());
            }
        }

        // Modifiers
        if (key.variant.modifier != buildcraft.silicon.gate.EnumGateModifier.NO_MODIFIER) {
            TextureAtlasSprite modSprite = getSprite(getModifierSpritePath(key.variant.modifier));
            buildBox(list, 1.8f / 16f, 5.5f / 16f, 5.5f / 16f, 4.2f / 16f, 6.5f / 16f, 6.5f / 16f, modSprite, key.side, true, 0);
            buildBox(list, 1.8f / 16f, 9.5f / 16f, 5.5f / 16f, 4.2f / 16f, 10.5f / 16f, 6.5f / 16f, modSprite, key.side, true, 0);
            buildBox(list, 1.8f / 16f, 5.5f / 16f, 9.5f / 16f, 4.2f / 16f, 6.5f / 16f, 10.5f / 16f, modSprite, key.side, true, 0);
            buildBox(list, 1.8f / 16f, 9.5f / 16f, 9.5f / 16f, 4.2f / 16f, 10.5f / 16f, 10.5f / 16f, modSprite, key.side, true, 0);
        }

        cached.put(key, list);
        return list;
    }

    private void buildBox(List<BakedQuad> list, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, TextureAtlasSprite sprite, Direction targetSide, boolean shade, int light) {
        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        Vector3f center = new Vector3f((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f);
        Vector3f radius = new Vector3f(maxX - center.x, maxY - center.y, maxZ - center.z);

        for (Direction face : Direction.values()) {
            ModelUtil.UvFaceData uv = new ModelUtil.UvFaceData();
            
            // Hardcode specific mapping required for gates 
            // This is effectively texture shrink-wrapping exactly as modeled in BC 1.12.2 gate.json
            if (face == Direction.WEST || face == Direction.EAST) {
                uv.minU = 5f / 16f;
                uv.maxU = 11f / 16f;
                uv.minV = 5f / 16f;
                uv.maxV = 11f / 16f;
            } else {
                uv.minU = 2f / 16f;
                uv.maxU = 4f / 16f;
                uv.minV = 5f / 16f;
                uv.maxV = 11f / 16f;
            }

            MutableQuad q = ModelUtil.createFace(face, center, radius, uv);
            q.texFromSprite(sprite);
            q.setTint(-1);
            q.setShade(shade);
            if (light > 0) {
                q.setLightEmission(light);
            }
            q.rotate(Direction.WEST, targetSide, 0.5f, 0.5f, 0.5f);
            
            // Adjust normal after rotate
            q.setCalculatedNormal();
            
            // We need to apply diffuse lighting manually since it's an item/block hybrid
            q.setCalculatedDiffuse();
            
            list.add(q.toBakedBlock());
        }
    }
}
