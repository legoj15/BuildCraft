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
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.model.MutableQuad;

import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.client.model.plug.PlugGateBaker;

/**
 * Dynamic ItemModel for gate items. Renders the gate as a 3D model using the
 * same geometry as the in-world PlugGateBaker, matching 1.12.2's behavior.
 *
 * <p>In 1.12.2, gate items used ModelGateItem which reused the pluggable baked
 * quads with TRANSFORM_PLUG_AS_ITEM_BIGGER camera transforms. In 26.1.1, we
 * follow the same FacadeItemModel pattern: implement ItemModel.update() and
 * feed quads into the render state layer.
 */
public class GateItemModel implements ItemModel {

    private static final LoadingCache<GateVariant, List<BakedQuad>> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(CacheLoader.from(variant -> {
            List<BakedQuad> quads = new ArrayList<>();
            for (MutableQuad mq : PlugGateBaker.INSTANCE.bakeForItem(variant)) {
                mq.setShade(false);
                // Scale 1.8× around center, matching 1.12.2's TRANSFORM_PLUG_AS_ITEM_BIGGER
                mq.translatef(-0.5f, -0.5f, -0.5f);
                mq.scalef(1.8f);
                mq.translatef(0.5f, 0.5f, 0.5f);
                quads.add(mq.toBakedItem());
            }
            return quads;
        }));

    public GateItemModel() {}

    public static void onModelBake() {
        cache.invalidateAll();
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        GateVariant variant = ItemPluggableGate.getVariant(stack);
        if (variant == null) {
            return;
        }

        List<BakedQuad> quads = cache.getUnchecked(variant);
        if (quads.isEmpty()) {
            return;
        }

        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(variant);

        var layer = renderState.newLayer();
        layer.prepareQuadList().addAll(quads);
    }
}
