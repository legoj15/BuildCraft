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
 *
 * <p>MC 26.1: BlockStateModelWrapper fields completely changed (quads/properties/
 * renderType/extents → model/tints/transformation). Reflection removed.
 * This model generates all quads via PlugBakerFacade and doesn't need wrapper internals.
 */
//? if >=1.21.10 {
public class FacadeItemModel implements ItemModel {
//?} else {
/*public class FacadeItemModel implements net.minecraft.client.resources.model.BakedModel {*/
//?}

    // Cache for hand/3rd-person rendering (EAST facing, includes plug connector)
    private static final LoadingCache<KeyPlugFacade, List<BakedQuad>> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(CacheLoader.from(key -> PlugBakerFacade.INSTANCE.bake(key)));

    // Cache for GUI/inventory rendering (NORTH facing, centered in slot)
    private static final LoadingCache<KeyPlugFacade, List<BakedQuad>> guiCache = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(CacheLoader.from(key -> {
            List<BakedQuad> quads = new ArrayList<>();
            float offsetZ = (16 - buildcraft.silicon.plug.PluggableFacade.SIZE) / 2f / 16f;
            for (MutableQuad quad : PlugBakerFacade.INSTANCE.bakeForKey(key)) {
                quad.setShade(false);
                quad.vertex_0.translatef(0, 0, offsetZ).normalf(0, 1, 0).colouri(255, 255, 255, 255);
                quad.vertex_1.translatef(0, 0, offsetZ).normalf(0, 1, 0).colouri(255, 255, 255, 255);
                quad.vertex_2.translatef(0, 0, offsetZ).normalf(0, 1, 0).colouri(255, 255, 255, 255);
                quad.vertex_3.translatef(0, 0, offsetZ).normalf(0, 1, 0).colouri(255, 255, 255, 255);
                quads.add(quad.toBakedItem());
            }
            return quads;
        }));

    //? if <1.21.10 {
    /*// 1.21.1 per-stack resolved facade state. Null on the registry-level model installed into
    // the baked-model map; populated by the ItemOverrides below for each rendered stack.
    private final net.minecraft.world.level.block.state.BlockState resolvedState;
    private final boolean resolvedHollow;
    private FacadeItemModel(net.minecraft.world.level.block.state.BlockState state, boolean hollow) {
        this.resolvedState = state;
        this.resolvedHollow = hollow;
    }*/
    //?}

    public FacadeItemModel() {
        // No-arg constructor — no reflection needed
        //? if <1.21.10 {
        /*this.resolvedState = null;
        this.resolvedHollow = false;*/
        //?}
    }

    public static void onModelBake() {
        cache.invalidateAll();
        guiCache.invalidateAll();
    }

    //? if >=1.21.10 {
    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        FacadeInstance inst = ItemPluggableFacade.getStates(stack);
        FacadePhasedState state = inst.getCurrentStateForStack();

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
            return;
        }

        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(key);

        var layer = renderState.newLayer();
        layer.prepareQuadList().addAll(quads);
        //? if <26.1 {
        /*// 1.21.11: the item layer needs an explicit render type (26.1 infers it from the quad's
        // MaterialInfo). Block-textured cutout → cutoutBlockSheet, matching vanilla BlockModelWrapper.
        layer.setRenderType(net.minecraft.client.renderer.Sheets.cutoutBlockSheet());*/
        //?}
    }
    //?} else {
    /*// 1.21.1 has no ItemModel / ItemStackRenderState. Dynamic item rendering uses the classic
    // ItemOverrides + applyTransform path: resolve() reads the facade state per-stack, then
    // applyTransform() returns a QuadItemBakedModel of the right cache per ItemDisplayContext
    // (GUI: NORTH / guiCache; hand & world: EAST / cache). Behaviour matches the modern update().
    private final net.minecraft.client.renderer.block.model.ItemOverrides overrides =
        new net.minecraft.client.renderer.block.model.ItemOverrides() {
            @Override
            public net.minecraft.client.resources.model.BakedModel resolve(
                    net.minecraft.client.resources.model.BakedModel model, ItemStack stack,
                    @Nullable ClientLevel level,
                    net.minecraft.world.entity.LivingEntity entity, int seed) {
                FacadeInstance inst = ItemPluggableFacade.getStates(stack);
                FacadePhasedState state = inst.getCurrentStateForStack();
                return new FacadeItemModel(state.stateInfo.state, inst.isHollow());
            }
        };

    @Override
    public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
        return overrides;
    }

    @Override
    public net.minecraft.client.resources.model.BakedModel applyTransform(
            ItemDisplayContext displayContext, com.mojang.blaze3d.vertex.PoseStack pose, boolean leftHand) {
        if (resolvedState == null) {
            return this;
        }
        List<BakedQuad> quads;
        if (displayContext == ItemDisplayContext.GUI) {
            quads = guiCache.getUnchecked(new KeyPlugFacade("cutout", Direction.NORTH, resolvedState, resolvedHollow));
        } else {
            quads = cache.getUnchecked(new KeyPlugFacade("cutout", Direction.EAST, resolvedState, resolvedHollow));
        }
        if (quads.isEmpty()) {
            return this;
        }
        return new buildcraft.lib.client.model.QuadItemBakedModel(
                quads, quads.get(0).getSprite(), false, net.minecraft.client.renderer.Sheets.cutoutBlockSheet());
    }

    @Override
    public List<BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState s,
            @Nullable Direction side, net.minecraft.util.RandomSource rand) {
        return List.of();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
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
