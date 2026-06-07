/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.silicon.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import buildcraft.api.transport.pluggable.IPlugDynamicRenderer;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.SpriteUtil;

import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.plug.PluggableGate;

/**
 * Per-frame BlockEntityRenderer for the entire visible gate body.
 *
 * Originally the gate's static parts (base material brick, logic component icon, modifier
 * dots) were baked into the chunk mesh, with only the dynamic active/dark overlay rendered
 * per frame. That meant placing/breaking ANY block within the 27-section cube around a
 * pipe network forced a chunk re-mesh whose cost scaled linearly with the number of gates
 * in the affected sections — gates contribute 12–36 quads each plus the per-vertex AO,
 * lighting, and tessellation work the chunk renderer does. With many gates clustered
 * together (a common pipe-network shape), placing a grass block nearby produced visible
 * frame stalls.
 *
 * Now the gate contributes ZERO quads to the chunk mesh ({@link buildcraft.silicon.client.model.plug.PlugGateBaker}
 * returns an empty list). All gate geometry is rendered here, per-frame, per visible gate.
 * The trade-off: a slight continuous BER cost (cheap on modern hardware — 6 boxes × 5 quads
 * after back-face cull = 30 quads per visible gate per frame) in exchange for chunk-mesh
 * cost that's now identical regardless of whether the pipe has gates. Block placements
 * near pipe networks no longer scale with gate count.
 *
 * Static-parts geometry is cached per {@link GateVariant}; the dynamic overlay is cached
 * once for ON and once for OFF. Both caches are populated lazily on first render.
 */
@SuppressWarnings("deprecation")
public enum PlugGateRenderer implements IPlugDynamicRenderer<PluggableGate> {
    INSTANCE;

    /** Dynamic overlay (the bright-red glow when isOn, black square when off). */
    private static List<MutableQuad> onBox;
    private static List<MutableQuad> offBox;

    /** Static parts (base material + logic + modifier) per variant. Built lazily. */
    private static final Map<GateVariant, List<MutableQuad>> staticByVariant = new ConcurrentHashMap<>();

    private static void initDynamicCache() {
        if (onBox != null) return;
        onBox = new ArrayList<>();
        offBox = new ArrayList<>();
        TextureAtlasSprite onSprite = getMcSprite("buildcraftunofficial:block/gates/gate_on");
        TextureAtlasSprite offSprite = getMcSprite("minecraft:block/black_concrete");
        addDynamicBox(onBox, onSprite);
        addDynamicBox(offBox, offSprite);
    }

    private static List<MutableQuad> staticQuadsFor(GateVariant variant) {
        List<MutableQuad> cached = staticByVariant.get(variant);
        if (cached != null) return cached;
        List<MutableQuad> list = buildStaticQuads(variant);
        staticByVariant.put(variant, list);
        return list;
    }

    private static List<MutableQuad> buildStaticQuads(GateVariant variant) {
        List<MutableQuad> list = new ArrayList<>();

        // Base material box (matches PlugGateBaker's base coordinates).
        TextureAtlasSprite matSprite = getMcSprite(materialSpritePath(variant.material));
        addBox(list, 2f / 16f, 5f / 16f, 5f / 16f, 4.01f / 16f, 11f / 16f, 11f / 16f, matSprite, true);

        // Logic component (skipped for clay-brick gates — they have no logic icon).
        if (variant.material != EnumGateMaterial.CLAY_BRICK) {
            TextureAtlasSprite logicSprite = getMcSprite(
                "buildcraftunofficial:block/gates/gate_" + variant.logic.tag);
            addBox(list, 1.8f / 16f, 7f / 16f, 7f / 16f, 4.2f / 16f, 9f / 16f, 9f / 16f, logicSprite, true);
        }

        // Modifier dots (4 small boxes at the corners), if a modifier is present.
        if (variant.modifier != EnumGateModifier.NO_MODIFIER) {
            TextureAtlasSprite modSprite = getMcSprite(modifierSpritePath(variant.modifier));
            addBox(list, 1.8f / 16f, 5.5f / 16f, 5.5f / 16f, 4.2f / 16f, 6.5f / 16f, 6.5f / 16f, modSprite, true);
            addBox(list, 1.8f / 16f, 9.5f / 16f, 5.5f / 16f, 4.2f / 16f, 10.5f / 16f, 6.5f / 16f, modSprite, true);
            addBox(list, 1.8f / 16f, 5.5f / 16f, 9.5f / 16f, 4.2f / 16f, 6.5f / 16f, 10.5f / 16f, modSprite, true);
            addBox(list, 1.8f / 16f, 9.5f / 16f, 9.5f / 16f, 4.2f / 16f, 10.5f / 16f, 10.5f / 16f, modSprite, true);
        }
        return list;
    }

    private static String materialSpritePath(EnumGateMaterial material) {
        return switch (material) {
            case CLAY_BRICK -> "minecraft:block/bricks";
            case IRON -> "minecraft:block/iron_block";
            case NETHER_BRICK -> "minecraft:block/nether_bricks";
            case GOLD -> "minecraft:block/gold_block";
        };
    }

    private static String modifierSpritePath(EnumGateModifier mod) {
        return switch (mod) {
            case LAPIS -> "minecraft:block/lapis_block";
            case QUARTZ -> "minecraft:block/quartz_block_top";
            case DIAMOND -> "minecraft:block/diamond_block";
            default -> "minecraft:block/stone";
        };
    }

    private static TextureAtlasSprite getMcSprite(String path) {
        net.minecraft.client.renderer.texture.TextureAtlas atlas =
            (net.minecraft.client.renderer.texture.TextureAtlas) Minecraft.getInstance()
                .getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(Identifier.parse(path));
        return sprite != null ? sprite : SpriteUtil.missingSprite();
    }

    /** Build a 6-face box with shared gate UV mapping. All faces are emitted — the EAST face
     *  (flush against the pipe surface after rotation) is still visible from oblique angles
     *  where the pipe's surface and the gate's box don't fully overlap.
     *
     *  Vertex colours are left at their default (full white = 0xFF). Vertex normals come
     *  directly from createFace's normalf(face.getStepX/Y/Z) — i.e. correct outward-pointing
     *  per-face normals. We deliberately do NOT call setCalculatedNormal(): that recomputes
     *  the normal as a cross-product of the quad's vertices, which for ModelUtil.createFace's
     *  vertex winding ends up INVERTED (UP face's cross-product points -Y), feeding the
     *  shader's automatic diffuse the wrong sign and producing a floor of darkness on every
     *  face regardless of orientation. With createFace's correct normals, the shader gets
     *  UP=full / DOWN=half / sides=0.6–0.8 like a vanilla block. */
    private static void addBox(List<MutableQuad> list, float minX, float minY, float minZ,
                               float maxX, float maxY, float maxZ, TextureAtlasSprite sprite, boolean shade) {
        Vector3f center = new Vector3f((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f);
        Vector3f radius = new Vector3f((maxX - minX) / 2f, (maxY - minY) / 2f, (maxZ - minZ) / 2f);
        for (Direction face : Direction.values()) {
            ModelUtil.UvFaceData uv = makeGateUVs(face);
            MutableQuad q = ModelUtil.createFace(face, center, radius, uv);
            q.texFromSprite(sprite);
            q.setTint(-1);
            q.setShade(shade);
            list.add(q);
        }
    }


    /** Dynamic overlay box uses fixed coordinates (matches the old chunk-baked dynamic-state box). */
    private static void addDynamicBox(List<MutableQuad> list, TextureAtlasSprite sprite) {
        addBox(list, 1.9f / 16f, 6f / 16f, 6f / 16f, 4.1f / 16f, 10f / 16f, 10f / 16f, sprite, false);
    }

    /** Same UV mapping as the original PlugGateBaker. */
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

    /** Clears caches on resource reload — sprite atlas references go stale otherwise. */
    public static void onModelBake() {
        onBox = null;
        offBox = null;
        staticByVariant.clear();
    }

    @Override
    public void render(PluggableGate plug, double x, double y, double z, float partialTicks,
                       VertexConsumer bb, PoseStack ps) {
        initDynamicCache();

        // Natural lightmap at the air block adjacent to the gate's outward side. We can't use
        // LevelRenderer.getLightCoords here — it returns vanilla's `blockLight | (skyLight << 16)`
        // format (raw values in low bits / bits 16-19), but MutableQuad.lighti(int combined)
        // unpacks the LightTexture.pack format (block << 4 / sky << 20). The format mismatch
        // produced lighti(0, 0) — full darkness — for any non-emissive lightmap. Using the
        // explicit getBrightness API (the pattern RenderPump and RenderMiningWell already use)
        // returns raw 0-15 values that lighti(int, int) consumes directly.
        //
        // Sampling at pipePos.relative(plug.side) instead of pipePos itself reads the lightmap
        // on the OUTSIDE of the pipe — where the gate's visible faces actually are — so the
        // gate is lit by the surrounding world rather than by the pipe block's own lightmap
        // value.
        // Full-bright by default so the offline snapshot 3D preview (which renders gates through this
        // same path but has no live world) shows the gate lit instead of pitch black. In world the
        // holder's level is always present, so the sampling below overrides this — unchanged there.
        int naturalBlockLight = 15;
        int naturalSkyLight = 15;
        boolean on = plug.logic.isOn;
        if (plug.holder != null && plug.holder.getPipeWorld() != null) {
            Level world = plug.holder.getPipeWorld();
            BlockPos sample = plug.holder.getPipePos().relative(plug.side);
            naturalBlockLight = world.getBrightness(LightLayer.BLOCK, sample);
            naturalSkyLight = world.getBrightness(LightLayer.SKY, sample);
        }

        ps.pushPose();
        ps.translate(x, y, z);

        // Rotate the WEST-facing geometry to the gate's actual side, mirroring the old
        // PlugGateBaker rotation (q.rotate(Direction.WEST, key.side, 0.5, 0.5, 0.5)).
        ps.translate(0.5f, 0.5f, 0.5f);
        switch (plug.side) {
            case EAST -> ps.mulPose(Axis.YP.rotationDegrees(180));
            case NORTH -> ps.mulPose(Axis.YP.rotationDegrees(-90));
            case SOUTH -> ps.mulPose(Axis.YP.rotationDegrees(90));
            case DOWN -> ps.mulPose(Axis.ZP.rotationDegrees(90));
            case UP -> ps.mulPose(Axis.ZP.rotationDegrees(-90));
            case WEST -> {} // already WEST-facing
        }
        ps.translate(-0.5f, -0.5f, -0.5f);

        // Render at full texture brightness — vertex colours stay at default white. The shader
        // applies its own per-face diffuse based on the vertex normals (which createFace set
        // to the correct outward direction in addBox), giving vanilla-style face shading.
        for (MutableQuad q : staticQuadsFor(plug.logic.variant)) {
            MutableQuad mq = new MutableQuad(q);
            mq.lighti(naturalBlockLight, naturalSkyLight);
            mq.render(ps.last(), bb);
        }
        // Dynamic overlay: ON state is fullbright (matches old setLightEmission(15) baked
        // behaviour), OFF state inherits the gate's natural lighting.
        for (MutableQuad q : (on ? onBox : offBox)) {
            MutableQuad mq = new MutableQuad(q);
            if (on) {
                mq.lighti(15, 15);
            } else {
                mq.lighti(naturalBlockLight, naturalSkyLight);
            }
            mq.render(ps.last(), bb);
        }

        ps.popPose();
    }
}
