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

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.pipe.EnumPipeColourType;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeFaceTex;

import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.SpriteUtil;

import buildcraft.transport.BCTransportItems;
import buildcraft.transport.client.PipeColourTintSource;
import buildcraft.transport.client.model.PipeModelCacheBase.PipeBaseCutoutKey;
import buildcraft.transport.client.model.key.PipeModelKey;

import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.RenderTypeHelper;

/**
 * A dynamic ItemModel for pipe items that wraps the vanilla JSON-baked model
 * and adds colour overlay quads when the item carries a PIPE_COLOUR data component.
 *
 * <p>The overlay quads are generated using the same {@link PipeBaseModelGenStandard}
 * logic used for in-world rendering, ensuring the item appearance matches the
 * placed block for each pipe type (border overlay for item pipes, mask overlay
 * for fluid/power pipes).
 *
 * <p>Follows the same pattern as NeoForge's {@code DynamicFluidContainerModel}.
 */
public class PipeItemModel implements ItemModel {
    private static final RenderTypeGroup TRANSLUCENT_RENDER_TYPES =
            new RenderTypeGroup(ChunkSectionLayer.TRANSLUCENT, NeoForgeRenderTypes::getUnsortedTranslucent);

    private final ItemModel vanillaModel;
    private final PipeDefinition definition;
    private final ItemTransforms itemTransforms;
    /** Cache of composed models per DyeColor. null key = unpainted (uses vanilla model directly). */
    private final Map<DyeColor, ItemModel> cache = new EnumMap<>(DyeColor.class);

    public PipeItemModel(ItemModel vanillaModel, PipeDefinition definition, ItemTransforms itemTransforms) {
        this.vanillaModel = vanillaModel;
        this.definition = definition;
        this.itemTransforms = itemTransforms;
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        DyeColor colour = stack.get(BCTransportItems.PIPE_COLOUR.get());
        if (colour == null) {
            // No paint — use vanilla model as-is
            vanillaModel.update(renderState, stack, modelResolver, displayContext, level, owner, seed);
            return;
        }
        // Get or create the composed coloured model
        cache.computeIfAbsent(colour, this::buildColouredModel)
                .update(renderState, stack, modelResolver, displayContext, level, owner, seed);
    }

    /**
     * Build a composed model for the given dye colour:
     * 1) Base layer = vanilla pipe model (cutout)
     * 2) Overlay layer = colour overlay quads from PipeBaseModelGenStandard (translucent, tinted)
     */
    private ItemModel buildColouredModel(DyeColor colour) {
        // Build a PipeModelKey for an item (no connections, center body only)
        PipeFaceTex centerTex = PipeFaceTex.get(0);
        PipeFaceTex[] sideTex = { centerTex, centerTex, centerTex, centerTex, centerTex, centerTex };
        float[] noConnections = { 0, 0, 0, 0, 0, 0 };
        PipeModelKey modelKey = new PipeModelKey(definition, centerTex, sideTex, noConnections, colour);

        // Generate overlay quads based on colour type
        EnumPipeColourType colourType = definition.getColourType();
        List<BakedQuad> overlayQuads = generateOverlayQuads(modelKey, colourType);

        if (overlayQuads.isEmpty()) {
            // No overlay possible — just use vanilla
            return vanillaModel;
        }

        // Build the overlay layer as a BlockModelWrapper with tint source
        TextureAtlasSprite particle = overlayQuads.get(0).sprite();
        ModelRenderProperties renderProps = new ModelRenderProperties(false, particle, itemTransforms);
        var renderType = RenderTypeHelper.detectItemModelRenderType(overlayQuads, TRANSLUCENT_RENDER_TYPES);

        ItemModel overlayModel = new BlockModelWrapper(
                List.of(PipeColourTintSource.INSTANCE),
                overlayQuads,
                renderProps,
                renderType
        );

        // Compose: vanilla base + colour overlay
        return new CompositeModel(List.of(vanillaModel, overlayModel));
    }

    /**
     * Generate overlay quads matching the world model for the given colour type.
     */
    private List<BakedQuad> generateOverlayQuads(PipeModelKey modelKey, EnumPipeColourType colourType) {
        PipeBaseCutoutKey key = new PipeBaseCutoutKey(modelKey);

        switch (colourType) {
            case BORDER_OUTER:
            case BORDER_INNER: {
                // Border types: generate cutout quads which include border sprite
                // The cutout generator already adds border sprite quads when colour is set
                List<MutableQuad> mutableQuads = PipeBaseModelGenStandard.INSTANCE.generateCutoutMutable(key);
                // Filter to only the border quads (the ones tinted with the dye colour)
                // Actually, just return the full cutout quads — the colour is baked into vertex colour
                List<BakedQuad> result = new ArrayList<>();
                for (MutableQuad q : mutableQuads) {
                    result.add(q.toBakedBlock());
                }
                return result;
            }
            case TRANSLUCENT: {
                // Mask-based: generate translucent overlay quads
                List<MutableQuad> mutableQuads = PipeBaseModelGenStandard.INSTANCE.generateMaskMutable(key, 76);
                if (mutableQuads.isEmpty()) {
                    // Fall back to standard translucent overlay
                    PipeModelCacheBase.PipeBaseTranslucentKey transKey =
                            new PipeModelCacheBase.PipeBaseTranslucentKey(modelKey);
                    mutableQuads = PipeBaseModelGenStandard.INSTANCE.generateTranslucentMutable(transKey);
                }
                List<BakedQuad> result = new ArrayList<>();
                for (MutableQuad q : mutableQuads) {
                    result.add(q.toBakedBlock());
                }
                return result;
            }
            default:
                return List.of();
        }
    }
}
