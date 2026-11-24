/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.model;

import java.util.ArrayList;
import java.util.List;

//? if >=26.1 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
//?} elif >=1.21.10 {
/*import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.resources.model.QuadCollection;*/
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;*/
//?}
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The single home for extracting {@code BakedQuad}s from a resolved block(-state) model across the render
 * cliff. Before this existed the same directive ladder was copy-pasted byte-for-byte into
 * {@link buildcraft.silicon.client.FacadeDeduplicator} and {@code PlugBakerFacade}; the modern-node half
 * also lived a third time (in a different, all-sides shape) in {@code PreviewBlockModelRenderer}. The
 * model APIs diverge by MC line, so keeping the fork in one place means one file to fix when the 26.x
 * pre-release model API churns again.
 *
 * <p>Three branches:
 * <ul>
 *   <li><b>&gt;=26.1</b> — {@code BlockStateModel.collectParts} → {@code SimpleModelWrapper.quads()} →
 *       {@code QuadCollection.getQuads(side)} (dispatch/geometry package split).</li>
 *   <li><b>&gt;=1.21.10</b> (1.21.10 / 1.21.11) — the same {@code collectParts} shape but the pre-26.1
 *       {@code BlockModelPart} / {@code SimpleModelWrapper} / {@code QuadCollection} types.</li>
 *   <li><b>&lt;1.21.10</b> (1.21.1) — the classic {@code BakedModel.getQuads(state, side, random)}. The
 *       {@code state} MUST be the real block state, never {@code null}: MULTIPART models evaluate their
 *       selectors against it and third-party {@code IDynamicBakedModel}s dereference it un-guarded (a
 *       null there was bug #24 — invisible mushroom facades + Modular Machinery Reborn log spam).</li>
 * </ul>
 *
 * <p><b>The {@link RandomSource} is owned by the caller, deliberately.</b> {@code collectParts} (and the
 * 1.21.1 {@code getQuads}) draw on it to pick weighted/rotated model variants, so the callers keep their
 * own instances (the facade dedup + facade baker use a fixed seed for a stable fingerprint; the preview
 * seeds 42L for flicker-free variant selection). Passing it in — rather than the helper holding a static
 * one — is what keeps every call site's quad set (and its order) byte-identical to the pre-hoist code; the
 * facade texture-fingerprint depends on that stability.
 */
public final class BlockModelQuadExtractor {

    private BlockModelQuadExtractor() {}

    /**
     * Extracts the {@code BakedQuad}s for a single face ({@code side}, or {@code null} for the
     * general/unculled quads) of {@code model}. On the modern nodes only {@code SimpleModelWrapper} parts
     * contribute (the shape the facade system bakes/compares); on 1.21.1 the real {@code state} is
     * forwarded into {@code BakedModel.getQuads} (see the class note).
     */
    //? if >=1.21.10 {
    public static List<BakedQuad> getQuadsFromModel(BlockState state, BlockStateModel model, Direction side, RandomSource random) {
    //?} else {
    /*public static List<BakedQuad> getQuadsFromModel(BlockState state, BakedModel model, Direction side, RandomSource random) {*/
    //?}
        return extract(state, model, side, random, null);
    }

    /**
     * {@link #getQuadsFromModel} narrowed to one chunk-layer group of the model: its translucent quads
     * ({@code translucent}), or every other quad (solid and cutout). The two groups partition the full set, so a
     * caller asking for both gets each quad once. The layer is the one the block's own model draws in, read where
     * each line keeps it:
     * <ul>
     *   <li><b>&gt;=26.1</b> — per quad, {@code BakedQuad.MaterialInfo.layer()} (vanilla derives it from the
     *       material's {@code force_translucent} flag or the sprite's own alpha).</li>
     *   <li><b>&gt;=1.21.10</b> — per part, NeoForge's {@code BlockModelPart.getRenderType(state)} (the part's own
     *       render type, else the block's registered chunk layer).</li>
     *   <li><b>&lt;1.21.10</b> — per render type of {@code BakedModel.getRenderTypes}, whose quads come from the
     *       render-type-aware {@code getQuads} (a model with one render type answers with all its quads).</li>
     * </ul>
     */
    //? if >=1.21.10 {
    public static List<BakedQuad> getLayerQuadsFromModel(BlockState state, BlockStateModel model, Direction side,
                                                         RandomSource random, boolean translucent) {
    //?} else {
    /*public static List<BakedQuad> getLayerQuadsFromModel(BlockState state, BakedModel model, Direction side,
                                                         RandomSource random, boolean translucent) {*/
    //?}
        return extract(state, model, side, random, translucent);
    }

    // The context-free collectParts / getQuads are deprecated in favour of NeoForge's level-aware overloads, but a
    // model is sampled here in the abstract (facade dedup, blueprint previews) — there is no level or position, and
    // 26.x ships no empty BlockAndTintGetter to stand in (the level-aware default just delegates back here anyway).
    /** {@code translucent} null takes every quad; otherwise only that layer group (see {@link #getLayerQuadsFromModel}). */
    @SuppressWarnings("deprecation")
    //? if >=1.21.10 {
    private static List<BakedQuad> extract(BlockState state, BlockStateModel model, Direction side, RandomSource random,
                                           Boolean translucent) {
    //?} else {
    /*private static List<BakedQuad> extract(BlockState state, BakedModel model, Direction side, RandomSource random,
                                           Boolean translucent) {*/
    //?}
        //? if >=26.1 {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        List<BakedQuad> result = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            if (part instanceof net.minecraft.client.resources.model.SimpleModelWrapper smw) {
                QuadCollection qc = smw.quads();
                if (translucent == null) {
                    result.addAll(qc.getQuads(side));
                    continue;
                }
                for (BakedQuad quad : qc.getQuads(side)) {
                    boolean quadTranslucent = quad.materialInfo().layer()
                        == net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT;
                    if (quadTranslucent == translucent) {
                        result.add(quad);
                    }
                }
            }
        }
        return result;
        //?} elif >=1.21.10 {
        /*List<BlockModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        List<BakedQuad> result = new ArrayList<>();
        for (BlockModelPart part : parts) {
            if (part instanceof net.minecraft.client.renderer.block.model.SimpleModelWrapper smw) {
                if (translucent != null && (buildcraft.platform.client.ClientPlatform.extensions().renderLayer(part, state)
                        == net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT) != translucent) {
                    continue;
                }
                QuadCollection qc = smw.quads();
                result.addAll(qc.getQuads(side));
            }
        }
        return result;*/
        //?} else {
        /*// 1.21.1: BakedModel.getQuads needs the REAL block state, not null. Vanilla SimpleBakedModel
        // ignores it, but MULTIPART models evaluate their selectors against it and third-party
        // IDynamicBakedModels may dereference it without a null-check (issue #24 — invisible mushroom-block
        // facades + Modular Machinery Reborn NPE log-spam once per face per hatch at every world join).
        if (translucent == null) {
            return model.getQuads(state, side, random);
        }
        return buildcraft.platform.client.ClientPlatform.extensions().layerQuads(model, state, side, random,
                translucent);*/
        //?}
    }

    /** Re-seeded on every probe, so the emptiness probe picks the same weighted variant each time (render thread). */
    private static final RandomSource EMPTY_PROBE_RANDOM = RandomSource.create(42L);

    /** One probe's answer and the model it was taken from: a reload swaps the model, which forces a new probe. */
    private record EmptyProbe(Object model, boolean empty) {}

    /** {@link #drawsNothingAsBlock} answers per state, so the per-frame callers resolve the model and nothing else.
     *  Render thread only, as both callers are. */
    private static final java.util.Map<BlockState, EmptyProbe> EMPTY_PROBES = new java.util.HashMap<>();

    /**
     * Whether drawing {@code state} from its block model shows nothing: an {@code INVISIBLE} render shape, or a
     * {@code MODEL} whose block model has no quads at all, on any face or in the general bucket, over every part (a
     * particle-only placeholder whose look is a block-entity renderer's: the pipe holder, the engines, the tube). The
     * place-task cube and the blueprint preview below 26.1 send such a block down the item path instead (#240); on
     * 26.x the preview builds the quad list itself and needs no probe, so that arm is here only to keep the answer
     * the same on every node. Every part counts, not only {@code SimpleModelWrapper}s, so a model baked to another
     * part type is never taken for empty. 1.21.1's {@code ENTITYBLOCK_ANIMATED} (chests, beds) is never empty:
     * {@code renderSingleBlock} draws it through the item renderer. The answer is cached per state for as long as
     * the state resolves to the same model instance.
     */
    @SuppressWarnings("deprecation") // context-free collectParts / getQuads: see getQuadsFromModel
    public static boolean drawsNothingAsBlock(BlockState state) {
        net.minecraft.world.level.block.RenderShape shape = state.getRenderShape();
        if (shape == net.minecraft.world.level.block.RenderShape.INVISIBLE) {
            return true;
        }
        if (shape != net.minecraft.world.level.block.RenderShape.MODEL) {
            return false;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        //? if >=26.1 {
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
        //?} elif >=1.21.10 {
        /*BlockStateModel model = mc.getBlockRenderer().getBlockModel(state);*/
        //?} else {
        /*BakedModel model = mc.getBlockRenderer().getBlockModel(state);*/
        //?}
        EmptyProbe probe = EMPTY_PROBES.get(state);
        if (probe == null || probe.model() != model) {
            probe = new EmptyProbe(model, hasNoQuads(state, model));
            EMPTY_PROBES.put(state, probe);
        }
        return probe.empty();
    }

    @SuppressWarnings("deprecation") // context-free collectParts / getQuads: see getQuadsFromModel
    //? if >=1.21.10 {
    private static boolean hasNoQuads(BlockState state, BlockStateModel model) {
    //?} else {
    /*private static boolean hasNoQuads(BlockState state, BakedModel model) {*/
    //?}
        RandomSource random = EMPTY_PROBE_RANDOM;
        random.setSeed(42L);
        //? if >=26.1 {
        return getAllQuads(model, random).isEmpty();
        //?} elif >=1.21.10 {
        /*List<BlockModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        for (BlockModelPart part : parts) {
            if (!part.getQuads(null).isEmpty()) {
                return false;
            }
            for (Direction dir : Direction.values()) {
                if (!part.getQuads(dir).isEmpty()) {
                    return false;
                }
            }
        }
        return true;*/
        //?} else {
        /*if (!model.getQuads(state, null, random).isEmpty()) {
            return false;
        }
        for (Direction dir : Direction.values()) {
            random.setSeed(42L);
            if (!model.getQuads(state, dir, random).isEmpty()) {
                return false;
            }
        }
        return true;*/
        //?}
    }

    //? if >=26.1 {
    /**
     * Extracts every quad of {@code model} (all six faces plus the general/unculled bucket) flattened into
     * one list, for whole-block rendering (the blueprint/architect cell preview). Unlike
     * {@link #getQuadsFromModel} this walks <i>all</i> {@code BlockStateModelPart}s via
     * {@code part.getQuads(dir)} — it must not filter to {@code SimpleModelWrapper}, or a block whose model
     * bakes to another part type would preview with missing geometry. 26.1+ only: below the cliff the
     * preview uses {@code BlockRenderDispatcher.renderSingleBlock} instead, so no quad list is built there.
     */
    @SuppressWarnings("deprecation") // context-free collectParts: see getQuadsFromModel
    public static List<BakedQuad> getAllQuads(BlockStateModel model, RandomSource random) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction dir : Direction.values()) {
                quads.addAll(part.getQuads(dir));
            }
            quads.addAll(part.getQuads(null));
        }
        return quads;
    }
    //?}
}
