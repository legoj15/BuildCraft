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
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
import net.neoforged.neoforge.client.RenderTypeHelper;

/**
 * A dynamic ItemModel for pipe items that wraps the vanilla JSON-baked model
 * and adds colour overlay quads when the item carries a PIPE_COLOUR data component.
 *
 * <p>The overlay uses simple 3-cube geometry matching pipe_item.json (bottom cap,
 * center body, top cap) with the appropriate overlay texture for the pipe's
 * colour type (border or translucent), slightly offset to avoid Z-fighting.
 */
public class PipeItemModel implements ItemModel {
    private static final RenderTypeGroup TRANSLUCENT_RENDER_TYPES =
            new RenderTypeGroup(ChunkSectionLayer.TRANSLUCENT, NeoForgeRenderTypes::getUnsortedTranslucent);

    /** Slight outward offset (in block-space units, i.e. 1/16 pixels) to avoid Z-fighting. */
    private static final float OFFSET = 0.002f;

    private final ItemModel vanillaModel;
    private final PipeDefinition definition;
    private final ModelRenderProperties vanillaRenderProps;
    /** Cache of composed models per DyeColor. */
    private final Map<DyeColor, ItemModel> cache = new EnumMap<>(DyeColor.class);

    public PipeItemModel(ItemModel vanillaModel, PipeDefinition definition, ModelRenderProperties vanillaRenderProps) {
        this.vanillaModel = vanillaModel;
        this.definition = definition;
        this.vanillaRenderProps = vanillaRenderProps;
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        DyeColor colour = stack.get(BCTransportItems.PIPE_COLOUR.get());
        if (colour == null) {
            vanillaModel.update(renderState, stack, modelResolver, displayContext, level, owner, seed);
            return;
        }
        cache.computeIfAbsent(colour, this::buildColouredModel)
                .update(renderState, stack, modelResolver, displayContext, level, owner, seed);
    }

    private ItemModel buildColouredModel(DyeColor colour) {
        List<BakedQuad> overlayQuads = generateOverlayQuads(colour);

        if (overlayQuads.isEmpty()) {
            return vanillaModel;
        }

        // Use the same render properties as the vanilla model to ensure matching transforms
        var renderType = RenderTypeHelper.detectItemModelRenderType(overlayQuads, TRANSLUCENT_RENDER_TYPES);

        ItemModel overlayModel = new BlockModelWrapper(
                List.of(PipeColourTintSource.INSTANCE),
                overlayQuads,
                vanillaRenderProps,
                renderType
        );

        return new CompositeModel(List.of(vanillaModel, overlayModel));
    }

    /**
     * Generate overlay quads matching the pipe_item.json 3-cube geometry
     * (bottom cap, center body, top cap), with the overlay texture and dye colour
     * baked into vertex colour.
     */
    private List<BakedQuad> generateOverlayQuads(DyeColor colour) {
        // Select overlay texture based on pipe colour type
        TextureAtlasSprite overlaySprite;
        EnumPipeColourType colourType = definition.getColourType();
        switch (colourType) {
            case BORDER_OUTER:
                overlaySprite = BCTransportSprites.PIPE_COLOUR_BORDER_OUTER.getSprite();
                break;
            case BORDER_INNER:
                overlaySprite = BCTransportSprites.PIPE_COLOUR_BORDER_INNER.getSprite();
                break;
            case TRANSLUCENT:
            default:
                overlaySprite = BCTransportSprites.PIPE_COLOUR.getSprite();
                break;
        }

        // Get dye colour for vertex tinting (RGB 0-255)
        int colourHex = ColourUtil.getLightHex(colour);
        float r = ((colourHex >> 16) & 0xFF) / 255f;
        float g = ((colourHex >> 8) & 0xFF) / 255f;
        float b = (colourHex & 0xFF) / 255f;

        List<BakedQuad> quads = new ArrayList<>();

        // pipe_item.json geometry (in block-space, 0..1):
        //   Bottom cap: [4,0,4] to [12,4,12]  → center=0.5,0.125,0.5 radius=0.25,0.125,0.25
        //   Center body: [4,4,4] to [12,12,12] → center=0.5,0.5,0.5 radius=0.25,0.25,0.25
        //   Top cap: [4,12,4] to [12,16,12]   → center=0.5,0.875,0.5 radius=0.25,0.125,0.25

        // Bottom cap — all faces except UP (it's internal to the pipe body)
        addBoxFaces(quads, overlaySprite, r, g, b,
                new Vector3f(0.5f, 0.125f, 0.5f),
                new Vector3f(0.25f + OFFSET, 0.125f + OFFSET, 0.25f + OFFSET),
                new Direction[]{ Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST },
                // UV mapping matching pipe_item.json: down=full face, sides=[4,12,12,16]
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
    private static void addBoxFaces(List<BakedQuad> quads, TextureAtlasSprite sprite,
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
