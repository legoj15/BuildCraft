/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.jspecify.annotations.Nullable;

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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.model.MutableQuad;

import buildcraft.silicon.client.model.key.KeyPlugFacade;
import buildcraft.silicon.client.model.plug.PlugBakerFacade;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadePhasedState;

/**
 * Dynamic ItemModel for facade items. Renders the facade as a thin slab
 * showing the block texture, using the same geometry as the in-world baker.
 */
public class FacadeItemModel implements ItemModel {

    private final BlockModelWrapper vanillaWrapper;

    // Extracted from vanilla model at construction time
    private final List<BakedQuad> vanillaQuads;
    private final ModelRenderProperties renderProperties;

    // Cache for hand/3rd-person rendering (EAST facing, no offset)
    private static final LoadingCache<KeyPlugFacade, List<BakedQuad>> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(CacheLoader.from(key -> {
            List<BakedQuad> quads = new ArrayList<>();
            for (MutableQuad quad : PlugBakerFacade.INSTANCE.bakeForKey(key)) {
                quads.add(quad.toBakedItem());
            }
            return quads;
        }));

    // Cache for GUI/inventory rendering (NORTH facing, centered in slot)
    private static final LoadingCache<KeyPlugFacade, List<BakedQuad>> guiCache = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(CacheLoader.from(key -> {
            List<BakedQuad> quads = new ArrayList<>();
            // Center the NORTH-facing facade in the block space:
            // facade spans z=0 to z=SIZE/16, center of block is z=0.5
            // offset = (16 - SIZE) / 2 / 16
            float offsetZ = (16 - buildcraft.silicon.plug.PluggableFacade.SIZE) / 2f / 16f;
            for (MutableQuad quad : PlugBakerFacade.INSTANCE.bakeForKey(key)) {
                // Disable directional shading and set full brightness for GUI
                quad.setShade(false);
                quad.vertex_0.translatef(0, 0, offsetZ); quad.vertex_0.lighti(15, 15); quad.vertex_0.colouri(255, 255, 255, 255);
                quad.vertex_1.translatef(0, 0, offsetZ); quad.vertex_1.lighti(15, 15); quad.vertex_1.colouri(255, 255, 255, 255);
                quad.vertex_2.translatef(0, 0, offsetZ); quad.vertex_2.lighti(15, 15); quad.vertex_2.colouri(255, 255, 255, 255);
                quad.vertex_3.translatef(0, 0, offsetZ); quad.vertex_3.lighti(15, 15); quad.vertex_3.colouri(255, 255, 255, 255);
                quads.add(quad.toBakedItem());
            }
            return quads;
        }));

    // Reflection to access BlockModelWrapper internals (same pattern as PipeItemModel)
    private static final java.lang.reflect.Field QUADS_FIELD;
    private static final java.lang.reflect.Field PROPERTIES_FIELD;
    private static final java.lang.reflect.Field RENDER_TYPE_FIELD;
    private static final java.lang.reflect.Field EXTENTS_FIELD;
    static {
        try {
            QUADS_FIELD = BlockModelWrapper.class.getDeclaredField("quads");
            QUADS_FIELD.setAccessible(true);
            PROPERTIES_FIELD = BlockModelWrapper.class.getDeclaredField("properties");
            PROPERTIES_FIELD.setAccessible(true);
            RENDER_TYPE_FIELD = BlockModelWrapper.class.getDeclaredField("renderType");
            RENDER_TYPE_FIELD.setAccessible(true);
            EXTENTS_FIELD = BlockModelWrapper.class.getDeclaredField("extents");
            EXTENTS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access BlockModelWrapper fields", e);
        }
    }

    @SuppressWarnings("unchecked")
    private final java.util.function.Function<ItemStack, RenderType> vanillaRenderType;
    @SuppressWarnings("unchecked")
    private final java.util.function.Supplier<org.joml.Vector3fc[]> extents;

    @SuppressWarnings("unchecked")
    public FacadeItemModel(BlockModelWrapper vanillaWrapper) {
        this.vanillaWrapper = vanillaWrapper;
        try {
            this.vanillaQuads = (List<BakedQuad>) QUADS_FIELD.get(vanillaWrapper);
            this.renderProperties = (ModelRenderProperties) PROPERTIES_FIELD.get(vanillaWrapper);
            this.vanillaRenderType = (java.util.function.Function<ItemStack, RenderType>) RENDER_TYPE_FIELD.get(vanillaWrapper);
            this.extents = (java.util.function.Supplier<org.joml.Vector3fc[]>) EXTENTS_FIELD.get(vanillaWrapper);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to read BlockModelWrapper fields", e);
        }
    }

    public static void onModelBake() {
        cache.invalidateAll();
        guiCache.invalidateAll();
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        FacadeInstance inst = ItemPluggableFacade.getStates(stack);
        FacadePhasedState state = inst.getCurrentStateForStack();

        // GUI: use NORTH facing (visible in standard isometric view) + centered
        // Hand/other: use EAST facing (visible when held)
        List<BakedQuad> quads;
        KeyPlugFacade key;
        if (displayContext == ItemDisplayContext.GUI) {
            key = new KeyPlugFacade("cutout", Direction.NORTH, state.stateInfo.state, inst.isHollow());
            quads = guiCache.getUnchecked(key);
        } else {
            key = new KeyPlugFacade("cutout", Direction.EAST, state.stateInfo.state, inst.isHollow());
            quads = cache.getUnchecked(key);
        }

        if (quads.isEmpty()) {
            // Fallback to the vanilla model if baking produced nothing
            vanillaWrapper.update(renderState, stack, modelResolver, displayContext, level, owner, seed);
            return;
        }

        // Use our identity so the cache differentiates per-block-state
        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(key);

        var layer = renderState.newLayer();
        layer.prepareQuadList().addAll(quads);
        layer.setExtents(extents);
        layer.setRenderType(vanillaRenderType.apply(stack));
        renderProperties.applyToLayer(layer, displayContext);
    }
}

