/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

//? if >=1.21.10 {
import net.minecraft.client.color.item.ItemTintSource;
//?}
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=26.1 {
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
//?} elif >=1.21.10 {
/*import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.item.BlockModelWrapper;*/
//?}
//? if >=1.21.10 {
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
//?}
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}
//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.QuadCollection;
//?}
import net.minecraft.core.Direction;
//? if >=1.21.10 {
import net.minecraft.world.entity.ItemOwner;
//?}
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.ModelUtil.UvFaceData;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.SpriteUtil;

import buildcraft.transport.BCTransportItems;

/**
 * A dynamic ItemModel for pipe items that wraps the vanilla CuboidItemModelWrapper
 * and adds colour rendering when the item carries a PIPE_COLOUR data component.
 *
 * <p>For painted fluid pipes: replaces the base quads with dyed-sprite quads
 * (colour baked into texture). For painted transport/kinesis pipes: adds a
 * translucent overlay layer. Both layers share transforms via
 * {@link ModelRenderProperties#applyToLayer}.
 *
 * <p>On 26.1 this reflects internal fields out of CuboidItemModelWrapper; on 1.21.11 the sibling
 * BlockModelWrapper exposes {@code properties} publicly, so its branch needs no reflection.
 */
@SuppressWarnings("deprecation")
//? if >=1.21.10 {
public class PipeItemModel implements ItemModel {

    // Reflection fields cached at class-load time. 26.1 only: CuboidItemModelWrapper hides quads/
    // properties/tints/extents behind private fields. 1.21.11's sibling BlockModelWrapper exposes
    // `properties` publicly and offers a public static computeExtents, so its branch in update()
    // needs no reflection (see the //?} else { branch there).
    //? if >=26.1 {
    private static final Field QUADS_FIELD;
    private static final Field PROPERTIES_FIELD;
    private static final Field TINTS_FIELD;
    private static final Field EXTENTS_FIELD;
    static {
        try {
            QUADS_FIELD = CuboidItemModelWrapper.class.getDeclaredField("quads");
            QUADS_FIELD.setAccessible(true);
            PROPERTIES_FIELD = CuboidItemModelWrapper.class.getDeclaredField("properties");
            PROPERTIES_FIELD.setAccessible(true);
            TINTS_FIELD = CuboidItemModelWrapper.class.getDeclaredField("tints");
            TINTS_FIELD.setAccessible(true);
            EXTENTS_FIELD = CuboidItemModelWrapper.class.getDeclaredField("extents");
            EXTENTS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access CuboidItemModelWrapper fields", e);
        }
    }
    //?}

    private final ItemModel vanillaDelegate;
    private final PipeDefinition definition;

    // Extracted from vanilla model at construction time (null if delegate isn't CuboidItemModelWrapper)
    //? if >=26.1 {
    private final @Nullable QuadCollection vanillaQuads;
    private final @Nullable ModelRenderProperties renderProperties;
    @SuppressWarnings("unchecked")
    private final java.util.function.Supplier<org.joml.Vector3fc[]> extents;
    //?}

    @SuppressWarnings("unchecked")
    public PipeItemModel(ItemModel vanillaDelegate, PipeDefinition definition) {
        this.vanillaDelegate = vanillaDelegate;
        this.definition = definition;

        //? if >=26.1 {
        if (vanillaDelegate instanceof CuboidItemModelWrapper wrapper) {
            try {
                this.vanillaQuads = (QuadCollection) QUADS_FIELD.get(wrapper);
                this.renderProperties = (ModelRenderProperties) PROPERTIES_FIELD.get(wrapper);
                this.extents = (java.util.function.Supplier<org.joml.Vector3fc[]>) EXTENTS_FIELD.get(wrapper);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to read CuboidItemModelWrapper fields", e);
            }
        } else {
            this.vanillaQuads = null;
            this.renderProperties = null;
            this.extents = null;
        }
        //?}
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        //? if >=26.1 {
        DyeColor colour = stack.get(BCTransportItems.PIPE_COLOUR.get());

        // If we can't reflect (non-CuboidItemModelWrapper), fall back to delegate
        if (vanillaQuads == null || renderProperties == null) {
            vanillaDelegate.update(renderState, stack, modelResolver, displayContext, level, owner, seed);
            return;
        }

        // Unpainted — vanilla rendering
        if (colour == null) {
            vanillaDelegate.update(renderState, stack, modelResolver, displayContext, level, owner, seed);
            return;
        }

        // === Painted pipe: render with colour ===
        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(colour);

        if (definition.flowType == PipeApi.flowFluids) {
            // Fluid pipes: render with the dye_replace-generated sprite variant.
            // ensureDyedSprites throws on missing — no silent fallback, per design.
            TextureAtlasSprite[] dyedSprites = PipeBaseModelGenStandard.ensureDyedSprites(definition, colour);
            int itemTexIndex = definition.itemTextureTop;
            TextureAtlasSprite dyedSprite = itemTexIndex < dyedSprites.length
                    ? dyedSprites[itemTexIndex] : dyedSprites[0];
            var layer = renderState.newLayer();
            layer.prepareQuadList().addAll(generatePipeQuads(dyedSprite));
            if (extents != null) layer.setExtents(extents);
            renderProperties.applyToLayer(layer, displayContext);
            return;
        }

        // Transport/kinesis: base quads + overlay layer
        // Layer 1: base pipe quads from vanilla model
        var baseLayer = renderState.newLayer();
        baseLayer.prepareQuadList().addAll(vanillaQuads.getAll());
        if (extents != null) baseLayer.setExtents(extents);
        renderProperties.applyToLayer(baseLayer, displayContext);

        // Layer 2: translucent colour overlay using mask sprites
        TextureAtlasSprite[] maskArray = PipeBaseModelGenStandard.ensureMaskSprites(definition);
        TextureAtlasSprite maskSprite = maskArray != null && maskArray.length > 0
                ? maskArray[0] : null;
        if (maskSprite != null && maskSprite != SpriteUtil.missingSprite()) {
            List<BakedQuad> overlayQuads = generateOverlayQuads(maskSprite);
            if (!overlayQuads.isEmpty()) {
                var overlayLayer = renderState.newLayer();
                overlayLayer.prepareQuadList().addAll(overlayQuads);
                if (extents != null) overlayLayer.setExtents(extents);
                renderProperties.applyToLayer(overlayLayer, displayContext);
                // Tint: semi-transparent dye colour at tintIndex=0
                // (alpha=76 for ~30% opacity stained-glass effect)
                int tintColour = (76 << 24) | buildcraft.lib.misc.ColourUtil.getLightHex(colour);
                overlayLayer.tintLayers().add(tintColour);
            }
        }
        //?} else {
        /*DyeColor colour = stack.get(BCTransportItems.PIPE_COLOUR.get());
        // Unpainted, or the baked delegate isn't the wrapper we expect → vanilla rendering.
        if (colour == null || !(vanillaDelegate instanceof BlockModelWrapper wrapper)) {
            vanillaDelegate.update(renderState, stack, modelResolver, displayContext, level, owner, seed);
            return;
        }
        // BlockModelWrapper.properties is public on 1.21.11 (no reflection needed). applyToLayer sets
        // transform/light/particle but NOT the render type, so each custom layer sets its own
        // (mirroring BlockModelWrapper.update). Extents come from the public static computeExtents.
        ModelRenderProperties props = wrapper.properties;

        renderState.appendModelIdentityElement(this);
        renderState.appendModelIdentityElement(colour);

        if (definition.flowType == PipeApi.flowFluids) {
            // Fluid pipes: replace the base with the dye_replace-generated sprite variant.
            TextureAtlasSprite[] dyedSprites = PipeBaseModelGenStandard.ensureDyedSprites(definition, colour);
            int itemTexIndex = definition.itemTextureTop;
            TextureAtlasSprite dyedSprite = itemTexIndex < dyedSprites.length
                    ? dyedSprites[itemTexIndex] : dyedSprites[0];
            List<BakedQuad> quads = generatePipeQuads(dyedSprite);
            var layer = renderState.newLayer();
            layer.prepareQuadList().addAll(quads);
            layer.setExtents(() -> BlockModelWrapper.computeExtents(quads));
            layer.setRenderType(Sheets.cutoutBlockSheet());
            props.applyToLayer(layer, displayContext);
            return;
        }

        // Transport/kinesis: let vanilla draw the base pipe, then add a tinted overlay layer.
        vanillaDelegate.update(renderState, stack, modelResolver, displayContext, level, owner, seed);
        TextureAtlasSprite[] maskArray = PipeBaseModelGenStandard.ensureMaskSprites(definition);
        TextureAtlasSprite maskSprite = maskArray != null && maskArray.length > 0 ? maskArray[0] : null;
        if (maskSprite != null && maskSprite != SpriteUtil.missingSprite()) {
            List<BakedQuad> overlayQuads = generateOverlayQuads(maskSprite);
            if (!overlayQuads.isEmpty()) {
                var overlayLayer = renderState.newLayer();
                overlayLayer.prepareQuadList().addAll(overlayQuads);
                overlayLayer.setExtents(() -> BlockModelWrapper.computeExtents(overlayQuads));
                overlayLayer.setRenderType(buildcraft.lib.client.render.BCLibRenderTypes.translucentItemSheet());
                props.applyToLayer(overlayLayer, displayContext);
                // Semi-transparent dye colour at tintIndex 0 (alpha 76 ~= 30% glaze).
                int tintColour = (76 << 24) | buildcraft.lib.misc.ColourUtil.getLightHex(colour);
                int[] tints = overlayLayer.prepareTintLayers(1);
                tints[0] = tintColour;
            }
        }*/
        //?}
    }

    /**
     * Generate full pipe item geometry (3-cube model matching pipe_item.json) using the given sprite.
     */
    private static List<BakedQuad> generatePipeQuads(TextureAtlasSprite sprite) {
        List<BakedQuad> quads = new ArrayList<>();

        UvFaceData bottomSideUvs = UvFaceData.from16(4, 12, 12, 16);
        UvFaceData centerUvs     = UvFaceData.from16(4, 4, 12, 12);
        UvFaceData topSideUvs    = UvFaceData.from16(4, 0, 12, 4);
        UvFaceData capFaceUvs    = UvFaceData.from16(4, 4, 12, 12);

        // Bottom cap
        addPipeFaces(quads, sprite, new Vector3f(0.5f, 0.125f, 0.5f),
                new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.DOWN }, capFaceUvs);
        addPipeFaces(quads, sprite, new Vector3f(0.5f, 0.125f, 0.5f),
                new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, bottomSideUvs);

        // Center body
        addPipeFaces(quads, sprite, new Vector3f(0.5f, 0.5f, 0.5f),
                new Vector3f(0.25f, 0.25f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, centerUvs);

        // Top cap
        addPipeFaces(quads, sprite, new Vector3f(0.5f, 0.875f, 0.5f),
                new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.UP }, capFaceUvs);
        addPipeFaces(quads, sprite, new Vector3f(0.5f, 0.875f, 0.5f),
                new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, topSideUvs);

        return quads;
    }

    private static void addPipeFaces(List<BakedQuad> quads, TextureAtlasSprite sprite,
                                      Vector3f center, Vector3f radius,
                                      Direction[] faces, UvFaceData uvs) {
        for (Direction face : faces) {
            MutableQuad quad = ModelUtil.createFace(face, center, radius, uvs);
            quad.setSprite(sprite);
            quad.texFromSprite(sprite);
            quads.add(quad.toBakedBlock());
        }
    }

    /** Slight inward offset per-face to avoid Z-fighting for overlay quads. */
    private static final double OVERLAY_OFFSET = 0.01;

    /**
     * Generate overlay quads using a mask sprite.
     * Same 3-cube geometry as pipe_item.json, slightly inward offset, with tintIndex=0.
     * Uses cutout quads with lightened tint to approximate translucency
     * (MC 26.1 item renderer doesn't support translucent quads).
     */
    private static List<BakedQuad> generateOverlayQuads(TextureAtlasSprite maskSprite) {
        List<BakedQuad> quads = new ArrayList<>();

        UvFaceData bottomSideUvs = UvFaceData.from16(4, 12, 12, 16);
        UvFaceData centerUvs     = UvFaceData.from16(4, 4, 12, 12);
        UvFaceData topSideUvs    = UvFaceData.from16(4, 0, 12, 4);
        UvFaceData capFaceUvs    = UvFaceData.from16(4, 4, 12, 12);

        // Bottom cap
        addOverlayFaces(quads, maskSprite, new Vector3f(0.5f, 0.125f, 0.5f),
                new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.DOWN }, capFaceUvs);
        addOverlayFaces(quads, maskSprite, new Vector3f(0.5f, 0.125f, 0.5f),
                new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, bottomSideUvs);

        // Center body
        addOverlayFaces(quads, maskSprite, new Vector3f(0.5f, 0.5f, 0.5f),
                new Vector3f(0.25f, 0.25f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, centerUvs);

        // Top cap
        addOverlayFaces(quads, maskSprite, new Vector3f(0.5f, 0.875f, 0.5f),
                new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.UP }, capFaceUvs);
        addOverlayFaces(quads, maskSprite, new Vector3f(0.5f, 0.875f, 0.5f),
                new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, topSideUvs);

        return quads;
    }

    /** Add overlay quads with slight inward offset and tintIndex=0. */
    private static void addOverlayFaces(List<BakedQuad> quads, TextureAtlasSprite sprite,
                                         Vector3f center, Vector3f radius,
                                         Direction[] faces, UvFaceData uvs) {
        for (Direction face : faces) {
            MutableQuad quad = ModelUtil.createFace(face, center, radius, uvs);
            // No offset for items — flush with base geometry (z-fighting isn't
            // an issue for items, and offset creates visible gaps at cutout edges)
            quad.setSprite(sprite);
            quad.texFromSprite(sprite);
            quad.setTint(0);
            quads.add(quad.toBakedTranslucent());
        }
    }
}
//?} else {
/*// 1.21.1 has no 1.21.4+ ItemModel/ItemStackRenderState system — pipe items are classic BakedModels.
// Unpainted pipes delegate to the vanilla baked model. Painted pipes are resolved per-stack through
// ItemOverrides into a small fixed-quad BakedModel (cached per colour): fluid pipes get the
// dye_replace sprite baked in; transport/kinesis pipes get the vanilla base quads plus a mask overlay
// (tintIndex 0) whose colour comes from the PipeColourTintSource ItemColor (registered for every pipe
// item in BCTransportClient on 1.21.1). The transport variant draws on the translucent item sheet so
// the ~30%-alpha overlay tint blends instead of rendering opaque — the 1.21.1 ItemRenderer reads the
// tint alpha and renders the model once per render type, so one render type avoids double-draw.
// Geometry mirrors the >=1.21.10 ItemModel branch (and pipe_item.json) exactly. 1.21.1-only.
public class PipeItemModel implements net.neoforged.neoforge.client.model.IDynamicBakedModel {
    private final net.minecraft.client.resources.model.BakedModel vanillaDelegate;
    private final PipeDefinition definition;
    private final net.minecraft.client.renderer.block.model.ItemOverrides paintOverrides;

    public PipeItemModel(net.minecraft.client.resources.model.BakedModel vanillaDelegate, PipeDefinition definition) {
        this.vanillaDelegate = vanillaDelegate;
        this.definition = definition;
        this.paintOverrides = new PaintOverrides();
    }

    // Unpainted base rendering — delegate to the vanilla pipe item model.
    @Override
    public java.util.List<BakedQuad> getQuads(
            net.minecraft.world.level.block.state.BlockState state, Direction side,
            net.minecraft.util.RandomSource rand, net.neoforged.neoforge.client.model.data.ModelData data,
            net.minecraft.client.renderer.RenderType renderType) {
        return vanillaDelegate.getQuads(state, side, rand, data, renderType);
    }

    @Override public boolean useAmbientOcclusion() { return vanillaDelegate.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return vanillaDelegate.isGui3d(); }
    @Override public boolean usesBlockLight() { return vanillaDelegate.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
        return vanillaDelegate.getParticleIcon();
    }
    @Override public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
        return vanillaDelegate.getTransforms();
    }
    @Override public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
        return paintOverrides;
    }

    // ItemOverrides: pick a painted, per-colour model when the stack carries PIPE_COLOUR; otherwise the
    // base model (which delegates to vanilla). Painted models are cached per colour.
    private final class PaintOverrides extends net.minecraft.client.renderer.block.model.ItemOverrides {
        private final java.util.Map<DyeColor, net.minecraft.client.resources.model.BakedModel> cache =
                new java.util.EnumMap<>(DyeColor.class);

        @Override
        public net.minecraft.client.resources.model.BakedModel resolve(
                net.minecraft.client.resources.model.BakedModel model, ItemStack stack,
                net.minecraft.client.multiplayer.ClientLevel level,
                net.minecraft.world.entity.LivingEntity entity, int seed) {
            DyeColor colour = stack.get(BCTransportItems.PIPE_COLOUR.get());
            if (colour == null) {
                return model;
            }
            return cache.computeIfAbsent(colour, PipeItemModel.this::buildPaintedModel);
        }
    }

    private net.minecraft.client.resources.model.BakedModel buildPaintedModel(DyeColor colour) {
        if (definition.flowType == PipeApi.flowFluids) {
            // Fluid pipes: dye_replace sprite variant (colour baked into the texture), opaque cutout.
            TextureAtlasSprite[] dyedSprites = PipeBaseModelGenStandard.ensureDyedSprites(definition, colour);
            int itemTexIndex = definition.itemTextureTop;
            TextureAtlasSprite dyedSprite = itemTexIndex < dyedSprites.length ? dyedSprites[itemTexIndex] : dyedSprites[0];
            return new PaintedPipeModel(generatePipeQuads(dyedSprite),
                    net.minecraft.client.renderer.Sheets.cutoutBlockSheet());
        }

        // Transport/kinesis: vanilla base quads + mask overlay (tintIndex 0), all on the translucent sheet.
        java.util.List<BakedQuad> quads = new ArrayList<>();
        net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create(42L);
        net.neoforged.neoforge.client.model.data.ModelData empty = net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
        quads.addAll(vanillaDelegate.getQuads(null, null, rand, empty, null));
        for (Direction d : Direction.values()) {
            quads.addAll(vanillaDelegate.getQuads(null, d, rand, empty, null));
        }
        TextureAtlasSprite[] maskArray = PipeBaseModelGenStandard.ensureMaskSprites(definition);
        TextureAtlasSprite maskSprite = (maskArray != null && maskArray.length > 0) ? maskArray[0] : null;
        if (maskSprite != null && maskSprite != SpriteUtil.missingSprite()) {
            quads.addAll(generateOverlayQuads(maskSprite));
        }
        return new PaintedPipeModel(quads, buildcraft.lib.client.render.BCLibRenderTypes.translucentItemSheet());
    }

    // A fixed-quad BakedModel: serves the painted quads under the null side on a single render type, and
    // delegates transforms / particle / lighting to the vanilla pipe model. Its own overrides are EMPTY
    // (it is already the resolved result, so no further per-stack resolution).
    private final class PaintedPipeModel implements net.neoforged.neoforge.client.model.IDynamicBakedModel {
        private final java.util.List<BakedQuad> quads;
        private final java.util.List<net.minecraft.client.renderer.RenderType> renderTypes;

        PaintedPipeModel(java.util.List<BakedQuad> quads, net.minecraft.client.renderer.RenderType renderType) {
            this.quads = quads;
            this.renderTypes = java.util.List.of(renderType);
        }

        @Override
        public java.util.List<BakedQuad> getQuads(
                net.minecraft.world.level.block.state.BlockState state, Direction side,
                net.minecraft.util.RandomSource rand, net.neoforged.neoforge.client.model.data.ModelData data,
                net.minecraft.client.renderer.RenderType renderType) {
            return side == null ? quads : java.util.Collections.emptyList();
        }

        @Override
        public java.util.List<net.minecraft.client.renderer.RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return renderTypes;
        }

        @Override public boolean useAmbientOcclusion() { return vanillaDelegate.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return vanillaDelegate.isGui3d(); }
        @Override public boolean usesBlockLight() { return vanillaDelegate.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
            return vanillaDelegate.getParticleIcon();
        }
        @Override public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
            return vanillaDelegate.getTransforms();
        }
        @Override public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
            return net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY;
        }
    }

    // --- geometry (matches pipe_item.json's 3-cube model; identical to the >=1.21.10 branch) ---

    private static java.util.List<BakedQuad> generatePipeQuads(TextureAtlasSprite sprite) {
        java.util.List<BakedQuad> quads = new ArrayList<>();
        UvFaceData bottomSideUvs = UvFaceData.from16(4, 12, 12, 16);
        UvFaceData centerUvs     = UvFaceData.from16(4, 4, 12, 12);
        UvFaceData topSideUvs    = UvFaceData.from16(4, 0, 12, 4);
        UvFaceData capFaceUvs    = UvFaceData.from16(4, 4, 12, 12);
        addPipeFaces(quads, sprite, new Vector3f(0.5f, 0.125f, 0.5f), new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.DOWN }, capFaceUvs);
        addPipeFaces(quads, sprite, new Vector3f(0.5f, 0.125f, 0.5f), new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, bottomSideUvs);
        addPipeFaces(quads, sprite, new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f(0.25f, 0.25f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, centerUvs);
        addPipeFaces(quads, sprite, new Vector3f(0.5f, 0.875f, 0.5f), new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.UP }, capFaceUvs);
        addPipeFaces(quads, sprite, new Vector3f(0.5f, 0.875f, 0.5f), new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, topSideUvs);
        return quads;
    }

    private static void addPipeFaces(java.util.List<BakedQuad> quads, TextureAtlasSprite sprite,
            Vector3f center, Vector3f radius, Direction[] faces, UvFaceData uvs) {
        for (Direction face : faces) {
            MutableQuad quad = ModelUtil.createFace(face, center, radius, uvs);
            quad.setSprite(sprite);
            quad.texFromSprite(sprite);
            quads.add(quad.toBakedBlock());
        }
    }

    private static java.util.List<BakedQuad> generateOverlayQuads(TextureAtlasSprite maskSprite) {
        java.util.List<BakedQuad> quads = new ArrayList<>();
        UvFaceData bottomSideUvs = UvFaceData.from16(4, 12, 12, 16);
        UvFaceData centerUvs     = UvFaceData.from16(4, 4, 12, 12);
        UvFaceData topSideUvs    = UvFaceData.from16(4, 0, 12, 4);
        UvFaceData capFaceUvs    = UvFaceData.from16(4, 4, 12, 12);
        addOverlayFaces(quads, maskSprite, new Vector3f(0.5f, 0.125f, 0.5f), new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.DOWN }, capFaceUvs);
        addOverlayFaces(quads, maskSprite, new Vector3f(0.5f, 0.125f, 0.5f), new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, bottomSideUvs);
        addOverlayFaces(quads, maskSprite, new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f(0.25f, 0.25f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, centerUvs);
        addOverlayFaces(quads, maskSprite, new Vector3f(0.5f, 0.875f, 0.5f), new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.UP }, capFaceUvs);
        addOverlayFaces(quads, maskSprite, new Vector3f(0.5f, 0.875f, 0.5f), new Vector3f(0.25f, 0.125f, 0.25f),
                new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, topSideUvs);
        return quads;
    }

    private static void addOverlayFaces(java.util.List<BakedQuad> quads, TextureAtlasSprite sprite,
            Vector3f center, Vector3f radius, Direction[] faces, UvFaceData uvs) {
        for (Direction face : faces) {
            MutableQuad quad = ModelUtil.createFace(face, center, radius, uvs);
            quad.setSprite(sprite);
            quad.texFromSprite(sprite);
            quad.setTint(0);
            quads.add(quad.toBakedTranslucent());
        }
    }
}*/
//?}
