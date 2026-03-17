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

import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.pipe.EnumPipeColourType;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.ModelUtil.UvFaceData;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.ColourUtil;

import buildcraft.transport.BCTransportItems;
import buildcraft.transport.BCTransportSprites;
import buildcraft.transport.client.PipeColourTintSource;

import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.RenderTypeGroup;

/**
 * A dynamic ItemModel for pipe items that wraps the vanilla JSON-baked model
 * and adds colour overlay quads when the item carries a PIPE_COLOUR data component.
 *
 * <p>For painted pipes, the vanilla quads and overlay quads are merged into a single
 * BlockModelWrapper with the same render properties as the vanilla model.
 */
public class PipeItemModel implements ItemModel {
    private static final RenderTypeGroup TRANSLUCENT_RENDER_TYPES =
            new RenderTypeGroup(ChunkSectionLayer.TRANSLUCENT, NeoForgeRenderTypes::getUnsortedTranslucent);

    /** Slight outward offset (in block-space units) to avoid Z-fighting. */
    private static final float OFFSET = 0.002f;

    // Reflection fields cached at class-load time
    private static final Field QUADS_FIELD;
    private static final Field PROPERTIES_FIELD;
    private static final Field TINTS_FIELD;
    static {
        try {
            QUADS_FIELD = BlockModelWrapper.class.getDeclaredField("quads");
            QUADS_FIELD.setAccessible(true);
            PROPERTIES_FIELD = BlockModelWrapper.class.getDeclaredField("properties");
            PROPERTIES_FIELD.setAccessible(true);
            TINTS_FIELD = BlockModelWrapper.class.getDeclaredField("tints");
            TINTS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access BlockModelWrapper fields", e);
        }
    }

    private final BlockModelWrapper vanillaWrapper;
    private final PipeDefinition definition;
    private final List<BakedQuad> vanillaQuads;
    private final ModelRenderProperties vanillaRenderProps;

    /** Cache of merged models per DyeColor. */
    private final Map<DyeColor, ItemModel> mergedCache = new EnumMap<>(DyeColor.class);

    @SuppressWarnings("unchecked")
    public PipeItemModel(BlockModelWrapper vanillaWrapper, PipeDefinition definition) {
        this.vanillaWrapper = vanillaWrapper;
        this.definition = definition;
        try {
            this.vanillaQuads = (List<BakedQuad>) QUADS_FIELD.get(vanillaWrapper);
            this.vanillaRenderProps = (ModelRenderProperties) PROPERTIES_FIELD.get(vanillaWrapper);
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
            // No paint — use vanilla model as-is
            vanillaWrapper.update(renderState, stack, modelResolver, displayContext, level, owner, seed);
            return;
        }
        // Use merged model (vanilla quads + overlay quads in one BlockModelWrapper)
        mergedCache.computeIfAbsent(colour, this::buildMergedModel)
                .update(renderState, stack, modelResolver, displayContext, level, owner, seed);
    }

    /**
     * Build a single BlockModelWrapper containing both the vanilla quads
     * AND the overlay quads for the given dye colour.
     */
    private ItemModel buildMergedModel(DyeColor colour) {
        List<BakedQuad> overlayQuads = generateOverlayQuads(colour);

        // Merge vanilla + overlay quads into a single list
        List<BakedQuad> mergedQuads = new ArrayList<>(vanillaQuads.size() + overlayQuads.size());
        mergedQuads.addAll(vanillaQuads);
        mergedQuads.addAll(overlayQuads);

        // Vanilla quads have tintIndex=-1 (no tint), overlay quads have tintIndex=0.
        // PipeColourTintSource at index 0 will only affect tintIndex=0 quads.
        var renderType = net.neoforged.neoforge.client.RenderTypeHelper
                .detectItemModelRenderType(mergedQuads, TRANSLUCENT_RENDER_TYPES);
        return new BlockModelWrapper(
                List.of(PipeColourTintSource.INSTANCE),
                mergedQuads,
                vanillaRenderProps,
                renderType
        );
    }

    /**
     * Generate overlay quads matching the pipe_item.json 3-cube geometry
     * (bottom cap, center body, top cap), with the overlay texture and dye colour
     * baked into vertex colour.
     */
    private List<BakedQuad> generateOverlayQuads(DyeColor colour) {
        // Select overlay texture based on pipe colour type
        var overlaySprite = switch (definition.getColourType()) {
            case BORDER_OUTER -> BCTransportSprites.PIPE_COLOUR_BORDER_OUTER.getSprite();
            case BORDER_INNER -> BCTransportSprites.PIPE_COLOUR_BORDER_INNER.getSprite();
            default -> BCTransportSprites.PIPE_COLOUR.getSprite();
        };

        // Get dye colour for vertex tinting (RGB 0-255)
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
     * Add quads for selected faces of a box, with colour tinting and the overlay texture.
     */
    private static void addBoxFaces(List<BakedQuad> quads,
                                     net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
                                     float r, float g, float b,
                                     Vector3f center, Vector3f radius,
                                     Direction[] faces, UvFaceData[] uvs) {
        for (int i = 0; i < faces.length; i++) {
            UvFaceData uv = uvs[i];
            if (uv == null) {
                uv = new UvFaceData(0, 0, 1, 1); // Full face
            }
            MutableQuad quad = ModelUtil.createFace(faces[i], center, radius, uv);
            quad.setTint(0);
            quad.setSprite(sprite);
            quad.texFromSprite(sprite);
            quad.colourf(r, g, b, 1.0f);
            quads.add(quad.toBakedBlock());
        }
    }
}
