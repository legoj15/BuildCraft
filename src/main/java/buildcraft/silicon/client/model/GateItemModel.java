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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}
//? if >=1.21.10 {
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
//?}
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.model.MutableQuad;

import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.client.model.plug.PlugGateBaker;

/**
 * Dynamic ItemModel for gate items. Renders the gate as 3D geometry using the
 * same quads as the in-world PlugGateBaker, with per-ItemDisplayContext camera
 * transforms matching 1.12.2's TRANSFORM_PLUG_AS_ITEM_BIGGER (see
 * common/buildcraft/lib/client/model/ModelItemSimple.java on the
 * 8.0.x-1.12.2 branch).
 *
 * <p>The base quads come out of PlugGateBaker NORTH-facing at native size;
 * this class applies the per-context rotate/scale/translate so dropped
 * entities, in-hand, and item-frame renderings each match what 1.12.2 did.
 */
//? if >=1.21.10 {
public class GateItemModel implements ItemModel {
//?} else {
/*public class GateItemModel implements net.minecraft.client.resources.model.BakedModel {*/
//?}

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
     * GUI rotation is identity here (not Y=90 as 1.12.2 used) because our base
     * geometry is already NORTH-facing — the gate face points at the camera
     * without needing the rotation 1.12.2 applied to its WEST-mounted geometry.
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

    private record CacheKey(GateVariant variant, ItemDisplayContext context) {}

    private static final LoadingCache<CacheKey, List<BakedQuad>> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(CacheLoader.from(key -> {
            ContextXform x = XFORMS.getOrDefault(key.context(), DEFAULT_XFORM);
            List<BakedQuad> quads = new ArrayList<>();
            for (MutableQuad mq : PlugGateBaker.INSTANCE.bakeForItem(key.variant())) {
                mq.setShade(false);
                applyXform(mq, x);
                mq.setCalculatedNormal();
                quads.add(mq.toBakedItem());
            }
            return quads;
        }));

    /** Pivot to centre, rotate, scale, pivot back, then apply world-space translation. */
    private static void applyXform(MutableQuad mq, ContextXform x) {
        mq.translatef(-0.5f, -0.5f, -0.5f);
        if (x.rotX() != 0f) mq.rotateX((float) Math.toRadians(x.rotX()));
        if (x.rotY() != 0f) mq.rotateY((float) Math.toRadians(x.rotY()));
        if (x.rotZ() != 0f) mq.rotateZ((float) Math.toRadians(x.rotZ()));
        if (x.scale() != 1f) mq.scalef(x.scale());
        mq.translatef(0.5f, 0.5f, 0.5f);
        if (x.tx() != 0f || x.ty() != 0f || x.tz() != 0f) {
            mq.translatef(x.tx() / 16f, x.ty() / 16f, x.tz() / 16f);
        }
    }

    //? if <1.21.10 {
    /*// 1.21.1 per-stack resolved gate variant. Null on the registry-level model; populated by the
    // ItemOverrides below for each rendered stack (and left null when the stack has no variant).
    @Nullable private final GateVariant resolvedVariant;
    private GateItemModel(@Nullable GateVariant variant) {
        this.resolvedVariant = variant;
    }*/
    //?}

    public GateItemModel() {
        //? if <1.21.10 {
        /*this.resolvedVariant = null;*/
        //?}
    }

    public static void onModelBake() {
        cache.invalidateAll();
    }

    //? if >=1.21.10 {
    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        GateVariant variant = ItemPluggableGate.getVariant(stack);
        if (variant == null) {
            return;
        }

        CacheKey key = new CacheKey(variant, displayContext);
        List<BakedQuad> quads = cache.getUnchecked(key);
        if (quads.isEmpty()) {
            return;
        }

        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(variant);
        renderState.appendModelIdentityElement(displayContext);

        var layer = renderState.newLayer();
        layer.prepareQuadList().addAll(quads);
        //? if <26.1 {
        /*// 1.21.11: the item layer needs an explicit render type (26.1 infers it from the quad's
        // MaterialInfo). Block-textured cutout → cutoutBlockSheet, matching vanilla BlockModelWrapper.
        layer.setRenderType(net.minecraft.client.renderer.Sheets.cutoutBlockSheet());*/
        //?}
    }
    //?} else {
    /*// 1.21.1 dynamic item path (no ItemModel / ItemStackRenderState): resolve() reads the gate
    // variant per-stack, applyTransform() picks the per-(variant, context) pre-baked quad set —
    // the same cache the modern update() uses — and wraps it in a QuadItemBakedModel.
    private final net.minecraft.client.renderer.block.model.ItemOverrides overrides =
        new net.minecraft.client.renderer.block.model.ItemOverrides() {
            @Override
            public net.minecraft.client.resources.model.BakedModel resolve(
                    net.minecraft.client.resources.model.BakedModel model, ItemStack stack,
                    @Nullable ClientLevel level,
                    net.minecraft.world.entity.LivingEntity entity, int seed) {
                return new GateItemModel(ItemPluggableGate.getVariant(stack));
            }
        };

    @Override
    public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
        return overrides;
    }

    @Override
    public net.minecraft.client.resources.model.BakedModel applyTransform(
            ItemDisplayContext displayContext, com.mojang.blaze3d.vertex.PoseStack pose, boolean leftHand) {
        if (resolvedVariant == null) {
            return this;
        }
        List<BakedQuad> quads = cache.getUnchecked(new CacheKey(resolvedVariant, displayContext));
        if (quads.isEmpty()) {
            return this;
        }
        return new buildcraft.lib.client.model.QuadItemBakedModel(
                quads, quads.get(0).getSprite(), true, net.minecraft.client.renderer.Sheets.cutoutBlockSheet());
    }

    @Override
    public List<BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState s,
            net.minecraft.core.Direction side, net.minecraft.util.RandomSource rand) {
        return List.of();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return false;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
        return net.minecraft.client.Minecraft.getInstance().getModelManager().getMissingModel().getParticleIcon();
    }

    @Override
    public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
        return net.minecraft.client.renderer.block.model.ItemTransforms.NO_TRANSFORMS;
    }*/
    //?}
}
