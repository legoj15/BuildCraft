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
/*public class FacadeItemModel implements buildcraft.platform.client.ClassicItemModel {*/
//?}

    /** The item's quads, both passes of the in-world baker (the cutout pass first, the translucent second), and
     *  whether any translucent quad is among them (the pre-26.1 lines pick the item sheet by it; 26.x reads each
     *  quad's own item sheet). */
    private record FacadeQuads(List<BakedQuad> quads, boolean translucent) {}

    /** Both cache loaders take the cutout-pass key and also bake its translucent twin. */
    private static KeyPlugFacade translucentTwin(KeyPlugFacade key) {
        return new KeyPlugFacade(PlugBakerFacade.LAYER_TRANSLUCENT, key.side, key.state, key.isHollow);
    }

    // Cache for hand/3rd-person rendering (EAST facing, includes plug connector)
    private static final LoadingCache<KeyPlugFacade, FacadeQuads> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(1))
        .build(CacheLoader.from(key -> {
            List<BakedQuad> translucent = PlugBakerFacade.INSTANCE.bake(translucentTwin(key));
            List<BakedQuad> quads = new ArrayList<>(PlugBakerFacade.INSTANCE.bake(key));
            quads.addAll(translucent);
            return new FacadeQuads(quads, !translucent.isEmpty());
        }));

    // Cache for GUI/inventory rendering (NORTH facing, centered in slot)
    private static final LoadingCache<KeyPlugFacade, FacadeQuads> guiCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(1))
        .build(CacheLoader.from(key -> {
            List<BakedQuad> quads = new ArrayList<>();
            boolean anyTranslucent = false;
            float offsetZ = (16 - buildcraft.silicon.plug.PluggableFacade.SIZE) / 2f / 16f;
            for (KeyPlugFacade pass : List.of(key, translucentTwin(key))) {
                boolean translucent = PlugBakerFacade.isTranslucent(pass);
                for (MutableQuad quad : PlugBakerFacade.INSTANCE.bakeForKey(pass)) {
                    quad.setShade(false);
                    quad.vertex_0.translatef(0, 0, offsetZ).normalf(0, 1, 0).colouri(255, 255, 255, 255);
                    quad.vertex_1.translatef(0, 0, offsetZ).normalf(0, 1, 0).colouri(255, 255, 255, 255);
                    quad.vertex_2.translatef(0, 0, offsetZ).normalf(0, 1, 0).colouri(255, 255, 255, 255);
                    quad.vertex_3.translatef(0, 0, offsetZ).normalf(0, 1, 0).colouri(255, 255, 255, 255);
                    quads.add(translucent ? quad.toBakedTranslucent() : quad.toBakedItem());
                    anyTranslucent |= translucent;
                }
            }
            return new FacadeQuads(quads, anyTranslucent);
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

        FacadeQuads baked;
        KeyPlugFacade key;
        if (displayContext == ItemDisplayContext.GUI) {
            key = new KeyPlugFacade("cutout", Direction.NORTH, state.stateInfo.state, inst.isHollow());
            baked = guiCache.getUnchecked(key);
        } else {
            key = new KeyPlugFacade("cutout", Direction.EAST, state.stateInfo.state, inst.isHollow());
            baked = cache.getUnchecked(key);
        }
        List<BakedQuad> quads = baked.quads();

        if (quads.isEmpty()) {
            return;
        }

        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(key);

        var layer = renderState.newLayer();
        //? if >=1.21.11 {
        // Through the platform, so the quads keep their vertex colours on Fabric too (26.3 also dropped
        // prepareQuadList(): a layer there takes one ItemQuads, pre-split into solid/translucent submits).
        buildcraft.platform.client.ClientPlatform.models().setItemQuads(layer, quads);
        //?} else {
        /*layer.prepareQuadList().addAll(quads);
        *///?}
        buildcraft.lib.client.model.ModelUtil.setItemExtents(layer, quads);
        FacadeItemTransforms.apply(layer, displayContext);
        // 1.21.10/11: the item layer needs one explicit render type (26.1 infers it per quad from its
        // MaterialInfo). Matching vanilla BlockModelWrapper: the cutout block sheet, or the translucent block-item
        // sheet when the facade's block draws translucent (1.21.10 calls it translucentItemSheet).
        //? if >=1.21.11 && <26.1 {
        /*layer.setRenderType(baked.translucent() ? net.minecraft.client.renderer.Sheets.translucentBlockItemSheet()
                : net.minecraft.client.renderer.Sheets.cutoutBlockSheet());*/
        //?} elif <1.21.11 {
        /*layer.setRenderType(baked.translucent() ? net.minecraft.client.renderer.Sheets.translucentItemSheet()
                : net.minecraft.client.renderer.Sheets.cutoutBlockSheet());*/
        //?}
    }
    //?} else {
    /*// 1.21.1 has no ItemModel / ItemStackRenderState. Dynamic item rendering uses the classic
    // ItemOverrides + applyTransform path: resolve() reads the facade state per-stack, then
    // applyTransform() returns a fixed-quad item model (ClientPlatform.models().fixedQuadItemModel) of the right cache per ItemDisplayContext
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
        FacadeQuads baked;
        if (displayContext == ItemDisplayContext.GUI) {
            baked = guiCache.getUnchecked(new KeyPlugFacade("cutout", Direction.NORTH, resolvedState, resolvedHollow));
        } else {
            baked = cache.getUnchecked(new KeyPlugFacade("cutout", Direction.EAST, resolvedState, resolvedHollow));
        }
        List<BakedQuad> quads = baked.quads();
        if (quads.isEmpty()) {
            return this;
        }
        FacadeItemTransforms.apply(displayContext, pose, leftHand);
        // The item's particle is the facade block's own (what the item shows), not the first quad's: a translucent-only
        // facade's cutout pass holds just the plug connector, whose sprite is the pipe plug's.
        net.minecraft.client.resources.model.BakedModel particleModel = net.minecraft.client.Minecraft.getInstance()
                .getModelManager().getBlockModelShaper().getBlockModel(resolvedState);
        net.minecraft.client.renderer.texture.TextureAtlasSprite particle =
                buildcraft.platform.client.ClientPlatform.extensions().modelParticle(particleModel);
        // A translucent facade takes the sheet vanilla 1.21.1 gives a translucent block item (cull + sorted).
        return buildcraft.platform.client.ClientPlatform.models().fixedQuadItemModel(
                quads, particle, false, baked.translucent()
                        ? net.minecraft.client.renderer.Sheets.translucentCullBlockSheet()
                        : net.minecraft.client.renderer.Sheets.cutoutBlockSheet());
    }

    // Vanilla's quad getter: NeoForge deprecates it for its model-data overload, which falls back here.
    @Override
    @SuppressWarnings("deprecation")
    public List<BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState s,
            net.minecraft.core.Direction side, net.minecraft.util.RandomSource rand) {
        return List.of();
    }

    // Only the ground entry, which vanilla's ItemEntityRenderer reads off the resolved model: applyTransform has already put
    // FacadeItemTransforms on the pose and answers a model with no transforms of its own (NeoForge deprecates the getter; on
    // Fabric it is abstract).
    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
        return FacadeItemTransforms.GROUND_ONLY;
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

    // Abstract-yet-deprecated on 1.21.1 (NeoForge's ModelData overload delegates back here), so overriding it is mandatory.
    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
        return buildcraft.platform.client.ClientPlatform.extensions().modelParticle(
                net.minecraft.client.Minecraft.getInstance().getModelManager().getMissingModel());
    }*/
    //?}
}
