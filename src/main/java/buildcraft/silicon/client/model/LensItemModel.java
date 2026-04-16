/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.model.MutableQuad;

import buildcraft.silicon.client.model.plug.PlugBakerLens;
import buildcraft.silicon.item.ItemPluggableLens;

/**
 * Dynamic ItemModel for lens/filter items. Renders the lens/filter as a 3D
 * model using the same geometry as the in-world PlugBakerLens, with correct
 * colour tinting and lens/filter frame distinction.
 *
 * <p>The lens geometry from PlugBakerLens is generated in WEST-facing
 * orientation (thin axis on X). For item rendering we rotate it to
 * NORTH-facing (thin axis on Z, face visible to camera) and scale 1.8×
 * around center, matching 1.12.2's TRANSFORM_PLUG_AS_ITEM_BIGGER.
 */
public class LensItemModel implements ItemModel {

    /** Cache key combining colour + isFilter. */
    private record LensKey(@Nullable DyeColor colour, boolean isFilter) {}

    private static final LoadingCache<LensKey, List<BakedQuad>> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(CacheLoader.from(key -> {
            List<BakedQuad> quads = new ArrayList<>();

            // Bake cutout quads (frame) — set white vertex colors so texture shows unmodified
            List<MutableQuad> cutoutQuads = PlugBakerLens.bakeForItem(key.colour(), key.isFilter(), true);
            for (MutableQuad mq : cutoutQuads) {
                transformForItem(mq, true);
                quads.add(mq.toBakedItem());
            }

            // Bake translucent quads (coloured glass overlay) — use toBakedTranslucent so the
            // quad is routed to the correct render layer and vertex colours are preserved via BakedColors
            List<MutableQuad> translucentQuads = PlugBakerLens.bakeForItem(key.colour(), key.isFilter(), false);
            for (MutableQuad mq : translucentQuads) {
                transformForItem(mq, false);
                quads.add(mq.toBakedTranslucent());
            }

            return quads;
        }));

    /**
     * Transforms a MutableQuad from WEST-facing in-world orientation to
     * NORTH-facing item orientation, scaled and lit for inventory display.
     *
     * @param resetColors true for cutout quads (white vertex colors so texture is unmodified),
     *                    false for translucent quads (preserve vertex colors set by tinting)
     */
    private static void transformForItem(MutableQuad mq, boolean resetColors) {
        mq.setShade(false);
        // Rotate from WEST → NORTH so the lens face points at the camera
        mq.rotate(Direction.WEST, Direction.NORTH, 0.5f, 0.5f, 0.5f);
        // Scale 1.8x around center, matching 1.12.2's TRANSFORM_PLUG_AS_ITEM_BIGGER
        mq.translatef(-0.5f, -0.5f, -0.5f);
        mq.scalef(1.8f);
        mq.translatef(0.5f, 0.5f, 0.5f);
        
        // Set full white vertex colours only for cutout (frame) quads.
        // Translucent quads keep the dye color set by bakeTranslucentQuads.
        if (resetColors) {
            mq.vertex_0.colouri(255, 255, 255, 255);
            mq.vertex_1.colouri(255, 255, 255, 255);
            mq.vertex_2.colouri(255, 255, 255, 255);
            mq.vertex_3.colouri(255, 255, 255, 255);
        }
    }

    public LensItemModel() {}

    public static void onModelBake() {
        cache.invalidateAll();
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        DyeColor colour = ItemPluggableLens.getColour(stack);
        boolean isFilter = ItemPluggableLens.isFilter(stack);
        LensKey key = new LensKey(colour, isFilter);

        List<BakedQuad> quads = cache.getUnchecked(key);
        if (quads.isEmpty()) {
            return;
        }

        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(key);

        var layer = renderState.newLayer();
        layer.prepareQuadList().addAll(quads);
    }
}
