/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
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
 * <p>The overlay is added by directly manipulating the ItemStackRenderState after
 * the vanilla model's update() call, adding a new layer with the overlay quads.
 * This avoids atlas-mismatch issues (vanilla uses items atlas, overlay uses blocks atlas)
 * because each layer binds its own render type independently.
 */
public class PipeItemModel implements ItemModel {
    /** Slight outward offset (in block-space units) to avoid Z-fighting. */
    private static final float OFFSET = 0.002f;

    private final BlockModelWrapper vanillaWrapper;
    private final PipeDefinition definition;

    /** Pre-baked overlay quads per DyeColor. */
    private final Map<DyeColor, List<BakedQuad>> overlayQuadCache = new EnumMap<>(DyeColor.class);

    public PipeItemModel(BlockModelWrapper vanillaWrapper, PipeDefinition definition) {
        this.vanillaWrapper = vanillaWrapper;
        this.definition = definition;
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        // Always render the vanilla base model first
        vanillaWrapper.update(renderState, stack, modelResolver, displayContext, level, owner, seed);

        // If painted, add overlay layer directly to the render state
        DyeColor colour = stack.get(BCTransportItems.PIPE_COLOUR.get());
        if (colour == null) {
            return;
        }

        List<BakedQuad> overlayQuads = overlayQuadCache.computeIfAbsent(colour, this::generateOverlayQuads);
        if (overlayQuads.isEmpty()) {
            return;
        }

        // Add a new layer directly to the render state for the overlay
        var overlayLayer = renderState.newLayer();

        // Copy quads to the layer
        overlayLayer.prepareQuadList().addAll(overlayQuads);

        // Set up tint colours
        int tintCount = 1; // we use tintIndex 0
        int[] tintLayers = overlayLayer.prepareTintLayers(tintCount);
        // Apply the dye colour as the tint
        int colourHex = ColourUtil.getLightHex(colour);
        // ARGB format: 0xAARRGGBB — full alpha
        tintLayers[0] = 0xFF000000 | colourHex;

        // Detect the correct render type for blocks-atlas overlay quads
        var renderTypeGetter = net.neoforged.neoforge.client.RenderTypeHelper
                .detectItemModelRenderType(overlayQuads,
                        new net.neoforged.neoforge.client.RenderTypeGroup(
                                net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT,
                                net.neoforged.neoforge.client.NeoForgeRenderTypes::getUnsortedTranslucent));
        overlayLayer.setRenderType(renderTypeGetter.apply(stack));

        // Use block lighting to match the vanilla layer
        overlayLayer.setUsesBlockLight(true);
    }

    /**
     * Generate overlay quads matching the pipe_item.json 3-cube geometry
     * (bottom cap, center body, top cap), with the overlay texture.
     * Vertex colour is set to white (tinting applied via tintLayers at render time).
     */
    private List<BakedQuad> generateOverlayQuads(DyeColor colour) {
        var overlaySprite = switch (definition.getColourType()) {
            case BORDER_OUTER -> BCTransportSprites.PIPE_COLOUR_BORDER_OUTER.getSprite();
            case BORDER_INNER -> BCTransportSprites.PIPE_COLOUR_BORDER_INNER.getSprite();
            default -> BCTransportSprites.PIPE_COLOUR.getSprite();
        };

        List<BakedQuad> quads = new ArrayList<>();

        // pipe_item.json geometry (block-space 0..1):
        //   Bottom cap: [4,0,4]-[12,4,12]   → center=0.5,0.125,0.5  radius=0.25,0.125,0.25
        //   Center:     [4,4,4]-[12,12,12]  → center=0.5,0.5,0.5    radius=0.25,0.25,0.25
        //   Top cap:    [4,12,4]-[12,16,12] → center=0.5,0.875,0.5  radius=0.25,0.125,0.25

        // Bottom cap — all faces except UP (internal)
        addBoxFaces(quads, overlaySprite,
                new Vector3f(0.5f, 0.125f, 0.5f),
                new Vector3f(0.25f + OFFSET, 0.125f + OFFSET, 0.25f + OFFSET),
                new Direction[]{ Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST },
                new UvFaceData[]{ null, UvFaceData.from16(4, 12, 12, 16), UvFaceData.from16(4, 12, 12, 16),
                        UvFaceData.from16(4, 12, 12, 16), UvFaceData.from16(4, 12, 12, 16) });

        // Center body — only side faces (top/bottom are internal)
        addBoxFaces(quads, overlaySprite,
                new Vector3f(0.5f, 0.5f, 0.5f),
                new Vector3f(0.25f + OFFSET, 0.25f + OFFSET, 0.25f + OFFSET),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST },
                new UvFaceData[]{ UvFaceData.from16(4, 4, 12, 12), UvFaceData.from16(4, 4, 12, 12),
                        UvFaceData.from16(4, 4, 12, 12), UvFaceData.from16(4, 4, 12, 12) });

        // Top cap — all faces except DOWN (internal)
        addBoxFaces(quads, overlaySprite,
                new Vector3f(0.5f, 0.875f, 0.5f),
                new Vector3f(0.25f + OFFSET, 0.125f + OFFSET, 0.25f + OFFSET),
                new Direction[]{ Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST },
                new UvFaceData[]{ null, UvFaceData.from16(4, 0, 12, 4), UvFaceData.from16(4, 0, 12, 4),
                        UvFaceData.from16(4, 0, 12, 4), UvFaceData.from16(4, 0, 12, 4) });

        return quads;
    }

    /**
     * Add quads for selected faces of a box, with the overlay texture.
     * Vertex colour is white; actual tinting is applied via tintLayers.
     */
    private static void addBoxFaces(List<BakedQuad> quads,
                                     net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
                                     Vector3f center, Vector3f radius,
                                     Direction[] faces, UvFaceData[] uvs) {
        for (int i = 0; i < faces.length; i++) {
            UvFaceData uv = uvs[i];
            if (uv == null) {
                uv = new UvFaceData(0, 0, 1, 1);
            }
            MutableQuad quad = ModelUtil.createFace(faces[i], center, radius, uv);
            quad.setTint(0); // tintIndex 0 for tintLayers colouring
            quad.setSprite(sprite);
            quad.texFromSprite(sprite);
            // White vertex colour — tinting happens via tintLayers in update()
            quad.colourf(1.0f, 1.0f, 1.0f, 1.0f);
            quads.add(quad.toBakedBlock());
        }
    }
}
