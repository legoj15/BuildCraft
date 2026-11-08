/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.model.MutableQuad;

import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.client.model.plug.PlugGateBaker;

/**
 * Dynamic ItemModel for gate items. Renders the gate as 3D geometry using the
 * same quads as the in-world PlugGateBaker, with the display transforms of 1.12.2's
 * TRANSFORM_PLUG_AS_ITEM_BIGGER (see {@link PlugItemTransforms}, and
 * common/buildcraft/lib/client/model/ModelItemSimple.java on the 8.0.x-1.12.2 branch).
 *
 * <p>Two quad sets per variant. The inventory icon is PlugGateBaker's NORTH-facing gate scaled 1.8 about the cell
 * centre, face on and with no display transform. Everywhere else the gate is turned to face WEST, as 1.12.2 baked it,
 * and the context's transform goes on the item layer (on the pose on 1.21.1), so the engine applies it the way 1.12.2's
 * did.
 */
//? if >=1.21.10 {
public class GateItemModel implements ItemModel {
//?} else {
/*public class GateItemModel implements buildcraft.platform.client.ClassicItemModel {*/
//?}

    private static final PlugItemTransforms TRANSFORMS = PlugItemTransforms.PLUG_AS_ITEM_BIGGER;

    /** The inventory icon's size, baked into its quads (1.12.2's GUI scale for the gate). */
    private static final float GUI_SCALE = 1.8f;

    /** {@code gui}: the inventory icon's quads; otherwise the WEST-facing ones every other context transforms. */
    private record CacheKey(GateVariant variant, boolean gui) {}

    private static final LoadingCache<CacheKey, List<BakedQuad>> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(1))
        .build(CacheLoader.from(key -> {
            List<BakedQuad> quads = new ArrayList<>();
            for (MutableQuad mq : PlugGateBaker.INSTANCE.bakeForItem(key.variant())) {
                mq.setShade(false);
                if (key.gui()) {
                    mq.translatef(-0.5f, -0.5f, -0.5f);
                    mq.scalef(GUI_SCALE);
                    mq.translatef(0.5f, 0.5f, 0.5f);
                } else {
                    mq.rotate(Direction.NORTH, Direction.WEST, 0.5f, 0.5f, 0.5f);
                }
                mq.setCalculatedNormal();
                quads.add(mq.toBakedItem());
            }
            return quads;
        }));

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

        List<BakedQuad> quads = cache.getUnchecked(new CacheKey(variant, displayContext == ItemDisplayContext.GUI));
        if (quads.isEmpty()) {
            return;
        }

        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(variant);
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
        /*// 1.21.11: the item layer needs an explicit render type (26.1 infers it from the quad's
        // MaterialInfo). Block-textured cutout → cutoutBlockSheet, matching vanilla BlockModelWrapper.
        layer.setRenderType(net.minecraft.client.renderer.Sheets.cutoutBlockSheet());*/
        //?}
    }
    //?} else {
    /*// 1.21.1 dynamic item path (no ItemModel / ItemStackRenderState): resolve() reads the gate
    // variant per-stack, applyTransform() picks the inventory or the WEST-facing quad set (the same cache the modern
    // update() uses), puts the context's transform on the pose and wraps the quads in a fixed-quad item model
    // (ClientModels.fixedQuadItemModel).
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
        List<BakedQuad> quads = cache.getUnchecked(
                new CacheKey(resolvedVariant, displayContext == ItemDisplayContext.GUI));
        if (quads.isEmpty()) {
            return this;
        }
        TRANSFORMS.apply(displayContext, pose, leftHand);
        return buildcraft.platform.client.ClientPlatform.models().fixedQuadItemModel(
                quads, quads.get(0).getSprite(), true, net.minecraft.client.renderer.Sheets.cutoutBlockSheet());
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
        return TRANSFORMS.groundOnly;
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
