/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

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
//?}
import net.minecraft.core.Direction;
//? if >=1.21.10 {
import net.minecraft.world.entity.ItemOwner;
//?}
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.model.MutableQuad;

import buildcraft.silicon.client.model.plug.PlugBakerLens;
import buildcraft.silicon.item.ItemPluggableLens;

/**
 * Dynamic ItemModel for lens/filter items. Renders the lens/filter as 3D
 * geometry using PlugBakerLens, with the display transforms of 1.12.2's
 * TRANSFORM_PLUG_AS_ITEM (see {@link PlugItemTransforms}, and
 * common/buildcraft/lib/client/model/ModelItemSimple.java and
 * common/buildcraft/silicon/client/model/plug/ModelLensItem.java on the 8.0.x-1.12.2 branch). The gate used the
 * BIGGER set; the lens did not, though its inventory and hand poses here are the port's BIGGER ones, kept as they were.
 *
 * <p>PlugBakerLens emits geometry in WEST-facing orientation (thin axis on X), the way 1.12.2 baked the lens item,
 * and every context but the inventory and the hands draws that, with the context's transform on the item layer (on the
 * pose on 1.21.1). The inventory icon is its own quad set: the lens turned NORTH (face on) and scaled 1.8 about the
 * cell centre, with no display transform. So is each hand: the lens turned NORTH with the hand's pose baked in
 * ({@link PlugItemTransforms#bakeHandPose}).
 */
//? if >=1.21.10 {
public class LensItemModel implements ItemModel {
//?} else {
/*public class LensItemModel implements buildcraft.platform.client.ClassicItemModel {*/
//?}

    /** Inner identity for the lens itself: colour + isFilter. */
    private record LensKey(@Nullable DyeColor colour, boolean isFilter) {}

    private static final PlugItemTransforms TRANSFORMS = PlugItemTransforms.PLUG_AS_ITEM;

    /** The inventory icon's size, baked into its quads. */
    private static final float GUI_SCALE = 1.8f;

    /** {@code quadSet}: {@link PlugItemTransforms#quadSet} of the display context. */
    private record CacheKey(LensKey lensKey, ItemDisplayContext quadSet) {}

    private static final LoadingCache<CacheKey, List<BakedQuad>> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(1))
        .build(CacheLoader.from(key -> {
            LensKey lk = key.lensKey();
            List<BakedQuad> quads = new ArrayList<>();

            // Bake cutout quads (frame) — set white vertex colors so texture shows unmodified
            for (MutableQuad mq : PlugBakerLens.bakeForItem(lk.colour(), lk.isFilter(), true)) {
                transformForItem(mq, key.quadSet(), true);
                quads.add(mq.toBakedItem());
            }

            // Bake translucent quads (coloured glass overlay) — use toBakedTranslucent so the
            // quad is routed to the correct render layer and vertex colours are preserved via BakedColors
            for (MutableQuad mq : PlugBakerLens.bakeForItem(lk.colour(), lk.isFilter(), false)) {
                transformForItem(mq, key.quadSet(), false);
                quads.add(mq.toBakedTranslucent());
            }

            return quads;
        }));

    /**
     * Readies a WEST-facing PlugBakerLens quad for the item: for the inventory icon, turns it NORTH and scales it about
     * the cell centre; for a hand, turns it NORTH and bakes the hand's pose in; then realigns normal and face with the
     * geometry and resets vertex colours for cutout (frame) quads.
     *
     * @param resetColors true for cutout quads (white vertex colors so texture is unmodified),
     *                    false for translucent quads (preserve vertex colors set by tinting)
     */
    private static void transformForItem(MutableQuad mq, ItemDisplayContext quadSet, boolean resetColors) {
        mq.setShade(false);
        if (quadSet != ItemDisplayContext.NONE) {
            // Rotate from WEST -> NORTH so the lens face points at the camera
            mq.rotate(Direction.WEST, Direction.NORTH, 0.5f, 0.5f, 0.5f);
            if (quadSet == ItemDisplayContext.GUI) {
                mq.translatef(-0.5f, -0.5f, -0.5f);
                mq.scalef(GUI_SCALE);
                mq.translatef(0.5f, 0.5f, 0.5f);
            } else {
                PlugItemTransforms.bakeHandPose(mq, quadSet);
            }
        }
        mq.setCalculatedNormal();
        // NeoForge lights an item quad by its face and Fabric by the geometry, so a face that disagrees with the
        // geometry drew the lens at the ambient floor (0.4) on NeoForge 1.21.11+ and fully lit on Fabric (#173).
        // Realigned, both agree; in the inventory both draw it fully lit, as 1.12.2 did: its lens item was not gui3d,
        // so the GUI drew it with lighting off.
        mq.setFaceFromCalculatedNormal();

        // Set full white vertex colours only for cutout (frame) quads.
        // Translucent quads keep the dye color set by bakeTranslucentQuads.
        if (resetColors) {
            mq.vertex_0.colouri(255, 255, 255, 255);
            mq.vertex_1.colouri(255, 255, 255, 255);
            mq.vertex_2.colouri(255, 255, 255, 255);
            mq.vertex_3.colouri(255, 255, 255, 255);
        }
    }

    //? if <1.21.10 {
    /*// 1.21.1 per-stack resolved lens (colour + isFilter). Null on the registry-level model;
    // populated by the ItemOverrides below for each rendered stack.
    @Nullable private final LensKey resolvedLens;
    private LensItemModel(LensKey lens) {
        this.resolvedLens = lens;
    }*/
    //?}

    public LensItemModel() {
        //? if <1.21.10 {
        /*this.resolvedLens = null;*/
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
        DyeColor colour = ItemPluggableLens.getColour(stack);
        boolean isFilter = ItemPluggableLens.isFilter(stack);
        CacheKey key = new CacheKey(new LensKey(colour, isFilter), PlugItemTransforms.quadSet(displayContext));

        List<BakedQuad> quads = cache.getUnchecked(key);
        if (quads.isEmpty()) {
            return;
        }

        // If any quad uses an animated sprite (the clear lens's water_flow overlay), flag the
        // render state as animated. Modern MC renders each GUI item once into GuiItemAtlas — a
        // cache keyed by model identity — and reuses that frozen frame; only render states flagged
        // animated get re-drawn each frame, re-sampling the live block atlas so the animation
        // advances. Vanilla's CuboidItemModelWrapper sets this automatically from FLAG_ANIMATED;
        // this custom ItemModel bypasses that wrapper, so it must propagate the flag itself.
        for (BakedQuad quad : quads) {
            //? if >=26.1 {
            if ((quad.materialInfo().flags() & BakedQuad.FLAG_ANIMATED) != 0) {
            //?} else {
            /*if (quad.sprite().contents().isAnimated()) {*/
            //?}
                renderState.setAnimated();
                break;
            }
        }

        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(key.lensKey());
        renderState.appendModelIdentityElement(displayContext);

        var layer = renderState.newLayer();
        //? if >=1.21.11 {
        // Through the platform, so the quads keep their vertex colours on Fabric too (26.3 also dropped
        // prepareQuadList(): a layer there takes one ItemQuads, pre-split into solid/translucent submits).
        buildcraft.platform.client.ClientPlatform.models().setItemQuads(layer, quads);
        //?} else {
        /*layer.prepareQuadList().addAll(quads);
        *///?}
        buildcraft.lib.client.model.ModelUtil.setItemExtents(layer, quads);
        TRANSFORMS.apply(layer, displayContext);
        //? if <26.1 {
        /*// Pre-26.1 doesn't infer a layer's render type from its quads, so set one explicitly —
        // without it the lens layer draws nothing (invisible in the inventory). A translucent sheet
        // covers both the opaque cutout frame (alpha 255 stays opaque) and the coloured glass overlay.
        layer.setRenderType(buildcraft.lib.client.render.BCLibRenderTypes.translucentItemSheet());*/
        //?}
    }
    //?} else {
    /*// 1.21.1 dynamic item path (no ItemModel / ItemStackRenderState): resolve() reads the lens
    // colour + filter flag per-stack, applyTransform() picks the context's quad set (the same cache the modern update()
    // uses), puts the context's transform on the pose (none for the inventory and the hands, whose quads carry theirs)
    // and wraps the quads in a fixed-quad item model (ClientModels.fixedQuadItemModel) on the translucent item sheet
    // (opaque frame stays opaque at alpha 255; coloured glass blends).
    // The modern setAnimated() bookkeeping is unnecessary here: 1.21.1's classic ItemRenderer GUI
    // path re-renders each frame, so the clear-lens water overlay animates without an explicit flag.
    private final net.minecraft.client.renderer.block.model.ItemOverrides overrides =
        new net.minecraft.client.renderer.block.model.ItemOverrides() {
            @Override
            public net.minecraft.client.resources.model.BakedModel resolve(
                    net.minecraft.client.resources.model.BakedModel model, ItemStack stack,
                    @Nullable ClientLevel level,
                    @Nullable net.minecraft.world.entity.LivingEntity entity, int seed) {
                DyeColor colour = ItemPluggableLens.getColour(stack);
                boolean isFilter = ItemPluggableLens.isFilter(stack);
                return new LensItemModel(new LensKey(colour, isFilter));
            }
        };

    @Override
    public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
        return overrides;
    }

    @Override
    public net.minecraft.client.resources.model.BakedModel applyTransform(
            ItemDisplayContext displayContext, com.mojang.blaze3d.vertex.PoseStack pose, boolean leftHand) {
        if (resolvedLens == null) {
            return this;
        }
        List<BakedQuad> quads = cache.getUnchecked(
                new CacheKey(resolvedLens, PlugItemTransforms.quadSet(displayContext)));
        if (quads.isEmpty()) {
            return this;
        }
        TRANSFORMS.apply(displayContext, pose, leftHand);
        return buildcraft.platform.client.ClientPlatform.models().fixedQuadItemModel(
                quads, quads.get(0).getSprite(), true,
                buildcraft.lib.client.render.BCLibRenderTypes.translucentItemSheet());
    }

    // Vanilla's quad getter: NeoForge deprecates it for its model-data overload, which falls back here.
    @Override
    @SuppressWarnings("deprecation")
    public List<BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState s,
            net.minecraft.core.Direction side, net.minecraft.util.RandomSource rand) {
        return List.of();
    }

    // Only the ground entry, which vanilla's ItemEntityRenderer reads off the resolved model: applyTransform has already put
    // the transform on the pose and answers a model with none of its own (NeoForge deprecates the getter; on Fabric it is
    // abstract).
    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
        return TRANSFORMS.reportedTransforms;
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

    // Abstract-yet-deprecated on 1.21.1 (NeoForge's ModelData overload delegates back here), so overriding it is mandatory.
    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
        return buildcraft.platform.client.ClientPlatform.extensions().modelParticle(
                net.minecraft.client.Minecraft.getInstance().getModelManager().getMissingModel());
    }*/
    //?}
}
