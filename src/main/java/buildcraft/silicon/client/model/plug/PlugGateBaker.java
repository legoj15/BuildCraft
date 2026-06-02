package buildcraft.silicon.client.model.plug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

import buildcraft.api.transport.pluggable.IPluggableStaticBaker;
import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.silicon.client.model.key.KeyPlugGate;

@SuppressWarnings("deprecation")
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
        // The gate contributes ZERO quads to the chunk mesh — every visible part (base material,
        // logic icon, modifier dots, on/off overlay) is rendered per-frame by PlugGateRenderer.
        //
        // Why: chunk re-mesh cost scales with the quad count of every block in the section, plus
        // the per-vertex AO/lighting work the chunk renderer does. Each gate previously
        // contributed 12–36 quads to the chunk mesh. When a player placed/broke any block within
        // the 27-section cube around a pipe network, EVERY pipe in those sections re-meshed,
        // and the cost was directly proportional to the gate count nearby. Moving gate geometry
        // to BER eliminates that scaling — chunks with 100 gates now re-mesh at the same speed
        // as chunks with 0 gates.
        //
        // The trade-off is a slight continuous BER cost per visible gate (~30 quads per frame
        // after back-face cull). On modern hardware that's negligible compared to the avoided
        // re-mesh spike.
        return java.util.Collections.emptyList();
    }

    /**
     * Bake gate geometry as MutableQuads for item rendering.
     * Unlike the in-world baker, items use gate_off.png directly (the dark red texture
     * is correct for items since vertex colors can be applied through the item rendering path).
     * Geometry is built facing NORTH (thin axis on Z) so the gate face points at the camera
     * without needing any post-hoc rotation (which would corrupt BakedQuad face metadata
     * and break item rendering brightness/size).
     */
    public List<MutableQuad> bakeForItem(buildcraft.silicon.gate.GateVariant variant) {
        List<MutableQuad> quads = new ArrayList<>();

        // Coordinates are the same as in-world, but with X↔Z swapped:
        // Original (WEST-facing): thin in X (2-4/16), wide in Y,Z (5-11/16)
        // Item (NORTH-facing): thin in Z (2-4/16), wide in X,Y (5-11/16)
        float baseZMin = 2f / 16f, baseZMax = 4.01f / 16f;
        float baseYMin = 5f / 16f, baseYMax = 11f / 16f;
        float baseXMin = 5f / 16f, baseXMax = 11f / 16f;

        // Base Material
        TextureAtlasSprite matSprite = getSprite(getMaterialSpritePath(variant.material));
        buildBoxMutableNorth(quads, baseXMin, baseYMin, baseZMin, baseXMax, baseYMax, baseZMax, matSprite, true);

        // Logic Component
        if (variant.material != buildcraft.silicon.gate.EnumGateMaterial.CLAY_BRICK) {
            TextureAtlasSprite logicSprite = getSprite("buildcraftunofficial:block/gates/gate_" + variant.logic.tag);
            buildBoxMutableNorth(quads, 7f / 16f, 7f / 16f, 1.8f / 16f, 9f / 16f, 9f / 16f, 4.2f / 16f, logicSprite, false);
        }

        // Dynamic State Component — items always show "off" with the original gate_off texture
        TextureAtlasSprite dynSprite = getSprite("buildcraftunofficial:block/gates/gate_off");
        buildBoxMutableNorth(quads, 6f / 16f, 6f / 16f, 1.9f / 16f, 10f / 16f, 10f / 16f, 4.1f / 16f, dynSprite, false);

        // Modifiers
        if (variant.modifier != buildcraft.silicon.gate.EnumGateModifier.NO_MODIFIER) {
            TextureAtlasSprite modSprite = getSprite(getModifierSpritePath(variant.modifier));
            buildBoxMutableNorth(quads, 5.5f / 16f, 5.5f / 16f, 1.8f / 16f, 6.5f / 16f, 6.5f / 16f, 4.2f / 16f, modSprite, false);
            buildBoxMutableNorth(quads, 5.5f / 16f, 9.5f / 16f, 1.8f / 16f, 6.5f / 16f, 10.5f / 16f, 4.2f / 16f, modSprite, false);
            buildBoxMutableNorth(quads, 9.5f / 16f, 5.5f / 16f, 1.8f / 16f, 10.5f / 16f, 6.5f / 16f, 4.2f / 16f, modSprite, false);
            buildBoxMutableNorth(quads, 9.5f / 16f, 9.5f / 16f, 1.8f / 16f, 10.5f / 16f, 10.5f / 16f, 4.2f / 16f, modSprite, false);
        }

        return quads;
    }

    private void buildBox(List<BakedQuad> list, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, TextureAtlasSprite sprite, Direction targetSide, boolean shade, int light) {
        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        Vector3f center = new Vector3f((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f);
        Vector3f radius = new Vector3f(maxX - center.x, maxY - center.y, maxZ - center.z);

        for (Direction face : Direction.values()) {
            // Skip the EAST face: pre-rotation, EAST is the face of the box that sits against
            // the pipe surface (gate boxes are mounted at x=2..4.x in the WEST-facing build, so
            // their EAST face is at the pipe body's WEST surface and is hidden by the pipe body
            // quad in front of it). After rotate(WEST, targetSide), the equivalent face still
            // ends up flush against the pipe body. Cutting it eliminates one quad per box per
            // gate from the chunk mesh — meaningful when many gates land in the same 27-section
            // re-mesh after a nearby block placement.
            if (face == Direction.EAST) continue;

            ModelUtil.UvFaceData uv = makeGateUVs(face);

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

    /**
     * Build box geometry as MutableQuads for item rendering (NORTH-facing).
     * The geometry is built with the thin axis on Z (gate face = NORTH),
     * so no rotation is needed.
     */
    private void buildBoxMutableNorth(List<MutableQuad> list, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, TextureAtlasSprite sprite, boolean shade) {
        Vector3f center = new Vector3f((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f);
        Vector3f radius = new Vector3f(maxX - center.x, maxY - center.y, maxZ - center.z);

        for (Direction face : Direction.values()) {
            ModelUtil.UvFaceData uv = makeGateItemUVs(face);

            MutableQuad q = ModelUtil.createFace(face, center, radius, uv);
            q.texFromSprite(sprite);
            q.setTint(-1);
            q.setShade(shade);
            q.setCalculatedNormal();
            list.add(q);
        }
    }

    /** Hardcoded UV region matching the BC 1.12.2 gate.json texture shrink-wrapping. */
    private static ModelUtil.UvFaceData makeGateUVs(Direction face) {
        ModelUtil.UvFaceData uv = new ModelUtil.UvFaceData();
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
        return uv;
    }

    /**
     * UV mapping for NORTH-facing item geometry. The box is wide in X (6/16)
     * and Y (6/16) but thin in Z (2/16), so three branches are needed:
     * front/back use the 6x6 face region; WEST/EAST edges use a 2x6 strip
     * with the thin axis on U; UP/DOWN edges use a 6x2 strip with the thin
     * axis on V (since ModelUtil maps U to X and V to Z for the top face).
     */
    private static ModelUtil.UvFaceData makeGateItemUVs(Direction face) {
        ModelUtil.UvFaceData uv = new ModelUtil.UvFaceData();
        if (face == Direction.NORTH || face == Direction.SOUTH) {
            uv.minU = 5f / 16f;
            uv.maxU = 11f / 16f;
            uv.minV = 5f / 16f;
            uv.maxV = 11f / 16f;
        } else if (face == Direction.WEST || face == Direction.EAST) {
            uv.minU = 2f / 16f;
            uv.maxU = 4f / 16f;
            uv.minV = 5f / 16f;
            uv.maxV = 11f / 16f;
        } else {
            uv.minU = 5f / 16f;
            uv.maxU = 11f / 16f;
            uv.minV = 2f / 16f;
            uv.maxV = 4f / 16f;
        }
        return uv;
    }
}
