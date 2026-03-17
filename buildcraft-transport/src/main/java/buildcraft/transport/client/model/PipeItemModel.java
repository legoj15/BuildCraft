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
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderType;
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

import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.RenderTypeHelper;

/**
 * A dynamic ItemModel for pipe items that wraps the vanilla JSON-baked model
 * and adds colour overlay quads when the item carries a PIPE_COLOUR data component.
 *
 * <p>For painted pipes, both vanilla quads and overlay quads are added as direct
 * render state layers (bypassing BlockModelWrapper.update) to avoid sharing
 * model identity with unpainted pipes (which would corrupt the GUI render cache).
 */
public class PipeItemModel implements ItemModel {
    private static final RenderTypeGroup TRANSLUCENT_RENDER_TYPES =
            new RenderTypeGroup(ChunkSectionLayer.TRANSLUCENT, NeoForgeRenderTypes::getUnsortedTranslucent);

    /** Slight outward offset (in block-space units) to avoid Z-fighting. */
    private static final float OFFSET = 0.002f;


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

    /** Pre-baked overlay quads per DyeColor (colour baked into vertex data). */
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

        // === Layer 2: Colour overlay (semi-transparent, tinted) ===
        List<BakedQuad> overlayQuads = overlayQuadCache.computeIfAbsent(colour, this::generateOverlayQuads);
        if (!overlayQuads.isEmpty()) {
            var overlayLayer = renderState.newLayer();
            overlayLayer.prepareQuadList().addAll(overlayQuads);

            // Render type for blocks-atlas overlay
            var overlayRenderType = RenderTypeHelper.detectItemModelRenderType(
                    overlayQuads, TRANSLUCENT_RENDER_TYPES);
            overlayLayer.setRenderType(overlayRenderType.apply(stack));
            overlayLayer.setUsesBlockLight(usesBlockLight);
            overlayLayer.setTransform(itemTransforms.getTransform(displayContext));
        }
    }

    /**
     * Generate overlay quads matching the pipe_item.json 3-cube geometry
     * (bottom cap, center body, top cap), with the overlay texture and
     * dye colour baked into the vertex colour at semi-transparent alpha.
     */
    private List<BakedQuad> generateOverlayQuads(DyeColor colour) {
        var overlaySprite = switch (definition.getColourType()) {
            case BORDER_OUTER -> BCTransportSprites.PIPE_COLOUR_BORDER_OUTER.getSprite();
            case BORDER_INNER -> BCTransportSprites.PIPE_COLOUR_BORDER_INNER.getSprite();
            default -> BCTransportSprites.PIPE_COLOUR.getSprite();
        };

        // Dye colour for vertex colour
        int colourHex = ColourUtil.getLightHex(colour);
        float r = ((colourHex >> 16) & 0xFF) / 255f;
        float g = ((colourHex >> 8) & 0xFF) / 255f;
        float b = (colourHex & 0xFF) / 255f;

        List<BakedQuad> quads = new ArrayList<>();

        // pipe_item.json geometry (block-space 0..1):
        //   Bottom cap: [4,0,4]-[12,4,12]   → center=0.5,0.125,0.5  radius=0.25,0.125,0.25
        //   Center:     [4,4,4]-[12,12,12]  → center=0.5,0.5,0.5    radius=0.25,0.25,0.25
        //   Top cap:    [4,12,4]-[12,16,12] → center=0.5,0.875,0.5  radius=0.25,0.125,0.25

        // Bottom cap — all faces except UP (internal)
        addBoxFaces(quads, overlaySprite, r, g, b,
                new Vector3f(0.5f, 0.125f, 0.5f),
                new Vector3f(0.25f + OFFSET, 0.125f + OFFSET, 0.25f + OFFSET),
                new Direction[]{ Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST },
                new UvFaceData[]{ null, UvFaceData.from16(4, 12, 12, 16), UvFaceData.from16(4, 12, 12, 16),
                        UvFaceData.from16(4, 12, 12, 16), UvFaceData.from16(4, 12, 12, 16) });

        // Center body — only side faces (top/bottom are internal)
        addBoxFaces(quads, overlaySprite, r, g, b,
                new Vector3f(0.5f, 0.5f, 0.5f),
                new Vector3f(0.25f + OFFSET, 0.25f + OFFSET, 0.25f + OFFSET),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST },
                new UvFaceData[]{ UvFaceData.from16(4, 4, 12, 12), UvFaceData.from16(4, 4, 12, 12),
                        UvFaceData.from16(4, 4, 12, 12), UvFaceData.from16(4, 4, 12, 12) });

        // Top cap — all faces except DOWN (internal)
        addBoxFaces(quads, overlaySprite, r, g, b,
                new Vector3f(0.5f, 0.875f, 0.5f),
                new Vector3f(0.25f + OFFSET, 0.125f + OFFSET, 0.25f + OFFSET),
                new Direction[]{ Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST },
                new UvFaceData[]{ null, UvFaceData.from16(4, 0, 12, 4), UvFaceData.from16(4, 0, 12, 4),
                        UvFaceData.from16(4, 0, 12, 4), UvFaceData.from16(4, 0, 12, 4) });

        return quads;
    }

    /**
     * Add quads for selected faces of a box, with the overlay texture
     * and dye colour baked into vertex colour at semi-transparent alpha.
     */
    private static void addBoxFaces(List<BakedQuad> quads,
                                     net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
                                     float r, float g, float b,
                                     Vector3f center, Vector3f radius,
                                     Direction[] faces, UvFaceData[] uvs) {
        for (int i = 0; i < faces.length; i++) {
            UvFaceData uv = uvs[i];
            if (uv == null) {
                uv = new UvFaceData(0, 0, 1, 1);
            }
            MutableQuad quad = ModelUtil.createFace(faces[i], center, radius, uv);
            quad.setSprite(sprite);
            quad.texFromSprite(sprite);
            // Bake dye colour — texture alpha handles cutout transparency
            quad.colourf(r, g, b, 1.0f);
            quads.add(quad.toBakedBlock());
        }
    }
}
