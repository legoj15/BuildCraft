/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client.model;

import java.util.ArrayList;
import java.util.EnumMap;
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
 * Dynamic ItemModel for lens/filter items. Renders the lens/filter as 3D
 * geometry using PlugBakerLens with per-ItemDisplayContext camera transforms
 * matching 1.12.2's TRANSFORM_PLUG_AS_ITEM_BIGGER (see
 * common/buildcraft/lib/client/model/ModelItemSimple.java on the
 * 8.0.x-1.12.2 branch).
 *
 * <p>PlugBakerLens emits geometry in WEST-facing orientation (thin axis on
 * X). The bake pre-rotates to NORTH-facing so the lens face points at the
 * camera without needing a runtime rotation, then per-context rotate/scale/
 * translate is applied so dropped, in-hand and item-frame views each match
 * what 1.12.2 did.
 */
public class LensItemModel implements ItemModel {

    /** Inner identity for the lens itself: colour + isFilter. */
    private record LensKey(@Nullable DyeColor colour, boolean isFilter) {}

    /**
     * Per-context display transform. Rotations are degrees, translation is in
     * 1/16 world units, scale is a uniform multiplier applied around the
     * model centre (0.5, 0.5, 0.5).
     */
    private record ContextXform(float rotX, float rotY, float rotZ, float tx, float ty, float tz, float scale) {}

    private static final ContextXform DEFAULT_XFORM = new ContextXform(0, 0, 0, 0, 0, 0, 1.8f);

    /**
     * Values from 1.12.2 ModelItemSimple.TRANSFORM_PLUG_AS_ITEM_BIGGER, which
     * is TRANSFORM_PLUG_AS_ITEM with each context's scale multiplied by 1.8.
     * GUI rotation is identity here (not Y=90 as 1.12.2 used) because the
     * base geometry has already been rotated WEST -> NORTH, so the lens face
     * points at the camera without needing 1.12.2's runtime Y rotation.
     */
    private static final EnumMap<ItemDisplayContext, ContextXform> XFORMS = new EnumMap<>(ItemDisplayContext.class);
    static {
        XFORMS.put(ItemDisplayContext.GUI,                     new ContextXform(0,    0, 0,  0,  0f,    0f, 1.8f));
        XFORMS.put(ItemDisplayContext.GROUND,                  new ContextXform(0,    0, 0,  0,  3f,    0f, 0.9f));
        XFORMS.put(ItemDisplayContext.HEAD,                    new ContextXform(0,    0, 0,  0,  0f,    0f, 1.8f));
        XFORMS.put(ItemDisplayContext.FIXED,                   new ContextXform(0,    0, 0,  0,  0f,    0f, 1.53f));
        XFORMS.put(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new ContextXform(0,   45, 0,  0,  0f,   -4f, 0.72f));
        XFORMS.put(ItemDisplayContext.FIRST_PERSON_LEFT_HAND,  new ContextXform(0,  225, 0,  0,  0f,   -4f, 0.72f));
        XFORMS.put(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, new ContextXform(75, 225, 0,  0,  2.5f,  0f, 0.675f));
        XFORMS.put(ItemDisplayContext.THIRD_PERSON_LEFT_HAND,  new ContextXform(75,  45, 0,  0,  2.5f,  0f, 0.675f));
    }

    private record CacheKey(LensKey lensKey, ItemDisplayContext context) {}

    private static final LoadingCache<CacheKey, List<BakedQuad>> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(CacheLoader.from(key -> {
            ContextXform xform = XFORMS.getOrDefault(key.context(), DEFAULT_XFORM);
            LensKey lk = key.lensKey();
            List<BakedQuad> quads = new ArrayList<>();

            // Bake cutout quads (frame) — set white vertex colors so texture shows unmodified
            for (MutableQuad mq : PlugBakerLens.bakeForItem(lk.colour(), lk.isFilter(), true)) {
                transformForItem(mq, xform, true);
                quads.add(mq.toBakedItem());
            }

            // Bake translucent quads (coloured glass overlay) — use toBakedTranslucent so the
            // quad is routed to the correct render layer and vertex colours are preserved via BakedColors
            for (MutableQuad mq : PlugBakerLens.bakeForItem(lk.colour(), lk.isFilter(), false)) {
                transformForItem(mq, xform, false);
                quads.add(mq.toBakedTranslucent());
            }

            return quads;
        }));

    /**
     * Rotates a MutableQuad from WEST-facing in-world orientation to
     * NORTH-facing item orientation, applies the per-context display
     * transform, and resets vertex colours for cutout (frame) quads.
     *
     * @param resetColors true for cutout quads (white vertex colors so texture is unmodified),
     *                    false for translucent quads (preserve vertex colors set by tinting)
     */
    private static void transformForItem(MutableQuad mq, ContextXform x, boolean resetColors) {
        mq.setShade(false);
        // Rotate from WEST -> NORTH so the lens face points at the camera
        mq.rotate(Direction.WEST, Direction.NORTH, 0.5f, 0.5f, 0.5f);

        // Per-context display transform around the model centre, then world-space translation
        mq.translatef(-0.5f, -0.5f, -0.5f);
        if (x.rotX() != 0f) mq.rotateX((float) Math.toRadians(x.rotX()));
        if (x.rotY() != 0f) mq.rotateY((float) Math.toRadians(x.rotY()));
        if (x.rotZ() != 0f) mq.rotateZ((float) Math.toRadians(x.rotZ()));
        if (x.scale() != 1f) mq.scalef(x.scale());
        mq.translatef(0.5f, 0.5f, 0.5f);
        if (x.tx() != 0f || x.ty() != 0f || x.tz() != 0f) {
            mq.translatef(x.tx() / 16f, x.ty() / 16f, x.tz() / 16f);
        }
        mq.setCalculatedNormal();

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
        CacheKey key = new CacheKey(new LensKey(colour, isFilter), displayContext);

        List<BakedQuad> quads = cache.getUnchecked(key);
        if (quads.isEmpty()) {
            return;
        }

        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(key.lensKey());
        renderState.appendModelIdentityElement(displayContext);

        var layer = renderState.newLayer();
        layer.prepareQuadList().addAll(quads);
    }
}
