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
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
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
    private static final Field EXTENTS_FIELD;
    static {
        try {
            QUADS_FIELD = BlockStateModelWrapper.class.getDeclaredField("quads");
            QUADS_FIELD.setAccessible(true);
            PROPERTIES_FIELD = BlockStateModelWrapper.class.getDeclaredField("properties");
            PROPERTIES_FIELD.setAccessible(true);
            RENDER_TYPE_FIELD = BlockStateModelWrapper.class.getDeclaredField("renderType");
            RENDER_TYPE_FIELD.setAccessible(true);
            EXTENTS_FIELD = BlockStateModelWrapper.class.getDeclaredField("extents");
            EXTENTS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access BlockStateModelWrapper fields", e);
        }
    }

    private final BlockStateModelWrapper vanillaWrapper;
    private final PipeDefinition definition;

    // Extracted from vanilla model at construction time
    private final List<BakedQuad> vanillaQuads;
    private final ModelRenderProperties renderProperties;
    // MC 26.1: ItemTransforms removed — transforms accessed differently
    // private final ItemTransforms itemTransforms;
    private final boolean usesBlockLight;
    @SuppressWarnings("unchecked")
    private final Function<ItemStack, RenderType> vanillaRenderType;
    @SuppressWarnings("unchecked")
    private final java.util.function.Supplier<org.joml.Vector3fc[]> extents;

    /** Pre-baked overlay quads per DyeColor. */
    private final Map<DyeColor, List<BakedQuad>> overlayQuadCache = new EnumMap<>(DyeColor.class);

    @SuppressWarnings("unchecked")
    public PipeItemModel(BlockStateModelWrapper vanillaWrapper, PipeDefinition definition) {
        this.vanillaWrapper = vanillaWrapper;
        this.definition = definition;
        try {
            this.vanillaQuads = (List<BakedQuad>) QUADS_FIELD.get(vanillaWrapper);
            this.renderProperties = (ModelRenderProperties) PROPERTIES_FIELD.get(vanillaWrapper);
            // MC 26.1: ItemTransforms removed — access transforms differently
            // this.itemTransforms = renderProperties.transforms();
            this.usesBlockLight = renderProperties.usesBlockLight();
            this.vanillaRenderType = (Function<ItemStack, RenderType>) RENDER_TYPE_FIELD.get(vanillaWrapper);
            this.extents = (java.util.function.Supplier<org.joml.Vector3fc[]>) EXTENTS_FIELD.get(vanillaWrapper);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to read BlockStateModelWrapper fields", e);
        }
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        DyeColor colour = stack.get(BCTransportItems.PIPE_COLOUR.get());
        if (colour == null) {
            // No paint — use vanilla model as-is (gets its own model identity)
            // MC 26.1: BlockStateModelWrapper.update() changed signature entirely
            // Cannot delegate to vanilla wrapper for item rendering.
            // TODO: Implement proper fallback using new ItemModel API.
            return;
        }

        // For painted pipes: use OUR identity (not vanillaWrapper's)
        // so the GUI cache doesn't confuse painted and unpainted versions
        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(colour);

        // === Layer 1: Base pipe quads (from vanilla model) ===
        var baseLayer = renderState.newLayer();
        baseLayer.prepareQuadList().addAll(vanillaQuads);
        baseLayer.setExtents(extents);  // Required for ground item rendering (culling bounds)
        // MC 26.1: setRenderType() and applyToLayer() removed from LayerRenderState
        // Render type is now determined automatically by the pipeline.

        // === Layer 2: Translucent colour overlay (matching in-world paint rendering) ===
        List<BakedQuad> overlayQuads = overlayQuadCache.computeIfAbsent(colour, this::generateOverlayQuads);
        if (!overlayQuads.isEmpty()) {
            var overlayLayer = renderState.newLayer();
            overlayLayer.prepareQuadList().addAll(overlayQuads);

            // MC 26.1: setRenderType() and applyToLayer() removed from LayerRenderState
            // overlayLayer.setRenderType(Sheets.translucentBlockItemSheet());
            // renderProperties.applyToLayer(overlayLayer, displayContext);
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

        List<BakedQuad> quads = new ArrayList<>();

        if (definition.flowType == buildcraft.api.transport.pipe.PipeApi.flowFluids) {
            // Fluid pipes: use mask sprites — opaque ONLY at frame, transparent at glass.
            // This paints the frame without tinting the glass ("waterproofing pixels").
            TextureAtlasSprite[] maskArray = PipeBaseModelGenStandard.ensureMaskSprites(definition);
            TextureAtlasSprite maskSprite = maskArray != null && maskArray.length > 0
                    ? maskArray[0] : null;
            if (maskSprite == null || maskSprite == buildcraft.lib.misc.SpriteUtil.missingSprite()) {
                return List.of();
            }

            // Per-cube UV mappings matching pipe_item.json exactly:
            //   Bottom cap side faces: uv [4,12,12,16]
            //   Center body side faces: uv [4,4,12,12]
            //   Top cap side faces: uv [4,0,12,4]
            //   Top/bottom faces: full face (default 0->1 mapped by createFace)
            UvFaceData bottomSideUvs = UvFaceData.from16(4, 12, 12, 16);
            UvFaceData centerUvs     = UvFaceData.from16(4, 4, 12, 12);
            UvFaceData topSideUvs    = UvFaceData.from16(4, 0, 12, 4);
            UvFaceData capFaceUvs    = UvFaceData.from16(4, 4, 12, 12);

            // Bottom cap — DOWN face + side faces with bottom UVs
            addMaskFaces(quads, maskSprite, dyeColour,
                    new Vector3f(0.5f, 0.125f, 0.5f),
                    new Vector3f(0.25f, 0.125f, 0.25f),
                    new Direction[]{ Direction.DOWN },
                    capFaceUvs);
            addMaskFaces(quads, maskSprite, dyeColour,
                    new Vector3f(0.5f, 0.125f, 0.5f),
                    new Vector3f(0.25f, 0.125f, 0.25f),
                    new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST },
                    bottomSideUvs);

            // Center body — side faces only with center UVs
            addMaskFaces(quads, maskSprite, dyeColour,
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Vector3f(0.25f, 0.25f, 0.25f),
                    new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST },
                    centerUvs);

            // Top cap — UP face + side faces with top UVs
            addMaskFaces(quads, maskSprite, dyeColour,
                    new Vector3f(0.5f, 0.875f, 0.5f),
                    new Vector3f(0.25f, 0.125f, 0.25f),
                    new Direction[]{ Direction.UP },
                    capFaceUvs);
            addMaskFaces(quads, maskSprite, dyeColour,
                    new Vector3f(0.5f, 0.875f, 0.5f),
                    new Vector3f(0.25f, 0.125f, 0.25f),
                    new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST },
                    topSideUvs);
        } else {
            // Item/kinesis/other pipes: use PIPE_COLOUR sprite with translucent overlay
            TextureAtlasSprite sprite = BCTransportSprites.PIPE_COLOUR.getSprite();
            if (sprite == null) {
                return List.of();
            }

            // Center UVs for all cubes (only semi-transparent area is visible)
            UvFaceData uvs = UvFaceData.from16(4, 4, 12, 12);

            // Bottom cap
            addColouredFaces(quads, sprite, dyeColour, uvs,
                    new Vector3f(0.5f, 0.125f, 0.5f),
                    new Vector3f(0.25f, 0.125f, 0.25f),
                    new Direction[]{ Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST });
            // Center body
            addColouredFaces(quads, sprite, dyeColour, uvs,
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Vector3f(0.25f, 0.25f, 0.25f),
                    new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST });
            // Top cap
            addColouredFaces(quads, sprite, dyeColour, uvs,
                    new Vector3f(0.5f, 0.875f, 0.5f),
                    new Vector3f(0.25f, 0.125f, 0.25f),
                    new Direction[]{ Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST });
        }

        return quads;
    }

    /**
     * Add double-faced translucent overlay quads, offset INWARD to sit just behind
     * the base pipe geometry (matching QUADS_COLOURED in PipeBaseModelGenStandard).
     * Works for non-fluid pipes where the translucent colour shows through the pipe.
     */
    private static void addColouredFaces(List<BakedQuad> quads, TextureAtlasSprite sprite,
                                          int dyeColour, UvFaceData uvs,
                                          Vector3f center, Vector3f radius, Direction[] faces) {
        for (Direction face : faces) {
            MutableQuad[] pair = ModelUtil.createDoubleFace(face, center, radius, uvs);
            for (MutableQuad quad : pair) {
                // Inward offset — translucent overlay sits behind base pipe geometry
                net.minecraft.world.phys.Vec3 offset = net.minecraft.world.phys.Vec3.atLowerCornerOf(
                        face.getOpposite().getUnitVec3i()).scale(COLOUR_OFFSET);
                quad.translatevd(offset);
                quad.setSprite(sprite);
                quad.texFromSprite(sprite);
                quad.colouri(dyeColour);
                quads.add(quad.toBakedBlock());
            }
        }
    }

    /**
     * Add single-faced mask overlay quads, offset OUTWARD to render in front of
     * the base pipe geometry. Used for fluid pipes where the mask texture controls
     * which areas (frame only) get painted.
     */
    private static void addMaskFaces(List<BakedQuad> quads, TextureAtlasSprite maskSprite,
                                      int dyeColour, Vector3f center, Vector3f radius,
                                      Direction[] faces, UvFaceData uvs) {
        for (Direction face : faces) {
            MutableQuad quad = ModelUtil.createFace(face, center, radius, uvs);
            // Outward offset — mask must render in front of pipe to be visible
            net.minecraft.world.phys.Vec3 offset = net.minecraft.world.phys.Vec3.atLowerCornerOf(
                    face.getUnitVec3i()).scale(COLOUR_OFFSET);
            quad.translatevd(offset);
            quad.setSprite(maskSprite);
            quad.texFromSprite(maskSprite);
            quad.colouri(dyeColour);
            quads.add(quad.toBakedBlock());
        }
    }
}
