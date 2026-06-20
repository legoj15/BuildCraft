/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import org.joml.Vector3f;

//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.DyeColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.pipe.EnumPipeColourType;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeFaceTex;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.ModelUtil.UvFaceData;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.ColourUtil;
import buildcraft.lib.misc.SpriteUtil;

import buildcraft.transport.BCTransportSprites;
import buildcraft.transport.client.model.PipeModelCacheBase.PipeBaseCutoutKey;
import buildcraft.transport.client.model.PipeModelCacheBase.PipeBaseTranslucentKey;

public enum PipeBaseModelGenStandard implements IPipeBaseModelGen {
    INSTANCE;

    // Textures — sprites are loaded lazily from the atlas via PipeDefinition texture names.
    // Caches are keyed by (def, colourblind-bit) so both variants can stay resolved simultaneously
    // — same pattern as DYED_SPRITES, which is keyed by (def, dyeColor ordinal).
    private static final Map<Long, TextureAtlasSprite[]> SPRITES = new java.util.HashMap<>();
    private static final Map<Long, TextureAtlasSprite[]> MASK_SPRITES = new java.util.HashMap<>();
    /** Cache of dyed fluid pipe sprites, keyed by (PipeDefinition, DyeColor ordinal, colourblind-bit). */
    private static final Map<Long, TextureAtlasSprite[]> DYED_SPRITES = new java.util.HashMap<>();

    /** Read the effective colourblind state — delegates to {@link buildcraft.lib.client.ColorBlindUtil#isActive()}
     *  which folds in both the BC config tri-state and (in AUTO mode) the vanilla MC
     *  High Contrast accessibility option. */
    private static boolean isCb() {
        return buildcraft.lib.client.ColorBlindUtil.isActive();
    }

    /** Cache key for SPRITES / MASK_SPRITES: top 32 bits = identityHashCode(def), low bit = cb.
     *  Package-visible for unit tests. */
    static long defKey(PipeDefinition def, boolean cb) {
        return ((long) System.identityHashCode(def) << 32) | (cb ? 1L : 0L);
    }

    /** Cache key for DYED_SPRITES: top 32 bits = identityHashCode(def), middle bits = colour ordinal, low bit = cb.
     *  Package-visible for unit tests. */
    static long dyedKey(PipeDefinition def, DyeColor colour, boolean cb) {
        return ((long) System.identityHashCode(def) << 32) | ((long) colour.ordinal() << 1) | (cb ? 1L : 0L);
    }

    /** Append "_cb" to a texture name only if a `_cb` companion sprite actually exists on
     *  the atlas. Falls through to the original name when no variant exists, so we don't
     *  paint missing-texture pink on pipes that have no colourblind variant. */
    private static String maybeCbName(String texName, boolean cb) {
        if (!cb) return texName;
        String candidate = texName + "_cb";
        TextureAtlasSprite probe = SpriteUtil.getSprite(candidate);
        return (probe == null || probe == SpriteUtil.missingSprite()) ? texName : candidate;
    }

    @Override
    public TextureAtlasSprite[] getItemSprites(PipeDefinition def) {
        return SPRITES.get(defKey(def, isCb()));
    }

    /** Ensure sprites are populated for a pipe definition. Resolves from the block atlas
     *  using the texture names in PipeDefinition.textures[]. When the colourblind toggle is
     *  on, each name is routed through {@link #maybeCbName} so any texture with a "_cb"
     *  companion on the atlas swaps to its colourblind variant. */
    private static TextureAtlasSprite[] ensureSprites(PipeDefinition def) {
        boolean cb = isCb();
        long key = defKey(def, cb);
        TextureAtlasSprite[] cached = SPRITES.get(key);
        if (cached != null) return cached;
        TextureAtlasSprite missing = SpriteUtil.missingSprite();
        TextureAtlasSprite[] array = new TextureAtlasSprite[def.textures.length];
        boolean allResolved = true;
        for (int i = 0; i < array.length; i++) {
            String name = maybeCbName(def.textures[i], cb);
            array[i] = SpriteUtil.getSprite(name);
            if (array[i] == null || array[i] == missing) {
                array[i] = missing;
                allResolved = false;
            }
        }
        // Only cache if all sprites resolved successfully — allows re-resolution
        // when atlas is reloaded or sprites become available later
        if (allResolved) {
            SPRITES.put(key, array);
        }
        return array;
    }

    /** Mapping from pipe texture basename to shared mask sprite name.
     *  Multiple textures sharing the same alpha channel pattern share one mask.
     *  Covers both RGBA and palette-mode (indexed) textures with transparency. */
    private static final Map<String, String> MASK_MAP = new java.util.HashMap<>();
    static {
        // Item pipes — 13 textures share 1 mask
        for (String s : new String[]{"clay_item","cobblestone_item","diamond_item",
                "diamond_wood_item_clear","emzuli_item_clear","gold_item",
                "iron_item_clear","obsidian_item","quartz_item",
                "sandstone_item","stone_item","stripes_item","wood_item_clear"})
            MASK_MAP.put(s, "mask_shared_item");
        // Power/RF base — 14 textures share 1 mask
        for (String s : new String[]{"cobblestone_power","cobblestone_rf",
                "diamond_wood_power_clear","diamond_wood_rf_clear",
                "gold_power","gold_rf",
                "quartz_power","quartz_rf",
                "sandstone_power","sandstone_rf",
                "stone_power","stone_rf",
                "wood_power_clear","wood_rf_clear"})
            MASK_MAP.put(s, "mask_shared_power");
        // Power/RF limiters (mN variants) — 28 textures share 1 mask
        for (String prefix : new String[]{"diamond_power_m","diamond_rf_m",
                "iron_power_m","iron_rf_m"})
            for (String suffix : new String[]{"0","4","8","16","32","64","128"})
                MASK_MAP.put(prefix + suffix, "mask_shared_power_limiter");
        // Power/RF filled — 4 textures share 1 mask
        for (String s : new String[]{"diamond_wood_power_filled","diamond_wood_rf_filled",
                "wood_power_filled","wood_rf_filled"})
            MASK_MAP.put(s, "mask_shared_power_filled");
        // Fluid pipes — all use the same waterproofing border mask (fully opaque dye colour)
        for (String s : new String[]{"clay_fluid","cobblestone_fluid",
                "diamond_fluid","diamond_fluid_down","diamond_fluid_east","diamond_fluid_north",
                "diamond_fluid_south","diamond_fluid_up","diamond_fluid_west",
                "diamond_fluid_west_cb","diamond_wood_fluid_clear","diamond_wood_fluid_filled",
                "gold_fluid","iron_fluid_clear","iron_fluid_filled","quartz_fluid",
                "sandstone_fluid","stone_fluid","void_fluid","wood_fluid_clear","wood_fluid_filled"})
            MASK_MAP.put(s, "mask_shared_fluid");
        // Daizuli/lapis colour variants — 32 textures share 1 mask
        for (String prefix : new String[]{"daizuli_item_","lapis_item_"})
            for (String colour : new String[]{"black","blue","brown","cyan","gray","green",
                    "light_blue","light_gray","lime","magenta","orange","pink","purple","red",
                    "white","yellow"})
                MASK_MAP.put(prefix + colour, "mask_shared_daizuli");
        // Diamond/iron/wood filled + daizuli filled — 12 textures share 1 mask
        for (String s : new String[]{"daizuli_item_filled","diamond_item_down","diamond_item_east",
                "diamond_item_north","diamond_item_south","diamond_item_up","diamond_item_west",
                "diamond_item_west_cb","diamond_wood_item_filled","emzuli_item_filled",
                "iron_item_filled","wood_item_filled"})
            MASK_MAP.put(s, "mask_shared_diamond_filled");
        // Unique masks (1 texture each)
        MASK_MAP.put("diamond_fluid_itemstack", "mask_shared_fluid");
        MASK_MAP.put("diamond_item_itemstack", "mask_diamond_item_itemstack");
        MASK_MAP.put("void_item", "mask_void_item");
    }

    /** Ensure mask sprites are populated for a pipe definition. Mask sprites use inverted
     *  alpha from the original pipe textures: opaque white where glass is, transparent
     *  where the frame is. Used for reverse-cutout colour rendering.
     *  Multiple textures sharing the same alpha pattern share one mask file. */
    static TextureAtlasSprite[] ensureMaskSprites(PipeDefinition def) {
        boolean cb = isCb();
        long key = defKey(def, cb);
        TextureAtlasSprite[] cached = MASK_SPRITES.get(key);
        if (cached != null) return cached;
        TextureAtlasSprite missing = SpriteUtil.missingSprite();
        TextureAtlasSprite[] array = new TextureAtlasSprite[def.textures.length];
        boolean allResolved = true;
        for (int i = 0; i < array.length; i++) {
            // textures[i] is e.g. "buildcraftunofficial:pipes/stone_item"; with cb on this
            // becomes e.g. ".../diamond_item_west_cb" when the _cb companion exists. MASK_MAP
            // already has entries for "diamond_item_west_cb" / "diamond_fluid_west_cb" → the
            // same shared-mask names as their non-_cb counterparts (see static initialiser),
            // so no MASK_MAP edits are needed.
            String texName = maybeCbName(def.textures[i], cb);
            int colonIdx = texName.indexOf(':');
            String namespace = colonIdx >= 0 ? texName.substring(0, colonIdx) : "minecraft";
            String path = colonIdx >= 0 ? texName.substring(colonIdx + 1) : texName;
            int lastSlash = path.lastIndexOf('/');
            String baseName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

            // Look up shared mask name, fall back to "mask_<baseName>"
            String maskBaseName = MASK_MAP.getOrDefault(baseName, "mask_" + baseName);
            String maskPath = lastSlash >= 0
                ? path.substring(0, lastSlash + 1) + maskBaseName
                : maskBaseName;
            String maskLoc = namespace + ":" + maskPath;

            array[i] = SpriteUtil.getSprite(net.minecraft.resources.Identifier.parse(maskLoc));
            if (array[i] == null || array[i] == missing) {
                array[i] = missing;
                allResolved = false;
            }
        }
        if (allResolved) {
            MASK_SPRITES.put(key, array);
        }
        return array;
    }

    /** Resolve dyed sprites for a painted fluid pipe. Appends "_dyed_<colour>" to each
     *  texture name in PipeDefinition.textures[] (after applying any colourblind suffix)
     *  and resolves from the block atlas. Throws {@link IllegalStateException} if any
     *  sprite is missing — dye_replace is supposed to generate every variant at stitch
     *  time, so a missing sprite means the atlas config regressed and we want a loud
     *  failure rather than a silent fall-through to mask-overlay rendering. */
    static TextureAtlasSprite[] ensureDyedSprites(PipeDefinition def, DyeColor colour) {
        boolean cb = isCb();
        long cacheKey = dyedKey(def, colour, cb);
        TextureAtlasSprite[] cached = DYED_SPRITES.get(cacheKey);
        if (cached != null) return cached;
        TextureAtlasSprite missing = SpriteUtil.missingSprite();
        TextureAtlasSprite[] array = new TextureAtlasSprite[def.textures.length];
        String dyeSuffix = "_dyed_" + colour.getName();
        for (int i = 0; i < array.length; i++) {
            String dyedTex = maybeCbName(def.textures[i], cb) + dyeSuffix;
            array[i] = SpriteUtil.getSprite(Identifier.parse(dyedTex));
            if (array[i] == null || array[i] == missing) {
                throw new IllegalStateException(
                    "Dyed sprite missing from blocks atlas: " + dyedTex
                        + " — DyeReplaceSpriteSource didn't emit it. Check that "
                        + "assets/minecraft/atlases/blocks.json has a buildcraftunofficial:dye_replace "
                        + "entry whose source matches " + def.textures[i]
                        + " and that the source/mask PNGs exist and have matching dimensions.");
            }
        }
        DYED_SPRITES.put(cacheKey, array);
        return array;
    }

    /** Called from the BC client config-reload listener (BCCoreClient) when our config spec
     *  changes — typically a player flipping {@code colorBlindMode} between AUTO/ON/OFF.
     *  Both texture variants are already baked into the block atlas (the `pipes/` directory
     *  source picks them up at startup), and our caches are keyed by (def, cb) so they don't
     *  need to be cleared — but in-world chunks hold references to the old sprite array, so
     *  request a level re-render to swap them. Equivalent to F3+A. */
    public static void onColorBlindToggle() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        //? if >=26.2 {
        /*// 26.2 relocated allChanged() off LevelRenderer onto the new LevelExtractor
        // (Minecraft.levelExtractor), which owns chunk re-bake scheduling.
        if (mc != null && mc.levelExtractor != null) {
            mc.levelExtractor.allChanged();
        }*/
        //?} else {
        if (mc != null && mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
        //?}
    }

    /** Called from the resource-manager reload listener (BCLibClient) — fires on F3+T, on
     *  any resource-pack change, AND on vanilla MC's High Contrast toggle (which switches
     *  the built-in {@code minecraft:high_contrast} pack and triggers a reload). After a
     *  reload the atlas has been restitched: any {@link TextureAtlasSprite} reference cached
     *  here points at freed atlas slots from the previous stitch, which would render as
     *  garbage UVs on the new atlas. Drop everything; the next {@code ensureSprites} call
     *  per pipe definition will re-resolve against the current atlas. The level renderer's
     *  own reload path takes care of triggering chunk re-bakes, so no explicit
     *  {@code allChanged()} call is needed here. */
    public static void clearSpriteCaches() {
        SPRITES.clear();
        MASK_SPRITES.clear();
        DYED_SPRITES.clear();
    }

    // Models - pre-built geometry templates
    private static final MutableQuad[][][] QUADS;
    private static final MutableQuad[][][] QUADS_COLOURED;

    static {
        QUADS = new MutableQuad[2][][];
        QUADS_COLOURED = new MutableQuad[2][][];
        final double colourOffset = 0.01;
        Vec3[] faceOffset = new Vec3[6];
        for (Direction face : Direction.values()) {
            //? if >=1.21.10 {
            Vec3 dir = Vec3.atLowerCornerOf(face.getOpposite().getUnitVec3i());
            //?} else {
            /*Vec3 dir = Vec3.atLowerCornerOf(face.getOpposite().getNormal());*/
            //?}
            faceOffset[face.ordinal()] = dir.scale(colourOffset);
        }

        // not connected
        QUADS[0] = new MutableQuad[6][2];
        QUADS_COLOURED[0] = new MutableQuad[6][2];
        Vector3f center = new Vector3f(0.5f, 0.5f, 0.5f);
        Vector3f radius = new Vector3f(0.25f, 0.25f, 0.25f);
        UvFaceData uvs = new UvFaceData();
        uvs.minU = uvs.minV = 4 / 16f;
        uvs.maxU = uvs.maxV = 12 / 16f;
        for (Direction face : Direction.values()) {
            MutableQuad quad = ModelUtil.createFace(face, center, radius, uvs);
            quad.setDiffuse(quad.normalvf());
            QUADS[0][face.ordinal()][0] = quad;
            dupDarker(QUADS[0][face.ordinal()]);

            MutableQuad[] colQuads = ModelUtil.createDoubleFace(face, center, radius, uvs);
            for (MutableQuad q : colQuads) {
                q.translatevd(faceOffset[face.ordinal()]);
            }
            QUADS_COLOURED[0][face.ordinal()] = colQuads;
        }

        int[][] uvsRot = {
            { 2, 0, 3, 3 },
            { 0, 2, 1, 1 },
            { 2, 0, 0, 2 },
            { 0, 2, 2, 0 },
            { 3, 3, 0, 2 },
            { 1, 1, 2, 0 }
        };

        UvFaceData[] types = {
            UvFaceData.from16(4, 0, 12, 4),
            UvFaceData.from16(4, 12, 12, 16),
            UvFaceData.from16(0, 4, 4, 12),
            UvFaceData.from16(12, 4, 16, 12)
        };

        // connected
        QUADS[1] = new MutableQuad[6][8];
        QUADS_COLOURED[1] = new MutableQuad[6][8];
        for (Direction side : Direction.values()) {
            Vector3f sCenter = new Vector3f(
                side.getStepX() * 0.375f,
                side.getStepY() * 0.375f,
                side.getStepZ() * 0.375f
            );
            Vector3f sRadius = new Vector3f(
                side.getAxis() == Axis.X ? 0.125f : 0.25f,
                side.getAxis() == Axis.Y ? 0.125f : 0.25f,
                side.getAxis() == Axis.Z ? 0.125f : 0.25f
            );
            sCenter.add(new Vector3f(0.5f, 0.5f, 0.5f));

            int i = 0;
            for (Direction face : Direction.values()) {
                if (face.getAxis() == side.getAxis()) continue;
                MutableQuad quad = ModelUtil.createFace(face, sCenter, sRadius, types[i]);
                quad.rotateTextureUp(uvsRot[side.ordinal()][i]);

                MutableQuad col = new MutableQuad(quad);

                quad.setDiffuse(quad.normalvf());
                QUADS[1][side.ordinal()][i] = quad;

                col.translatevd(faceOffset[face.ordinal()]);
                QUADS_COLOURED[1][side.ordinal()][i++] = col;
            }
            dupDarker(QUADS[1][side.ordinal()]);
            dupInverted(QUADS_COLOURED[1][side.ordinal()]);
        }
    }

    private static void dupDarker(MutableQuad[] quads) {
        int halfLength = quads.length / 2;
        float mult = OPTION_INSIDE_COLOUR_MULT.getAsFloat();
        for (int i = 0; i < halfLength; i++) {
            int n = i + halfLength;
            MutableQuad from = quads[i];
            if (from != null) {
                MutableQuad to = from.copyAndInvertNormal();
                to.setCalculatedDiffuse();
                to.multColourd(mult);
                quads[n] = to;
            }
        }
    }

    private static void dupInverted(MutableQuad[] quads) {
        int halfLength = quads.length / 2;
        for (int i = 0; i < halfLength; i++) {
            int n = i + halfLength;
            MutableQuad from = quads[i];
            if (from != null) {
                quads[n] = from.copyAndInvertNormal();
            }
        }
    }

    // Model Usage

    @Override
    public List<MutableQuad> generateCutoutMutable(PipeBaseCutoutKey key) {
        List<MutableQuad> quads = new ArrayList<>();

        // For painted fluid pipes, use the dye_replace-generated sprite variants
        // (colour baked into texture at stitch time). ensureDyedSprites throws on
        // missing — no silent fallback, per design.
        TextureAtlasSprite[] spriteArray;
        if (key.definition != null && key.colour != null
                && key.definition.flowType == PipeApi.flowFluids) {
            spriteArray = ensureDyedSprites(key.definition, key.colour);
        } else {
            spriteArray = key.definition != null ? ensureSprites(key.definition) : null;
        }
        TextureAtlasSprite borderSprite = getBorderSprite(key);
        int colour = borderSprite == null ? -1 : getPipeModelColour(key.colour);
        int border_r = (colour >> 0) & 0xFF;
        int border_g = (colour >> 8) & 0xFF;
        int border_b = (colour >> 16) & 0xFF;
        for (Direction face : Direction.values()) {
            float size = key.connections[face.ordinal()];
            PipeFaceTex tex = size > 0 ? key.sideSprites[face.ordinal()] : key.centerSprite;
            int quadsIndex = size > 0 ? 1 : 0;
            MutableQuad[] quadArray = QUADS[quadsIndex][face.ordinal()];

            int startIndex = quads.size();

            for (int i = 0; i < tex.getCount(); i++) {
                addQuads(quadArray, quads, getSprite(spriteArray, tex, i));

                int c = tex.getColour(i);
                int r = (c >> 0) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = (c >> 16) & 0xFF;

                for (int q = startIndex; q < quads.size(); q++) {
                    quads.get(q).multColouri(r, g, b, 0xFF);
                }

                startIndex = quads.size();
            }

            if (borderSprite != null) {
                addQuads(quadArray, quads, borderSprite);

                for (int ii = startIndex; ii < quads.size(); ii++) {
                    quads.get(ii).multColouri(border_r, border_g, border_b, 0xFF);
                }
            }
        }

        return quads;
    }

    /** Generate reverse-cutout mask quads for TRANSLUCENT coloured pipes.
     *  These quads use inverted-alpha mask sprites (opaque white where glass is,
     *  transparent where frame is) tinted with the dye colour.
     *
     *  Designed to be rendered to a TRANSLUCENT buffer AFTER the cutout pass.
     *  The cutout pass writes no depth for glass pixels, so these mask quads
     *  pass the depth test for glass regions and fail for frame regions —
     *  no Z-fighting, proper semi-transparent tinting.
     *
     *  @param alpha vertex alpha (0-255) for the tint intensity. 76 matches overlay_stained.png.
     */
    public List<MutableQuad> generateMaskMutable(PipeBaseCutoutKey key, int alpha) {
        if (key.colour == null || key.colourType != EnumPipeColourType.TRANSLUCENT) {
            return Collections.emptyList();
        }

        List<MutableQuad> quads = new ArrayList<>();
        TextureAtlasSprite[] maskArray = key.definition != null ? ensureMaskSprites(key.definition) : null;
        int dyeColour = getPipeModelColour(key.colour);
        int dye_r = (dyeColour >> 0) & 0xFF;
        int dye_g = (dyeColour >> 8) & 0xFF;
        int dye_b = (dyeColour >> 16) & 0xFF;

        for (Direction face : Direction.values()) {
            float size = key.connections[face.ordinal()];
            PipeFaceTex tex = size > 0 ? key.sideSprites[face.ordinal()] : key.centerSprite;
            int quadsIndex = size > 0 ? 1 : 0;
            MutableQuad[] quadArray = QUADS[quadsIndex][face.ordinal()];

            int startIndex = quads.size();
            for (int i = 0; i < tex.getCount(); i++) {
                TextureAtlasSprite maskSprite = getSprite(maskArray, tex, i);
                if (maskSprite != SpriteUtil.missingSprite()) {
                    addQuads(quadArray, quads, maskSprite);
                    for (int q = startIndex; q < quads.size(); q++) {
                        quads.get(q).multColouri(dye_r, dye_g, dye_b, alpha);
                    }
                }
                startIndex = quads.size();
            }
        }
        return quads;
    }

    @Override
    public List<BakedQuad> generateCutout(PipeBaseCutoutKey key) {
        List<MutableQuad> quads = generateCutoutMutable(key);
        List<BakedQuad> bakedQuads = new ArrayList<>();
        for (MutableQuad q : quads) {
            bakedQuads.add(q.toBakedBlock());
        }
        return bakedQuads;
    }

    @Nullable
    private static TextureAtlasSprite getBorderSprite(PipeBaseCutoutKey key) {
        if (key.colour == null) {
            return null;
        }
        if (key.colourType == EnumPipeColourType.BORDER_INNER) {
            return BCTransportSprites.PIPE_COLOUR_BORDER_INNER.getSprite();
        }
        if (key.colourType == EnumPipeColourType.BORDER_OUTER) {
            return BCTransportSprites.PIPE_COLOUR_BORDER_OUTER.getSprite();
        }
        return null;
    }

    private static TextureAtlasSprite getSprite(TextureAtlasSprite[] array, PipeFaceTex tex, int spriteIndex) {
        int index = tex.getTexture(spriteIndex);
        return getSprite(array, index);
    }

    private static TextureAtlasSprite getSprite(TextureAtlasSprite[] array, int index) {
        if (array == null || index < 0 || index >= array.length) {
            return SpriteUtil.missingSprite();
        }
        return array[index];
    }

    @Override
    public List<BakedQuad> generateTranslucent(PipeBaseTranslucentKey key) {
        if (!key.shouldRender()) return ImmutableList.of();
        List<MutableQuad> mutableQuads;
        if (key.cutoutKey != null && key.cutoutKey.definition != null
                && key.cutoutKey.definition.flowType == PipeApi.flowFluids) {
            // Fluid pipes: for painted variants, colour is baked into the cutout
            // sprite by DyeReplaceSpriteSource — no overlay needed. ensureDyedSprites
            // (called from the cutout path) throws on missing, so reaching here for a
            // painted fluid pipe means the dyed sprite is on the atlas.
            if (key.cutoutKey.colour != null) {
                return ImmutableList.of();
            }
            mutableQuads = generateMaskMutable(key.cutoutKey, 255);
        } else {
            mutableQuads = generateTranslucentMutable(key);
        }
        List<BakedQuad> bakedQuads = new ArrayList<>();
        for (MutableQuad q : mutableQuads) {
            bakedQuads.add(q.toBakedTranslucent());
        }
        return bakedQuads;
    }

    /** Returns translucent colour overlay quads for chunk-baked rendering.
     *  Uses mask sprites (which have alpha=0 over the frame, alpha=255 over
     *  the glass area) so the tint only colours the see-through parts.
     *  Alpha=76 gives the same semi-transparency as overlay_stained.png. */
    public List<MutableQuad> generateTranslucentMutable(PipeBaseTranslucentKey key) {
        if (!key.shouldRender()) return ImmutableList.of();
        List<MutableQuad> quads = new ArrayList<>();

        // Use per-definition mask sprites — these have proper alpha masking
        // (transparent over frame, opaque over glass)
        TextureAtlasSprite[] maskArray = key.cutoutKey != null && key.cutoutKey.definition != null
                ? ensureMaskSprites(key.cutoutKey.definition) : null;

        for (Direction face : Direction.values()) {
            float size = key.connections[face.ordinal()];
            PipeFaceTex tex = key.cutoutKey != null
                    ? (size > 0 ? key.cutoutKey.sideSprites[face.ordinal()] : key.cutoutKey.centerSprite)
                    : null;
            int quadsIndex = size > 0 ? 1 : 0;

            if (tex != null) {
                for (int i = 0; i < tex.getCount(); i++) {
                    TextureAtlasSprite maskSprite = getSprite(maskArray, tex, i);
                    int startIndex = quads.size();
                    addQuads(QUADS[quadsIndex][face.ordinal()], quads, maskSprite);
                    // Apply semi-transparent alpha (76 = overlay_stained.png's glass alpha)
                    for (int q = startIndex; q < quads.size(); q++) {
                        quads.get(q).multColouri(0xFF, 0xFF, 0xFF, 76);
                    }
                }
            } else {
                // Fallback: use PIPE_COLOUR sprite (full-face)
                TextureAtlasSprite sprite = BCTransportSprites.PIPE_COLOUR.getSprite();
                if (sprite == null) sprite = SpriteUtil.missingSprite();
                addQuads(QUADS[quadsIndex][face.ordinal()], quads, sprite);
            }
        }
        // Set tintIndex=1 on all translucent overlay quads.
        // The chunk renderer applies colour via BlockTintSource (tintIndex=1 → dye colour),
        // since BakedQuad in NeoForge 26.1 has no vertex colour — only tint-based colouring.
        for (MutableQuad q : quads) {
            q.setTint(1);
        }
        return quads;
    }

    private static int getPipeModelColour(DyeColor c) {
        if (c == null) return 0xFF_FF_FF_FF;
        return 0xFF_00_00_00 | ColourUtil.swapArgbToAbgr(ColourUtil.getLightHex(c));
    }

    /** Returns the dye tint colour in ABGR format for use in vertex colour tinting. */
    public static int getDyeTintColour(DyeColor c) {
        return getPipeModelColour(c);
    }

    private static void addQuads(MutableQuad[] from, List<MutableQuad> to, TextureAtlasSprite sprite) {
        for (MutableQuad f : from) {
            if (f == null) {
                continue;
            }
            MutableQuad copy = new MutableQuad(f);
            copy.setSprite(sprite);
            copy.texFromSprite(sprite);
            to.add(copy);
        }
    }
}
