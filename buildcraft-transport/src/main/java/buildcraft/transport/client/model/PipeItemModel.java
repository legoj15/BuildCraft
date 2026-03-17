/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.ModelUtil.UvFaceData;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.ColourUtil;

import buildcraft.transport.BCTransportItems;
import buildcraft.transport.BCTransportSprites;

/**
 * A dynamic ItemModel for pipe items that wraps the vanilla JSON-baked model
 * and adds colour overlay quads when the item carries a PIPE_COLOUR data component.
 *
 * <p>The overlay replicates the in-world translucent paint rendering using the
 * PIPE_COLOUR sprite with slightly-offset double-faced geometry, tinted with
 * the dye colour — matching exactly what {@link PipeBaseModelGenStandard}
 * does for the translucent pass.
 */
public class PipeItemModel implements ItemModel {
    /** Slight outward offset per-face to avoid Z-fighting (matches QUADS_COLOURED in PipeBaseModelGenStandard). */
    private static final double COLOUR_OFFSET = 0.01;

    // Reflection fields cached at class-load time
    private static final Field QUADS_FIELD;
    private static final Field PROPERTIES_FIELD;
    private static final Field RENDER_TYPE_FIELD;
    static {
        try {
            QUADS_FIELD = BlockModelWrapper.class.getDeclaredField("quads");
            QUADS_FIELD.setAccessible(true);
            PROPERTIES_FIELD = BlockModelWrapper.class.getDeclaredField("properties");
            PROPERTIES_FIELD.setAccessible(true);
            RENDER_TYPE_FIELD = BlockModelWrapper.class.getDeclaredField("renderType");
            RENDER_TYPE_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access BlockModelWrapper fields", e);
        }
    }

    private final BlockModelWrapper vanillaWrapper;
    private final PipeDefinition definition;

    // Extracted from vanilla model at construction time
    private final List<BakedQuad> vanillaQuads;
    private final ItemTransforms itemTransforms;
    private final boolean usesBlockLight;
    @SuppressWarnings("unchecked")
    private final Function<ItemStack, RenderType> vanillaRenderType;

    /** Pre-baked overlay quads per DyeColor. */
    private final Map<DyeColor, List<BakedQuad>> overlayQuadCache = new EnumMap<>(DyeColor.class);

    @SuppressWarnings("unchecked")
    public PipeItemModel(BlockModelWrapper vanillaWrapper, PipeDefinition definition) {
        this.vanillaWrapper = vanillaWrapper;
        this.definition = definition;
        try {
            this.vanillaQuads = (List<BakedQuad>) QUADS_FIELD.get(vanillaWrapper);
            var renderProps = (ModelRenderProperties) PROPERTIES_FIELD.get(vanillaWrapper);
            this.itemTransforms = renderProps.transforms();
            this.usesBlockLight = renderProps.usesBlockLight();
            this.vanillaRenderType = (Function<ItemStack, RenderType>) RENDER_TYPE_FIELD.get(vanillaWrapper);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to read BlockModelWrapper fields", e);
        }
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        DyeColor colour = stack.get(BCTransportItems.PIPE_COLOUR.get());
        if (colour == null) {
            // No paint — use vanilla model as-is (gets its own model identity)
            vanillaWrapper.update(renderState, stack, modelResolver, displayContext, level, owner, seed);
            return;
        }

        // For painted pipes: use OUR identity (not vanillaWrapper's)
        // so the GUI cache doesn't confuse painted and unpainted versions
        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(colour);

        // === Layer 1: Base pipe quads (from vanilla model) ===
        var baseLayer = renderState.newLayer();
        baseLayer.prepareQuadList().addAll(vanillaQuads);
        baseLayer.setRenderType(vanillaRenderType.apply(stack));
        baseLayer.setUsesBlockLight(usesBlockLight);
        baseLayer.setTransform(itemTransforms.getTransform(displayContext));

        // === Layer 2: Translucent colour overlay (matching in-world paint rendering) ===
        List<BakedQuad> overlayQuads = overlayQuadCache.computeIfAbsent(colour, this::generateOverlayQuads);
        if (!overlayQuads.isEmpty()) {
            var overlayLayer = renderState.newLayer();
            overlayLayer.prepareQuadList().addAll(overlayQuads);

            // Use blocks-atlas translucent render type — overlay sprites are on
            // the blocks atlas and need alpha blending for transparent areas
            overlayLayer.setRenderType(net.minecraft.client.renderer.Sheets.translucentBlockItemSheet());
            overlayLayer.setUsesBlockLight(usesBlockLight);
            overlayLayer.setTransform(itemTransforms.getTransform(displayContext));
        }
    }

    /**
     * Generate colour overlay quads matching the in-world translucent paint pass.
     *
     * <p>Uses the PIPE_COLOUR sprite on slightly-offset double-faced geometry
     * (matching QUADS_COLOURED in PipeBaseModelGenStandard), tinted with the
     * dye colour. This produces the same visual as in-world painted pipes.
     *
     * <p>The pipe_item.json geometry uses 3 cubes (bottom cap, center, top cap).
     * For each cube's visible faces, we create double-sided coloured overlay quads.
     */
    private List<BakedQuad> generateOverlayQuads(DyeColor colour) {
        // Get the dye colour in ABGR vertex format (matching PipeBaseModelGenStandard.getPipeModelColour)
        int dyeColour = PipeBaseModelGenStandard.getDyeTintColour(colour);

        // UVs matching the center (no-connection) geometry: 4/16 to 12/16
        UvFaceData uvs = new UvFaceData(4 / 16f, 4 / 16f, 12 / 16f, 12 / 16f);

        List<BakedQuad> quads = new ArrayList<>();

        if (definition.flowType == buildcraft.api.transport.pipe.PipeApi.flowFluids) {
            // Fluid pipes: use mask sprites with fully opaque painting
            // (matching in-world generateMaskMutable with alpha=255)
            TextureAtlasSprite[] maskArray = PipeBaseModelGenStandard.ensureMaskSprites(definition);
            TextureAtlasSprite maskSprite = maskArray != null && maskArray.length > 0
                    ? maskArray[0] : null;
            if (maskSprite == null || maskSprite == buildcraft.lib.misc.SpriteUtil.missingSprite()) {
                return List.of();
            }
            // Mask quads use full UVs to show entire frame pattern (corners + connecting lines)
            UvFaceData maskUvs = new UvFaceData(0, 0, 1, 1);
            addMaskFaces(quads, maskSprite, dyeColour, maskUvs,
                    new Vector3f(0.5f, 0.125f, 0.5f),
                    new Vector3f(0.25f, 0.125f, 0.25f),
                    new Direction[]{ Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST });
            addMaskFaces(quads, maskSprite, dyeColour, maskUvs,
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Vector3f(0.25f, 0.25f, 0.25f),
                    new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST });
            addMaskFaces(quads, maskSprite, dyeColour, maskUvs,
                    new Vector3f(0.5f, 0.875f, 0.5f),
                    new Vector3f(0.25f, 0.125f, 0.25f),
                    new Direction[]{ Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST });
        } else {
            // Item/kinesis/other pipes: use PIPE_COLOUR sprite with translucent overlay
            TextureAtlasSprite sprite = BCTransportSprites.PIPE_COLOUR.getSprite();
            if (sprite == null) {
                return List.of();
            }

            // pipe_item.json geometry (block-space 0..1):
            //   Bottom cap: [4,0,4]-[12,4,12]   → center=0.5,0.125,0.5  radius=0.25,0.125,0.25
            //   Center:     [4,4,4]-[12,12,12]  → center=0.5,0.5,0.5    radius=0.25,0.25,0.25
            //   Top cap:    [4,12,4]-[12,16,12] → center=0.5,0.875,0.5  radius=0.25,0.125,0.25

            addColouredFaces(quads, sprite, dyeColour, uvs,
                    new Vector3f(0.5f, 0.125f, 0.5f),
                    new Vector3f(0.25f, 0.125f, 0.25f),
                    new Direction[]{ Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST });
            addColouredFaces(quads, sprite, dyeColour, uvs,
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Vector3f(0.25f, 0.25f, 0.25f),
                    new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST });
            addColouredFaces(quads, sprite, dyeColour, uvs,
                    new Vector3f(0.5f, 0.875f, 0.5f),
                    new Vector3f(0.25f, 0.125f, 0.25f),
                    new Direction[]{ Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST });
        }

        return quads;
    }

    /**
     * Add double-faced coloured overlay quads for the given faces, slightly offset
     * outward to avoid Z-fighting (matching QUADS_COLOURED in PipeBaseModelGenStandard).
     */
    private static void addColouredFaces(List<BakedQuad> quads, TextureAtlasSprite sprite,
                                          int dyeColour, UvFaceData uvs,
                                          Vector3f center, Vector3f radius, Direction[] faces) {
        for (Direction face : faces) {
            // Create double-sided quads (front + back) matching QUADS_COLOURED
            MutableQuad[] pair = ModelUtil.createDoubleFace(face, center, radius, uvs);
            for (MutableQuad quad : pair) {
                // Offset slightly outward from face to avoid Z-fighting
                net.minecraft.world.phys.Vec3 offset = net.minecraft.world.phys.Vec3.atLowerCornerOf(
                        face.getOpposite().getUnitVec3i()).scale(COLOUR_OFFSET);
                quad.translatevd(offset);
                quad.setSprite(sprite);
                quad.texFromSprite(sprite);
                // Apply dye colour (ABGR format from getPipeModelColour)
                quad.colouri(dyeColour);
                quads.add(quad.toBakedBlock());
            }
        }
    }

    /**
     * Add mask overlay quads for fluid pipes — single-faced, slightly offset,
     * with the mask sprite's alpha controlling paint coverage and dye colour applied.
     * Matches in-world generateMaskMutable with alpha=255.
     */
    private static void addMaskFaces(List<BakedQuad> quads, TextureAtlasSprite maskSprite,
                                      int dyeColour, UvFaceData uvs,
                                      Vector3f center, Vector3f radius, Direction[] faces) {
        int dye_r = (dyeColour >> 0) & 0xFF;
        int dye_g = (dyeColour >> 8) & 0xFF;
        int dye_b = (dyeColour >> 16) & 0xFF;

        for (Direction face : faces) {
            MutableQuad quad = ModelUtil.createFace(face, center, radius, uvs);
            // Offset slightly outward (in face direction) so overlay renders in front of base pipe
            net.minecraft.world.phys.Vec3 offset = net.minecraft.world.phys.Vec3.atLowerCornerOf(
                    face.getUnitVec3i()).scale(COLOUR_OFFSET);
            quad.translatevd(offset);
            quad.setSprite(maskSprite);
            quad.texFromSprite(maskSprite);
            // Apply dye colour with full alpha — mask sprite alpha controls paint coverage
            quad.multColouri(dye_r, dye_g, dye_b, 255);
            quads.add(quad.toBakedBlock());
        }
    }
}
